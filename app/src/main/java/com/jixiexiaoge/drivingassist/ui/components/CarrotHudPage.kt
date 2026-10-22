package com.jixiexiaoge.drivingassist.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jixiexiaoge.drivingassist.data.CompactState
import com.jixiexiaoge.drivingassist.data.CarrotWsClient
import com.jixiexiaoge.drivingassist.ui.utils.localized
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

private const val TAG = "CarrotHudPage"
private const val STATUS_POLL_MS = 500L
private const val DATA_FRESH_MS = 2500L
/** 道路相机水平视场半角（针孔 fx = cx / tan(58°)），与旧视频页投影一致 */
private const val H_FOV_DEG = 58.0
private const val PATH_HALF_WIDTH = 0.9f
private const val PATH_Z_OFFSET = 1.22f
private const val BLINDSPOT_SHIFT = 1.7f
private const val BLINDSPOT_Z_TOP = 1.15f
private const val BLINDSPOT_Z_BOTTOM = 0.60f
private const val BLINDSPOT_MAX_DIST = 40f

// 与 web / BYD 原生 ADAS 同风格配色
private val LaneGreen = Color(0xFF53DC76)
private val PathGreen = Color(0xFF3ACC66)
private val Gold = Color(0xFFFFD700)
private val AssistGreen = Color(0xFF00CC00)
private val LeadOrange = Color(0xFFFFAF03)
private val DistGreen = Color(0xFF23D55D)
private val SideWarn = Color(0xFFF97316)
private val RoadEdgeColor = Color(0xFFFF4E3B)

/**
 * Carrot HUD 原生页（替代 Carrot Vision 视频页）。
 *
 * 数据: /ws/compact_state（与 web 同源 CVS1/CVB1 协议）→ Qt 全集 HUD：
 * 大车速 / 绿色巡航车速 / eco 目标车速 / 驾驶模式徽章(ECO·SAFE·NORM·FAST) /
 * GPS / AUTOHOLD·SOFTHOLD·CARROT / 限速牌 / 转向灯 / 盲区条+侧缘警示 /
 * 前车 box+距离 / 车道线 / 路径 / 路径终点 / 警报 / 导航信息。
 *
 * 不显示视频画面——只收 20Hz 小数据包 + Canvas 绘制，WiFi 带宽与 CPU 开销
 * 比视频页低两三个数量级（视频 Mbps 级 vs 状态流几十 KB/s）。
 */
