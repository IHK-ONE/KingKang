package com.kingkang.app.device

/**
 * The small, honest description of device-side abilities handed to the model. Only
 * capabilities that are actually available are mentioned: an absent OTG adapter or an
 * unauthorized Shizuku says nothing at all.
 *
 * PRoot runs as an ordinary Android app, so the kernel refuses raw sockets, AF_PACKET and
 * nl80211 even though the Kali image ships nmap/tcpdump/iw. Saying so up front keeps the
 * agent from burning turns on tools that cannot work in this mode.
 */
object DeviceCapabilities {

    const val CONTEXT_MARKER = "[KingKang 环境]"

    /** Changes whenever the usable capability set (or the runtime mode) changes. */
    fun signature(limitedNetwork: Boolean): String {
        val otg = UsbMonitor.state.value
        val shizuku = ShizukuManager.state.value.let { it.authorized && it.connected }
        return "otg=${otg.interfaces.joinToString(",")}|eth=${otg.ethernet}|" +
            "shizuku=$shizuku|limited=$limitedNetwork"
    }

    fun promptNote(limitedNetwork: Boolean): String? {
        val otgState = UsbMonitor.state.value
        val shizuku = ShizukuManager.state.value.let { it.authorized && it.connected }
        return buildNote(otgState.interfaces, otgState.ethernet, shizuku, limitedNetwork)
    }

    internal fun buildNote(
        otg: List<String>,
        ethernet: Boolean,
        shizuku: Boolean,
        limitedNetwork: Boolean = false
    ): String? {
        if (otg.isEmpty() && !ethernet && !shizuku && !limitedNetwork) return null
        return buildString {
            append(CONTEXT_MARKER).append('\n')
            if (limitedNetwork) {
                append("Kali 运行在 PRoot（非 root）：\n")
                append(
                    "- 普通 TCP/UDP、HTTP、DNS 可用；Android 禁止原始套接字与抓包，" +
                        "nmap 常因路由表不可见而失败，tcpdump、airmon-ng/monitor 不可用。\n"
                )
                append(
                    "- 端口与服务探测优先用 nc、curl、bash /dev/tcp 或 python socket，" +
                        "不要反复尝试 nmap/tcpdump。\n"
                )
            }
            if (otg.isNotEmpty()) {
                append(
                    "- OTG：系统已创建 USB 网络接口 ${otg.joinToString("、")}。" +
                        if (limitedNetwork) {
                            "接口可用，但上面的 PRoot 网络限制同样适用。\n"
                        } else {
                            "Kali 可直接使用。\n"
                        }
                )
            } else if (ethernet) {
                append("- OTG/以太网：Android 已建立以太网连接，Kali 可使用该网络。\n")
            }
            if (shizuku) {
                append(
                    "- Shizuku ADB：已授权。可在 Kali 中运行 `/usr/local/bin/kk-adb <命令>` " +
                        "执行设备端 adb shell 命令（shell 权限，不是 root）。"
                )
            }
        }.trimEnd()
    }
}
