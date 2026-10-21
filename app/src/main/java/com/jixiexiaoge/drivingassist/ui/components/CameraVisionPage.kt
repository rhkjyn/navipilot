package com.jixiexiaoge.drivingassist.ui.components

import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.jixiexiaoge.drivingassist.data.CameraFrame
import com.jixiexiaoge.drivingassist.data.CarrotWsClient
import com.jixiexiaoge.drivingassist.ui.utils.localized
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong

private const val TAG = "CameraVisionPage"

/** 状态栏刷新间隔（统计在引擎内部累积，UI 低频轮询避免逐帧重组） */
private const val STATUS_POLL_MS = 500L

/**
 * Carrot Vision 原生视频页（性能优先）。
 *
 * 直连设备 carrot server 的 /ws/camera/road?fps=N（H264 Annex-B 裸流），
 * SurfaceView + MediaCodec 硬解码（独立硬件层直渲染，无额外 GPU 合成），
 * 解码循环跑在后台 dispatcher，永不阻塞主线程；解码跟不上时整帧丢弃
 * （最新帧优先），必要时通过降低 fps 换流畅。
 */
@Composable
fun CameraVisionPage(
    deviceIp: String?,
    wsClient: CarrotWsClient?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val engine = remember { H264DecoderEngine() }

    // 进入页面连接摄像头，离开断开并释放解码器
    DisposableEffect(deviceIp, wsClient) {
        if (deviceIp != null && wsClient != null) {
            wsClient.connectCamera(deviceIp)
        }
        onDispose {
            engine.release()
            wsClient?.disconnectCamera()
        }
    }

    // 解码循环：后台线程 + 最新帧优先（慢解码自动丢中间帧，不积压）
    LaunchedEffect(engine, wsClient) {
        wsClient?.cameraFrame?.let { flow ->
            withContext(Dispatchers.Default) {
                flow.collectLatest { frame ->
                    if (frame != null) engine.feed(frame)
                }
            }
        }
    }

    // 状态栏：低频轮询引擎统计，避免逐帧重组；流中断时自动重连（自愈）
    var statusText by remember { mutableStateOf(localized("连接中…", "Connecting…")) }
    LaunchedEffect(engine, wsClient, deviceIp) {
        var lastReconnectAt = 0L
        while (isActive) {
            val stats = engine.stats()
            statusText = when {
                !stats.ready -> localized("等待关键帧…", "Waiting for keyframe…")
                stats.stalled -> localized("画面中断", "Stream stalled")
                else -> localized(
                    "画面中 ${stats.fps} FPS ${stats.dimension}",
                    "Live ${stats.fps} FPS ${stats.dimension}"
                )
            }
            // WS 掉线（onFailure 已重置 cameraConnected）后低频重连，不 hammering
            val now = System.currentTimeMillis()
            if (deviceIp != null && now - lastReconnectAt >= 5000 &&
                (stats.stalled || (!stats.ready && now - engine.firstFeedAttemptMs > 6000))
            ) {
                lastReconnectAt = now
                wsClient?.connectCamera(deviceIp)
            }
            delay(STATUS_POLL_MS)
        }
    }

    BackHandler { onBack() }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                SurfaceView(ctx).apply {
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            engine.attach(holder.surface)
                        }

                        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            engine.detach()
                        }
                    })
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0x66000000)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.size(64.dp, 34.dp)
            ) {
                Text(localized("返回", "Back"), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                text = statusText,
                color = if (statusText.startsWith(localized("画面中", "Live"))) Color(0xFF22C55E) else Color(0xFFF59E0B),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/** 引擎对外统计快照 */
data class DecoderStats(
    val ready: Boolean,
    val fps: Int,
    val dimension: String,
    val stalled: Boolean
)

/**
 * H264 (Annex-B) → MediaCodec 硬解码渲染引擎。
 *
 * 帧来自 /ws/camera/road：payload 以 00 00 00 01 起始码分段，关键帧内携带
 * SPS(nal 7)/PPS(nal 8)，解析后作为 csd-0/csd-1 配置解码器；中途接入先
 * 丢弃非关键帧，等到 IDR 再初始化。
 *
 * 线程模型：feed() 在后台解码线程串行调用（collectLatest 保证），
 * attach/detach 来自主线程 Surface 回调，内部以锁同步。性能优先：
 * 输入缓冲拿不到立即丢帧（永不阻塞），超缓冲容量的整帧丢弃防花屏，
 * 输出缓冲及时排水渲染。
 */
private class H264DecoderEngine {
    private val lock = Any()
    private var codec: MediaCodec? = null
    private var surface: Surface? = null
    private var csdSps: ByteArray? = null
    private var csdPps: ByteArray? = null
    private val ptsUs = AtomicLong(0L)
    private val outputInfo = MediaCodec.BufferInfo()

    // 统计（解码线程写，UI 线程低频读，允许弱一致）
    @Volatile private var ready = false
    @Volatile private var lastFrameAtMs = 0L
    @Volatile private var fps = 0
    @Volatile private var dimension = ""
    @Volatile var firstFeedAttemptMs = 0L
        private set
    private var fpsWindowStart = 0L
    private var fpsCount = 0

    fun attach(s: Surface) {
        synchronized(lock) {
            surface = s
            // 新 Surface 必须重建解码器（旧 surface 已失效）
            resetLocked()
        }
    }

    fun detach() {
        synchronized(lock) {
            surface = null
            resetLocked()
        }
    }

    fun feed(frame: CameraFrame) {
        val payload = frame.payload
        if (payload.isEmpty()) return

        var decoded = false
        synchronized(lock) {
            try {
                if (frame.keyFrame) {
                    val (sps, pps) = extractSpsPps(payload)
                    if (sps != null && pps != null) {
                        val spsChanged = csdSps?.contentEquals(sps) != true
                        val ppsChanged = csdPps?.contentEquals(pps) != true
                        if (codec == null || spsChanged || ppsChanged) {
                            ensureCodecLocked(frame, sps, pps)
                        }
                    }
                }

                val codec = this.codec ?: return // 未同步到关键帧，丢弃
                if (payload.size > 512 * 1024) return // 异常超大帧直接丢弃

                // 性能优先：拿不到输入缓冲立即丢帧，绝不阻塞解码循环
                val inputIndex = codec.dequeueInputBuffer(0)
                if (inputIndex < 0) return
                val input = codec.getInputBuffer(inputIndex) ?: return
                input.clear()
                if (payload.size > input.capacity()) return // 单缓冲装不下，整帧丢弃防花屏
                input.put(payload, 0, payload.size)
                codec.queueInputBuffer(inputIndex, 0, payload.size, ptsUs.getAndAdd(50_000), 0)

                // 排水输出缓冲并渲染（不解码输出会耗尽缓冲导致画面冻结）
                var dropped = 0
                while (dropped < 4) {
                    val outIndex = codec.dequeueOutputBuffer(outputInfo, 0)
                    if (outIndex < 0) break
                    codec.releaseOutputBuffer(outIndex, true)
                    dropped++
                }
                decoded = true
            } catch (e: Exception) {
                Log.w(TAG, "解码异常，等待下一个关键帧重建: ${e.message}")
                resetLocked()
            }
        }

        if (decoded || frame.keyFrame) {
            if (firstFeedAttemptMs == 0L) firstFeedAttemptMs = System.currentTimeMillis()
            lastFrameAtMs = System.currentTimeMillis()
            if (frame.width > 0 && frame.height > 0) {
                dimension = "${frame.width}x${frame.height}"
            }
            val now = lastFrameAtMs
            if (fpsWindowStart == 0L) {
                fpsWindowStart = now
                fpsCount = 0
            } else if (now - fpsWindowStart >= 1000) {
                fps = fpsCount
                fpsCount = 0
                fpsWindowStart = now
            }
            fpsCount++
        }
    }

    fun stats(): DecoderStats {
        val last = lastFrameAtMs
        return DecoderStats(
            ready = ready,
            fps = fps,
            dimension = dimension,
            stalled = ready && last > 0 && System.currentTimeMillis() - last > 2000
        )
    }

    fun release() {
        synchronized(lock) {
            surface = null
            resetLocked()
        }
    }

    private fun ensureCodecLocked(frame: CameraFrame, sps: ByteArray, pps: ByteArray) {
        val s = surface ?: return
        resetLocked()

        val format = MediaFormat.createVideoFormat(
            "video/avc",
            frame.width.coerceAtLeast(2),
            frame.height.coerceAtLeast(2)
        )
        format.setByteBuffer("csd-0", java.nio.ByteBuffer.wrap(sps))
        format.setByteBuffer("csd-1", java.nio.ByteBuffer.wrap(pps))
        var newCodec: MediaCodec? = null
        try {
            newCodec = MediaCodec.createDecoderByType("video/avc")
            newCodec.configure(format, s, null, 0)
            newCodec.start()
            codec = newCodec
            csdSps = sps
            csdPps = pps
            ready = true
            Log.i(TAG, "解码器就绪 ${frame.width}x${frame.height}")
        } catch (e: Exception) {
            Log.w(TAG, "解码器初始化失败: ${e.message}")
            try {
                newCodec?.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun resetLocked() {
        val old = codec
        codec = null
        csdSps = null
        csdPps = null
        ready = false
        if (old != null) {
            try {
                old.stop()
            } catch (_: Exception) {
            }
            try {
                old.release()
            } catch (_: Exception) {
            }
        }
    }

    companion object {
        /** 从 Annex-B 字节流中提取 SPS/PPS（nal type 7/8），无则返回 null。
         *  返回值含起始码（00 00 00 01 + NAL 全体），可直接作 csd-0/csd-1。 */
        fun extractSpsPps(data: ByteArray): Pair<ByteArray?, ByteArray?> {
            var sps: ByteArray? = null
            var pps: ByteArray? = null
            val n = data.size

            // 找出所有起始码位置（00 00 01 / 00 00 00 01）
            val starts = mutableListOf<Int>()
            var i = 0
            while (i + 3 < n) {
                if (data[i] == 0.toByte() && data[i + 1] == 0.toByte() && data[i + 2] == 1.toByte()) {
                    starts.add(i)
                    i += 3
                } else if (i + 4 < n && data[i] == 0.toByte() && data[i + 1] == 0.toByte()
                    && data[i + 2] == 0.toByte() && data[i + 3] == 1.toByte()) {
                    starts.add(i)
                    i += 4
                } else {
                    i++
                }
            }

            // 每个 NAL 区间 = [本起始码, 下一起始码)，按类型取完整 NAL
            for (k in starts.indices) {
                val start = starts[k]
                val end = if (k + 1 < starts.size) starts[k + 1] else n
                val scLen = if (data[start + 2] == 1.toByte()) 3 else 4
                if (end <= start + scLen) continue
                when (data[start + scLen].toInt() and 0x1F) {
                    7 -> if (sps == null) sps = data.copyOfRange(start, end)
                    8 -> if (pps == null) pps = data.copyOfRange(start, end)
                }
                if (sps != null && pps != null) break
            }
            return sps to pps
        }
    }
}
