package com.kingkang.app.agent

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.kingkang.app.MainActivity
import com.kingkang.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps the local dsh bridge alive while the app is in the background. dsh itself only
 * listens on loopback inside Kali; this service owns no socket and performs no network
 * access on its own.
 */
class AgentService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val manager by lazy { getSystemService(NotificationManager::class.java) }

    override fun onCreate() {
        super.onCreate()
        AgentEngine.init(this)
        createChannel()
        startForeground(NOTIFICATION_ID, notification())
        // The notification is the app's face in the background; keep it in step with the
        // turn that is actually running instead of a static "connected" line.
        scope.launch {
            var lastText: String? = null
            AgentEngine.state.collect { state ->
                val text = statusText(state)
                if (text != lastText) {
                    lastText = text
                    manager?.notify(NOTIFICATION_ID, notification(state))
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            AgentEngine.stop()
            stopSelf()
            return START_NOT_STICKY
        }
        AgentEngine.connect()
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        AgentEngine.onServiceDestroyed()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun statusText(state: AgentState): String = when {
        state.connection == AgentConnection.Starting -> "正在启动本地 dsh…"
        state.connection == AgentConnection.Failed -> state.error ?: "未连接"
        state.running -> "正在执行：${state.activeSession?.displayTitle ?: "对话"}"
        state.connection == AgentConnection.Ready ->
            state.activeSession?.takeIf { !it.blank }?.displayTitle?.let { "后台运行 · $it" }
                ?: "后台保持连接"
        else -> "已停止"
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Agent",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "本地 dsh 对话桥接"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun notification(state: AgentState = AgentEngine.state.value): Notification {
        val open = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this,
            2,
            Intent(this, AgentService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_agent_notification)
            .setContentTitle("KingKang Agent")
            .setContentText(statusText(state))
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, "停止", stop)
            .build()
    }

    companion object {
        const val ACTION_START = "com.kingkang.app.agent.START"
        const val ACTION_STOP = "com.kingkang.app.agent.STOP"
        private const val CHANNEL_ID = "kingkang_agent"
        private const val NOTIFICATION_ID = 41
    }
}
