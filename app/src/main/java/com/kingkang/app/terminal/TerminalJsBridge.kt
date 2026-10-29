package com.kingkang.app.terminal

import android.webkit.JavascriptInterface

class TerminalJsBridge {
    var onInput: (String) -> Unit = {}
    var onResize: (Int, Int) -> Unit = { _, _ -> }
    var onReady: () -> Unit = {}

    @JavascriptInterface
    fun send(data: String) {
        onInput(data)
    }

    @JavascriptInterface
    fun resize(cols: Int, rows: Int) {
        onResize(cols, rows)
    }

    @JavascriptInterface
    fun ready() {
        onReady()
    }
}
