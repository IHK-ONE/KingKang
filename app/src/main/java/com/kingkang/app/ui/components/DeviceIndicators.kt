package com.kingkang.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kingkang.app.device.ShizukuManager
import com.kingkang.app.device.UsbMonitor
import com.kingkang.app.ui.theme.Amber
import com.kingkang.app.ui.theme.BorderStrong
import com.kingkang.app.ui.theme.Success
import com.kingkang.app.ui.theme.TextMuted
import com.kingkang.app.ui.theme.TextSecondary

/**
 * The two device lamps: OTG network adapter and Shizuku ADB. A green dot means the
 * capability is usable right now; grey means it is not, with a short reason next to it.
 * Tapping opens the device-capability settings when a callback is supplied.
 */
@Composable
fun DeviceIndicators(
    modifier: Modifier = Modifier,
    onOpenSettings: (() -> Unit)? = null
) {
    val otg by UsbMonitor.networkPresent.collectAsState()
    val shizuku by ShizukuManager.state.collectAsState()
    val shizukuText = when {
        shizuku.authorized -> "已授权"
        !shizuku.installed -> "未安装"
        shizuku.running -> "未授权"
        else -> "未运行"
    }
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (onOpenSettings != null) Modifier.clickable(onClick = onOpenSettings) else Modifier
            )
            .padding(horizontal = 2.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        DeviceLamp("OTG", if (otg) "已接入" else "未接入", otg)
        DeviceLamp(
            "Shizuku",
            shizukuText,
            shizuku.authorized,
            attention = shizuku.running && !shizuku.authorized
        )
    }
}

@Composable
private fun DeviceLamp(
    name: String,
    status: String,
    active: Boolean,
    attention: Boolean = false
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(
                    when {
                        active -> Success
                        attention -> Amber
                        else -> BorderStrong
                    }
                )
        )
        Spacer(Modifier.width(5.dp))
        Text(name, color = TextSecondary, fontSize = 10.sp)
        Spacer(Modifier.width(4.dp))
        Text(
            status,
            color = when {
                active -> Success
                attention -> Amber
                else -> TextMuted
            },
            fontSize = 9.5.sp
        )
    }
}
