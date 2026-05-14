package com.andrewwin.sumup.ui.screen.webview

import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.andrewwin.sumup.R
import com.andrewwin.sumup.ui.components.AppTopBar

@Composable
fun WebViewScreen(
    url: String,
    onNavigateBack: () -> Unit
) {
    var isLoading by remember { mutableStateOf(true) }
    var webView: WebView? by remember { mutableStateOf(null) }
    var title by remember { mutableStateOf("") }
    val cookieManager = remember { CookieManager.getInstance() }

    BackHandler {
        val currentWebView = webView
        if (currentWebView?.canGoBack() == true) {
            currentWebView.goBack()
        } else {
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = {
                    Column {
                        Text(
                            text = title.ifBlank { stringResource(R.string.nav_webview) },
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (url.isNotBlank()) {
                            Text(
                                text = url,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.Close, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = { webView?.reload() }) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        setLayerType(WebView.LAYER_TYPE_HARDWARE, null)
                        cookieManager.setAcceptCookie(true)
                        cookieManager.setAcceptThirdPartyCookies(this, true)
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                isLoading = true
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                isLoading = false
                                title = view?.title ?: ""
                            }

                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val nextUrl = request?.url?.toString().orEmpty()
                                if (nextUrl.isBlank()) return false

                                if (nextUrl.isWebUrl()) return false

                                val telegramWebUrl = nextUrl.toTelegramWebUrlOrNull()
                                if (telegramWebUrl != null) {
                                    view?.loadUrl(telegramWebUrl)
                                    return true
                                }

                                return runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(nextUrl)))
                                }.isSuccess
                            }
                        }
                        with(settings) {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true

                            cacheMode = WebSettings.LOAD_DEFAULT
                            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            setSupportZoom(false)
                            builtInZoomControls = false
                            displayZoomControls = false

                            loadsImagesAutomatically = true
                            blockNetworkImage = false
                            mediaPlaybackRequiresUserGesture = true

                            allowFileAccess = false
                            allowContentAccess = false
                            setGeolocationEnabled(false)
                            javaScriptCanOpenWindowsAutomatically = false
                            setSupportMultipleWindows(false)

                            offscreenPreRaster = false
                        }
                        loadUrl(url)
                        webView = this
                    }
                },
                update = {
                    webView = it
                }
            )

            if (isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

private fun String.isWebUrl(): Boolean {
    val scheme = runCatching { Uri.parse(this).scheme }.getOrNull()
    return scheme.equals("http", ignoreCase = true) || scheme.equals("https", ignoreCase = true)
}

private fun String.toTelegramWebUrlOrNull(): String? {
    val uri = runCatching { Uri.parse(this) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase() ?: return null
    if (scheme != "tg") return null

    val isResolveLink = uri.schemeSpecificPart.startsWith("resolve", ignoreCase = true) ||
        uri.host.equals("resolve", ignoreCase = true)
    if (!isResolveLink) return null

    val query = uri.query ?: uri.schemeSpecificPart.substringAfter("?", missingDelimiterValue = "")
    val queryUri = runCatching { Uri.parse("https://t.me/?$query") }.getOrNull() ?: return null
    val domain = queryUri.getQueryParameter("domain")?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val post = queryUri.getQueryParameter("post")?.trim().orEmpty()
    return if (post.isBlank()) {
        "https://t.me/s/$domain"
    } else {
        "https://t.me/s/$domain/$post"
    }
}
