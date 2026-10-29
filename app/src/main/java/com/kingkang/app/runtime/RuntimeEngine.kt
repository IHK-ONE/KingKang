package com.kingkang.app.runtime

import java.io.File

/** Guest path of the rename(2) shim built by assets/harness/compat.sh. */
const val RENAME_SHIM_GUEST_PATH = "/usr/local/lib/kingkang/libkkrename.so"

data class CommandSpec(
    val executable: String,
    val argv: List<String>,
    val env: Map<String, String>,
    val cwd: String? = null
) {
    fun envArray(): Array<String> = env.map { (key, value) -> "$key=$value" }.toTypedArray()
}

interface RuntimeEngine {
    val id: String
    val label: String

    /**
     * @param scriptPath guest-side path of a one-shot script. When null the engine starts an
     * interactive login shell; otherwise it runs the script and exits.
     */
    fun buildCommand(
        rootfs: File,
        paths: RuntimePaths,
        resolvConf: File,
        sharedDir: File,
        cacheDir: File,
        scriptPath: String? = null
    ): CommandSpec

    /**
     * Runs one guest executable with an argv directly, without a shell. Used by the agent
     * bridge, whose stdin/stdout carry NDJSON and must not be merged with a login shell's
     * startup output.
     */
    fun buildExec(
        rootfs: File,
        paths: RuntimePaths,
        resolvConf: File,
        sharedDir: File,
        cacheDir: File,
        executable: String,
        args: List<String> = emptyList(),
        extraEnv: Map<String, String> = emptyMap()
    ): CommandSpec
}

class ProotEngine : RuntimeEngine {
    override val id: String = "proot"
    override val label: String = "PRoot · 无 root"

    /** Host-side file of the rename(2) shim that assets/harness/compat.sh builds. */
    fun shimFile(rootfs: File): File =
        File(rootfs, RENAME_SHIM_GUEST_PATH.removePrefix("/"))

    override fun buildCommand(
        rootfs: File,
        paths: RuntimePaths,
        resolvConf: File,
        sharedDir: File,
        cacheDir: File,
        scriptPath: String?
    ): CommandSpec {
        val tail = if (scriptPath == null) {
            listOf("/bin/bash", "-l")
        } else {
            listOf("/bin/bash", scriptPath)
        }
        return build(rootfs, paths, resolvConf, sharedDir, cacheDir, tail, emptyMap())
    }

    override fun buildExec(
        rootfs: File,
        paths: RuntimePaths,
        resolvConf: File,
        sharedDir: File,
        cacheDir: File,
        executable: String,
        args: List<String>,
        extraEnv: Map<String, String>
    ): CommandSpec {
        val guestEnv = extraEnv.map { (key, value) -> "$key=$value" }
        return build(
            rootfs,
            paths,
            resolvConf,
            sharedDir,
            cacheDir,
            guestEnv + listOf(executable) + args,
            emptyMap()
        )
    }

    private fun build(
        rootfs: File,
        paths: RuntimePaths,
        resolvConf: File,
        sharedDir: File,
        cacheDir: File,
        tail: List<String>,
        extraEnv: Map<String, String>
    ): CommandSpec {
        val prootTmp = File(cacheDir, "proot").apply { mkdirs() }
        val env = mapOf(
            "LD_LIBRARY_PATH" to paths.dir.absolutePath,
            "PROOT_TMP_DIR" to prootTmp.absolutePath,
            "PROOT_LOADER" to paths.loader.absolutePath,
            "PROOT_LOADER_32" to paths.loader32.absolutePath,
            "PROOT_NO_SECCOMP" to "1"
        )

        val args = mutableListOf(
            "proot",
            "--link2symlink",
            "--kill-on-exit",
            "-0",
            "-r", rootfs.absolutePath,
            "-b", "/dev",
            "-b", "/proc",
            "-b", "/sys",
            "-b", "${resolvConf.absolutePath}:/etc/resolv.conf",
            "-b", "${sharedDir.absolutePath}:/mnt/sdcard",
            "-b", "${sharedDir.absolutePath}:/root/shared",
            "-w", "/root",
            "/usr/bin/env", "-i",
            "HOME=/root",
            "TERM=xterm-256color",
            "LANG=C.UTF-8",
            "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
        )
        // Android's app sandbox rejects rename(2) with ENOSYS; preload the shim that
        // rewrites it onto renameat2(2) so npm and ordinary tools can move files.
        if (shimFile(rootfs).length() > 0) {
            args += "LD_PRELOAD=$RENAME_SHIM_GUEST_PATH"
        }
        args += extraEnv.map { (key, value) -> "$key=$value" }
        args += tail

        return CommandSpec(
            executable = paths.proot.absolutePath,
            argv = args,
            env = env,
            cwd = null
        )
    }
}

class ChrootEngine(private val suPath: String) : RuntimeEngine {
    override val id: String = "chroot"
    override val label: String = "ROOT · chroot"

    override fun buildCommand(
        rootfs: File,
        paths: RuntimePaths,
        resolvConf: File,
        sharedDir: File,
        cacheDir: File,
        scriptPath: String?
    ): CommandSpec {
        val shell = if (scriptPath == null) {
            "/bin/bash -l"
        } else {
            "/bin/bash ${shellQuote(scriptPath)}"
        }
        return build(rootfs, resolvConf, shell, emptyMap())
    }

    override fun buildExec(
        rootfs: File,
        paths: RuntimePaths,
        resolvConf: File,
        sharedDir: File,
        cacheDir: File,
        executable: String,
        args: List<String>,
        extraEnv: Map<String, String>
    ): CommandSpec {
        val command = (listOf(executable) + args).joinToString(" ") { shellQuote(it) }
        return build(rootfs, resolvConf, command, extraEnv)
    }

    private fun build(
        rootfs: File,
        resolvConf: File,
        inner: String,
        extraEnv: Map<String, String>
    ): CommandSpec {
        val root = shellQuote(rootfs.absolutePath)
        val resolv = shellQuote(resolvConf.absolutePath)
        val guestEnv = (mapOf(
            "HOME" to "/root",
            "TERM" to "xterm-256color",
            "LANG" to "C.UTF-8",
            "PATH" to "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
        ) + extraEnv).entries.joinToString(" ") { (key, value) ->
            shellQuote("$key=$value")
        }
        val script = """
            ROOT=$root
            mkdir -p "${'$'}ROOT/proc" "${'$'}ROOT/dev" "${'$'}ROOT/sys"
            mount -t proc proc "${'$'}ROOT/proc" 2>/dev/null || true
            mount -o bind /dev "${'$'}ROOT/dev" 2>/dev/null || mount --bind /dev "${'$'}ROOT/dev" 2>/dev/null || true
            mount -o bind /sys "${'$'}ROOT/sys" 2>/dev/null || mount --bind /sys "${'$'}ROOT/sys" 2>/dev/null || true
            trap 'umount "${'$'}ROOT/proc" 2>/dev/null; umount "${'$'}ROOT/dev" 2>/dev/null; umount "${'$'}ROOT/sys" 2>/dev/null' EXIT
            cp $resolv "${'$'}ROOT/etc/resolv.conf" 2>/dev/null || true
            chroot "${'$'}ROOT" /usr/bin/env -i $guestEnv $inner
        """.trimIndent()

        return CommandSpec(
            executable = suPath,
            argv = listOf("su", "-c", script),
            env = emptyMap(),
            cwd = null
        )
    }

    private fun shellQuote(value: String): String =
        "'" + value.replace("'", "'\\''") + "'"
}