@Composable
fun CarrotHudPage(
    deviceIp: String?,
    wsClient: CarrotWsClient?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 进入页面连接紧凑状态，离开断开（/ws/raw_multiplex 数据流由首页持有，不在此动）
    DisposableEffect(deviceIp, wsClient) {
        if (deviceIp != null && wsClient != null) {
            wsClient.connectCompact(deviceIp)
        }
        onDispose {
            wsClient?.disconnectCompact()
        }
    }

    // 紧凑状态（HUD + overlay 数据源）：按模型帧频采样（≈20Hz）
    var cs by remember { mutableStateOf(wsClient?.compactState?.value) }
    LaunchedEffect(wsClient) {
        while (isActive) {
            cs = wsClient?.compactState?.value
            delay(50)
        }
    }

    // 连接状态：数据新鲜度判定
    var statusText by remember { mutableStateOf(localized("未连接", "Offline")) }
    LaunchedEffect(wsClient) {
        while (isActive) {
            val s = wsClient?.compactState?.value
            statusText = if (s != null && System.currentTimeMillis() - s.recvAt < DATA_FRESH_MS) {
                localized("HUD 实时", "HUD live")
            } else {
                localized("未连接", "Offline")
            }
            delay(STATUS_POLL_MS)
        }
    }

    BackHandler { onBack() }

    val state = cs
    val fresh = state != null && System.currentTimeMillis() - state.recvAt < DATA_FRESH_MS

    Box(modifier = modifier.fillMaxSize().background(Color(0xFF0B0F14))) {
        // 中央示意驾驶视图（无视频，静态路面 + 实时 overlay）
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = if (fresh) state else null

            // 静态示意路面：深灰梯形 + 地平线
            val horizonY = size.height * 0.42f
            val roadPath = Path().apply {
                moveTo(size.width * 0.18f, horizonY)
                lineTo(size.width * 0.82f, horizonY)
                lineTo(size.width * 1.08f, size.height)
                lineTo(-size.width * 0.08f, size.height)
                close()
            }
            drawPath(roadPath, Color(0xFF141A22))
            drawLine(Color(0xFF2A3644), Offset(size.width * 0.18f, horizonY), Offset(size.width * 0.82f, horizonY), 2f)

            if (s == null) return@Canvas
            val cx = size.width / 2f
            val cy = size.height / 2f
            val hFovRad = Math.toRadians(H_FOV_DEG).toFloat()
            val fx = cx / tan(hFovRad)
            val fz = fx
            // 标定旋转（仅 calibrated 生效，与 web getCalibrationMatrix 同源）
            val rot = if (s.calStatus == 1) rotationFromRpy(s.rpyCalib) else null

            fun project(xf: Float, yf: Float, zf: Float): Offset? {
                if (xf < 0.4f || xf > 120f) return null
                var x = xf
                var y = yf
                var z = zf
                if (rot != null) {
                    val nx = rot[0] * xf + rot[1] * yf + rot[2] * zf
                    val ny = rot[3] * xf + rot[4] * yf + rot[5] * zf
                    val nz = rot[6] * xf + rot[7] * yf + rot[8] * zf
                    if (nx < 0.4f || nx > 120f) return null
                    x = nx
                    y = ny
                    z = nz
                }
                val u = cx + fx * y / x
                val v = cy - fz * z / x // 相机系 z 向上
                return Offset(u, v)
            }

            fun polyline(points: List<FloatArray>, color: Color, widthPx: Float, dash: FloatArray? = null) {
                if (points.size < 2) return
                val path = Path()
                var started = false
                for (pt in points) {
                    val p = project(pt[0], pt[1], pt[2]) ?: continue
                    if (!started) {
                        path.moveTo(p.x, p.y)
                        started = true
                    } else {
                        path.lineTo(p.x, p.y)
                    }
                }
                if (started) {
                    val effect = dash?.let { PathEffect.dashPathEffect(it) }
                    drawPath(path, color, style = Stroke(widthPx, cap = StrokeCap.Round, pathEffect = effect))
                }
            }

            // 路径 ribbon（绿色填充，半宽 0.9m / z 抬高 1.22m）
            if (s.path.size >= 2) {
                val left = mutableListOf<Offset>()
                val right = mutableListOf<Offset>()
                for (pt in s.path) {
                    val l = project(pt[0], pt[1] - PATH_HALF_WIDTH, pt[2] + PATH_Z_OFFSET) ?: continue
                    val r = project(pt[0], pt[1] + PATH_HALF_WIDTH, pt[2] + PATH_Z_OFFSET) ?: continue
                    left.add(l)
                    right.add(r)
                }
                if (left.size >= 2 && right.size >= 2) {
                    val band = Path().apply {
                        moveTo(left.first().x, left.first().y)
                        left.forEach { lineTo(it.x, it.y) }
                        right.asReversed().forEach { lineTo(it.x, it.y) }
                        close()
                    }
                    drawPath(band, PathGreen.copy(alpha = 0.42f))
                }
            }

            // 车道线（绿色虚线，概率决定透明度；对照 BYD 原生 ADAS 显示）
            val laneDash = floatArrayOf(24f, 20f)
            s.laneLines.forEachIndexed { i, line ->
                val prob = if (i < s.laneLineProbs.size) s.laneLineProbs[i] else 0f
                val alpha = (0.25f + 0.75f * prob.coerceIn(0f, 1f)).coerceIn(0f, 1f)
                polyline(line, LaneGreen.copy(alpha = alpha), 6f, laneDash)
            }
            // 路沿（红色）
            s.roadEdges.forEach { edge -> polyline(edge, RoadEdgeColor.copy(alpha = 0.8f), 5f) }

            // 盲区条（±1.7m 竖直色带 + 斜纹）：盲区金色 / 变道辅助绿色（对照 QT BlindSpotDrawer）
            fun drawBlindspotBarrier(shift: Float, blindspot: Boolean, assist: Boolean) {
                if (!blindspot && !assist) return
                val fill = if (blindspot) Gold.copy(alpha = 0.48f) else AssistGreen.copy(alpha = 0.44f)
                val stroke = if (blindspot) Gold.copy(alpha = 0.84f) else AssistGreen.copy(alpha = 0.80f)
                val top = mutableListOf<Offset>()
                val bottom = mutableListOf<Offset>()
                for (pt in s.path) {
                    if (pt[0] > BLINDSPOT_MAX_DIST) break
                    val t = project(pt[0], pt[1] + shift, pt[2] + BLINDSPOT_Z_TOP) ?: continue
                    val b = project(pt[0], pt[1] + shift, pt[2] + BLINDSPOT_Z_BOTTOM) ?: continue
                    top.add(t)
                    bottom.add(b)
                }
                if (top.size < 4) return
                val band = Path().apply {
                    moveTo(top.first().x, top.first().y)
                    top.forEach { lineTo(it.x, it.y) }
                    bottom.asReversed().forEach { lineTo(it.x, it.y) }
                    close()
                }
                drawPath(band, fill)
                drawPath(band, stroke, style = Stroke(2f))
                // 斜纹
                val step = maxOf(2, top.size / 10)
                var i = step
                while (i < top.size) {
                    drawLine(stroke, top[i], bottom[i - step], strokeWidth = 2.2f, cap = StrokeCap.Round)
                    i += step
                }
            }
            val assistLeft = s.laneChangeState == 1 && s.laneChangeDirection == 1 &&
                s.leadLeft.status && s.leadLeft.dRel > 0 && s.leadLeft.dRel < s.vEgo * 3.0f
            val assistRight = s.laneChangeState == 1 && s.laneChangeDirection == 2 &&
                s.leadRight.status && s.leadRight.dRel > 0 && s.leadRight.dRel < s.vEgo * 3.0f
            drawBlindspotBarrier(-BLINDSPOT_SHIFT, s.leftBlindspot, !s.leftBlindspot && assistLeft)
            drawBlindspotBarrier(BLINDSPOT_SHIFT, s.rightBlindspot, !s.rightBlindspot && assistRight)

            // 路径终点横线（对齐 QT PathEndDrawer：engaged 且目标距离有效时）
            if (s.engaged && s.desiredDistance in 1f..60f) {
                val a = project(s.desiredDistance, -PATH_HALF_WIDTH, -1.2f)
                val b = project(s.desiredDistance, PATH_HALF_WIDTH, -1.2f)
                if (a != null && b != null) {
                    drawLine(Color.White.copy(alpha = 0.9f), a, b, strokeWidth = 3f, cap = StrokeCap.Round)
                }
            }

            // 前车 box（雷达 leadOne / 模型 leadsV3 优先）+ 绿色距离数字
            val lead = s.lead()
            val leadBox: RadarBox? = lead?.let { RadarBox(it.dRel, it.yRel, 1.0f) }
                ?: s.leadsV3.firstOrNull { it.prob > 0.5f }?.let {
                    val x = it.x.firstOrNull() ?: return@let null
                    RadarBox(x, it.y.firstOrNull() ?: 0f, 1.0f)
                }
            if (leadBox != null) {
                val corners = listOf(
                    project(leadBox.x, leadBox.y - leadBox.halfWidth, -1.2f),
                    project(leadBox.x, leadBox.y + leadBox.halfWidth, -1.2f),
                    project(leadBox.x, leadBox.y + leadBox.halfWidth, 0.6f),
                    project(leadBox.x, leadBox.y - leadBox.halfWidth, 0.6f),
                )
                if (corners.none { it == null }) {
                    val boxPath = Path()
                    corners.forEachIndexed { i, p ->
                        if (i == 0) boxPath.moveTo(p!!.x, p!!.y) else boxPath.lineTo(p!!.x, p!!.y)
                    }
                    boxPath.close()
                    drawPath(boxPath, LeadOrange, style = Stroke(4f))
                }
            }
            // 雷达轨迹点
            s.tracks.forEach { t ->
                project(t.dRel, t.yRel, -1.2f)?.let { p -> drawCircle(Color(0xFF38BDF8), radius = 5f, center = p) }
            }
        }

        // 限速牌（右上，QT drawSpeedLimit 静态版）
        state?.takeIf { it.roadLimitKph > 0 }?.let { s ->
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 48.dp, end = 14.dp)
                    .size(56.dp)
                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(28.dp)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color.White, RoundedCornerShape(24.dp))
                        .padding(2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFFDC2626), RoundedCornerShape(22.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "${s.roadLimitKph}",
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // 盲区侧缘警示圆（对照 web HUD 盲区警示：橙底白"!"）
        state?.let { s ->
            @Composable
            fun warnCircle(show: Boolean, align: Alignment) {
                if (!show) return
                Box(
                    modifier = Modifier
                        .align(align)
                        .padding(horizontal = 10.dp)
                        .size(30.dp)
                        .background(SideWarn.copy(alpha = 0.92f), RoundedCornerShape(15.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("!", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
            warnCircle(s.leftBlindspot, Alignment.CenterStart)
            warnCircle(s.rightBlindspot, Alignment.CenterEnd)
        }

        // 顶部状态栏：返回 + 导航信息 + 状态
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
            state?.let { s ->
                if (s.posRoadName.isNotBlank() || s.tbtText.isNotBlank() || s.roadLimitKph > 0) {
                    Text(
                        text = listOfNotNull(
                            if (s.roadLimitKph > 0) "限速 ${s.roadLimitKph}" else null,
                            if (s.turnInfo > 0) "转弯${if (s.distToTurn > 0) " ${s.distToTurn}m" else ""}" else null,
                            if (s.tbtText.isNotBlank()) s.tbtText else s.posRoadName.ifBlank { null },
                        ).joinToString(" · "),
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
            Text(
                text = statusText,
                color = if (statusText.startsWith(localized("HUD", "HUD"))) Color(0xFF22C55E) else Color(0xFFF59E0B),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // 警告文本（selfdriveState alert）
        state?.let { s ->
            val alert = s.alertText1.ifBlank { s.alertText2 }
            if (alert.isNotBlank()) {
                Text(
                    text = alert,
                    color = if (s.alertStatus == 2) Color(0xFFEF4444) else Color(0xFFFBBF24),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 44.dp)
                )
            }
        }

        // HUD 底部（对照 QT drawHud：大车速/绿色巡航/eco 目标/模式徽章/GPS/AUTOHOLD/盲区/前车）
        state?.let { s ->
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (s.leftBlinker) {
                                Text("◀", color = LaneGreen, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(
                                "${(s.vEgo * 3.6f).roundToInt()}",
                                color = Color.White,
                                fontSize = 48.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(" km/h", color = Color.White.copy(alpha = 0.7f), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            if (s.rightBlinker) {
                                Text("▶", color = LaneGreen, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (s.engaged) "巡航 ${(s.vCruise * 3.6f).roundToInt()}" else "--",
                                color = DistGreen,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (s.engaged && s.cruiseTarget > 0f && kotlin.math.abs(s.cruiseTarget - s.vCruise) > 0.5f) {
                                Text(
                                    " · eco ${(s.cruiseTarget * 3.6f).roundToInt()}",
                                    color = DistGreen.copy(alpha = 0.9f),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            val hold = when {
                                s.brakeHold -> "AUTOHOLD"
                                s.softHoldActive > 0 -> "SOFTHOLD"
                                s.carrotCruise > 0 -> "CARROT"
                                else -> ""
                            }
                            if (hold.isNotEmpty()) {
                                Text(" · $hold", color = Color(0xFFFBBF24), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                            if (s.steerDeg != 0f) {
                                Text(
                                    " · 转向 ${s.steerDeg.roundToInt()}°",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        // 驾驶模式徽章（QT: 1 ECO 绿 / 2 SAFE 橙 / 3 NORM 灰 / 4 FAST 红）
                        val mode = when (s.myDrivingMode) {
                            1 -> "ECO" to Color(0xFF22C55E)
                            2 -> "SAFE" to Color(0xFFF97316)
                            3 -> "NORM" to Color(0xFF9CA3AF)
                            4 -> "FAST" to Color(0xFFEF4444)
                            else -> "" to Color.Transparent
                        }
                        if (mode.first.isNotEmpty()) {
                            Text(
                                text = mode.first,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .background(mode.second, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 12.dp, vertical = 3.dp)
                            )
                        }
                        if (s.gpsHasFix) {
                            Text("GPS", color = DistGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        if (s.leftBlindspot) Text("⚠ 左", color = SideWarn, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        if (s.rightBlindspot) Text("⚠ 右", color = SideWarn, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        s.lead()?.let { l ->
                            Text("${l.dRel.roundToInt()} m", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "前车 ${(l.vLead * 3.6f).roundToInt()} km/h",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 13.sp
                            )
                        }
                        if (s.tFollow > 0f) {
                            Text("gap ${s.tFollow}s", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

/** 前车方框（雷达坐标：x 纵向距离 m, y 横向偏移 m） */
private data class RadarBox(val x: Float, val y: Float, val halfWidth: Float)

/**
 * 标定旋转矩阵（与 web rotFromEuler 同构）：rpy 弧度 [roll, pitch, yaw]，
 * 行主序 3x3。仅 calStatus=1(calibrated) 时生效；未标定/重标定时返回 null，
 * 投影退化为无旋转的针孔（与旧视频页一致）。
 */
private fun rotationFromRpy(rpy: FloatArray?): FloatArray? {
    if (rpy == null || rpy.size < 3) return null
    val roll = rpy[0].toDouble()
    val pitch = rpy[1].toDouble()
    val yaw = rpy[2].toDouble()
    val sr = sin(roll)
    val cr = cos(roll)
    val sp = sin(pitch)
    val cp = cos(pitch)
    val sy = sin(yaw)
    val cy = cos(yaw)
    return floatArrayOf(
        (cy * cp).toFloat(), (cy * sp * sr - sy * cr).toFloat(), (cy * sp * cr + sy * sr).toFloat(),
        (sy * cp).toFloat(), (sy * sp * sr + cy * cr).toFloat(), (sy * sp * cr - cy * sr).toFloat(),
        (-sp).toFloat(), (cp * sr).toFloat(), (cp * cr).toFloat(),
    )
}
