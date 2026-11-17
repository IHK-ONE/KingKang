package com.kingkang.app.agent

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.kingkang.app.MainActivity

/**
 * Keeps the process alive while Kali or dsh is being installed. Extraction can run for
 * many minutes and is not interruptible; without a foreground service Android may kill the
 * app once it is backgrounded. The notification is intentionally minimal — the detailed
 * progress stays in the settings card.
 */
class InstallService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val text = intent?.getStringExtra(EXTRA_TEXT) ?: "正在安装…"
        createChannel()
        startForeground(
            NOTIFICATION_ID,
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("KingKang")
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(
                    android.app.PendingIntent.getActivity(
                        this,
                        0,
                        Intent(this, MainActivity::class.java),
                        android.app.PendingIntent.FLAG_IMMUTABLE or
                            android.app.PendingIntent.FLAG_UPDATE_CURRENT
                    )
                )
                .build()
        )
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "安装", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Kali 与 dsh 安装期间保持任务继续运行"
                setShowBadge(false)
            }
        )
    }

    companion object {
        private const val CHANNEL_ID = "kingkang-install"
        private const val NOTIFICATION_ID = 2201
        private const val EXTRA_TEXT = "text"

        fun start(context: Context, text: String) {
            val intent = Intent(context, InstallService::class.java).putExtra(EXTRA_TEXT, text)
            runCatching { ContextCompat.startForegroundService(context, intent) }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, InstallService::class.java)) }
        }
    }
}
