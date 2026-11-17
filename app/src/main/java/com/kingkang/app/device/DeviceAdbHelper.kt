package com.kingkang.app.device

import android.content.Context
import java.io.File

/**
 * Installs the guest-side `kk-adb` helper into the Kali image. The helper is a plain
 * POSIX script that posts a command to the shared queue and waits for the app's reply, so
 * it keeps working across dsh/Kali updates without version-specific text.
 */
object DeviceAdbHelper {

    private const val SCRIPT = """#!/bin/sh
# kk-adb: run one Android device adb-shell command through KingKang + Shizuku.
DIR=/root/shared/.kk-device
mkdir -p "${'$'}DIR" 2>/dev/null || true
if [ "${'$'}#" -eq 0 ]; then
  echo "用法: kk-adb <命令>" >&2
  exit 2
fi
id="${'$'}${'$'}-${'$'}(date +%s)"
req="${'$'}DIR/req-${'$'}id"
res="${'$'}DIR/res-${'$'}id"
printf '%s' "${'$'}*" > "${'$'}req" || { echo "kk-adb: 无法写入请求队列" >&2; exit 1; }
i=0
while [ ! -f "${'$'}res" ]; do
  i=${'$'}((i + 1))
  if [ "${'$'}i" -ge 600 ]; then
    rm -f "${'$'}req"
    echo "kk-adb: 超时。请确认 KingKang 正在运行且 Shizuku 已授权。" >&2
    exit 1
  fi
  sleep 0.2
done
cat "${'$'}res"
rm -f "${'$'}req" "${'$'}res"
"""

    fun ensureInstalled(context: Context) {
        val rootfs = File(context.filesDir, "rootfs/kali")
        if (!File(rootfs, "etc/os-release").isFile) return
        // Both standard PATH locations: dsh's bash tool environment is not guaranteed
        // to include /usr/local/bin, while the interactive Kali shell usually does.
        listOf("usr/local/bin", "usr/bin").forEach { relative ->
            val bin = File(rootfs, relative).apply { mkdirs() }
            val target = File(bin, "kk-adb")
            val current = runCatching {
                if (target.isFile) target.readText() else null
            }.getOrNull()
            if (current != SCRIPT) {
                runCatching { target.writeText(SCRIPT) }
            }
            runCatching {
                target.setReadable(true, false)
                target.setExecutable(true, false)
            }
        }
    }
}
