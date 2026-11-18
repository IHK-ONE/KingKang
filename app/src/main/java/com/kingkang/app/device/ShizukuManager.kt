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
import com.kingkang.app.data.SettingsStore
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
        val connected: Boolean = false,
        /** The user turned the capability off in KingKang; the Shizuku grant may still exist. */
        val disabled: Boolean = false
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
        val app = context.applicationContext
        appContext = app
        _state.value = _state.value.copy(
            disabled = SettingsStore(app).adbCapabilityDisabled
        )
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
        if (_state.value.disabled) return
        if (!_state.value.running || _state.value.authorized) return
        runCatching { Shizuku.requestPermission(REQUEST_CODE) }
    }

    /**
     * Turns the capability off (or back on) inside KingKang. Off unbinds the user service
     * immediately; the Shizuku grant itself can only be revoked in Shizuku's own manager,
     * which [openManager] opens. On re-enable the service is bound again if authorized.
     */
    fun setDisabled(disabled: Boolean) {
        val context = appContext ?: return
        SettingsStore(context).adbCapabilityDisabled = disabled
        if (disabled) unbind()
        _state.value = _state.value.copy(disabled = disabled)
        if (disabled) {
            _state.value = _state.value.copy(connected = false, authorized = false)
        } else {
            scheduleRefresh()
        }
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
        if (_state.value.disabled) {
            throw IllegalStateException("设备端 ADB 能力已在 KingKang 中停用")
        }
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
        val disabled = _state.value.disabled
        val granted = running && runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        val authorized = granted && !disabled
        _state.value = State(
            installed = installed,
            running = running,
            authorized = authorized,
            connected = authorized && _state.value.connected,
            disabled = disabled
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
