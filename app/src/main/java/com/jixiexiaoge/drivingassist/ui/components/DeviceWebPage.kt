package com.jixiexiaoge.drivingassist.ui.components

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

/**
 * 设备 Web 控制页 — 内嵌 WebView 加载设备端 7000 网页服务。
 *
 * 完整显示/操作设备 Web 管理页（设置、参数、终端、dashcam 等），
 * 替代原先跳转外部浏览器打开 http://<ip>:7000 的方式。
 */
@Composable
fun DeviceWebPage(
    url: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // 记住 WebView 实例，避免重组时重建
    val webView = remember {
        WebView(context).apply {
            @SuppressLint("SetJavaScriptEnabled")
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
        }
    }

    // 进入页面加载 7000 网页；离开时销毁 WebView 防泄漏
    DisposableEffect(url) {
        if (url.isNotBlank()) {
            webView.loadUrl(url)
        }
        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
    }

    // 返回键：WebView 内有历史则回退一页，否则返回主页
    BackHandler {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            onBack()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { webView },
            modifier = Modifier.fillMaxSize()
        )
    }
}
