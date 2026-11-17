package com.kingkang.app.device

/**
 * The small, honest description of device-side abilities handed to the model. Only
 * capabilities that are actually available are mentioned: an absent OTG adapter or an
 * unauthorized Shizuku says nothing at all.
 */
object DeviceCapabilities {

    const val CONTEXT_MARKER = "[KingKang 环境]"

    /** Changes whenever the usable capability set changes. */
    fun signature(): String {
        val otg = UsbMonitor.state.value
        val shizuku = ShizukuManager.state.value.let { it.authorized && it.connected }
        return "otg=${otg.interfaces.joinToString(",")}|eth=${otg.ethernet}|shizuku=$shizuku"
    }

    fun promptNote(): String? {
        val otgState = UsbMonitor.state.value
        val shizuku = ShizukuManager.state.value.let { it.authorized && it.connected }
        return buildNote(otgState.interfaces, otgState.ethernet, shizuku)
    }

    internal fun buildNote(
        otg: List<String>,
        ethernet: Boolean,
        shizuku: Boolean
    ): String? {
        if (otg.isEmpty() && !ethernet && !shizuku) return null
        return buildString {
            append(CONTEXT_MARKER).append('\n')
            append("当前设备能力（仅在需要访问 Android 设备本身时使用）：\n")
            if (otg.isNotEmpty()) {
                append(
                    "- OTG：系统已创建 USB 网络接口 ${otg.joinToString("、")}，" +
                        "Kali 可直接使用。\n"
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
