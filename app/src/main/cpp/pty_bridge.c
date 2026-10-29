/*
 * KingKang PTY bridge.
 *
 * Android's public Java API has no fork()/posix_openpt(), so the terminal needs a tiny
 * native layer. This file is deliberately dependency-free and does only four things:
 * allocate a PTY, start the runtime, resize it, and reap it.
 */

#define _GNU_SOURCE

#include <jni.h>

#include <errno.h>
#include <fcntl.h>
#include <signal.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <termios.h>
#include <unistd.h>

#ifndef TIOCSCTTY
#define TIOCSCTTY 0x540E
#endif
#ifndef TIOCSWINSZ
#define TIOCSWINSZ 0x5414
#endif

static void free_string_array(char **array, int count) {
    if (array == NULL) return;
    for (int i = 0; i < count; i++) {
        free(array[i]);
    }
    free(array);
}

static char **to_string_array(JNIEnv *env, jobjectArray source, int *out_count) {
    *out_count = 0;
    if (source == NULL) return NULL;

    jsize length = (*env)->GetArrayLength(env, source);
    char **result = (char **) calloc((size_t) length + 1, sizeof(char *));
    if (result == NULL) return NULL;

    for (jsize i = 0; i < length; i++) {
        jstring item = (jstring) (*env)->GetObjectArrayElement(env, source, i);
        if (item == NULL) {
            result[i] = strdup("");
        } else {
            const char *chars = (*env)->GetStringUTFChars(env, item, NULL);
            result[i] = chars == NULL ? NULL : strdup(chars);
            if (chars != NULL) (*env)->ReleaseStringUTFChars(env, item, chars);
            (*env)->DeleteLocalRef(env, item);
        }
        if (result[i] == NULL) {
            free_string_array(result, (int) i);
            return NULL;
        }
    }
    *out_count = (int) length;
    return result;
}

static int set_winsize(int fd, int rows, int cols) {
    struct winsize ws;
    memset(&ws, 0, sizeof(ws));
    ws.ws_row = (unsigned short) (rows > 0 ? rows : 24);
    ws.ws_col = (unsigned short) (cols > 0 ? cols : 80);
    return ioctl(fd, TIOCSWINSZ, &ws);
}

/*
 * Fallback path if posix_openpt() is not available in the NDK's bionic headers.
 * /dev/ptmx is the same device; this is what Termux relies on.
 */
static int open_master(char *slave_name, size_t slave_size) {
    int master = open("/dev/ptmx", O_RDWR | O_CLOEXEC);
    if (master < 0) return -1;

    if (grantpt(master) != 0 || unlockpt(master) != 0) {
        int saved = errno;
        close(master);
        errno = saved;
        return -1;
    }

    const char *name = ptsname(master);
    if (name == NULL) {
        int saved = errno;
        close(master);
        errno = saved;
        return -1;
    }
    if (strlen(name) + 1 > slave_size) {
        close(master);
        errno = ENAMETOOLONG;
        return -1;
    }
    strcpy(slave_name, name);
    return master;
}

static jlongArray error_result(JNIEnv *env, int error_code) {
    jlong values[2];
    values[0] = -(jlong) (error_code > 0 ? error_code : EIO);
    values[1] = 0;
    jlongArray result = (*env)->NewLongArray(env, 2);
    if (result != NULL) {
        (*env)->SetLongArrayRegion(env, result, 0, 2, values);
    }
    return result;
}

