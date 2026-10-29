#!/bin/sh
# Builds the rename(2) compatibility shim.
#
# Android runs every app process under a seccomp filter that answers the legacy
# rename(2) syscall with ENOSYS, and the filter is inherited by everything PRoot
# starts. glibc's rename() uses that syscall, so npm (which moves cacache temp files
# into place with rename) fails on its first cache write. renameat(2) and renameat2(2)
# are allowed, so the shim re-implements rename() on top of them; the launcher
# preloads it into every guest process when the file exists.
#
# Called once per rootfs image, whenever the app is about to start a PRoot session.
set -u

DEST=/usr/local/lib/kingkang
SHIM=$DEST/libkkrename.so
SRC=/tmp/kingkang-rename.c

[ -s "$SHIM" ] && { echo "KK_COMPAT=ready"; exit 0; }

mkdir -p "$DEST" || { echo "KK_ERROR=COMPAT_FAILED"; exit 1; }
cat >"$SRC" <<'EOC'
#define _GNU_SOURCE
#include <fcntl.h>
#include <errno.h>
#include <unistd.h>
#include <sys/syscall.h>

/*
 * rename(2) is blocked with ENOSYS inside Android's app sandbox; renameat(2) and
 * renameat2(2) are not. Override rename() so glibc callers keep working.
 */
int rename(const char *oldpath, const char *newpath) {
    long r = syscall(SYS_renameat2, AT_FDCWD, oldpath, AT_FDCWD, newpath, 0);
    if (r == 0) return 0;
    if (errno == ENOSYS)
        r = syscall(SYS_renameat, AT_FDCWD, oldpath, AT_FDCWD, newpath);
    return r == 0 ? 0 : -1;
}
EOC

# gcc ships with the full Kali image; without it the guest keeps working, minus rename().
if gcc -O2 -shared -fPIC -o "$SHIM" "$SRC" >/dev/null 2>&1 && [ -s "$SHIM" ]; then
  rm -f "$SRC"
  echo "KK_COMPAT=ready"
else
  rm -f "$SHIM"
  echo "KK_ERROR=COMPAT_FAILED"
fi
