package com.kingkang.app.agent

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.kingkang.app.MainActivity
import com.kingkang.app.R

/**
 * Background task notices live in one place so the settings switch, the permission card
 * and "send a test notification" all exercise the same code path the agent uses.
 *
 * The content is deliberately quiet and private: the public version on the lock screen
 * never names a conversation or a tool result.
 */
object TaskNotifications {

    const val CHANNEL_ID = "kingkang_tasks"
    private const val TEST_ID = 0x4b50

    /** Whether Android would actually show a notification right now. */
    fun permitted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        return manager.areNotificationsEnabled()
    }

    /** Creates the low-importance channel on first use; repeated calls are cheap. */
    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            manager.getNotificationChannel(CHANNEL_ID) != null
        ) {
            return
        }
        val channel = NotificationChannel(
            CHANNEL_ID,
            "后台任务",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "对话、定时任务与后台任务的完成提醒"
            setShowBadge(true)
        }
        runCatching { manager.createNotificationChannel(channel) }
    }

    /**
     * Posts one task notice. Returns false when the caller should keep the in-app surface
     * only (switch off, permission missing, or the system rejected the post).
     */
    fun post(
        context: Context,
        key: String,
        title: String,
        text: String,
        sessionId: String?,
        isError: Boolean
    ): Boolean {
        ensureChannel(context)
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            sessionId?.takeIf { it.isNotBlank() }?.let {
                putExtra(AgentEngine.EXTRA_SESSION_ID, it)
            }
        }
        val id = key.hashCode()
        val pending = PendingIntent.getActivity(
            context,
            id,
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_agent_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(
                if (isError) NotificationCompat.PRIORITY_DEFAULT else NotificationCompat.PRIORITY_LOW
            )
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_agent_notification)
                    .setContentTitle("KingKang")
                    .setContentText(if (isError) "有任务需要查看" else "有任务已完成")
                    .setContentIntent(pending)
                    .setAutoCancel(true)
                    .setSilent(true)
                    .build()
            )
            .build()
        return runCatching {
            manager.notify(id, notification)
            true
        }.getOrDefault(false)
    }

    /**
     * Posts a sample through the same builder. Returns null on success, or the reason the
     * user needs to fix first; the settings card shows that sentence verbatim.
     */
    fun sendTest(context: Context): String? {
        if (!permitted(context)) {
            return "系统通知权限未开启。请允许 KingKang 发送通知后再试。"
        }
        val posted = post(
            context = context,
            key = "test-notification",
            title = "KingKang 测试通知",
            text = "通知正常。后台任务完成后会以同样的方式提醒你。",
            sessionId = null,
            isError = false
        )
        return if (posted) null else "系统拒绝了这次通知，请在系统设置中检查 KingKang 的通知权限。"
    }

    internal fun testNotificationId(): Int = TEST_ID
}
