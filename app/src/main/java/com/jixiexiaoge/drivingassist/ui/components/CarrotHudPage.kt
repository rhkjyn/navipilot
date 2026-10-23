package com.jixiexiaoge.drivingassist.ui.components

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Carrot HUD 页（WebView 版）— 加载设备端 7000 网页的"纯 HUD 无视频模式"。
 *
 * URL: http://<ip>:7000/?hud_only=1
 * web 端该模式：不连摄像头视频流，只收 /ws/compact_state 数据，
 * 在深色舞台上显示与网页 Carrot Vision 完全一致的 HUD 排版（SVG overlay）
 * 与道路几何 overlay（路径/车道线/盲区带/前车框等）。
 *
 * 相比旧的本地 Canvas 绘制版，排版/配色 100% 复用网页实现，数据接口同源
 * （/ws/compact_state），且不再占用设备端视频编码资源。
 */
@Composable
fun CarrotHudPage(
    deviceIp: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val url = remember(deviceIp) {
        if (deviceIp.isNullOrBlank()) "" else "http://$deviceIp:7000/?hud_only=1"
    }

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

    // 进入页面加载 7000 纯 HUD 页；离开时销毁 WebView 防泄漏
    DisposableEffect(url) {
        if (url.isNotBlank()) {
            webView.loadUrl(url)
        }
        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
    }

    // 返回键：页面内无多级导航（驾驶页），直接返回主页
    BackHandler { onBack() }

    Box(modifier = modifier.fillMaxSize().background(Color(0xFF0B0F14))) {
        AndroidView(
            factory = { webView },
            modifier = Modifier.fillMaxSize()
        )
    }
}
