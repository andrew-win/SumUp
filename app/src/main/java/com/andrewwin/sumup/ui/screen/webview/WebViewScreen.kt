package com.andrewwin.sumup.ui.screen.webview

import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceResponse
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream
import java.util.Locale
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebViewScreen(
    url: String,
    onNavigateBack: () -> Unit
) {
    var isLoading by remember { mutableStateOf(true) }
    var webView: WebView? by remember { mutableStateOf(null) }
    var title by remember { mutableStateOf("") }
    val cookieManager = remember { CookieManager.getInstance() }

    Scaffold(
        topBar = {
            TopAppBar(
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
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
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
                                settings.blockNetworkImage = true
                            }

                            override fun onPageCommitVisible(view: WebView?, url: String?) {
                                settings.blockNetworkImage = false
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                isLoading = false
                                title = view?.title ?: ""
                            }

                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                return false
                            }

                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): WebResourceResponse? {
                                if (shouldBlockRequest(request)) {
                                    return EMPTY_WEB_RESPONSE
                                }
                                return super.shouldInterceptRequest(view, request)
                            }
                        }
                        with(settings) {
                            javaScriptEnabled = true
                            domStorageEnabled = true

                            cacheMode = WebSettings.LOAD_DEFAULT
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

private fun shouldBlockRequest(request: WebResourceRequest?): Boolean {
    val host = request?.url?.host?.lowercase(Locale.ROOT) ?: return false
    return TRACKER_AND_AD_HOST_KEYWORDS.any { keyword -> host.contains(keyword) }
}

private val TRACKER_AND_AD_HOST_KEYWORDS = listOf(
    "doubleclick.net",
    "googlesyndication.com",
    "googleadservices.com",
    "adservice.google.com",
    "adnxs.com",
    "taboola.com",
    "outbrain.com",
    "criteo.com",
    "adsrvr.org",
    "scorecardresearch.com",
    "hotjar.com",
    "mixpanel.com",
    "segment.io",
    "facebook.net",
    "analytics",
    "tracker"
)

private val EMPTY_WEB_RESPONSE = WebResourceResponse(
    "text/plain",
    "utf-8",
    ByteArrayInputStream(ByteArray(0))
)







