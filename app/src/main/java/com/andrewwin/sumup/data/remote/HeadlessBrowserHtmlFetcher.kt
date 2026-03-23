package com.andrewwin.sumup.data.remote

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class HeadlessBrowserHtmlFetcher(
    private val appContext: Context
) {
    suspend fun fetchHtml(url: String): String? {
        return withTimeoutOrNull(FETCH_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                val handler = Handler(Looper.getMainLooper())
                handler.post {
                    var finished = false
                    val webView = WebView(appContext)

                    fun finish(result: String?) {
                        if (finished) return
                        finished = true
                        runCatching { webView.stopLoading() }
                        runCatching { webView.destroy() }
                        if (continuation.isActive) continuation.resume(result)
                    }

                    @SuppressLint("SetJavaScriptEnabled")
                    fun configure() {
                        val settings = webView.settings
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.cacheMode = WebSettings.LOAD_NO_CACHE
                        settings.loadsImagesAutomatically = false
                        settings.blockNetworkImage = true
                        settings.userAgentString = USER_AGENT_VALUE
                    }

                    configure()
                    webView.webChromeClient = WebChromeClient()
                    webView.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, pageUrl: String?) {
                            super.onPageFinished(view, pageUrl)
                            if (finished) return
                            Log.d(TAG_HEADLESS_BROWSER, "page_finished: url=$pageUrl")
                            handler.postDelayed({
                                webView.evaluateJavascript(
                                    "(function(){return document.documentElement.outerHTML;})();"
                                ) { html ->
                                    val raw = html.orEmpty()
                                    val decoded = decodeEvaluateJavascriptString(raw)
                                    Log.d(
                                        TAG_HEADLESS_BROWSER,
                                        "html_eval: rawLen=${raw.length}, decodedLen=${decoded.length}, rawHasUnicodeEscapes=${raw.contains("\\u003C")}, decodedHasHtmlTag=${decoded.contains("<html", ignoreCase = true)}, decodedHasAnchor=${decoded.contains("<a", ignoreCase = true)}"
                                    )
                                    Log.d(
                                        TAG_HEADLESS_BROWSER,
                                        "html_preview: ${decoded.take(HTML_PREVIEW_LEN).replace('\n', ' ')}"
                                    )
                                    finish(decoded)
                                }
                            }, AFTER_LOAD_DELAY_MS)
                        }

                        override fun onReceivedHttpError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            errorResponse: WebResourceResponse?
                        ) {
                            super.onReceivedHttpError(view, request, errorResponse)
                            if (request?.isForMainFrame == true && (errorResponse?.statusCode ?: 200) >= 400) {
                                finish(null)
                            }
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: android.webkit.WebResourceError?
                        ) {
                            super.onReceivedError(view, request, error)
                            if (request?.isForMainFrame == true) {
                                finish(null)
                            }
                        }
                    }

                    continuation.invokeOnCancellation {
                        handler.post { finish(null) }
                    }

                    webView.loadUrl(url)
                }
            }
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
        private const val TAG_HEADLESS_BROWSER = "HeadlessBrowser"
        private const val HTML_PREVIEW_LEN = 300
    }
}
