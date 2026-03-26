package com.andrewwin.sumup.data.remote

import kotlin.coroutines.resume

class HeadlessBrowserHtmlFetcher(
    private val appContext: android.content.Context
) {
    private var sharedWebView: android.webkit.WebView? = null
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    suspend fun fetchHtml(url: String): String? {
        return kotlinx.coroutines.withTimeoutOrNull(FETCH_TIMEOUT_MS) {
            kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
                mainHandler.post {
                    val webView = getOrCreateWebView()
                    var finished = false

                    fun finish(result: String?) {
                        if (finished) return
                        finished = true
                        runCatching { webView.stopLoading() }
                        webView.webViewClient = android.webkit.WebViewClient()
                        if (continuation.isActive) continuation.resume(result)
                    }

                    webView.webViewClient = object : android.webkit.WebViewClient() {
                        override fun onPageFinished(view: android.webkit.WebView?, pageUrl: String?) {
                            super.onPageFinished(view, pageUrl)
                            if (finished) return
                            mainHandler.postDelayed({
                                webView.evaluateJavascript(
                                    "(function(){return document.documentElement.outerHTML;})();"
                                ) { html ->
                                    finish(decodeEvaluateJavascriptString(html.orEmpty()))
                                }
                            }, AFTER_LOAD_DELAY_MS)
                        }

                        override fun onReceivedHttpError(
                            view: android.webkit.WebView?,
                            request: android.webkit.WebResourceRequest?,
                            errorResponse: android.webkit.WebResourceResponse?
                        ) {
                            if (request?.isForMainFrame == true && (errorResponse?.statusCode ?: 200) >= 400) {
                                finish(null)
                            }
                        }

                        override fun onReceivedError(
                            view: android.webkit.WebView?,
                            request: android.webkit.WebResourceRequest?,
                            error: android.webkit.WebResourceError?
                        ) {
                            if (request?.isForMainFrame == true) {
                                finish(null)
                            }
                        }
                    }

                    continuation.invokeOnCancellation {
                        mainHandler.post { finish(null) }
                    }

                    webView.loadUrl(url)
                }
            }
        }
    }

    private fun getOrCreateWebView(): android.webkit.WebView {
        return sharedWebView ?: android.webkit.WebView(appContext).also {
            configureWebView(it)
            sharedWebView = it
        }
    }

    @android.annotation.SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView(webView: android.webkit.WebView) {
        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
            loadsImagesAutomatically = false
            blockNetworkImage = true
            userAgentString = USER_AGENT_VALUE
        }
    }

    private fun decodeEvaluateJavascriptString(raw: String): String {
        if (raw.isBlank() || raw == "null") return ""
        return runCatching { org.json.JSONArray("[$raw]").getString(0) }.getOrElse {
            raw.trim('"')
                .replace("\\u003C", "<")
                .replace("\\u003E", ">")
                .replace("\\n", "\n")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
        }
    }

    private companion object {
        private const val USER_AGENT_VALUE =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
        private const val FETCH_TIMEOUT_MS = 20_000L
        private const val AFTER_LOAD_DELAY_MS = 1_000L
    }
}






