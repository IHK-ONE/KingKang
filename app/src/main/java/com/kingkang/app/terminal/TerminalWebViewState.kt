package com.kingkang.app.terminal

import android.content.Context
import android.util.Base64
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView
import java.util.ArrayDeque

class TerminalWebViewState {
    internal var webView: WebView? = null
        private set
    private var ready = false
    private val pending = ArrayDeque<ByteArray>()

    internal fun attach(view: WebView) {
        webView = view
    }

    internal fun ready() {
        ready = true
        val chunks = synchronized(pending) {
            val copy = pending.toList()
            pending.clear()
            copy
        }
        chunks.forEach(::write)
    }

    fun write(bytes: ByteArray) {
        val view = webView
        if (view == null || !ready) {
            synchronized(pending) {
                pending.addLast(bytes)
                while (pending.size > 256) pending.removeFirst()
            }
            return
        }
        val payload = Base64.encodeToString(bytes, Base64.NO_WRAP)
        view.post {
            runCatching {
                view.evaluateJavascript("window.kingkangWriteB64('$payload')", null)
            }
        }
    }

    fun focus() {
        webView?.requestFocus()
        webView?.post {
            runCatching { webView?.evaluateJavascript("window.kingkangFocus && window.kingkangFocus()", null) }
        }
    }

    fun showKeyboard() {
        val view = webView ?: return
        view.requestFocus()
        focus()
        view.post {
            val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    fun hideKeyboard() {
        val view = webView ?: return
        val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(view.windowToken, 0)
    }

    fun fit() {
        webView?.post {
            runCatching { webView?.evaluateJavascript("window.kingkangFit && window.kingkangFit()", null) }
        }
    }

    fun reset() {
        synchronized(pending) { pending.clear() }
        webView?.post {
            runCatching { webView?.evaluateJavascript("window.kingkangReset && window.kingkangReset()", null) }
        }
    }

    fun destroy() {
        webView?.destroy()
        webView = null
        ready = false
        synchronized(pending) { pending.clear() }
    }
}
