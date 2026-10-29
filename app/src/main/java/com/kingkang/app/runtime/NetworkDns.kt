package com.kingkang.app.runtime

import android.content.Context
import android.net.ConnectivityManager
import android.util.Log
import java.io.File

/**
 * Android does not expose the system resolver through /etc/resolv.conf. Build a small
 * resolver file and bind it into the guest so apt and friends can resolve mirrors.
 */
object NetworkDns {
    private const val TAG = "NetworkDns"

    fun writeResolvConf(context: Context): File {
        val servers = linkedSetOf<String>()
        runCatching {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork
            val props = network?.let { cm.getLinkProperties(it) }
            props?.dnsServers?.forEach { address ->
                address.hostAddress?.let { servers.add(it.substringBefore('%')) }
            }
        }.onFailure { Log.w(TAG, "failed to read DNS servers", it) }

        if (servers.isEmpty()) {
            servers.add("1.1.1.1")
            servers.add("8.8.8.8")
        }

        val file = File(context.filesDir, "runtime/resolv.conf")
        file.parentFile?.mkdirs()
        file.writeText(servers.take(4).joinToString(separator = "\n", postfix = "\n") {
            "nameserver $it"
        })
        return file
    }
}
