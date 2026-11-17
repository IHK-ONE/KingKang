package com.kingkang.app.device

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.kingkang.app.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Tracks whether Shizuku is installed, running and authorized, and owns the user service
 * that runs device commands with the shell UID. Nothing here is root: the service is the
 * same authority `adb shell` has.
 *
 * Binder probes run off the main thread: a starting or busy Shizuku service must never
 * delay the activity's first frames. Only the ServiceConnection bookkeeping stays on main.
 */
object ShizukuManager {

    data class State(
        val installed: Boolean = false,
        val running: Boolean = false,
        val authorized: Boolean = false,
        /** The user service is bound and can execute commands. */
        val connected: Boolean = false
    )

    private const val MANAGER_PACKAGE = "moe.shizuku.privileged.api"
    private const val REQUEST_CODE = 0x4b4b

    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshing = AtomicBoolean(false)
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var appContext: Context? = null
    private var args: Shizuku.UserServiceArgs? = null

    @Volatile
    private var bound = false

    @Volatile
    private var shell: IDeviceShell? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            shell = service?.let { IDeviceShell.Stub.asInterface(it) }
            _state.value = _state.value.copy(connected = shell != null)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            shell = null
            bound = false
            _state.value = _state.value.copy(connected = false)
        }
    }

    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        runCatching {
            Shizuku.addBinderReceivedListenerSticky { scheduleRefresh() }
            Shizuku.addBinderDeadListener {
                shell = null
                bound = false
                scheduleRefresh()
            }
            Shizuku.addRequestPermissionResultListener { _, _ -> scheduleRefresh() }
        }
        scheduleRefresh()
    }

    fun refresh() = scheduleRefresh()

    fun requestPermission() {
        if (!_state.value.running || _state.value.authorized) return
        runCatching { Shizuku.requestPermission(REQUEST_CODE) }
    }

    /** Launches the Shizuku manager so the user can start its service or grant access. */
    fun openManager(context: Context): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(MANAGER_PACKAGE)
            ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    fun exec(command: String, timeoutMs: Int = 60_000): String {
        if (!_state.value.authorized) throw IllegalStateException("Shizuku 未授权")
        val service = shell ?: throw IllegalStateException("Shizuku ADB 服务尚未连接")
        return service.exec(command, timeoutMs)
    }

    private fun scheduleRefresh() {
        if (!refreshing.compareAndSet(false, true)) return
        scope.launch {
            try {
                doRefresh()
            } finally {
                refreshing.set(false)
            }
        }
    }

    private fun doRefresh() {
        val context = appContext ?: return
        val installed = runCatching {
            context.packageManager.getPackageInfo(MANAGER_PACKAGE, 0)
            true
        }.getOrDefault(false)
        val running = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        val authorized = running && runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        _state.value = State(
            installed = installed,
            running = running,
            authorized = authorized,
            connected = authorized && _state.value.connected
        )
        main.post { if (_state.value.authorized) bind() else unbind() }
    }

    private fun bind() {
        if (bound) return
        val context = appContext ?: return
        val userService = args ?: Shizuku.UserServiceArgs(
            ComponentName(context.packageName, DeviceShellService::class.java.name)
        )
            .daemon(false)
            .processNameSuffix("kkadb")
            .tag("kk-adb")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)
            .also { args = it }
        runCatching {
            Shizuku.bindUserService(userService, connection)
            bound = true
        }
    }

    private fun unbind() {
        val userService = args
        if (!bound || userService == null) return
        runCatching { Shizuku.unbindUserService(userService, connection, true) }
        bound = false
        shell = null
        _state.value = _state.value.copy(connected = false)
    }
}
