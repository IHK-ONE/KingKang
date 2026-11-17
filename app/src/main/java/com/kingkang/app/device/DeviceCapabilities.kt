package com.kingkang.app.device

/**
 * The small, honest description of device-side abilities handed to the model. Only
 * capabilities that are actually available are mentioned: an absent OTG adapter or an
 * unauthorized Shizuku says nothing at all.
 */
object DeviceCapabilities {

    const val CONTEXT_MARKER = "[KingKang 环境]"

    fun promptNote(): String? {
        val otg = UsbMonitor.networkPresent.value
        val shizuku = ShizukuManager.state.value.authorized
        if (!otg && !shizuku) return null
        return buildString {
            append(CONTEXT_MARKER).append('\n')
            append("当前设备能力（仅在需要访问 Android 设备本身时使用）：\n")
            if (otg) {
                append("- OTG：Android 已识别到 USB 网络适配器。\n")
            }
            if (shizuku) {
                append(
                    "- Shizuku ADB：已授权。可在 Kali 中运行 `kk-adb <命令>` " +
                        "执行设备端 adb shell 命令（shell 权限，不是 root）。"
                )
            }
        }.trimEnd()
    }
}