JNIEXPORT jlongArray JNICALL
Java_com_kingkang_app_terminal_PtyBridge_create(
        JNIEnv *env,
        jobject thiz,
        jstring executable,
        jobjectArray argv_array,
        jobjectArray env_array,
        jstring cwd,
        jint rows,
        jint cols) {
    (void) thiz;

    if (executable == NULL) return error_result(env, EINVAL);

    const char *exec_chars = (*env)->GetStringUTFChars(env, executable, NULL);
    if (exec_chars == NULL) return error_result(env, ENOMEM);
    char *exec_path = strdup(exec_chars);
    (*env)->ReleaseStringUTFChars(env, executable, exec_chars);
    if (exec_path == NULL) return error_result(env, ENOMEM);

    char *working_dir = NULL;
    if (cwd != NULL) {
        const char *cwd_chars = (*env)->GetStringUTFChars(env, cwd, NULL);
        if (cwd_chars != NULL) {
            working_dir = strdup(cwd_chars);
            (*env)->ReleaseStringUTFChars(env, cwd, cwd_chars);
        }
    }

    int argc = 0;
    int envc = 0;
    char **argv = to_string_array(env, argv_array, &argc);
    char **envp = to_string_array(env, env_array, &envc);
    if (argv == NULL || argc == 0 || envp == NULL) {
        free(exec_path);
        free(working_dir);
        free_string_array(argv, argc);
        free_string_array(envp, envc);
        return error_result(env, ENOMEM);
    }

    char slave_name[128];
    int master = open_master(slave_name, sizeof(slave_name));
    if (master < 0) {
        int saved = errno;
        free(exec_path);
        free(working_dir);
        free_string_array(argv, argc);
        free_string_array(envp, envc);
        return error_result(env, saved);
    }

    if (set_winsize(master, rows, cols) != 0) {
        /* A bad initial size is recoverable; the app will resize when xterm fits. */
    }

    int slave = open(slave_name, O_RDWR | O_CLOEXEC);
    if (slave < 0) {
        int saved = errno;
        close(master);
        free(exec_path);
        free(working_dir);
        free_string_array(argv, argc);
        free_string_array(envp, envc);
        return error_result(env, saved);
    }

    pid_t pid = fork();
    if (pid < 0) {
        int saved = errno;
        close(master);
        close(slave);
        free(exec_path);
        free(working_dir);
        free_string_array(argv, argc);
        free_string_array(envp, envc);
        return error_result(env, saved);
    }

    if (pid == 0) {
        /* Child: only async-signal-safe calls after fork. */
        close(master);
        setsid();

        /* Become the session leader and attach the PTY as the controlling terminal. */
        ioctl(slave, TIOCSCTTY, 0);

        if (dup2(slave, STDIN_FILENO) < 0 ||
            dup2(slave, STDOUT_FILENO) < 0 ||
            dup2(slave, STDERR_FILENO) < 0) {
            _exit(127);
        }
        if (slave > STDERR_FILENO) close(slave);

        if (working_dir != NULL && working_dir[0] != '\0') {
            if (chdir(working_dir) != 0) chdir("/");
        } else {
            chdir("/");
        }

        /* TERM is supplied through envp by the Kotlin side; setenv() is not
         * async-signal-safe and can deadlock after fork in a multi-threaded app. */
        execve(exec_path, argv, envp);

        /* exec failed: put a useful message on the PTY before exiting. */
        const char *prefix = "kingkang: exec failed: ";
        const char *reason = strerror(errno);
        write(STDERR_FILENO, prefix, strlen(prefix));
        write(STDERR_FILENO, exec_path, strlen(exec_path));
        write(STDERR_FILENO, ": ", 2);
        write(STDERR_FILENO, reason, strlen(reason));
        write(STDERR_FILENO, "\r\n", 2);
        _exit(127);
    }

    /* Parent. */
    close(slave);
    free(exec_path);
    free(working_dir);
    free_string_array(argv, argc);
    free_string_array(envp, envc);

    jlong values[2];
    values[0] = (jlong) master;
    values[1] = (jlong) pid;
    jlongArray result = (*env)->NewLongArray(env, 2);
    if (result == NULL) {
        close(master);
        kill(pid, SIGKILL);
        waitpid(pid, NULL, 0);
        return error_result(env, ENOMEM);
    }
    (*env)->SetLongArrayRegion(env, result, 0, 2, values);
    return result;
}

JNIEXPORT jint JNICALL
Java_com_kingkang_app_terminal_PtyBridge_resize(
        JNIEnv *env, jobject thiz, jint fd, jint rows, jint cols) {
    (void) env;
    (void) thiz;
    return set_winsize(fd, rows, cols);
}

JNIEXPORT jint JNICALL
Java_com_kingkang_app_terminal_PtyBridge_kill(
        JNIEnv *env, jobject thiz, jint pid, jint signal_number) {
    (void) env;
    (void) thiz;
    if (pid <= 0) return -1;

    /*
     * The child called setsid(), so pid is also its process-group id. Killing the group
     * takes down helpers (proot's loader, su's shell) instead of orphaning them.
     */
    int result = kill(-pid, signal_number);
    if (result != 0 && errno == ESRCH) {
        result = kill(pid, signal_number);
    }
    return result;
}

JNIEXPORT jint JNICALL
Java_com_kingkang_app_terminal_PtyBridge_waitFor(
        JNIEnv *env, jobject thiz, jint pid) {
    (void) env;
    (void) thiz;
    if (pid <= 0) return -1;

    int status = 0;
    pid_t result;
    do {
        result = waitpid(pid, &status, 0);
    } while (result < 0 && errno == EINTR);

    if (result < 0) return -1;
    if (WIFEXITED(status)) return WEXITSTATUS(status);
    if (WIFSIGNALED(status)) return 128 + WTERMSIG(status);
    return -1;
}
