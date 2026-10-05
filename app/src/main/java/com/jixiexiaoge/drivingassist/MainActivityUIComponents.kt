package com.jixiexiaoge.drivingassist

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.jixiexiaoge.drivingassist.CustomIcons
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import com.jixiexiaoge.drivingassist.ui.utils.localized
import com.jixiexiaoge.drivingassist.ui.theme.*
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction

/**
 * MainActivity UI组件 - 辅助组件和工具函数
 * 包含车辆控制按钮、高阶功能弹窗、导航相关函数等
 */
object MainActivityUIComponents {

    private const val AMAP_AUTO_PKG = "com.autonavi.amapauto"
    /** 冷启动后车机进程需要时间注册广播接收器，略延迟再发导航广播 */
    private const val AMAP_AUTO_BROADCAST_DELAY_MS = 320L

    /**
     * 拉起高德地图车机版主界面（与 [MainActivityCore.launchAmapAuto] 策略一致），便于未运行时仍能收到后续标准广播。
     */
    private fun tryLaunchAmapAutoApp(context: Context): Boolean {
        val appCtx = context.applicationContext
        try {
            val explicit = Intent().apply {
                setComponent(
                    ComponentName(AMAP_AUTO_PKG, "com.autonavi.auto.MainMapActivity")
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (explicit.resolveActivity(appCtx.packageManager) != null) {
                appCtx.startActivity(explicit)
                android.util.Log.i("MainActivity", "已拉起高德车机版 (MainMapActivity)")
                return true
            }
        } catch (e: Exception) {
            android.util.Log.w("MainActivity", "显式启动高德车机版失败: ${e.message}")
        }
        return try {
            val li = appCtx.packageManager.getLaunchIntentForPackage(AMAP_AUTO_PKG) ?: return false
            li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appCtx.startActivity(li)
            android.util.Log.i("MainActivity", "已拉起高德车机版 (getLaunchIntentForPackage)")
            true
        } catch (e: Exception) {
            android.util.Log.w("MainActivity", "隐式启动高德车机版失败: ${e.message}")
            false
        }
    }

    private fun sendAmapAutoBroadcastAfterLaunch(
        context: Context,
        launchedAmap: Boolean,
        broadcast: Intent,
        successLog: String
    ) {
        val appCtx = context.applicationContext
        fun sendNow() {
            try {
                appCtx.sendBroadcast(broadcast)
                android.util.Log.i("MainActivity", successLog)
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "❌ 高德车机广播发送失败: ${e.message}", e)
            }
        }
        if (launchedAmap) {
            Handler(Looper.getMainLooper()).postDelayed({ sendNow() }, AMAP_AUTO_BROADCAST_DELAY_MS)
        } else {
            sendNow()
        }
    }
    
    /**
     * 车辆控制按钮组件 - 带速度圆环显示
     */
    @Composable
    fun VehicleControlButtons(
        core: MainActivityCore,
        onPageChange: (Page) -> Unit,
        onSendCommand: (String, String) -> Unit,
        onSendRoadLimitSpeed: () -> Unit,
        onLaunchAmap: () -> Unit,
        onSendNavConfirmation: () -> Unit,
        carrotManFields: CarrotManFields
    ) {
        var showAdvancedDialog by remember { mutableStateOf(false) }
        val context = androidx.compose.ui.platform.LocalContext.current
        
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .semantics {
                    contentDescription = context.getString(R.string.app_name) + " " + localized("车辆控制面板", "Vehicle control panel")
                },
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧：速度圆环（蓝色在上，绿色在下）
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // 蓝色圆环：巡航设定速度
                    SpeedRing(
                        speed = carrotManFields.vCruiseKph.toInt(),
                        label = localized("巡航", "Cruise"),
                        color = SpeedCruise,
                        onClick = { /* 模拟导航 */ }
                    )
                    // 绿色圆环：当前车速
                    SpeedRing(
                        speed = carrotManFields.vEgoKph,
                        label = localized("车速", "Speed"),
                        color = SpeedCurrent,
                        onClick = { onLaunchAmap() }
                    )
                }

                // 右侧：控制按钮
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 回家按钮
                    ControlButton(
                        icon = Icons.Default.Home,
                        label = "",
                        color = ButtonHome,
                        onClick = {
                            android.util.Log.i("MainActivity", "🏠 主页：用户点击回家按钮")
                            MainActivityUIComponents.sendHomeNavigationToAmap(context)
                        }
                    )

                    // 高阶按钮
                    ControlButton(
                        icon = Icons.Default.Settings,
                        label = "",
                        color = ButtonAdvanced,
                        onClick = {
                            android.util.Log.i("MainActivity", "🚀 主页：用户点击高阶按钮")
                            showAdvancedDialog = true
                        }
                    )

                    // 公司按钮
                    ControlButton(
                        icon = CustomIcons.Work,
                        label = "",
                        color = ButtonCompany,
                        onClick = {
                            android.util.Log.i("MainActivity", "🏢 主页：用户点击公司按钮")
                            MainActivityUIComponents.sendCompanyNavigationToAmap(context)
                        }
                    )
                }
            }
        }
        
        // 高阶功能弹窗
        if (showAdvancedDialog) {
            AdvancedFunctionsDialog(
                onDismiss = { showAdvancedDialog = false },
                onSendCommand = onSendCommand,
                onSendRoadLimitSpeed = onSendRoadLimitSpeed,
                onLaunchAmap = onLaunchAmap,
                onSendNavConfirmation = onSendNavConfirmation,
                onPageChange = onPageChange, // 传递页面切换回调
                isOpenpilotActive = carrotManFields.active,
                carrotManFields = carrotManFields,
                networkManager = core.networkManager, // 传递networkManager用于直接发送坐标
                context = context
            )
        }
    }

    /**
     * 控制按钮组件（优化版 - 使用Material Icons + 动画效果）
     */
    @Composable
    fun ControlButton(
        icon: ImageVector,
        label: String,
        color: Color,
        onClick: () -> Unit
    ) {
        var isPressed by remember { mutableStateOf(false) }
        val scale by animateFloatAsState(
            targetValue = if (isPressed) 0.92f else 1f,
            animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
            label = "button_press_animation"
        )

        Box(
            modifier = Modifier
                .size(48.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clickable(
                    onClick = {
                        android.util.Log.i("MainActivity", "🔍 ControlButton: 检测到点击事件")
                        onClick()
                    },
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                        .also { interactionSource ->
                            LaunchedEffect(interactionSource) {
                                interactionSource.interactions.collect { interaction ->
                                    when (interaction) {
                                        is PressInteraction.Press -> isPressed = true
                                        is PressInteraction.Release -> isPressed = false
                                        is PressInteraction.Cancel -> isPressed = false
                                    }
                                }
                            }
                        }
                )
                .shadow(2.dp, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                .background(
                    color = color,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (label.isEmpty()) {
                // 只有图标，居中显示
                Icon(
                    imageVector = icon,
                    contentDescription = localized("控制按钮", "Control button"),
                    modifier = Modifier.size(24.dp),
                    tint = Color.White
                )
            } else {
                // 图标 + 文字，垂直排列
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        modifier = Modifier.size(18.dp),
                        tint = Color.White
                    )
                    Text(
                        text = label,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                }
            }
        }
    }

    /**
     * 高级功能内容（九宫格 + 超车参数）— 可同时用于弹窗和主页卡片
     */
    @Composable
    fun AdvancedFunctionsContent(
        onSendCommand: (String, String) -> Unit,
        onSendRoadLimitSpeed: () -> Unit,
        onLaunchAmap: () -> Unit,
        onSendNavConfirmation: () -> Unit,
        onPageChange: (Page) -> Unit,
        isOpenpilotActive: Boolean,
        carrotManFields: CarrotManFields,
        networkManager: NetworkManager? = null,
        context: android.content.Context,
        onDismiss: (() -> Unit)? = null,
        onSearchClick: (() -> Unit)? = null,    // 搜索回调
        onShow7706Debug: (() -> Unit)? = null,  // 7706 调试面板回调
        commaConnectionState: Int = 0,           // 连接状态
        onHomeNavClick: () -> Unit = {},         // 回家导航
        onHomeNavLongClick: () -> Unit = {},     // 清除回家地址
        onCompanyNavClick: () -> Unit = {},      // 公司导航
        onCompanyNavLongClick: () -> Unit = {},  // 清除公司地址
        homeAddressSet: Boolean = false,         // 家地址是否已设置
        companyAddressSet: Boolean = false,      // 公司地址是否已设置
        onExperimentClick: () -> Unit = {},      // 实验模式切换
        onShareDataClick: () -> Unit = {},       // 数据分享切换
        isShareDataEnabled: Boolean? = null,     // 数据分享状态
        trafficLightState: Int = -1,            // 红绿灯状态
        trafficLightCountdown: Int = 0,         // 红绿灯倒计时
        trafficLightDir: Int = 0,               // 红绿灯方向
    ) {
        var showAboutDialog by remember { mutableStateOf(false) }
        // 重启/关机二次确认弹窗状态: null=隐藏, 否则为待发送的系统命令字符串
        var powerConfirmCommand by remember { mutableStateOf<String?>(null) }
        fun playSound(resourceId: Int, soundName: String) {
            try {
                MediaPlayer.create(context, resourceId)?.apply {
                    setOnCompletionListener { release() }
                    setOnErrorListener { _, what, extra ->
                        android.util.Log.e("MainActivity", "❌ 音频播放错误($soundName): what=$what, extra=$extra")
                        release()
                        true
                    }
                    start()
                    android.util.Log.d("MainActivity", "🔊 开始播放${soundName}提示音")
                } ?: android.util.Log.w("MainActivity", "⚠️ 无法创建音频播放器($soundName)")
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "❌ 播放${soundName}提示音失败: ${e.message}", e)
            }
        }

        var speedControlMode by remember {
            mutableStateOf(
                context.getSharedPreferences("CarrotAmap", android.content.Context.MODE_PRIVATE)
                    .getInt("speed_from_pcm_mode", 0)
            )
        }
        var isSpeedModeLoading by remember { mutableStateOf(false) }

        var overtakeMode by remember {
            mutableStateOf(
                context.getSharedPreferences("CarrotAmap", android.content.Context.MODE_PRIVATE)
                    .getInt("overtake_mode", 1)
            )
        }
        var isOvertakeModeLoading by remember { mutableStateOf(false) }

        val coroutineScope = rememberCoroutineScope()

        Column {
            // 标题栏
            Text(
                text = "CP搭子免费开源版",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Start
            )
            Text(
                text = localized("由 机械小鸽 分享", "by JiXieXiaoGe"),
                fontSize = 9.sp,
                color = Color(0xFF94A3B8),
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Start
            )
            // 3x3 九宫格按钮区域
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🎮", fontSize = 11.sp)
                Spacer(Modifier.width(3.dp))
                Text(localized("由机械小鸽 开发并分享", "by JiXieXiaoGe"), color = Color(0xFF94A3B8), fontSize = 9.sp, fontWeight = FontWeight.Normal)
                Spacer(Modifier.weight(1f))
                // 连接状态指示
                val connDotColor = when (commaConnectionState) {
                    1 -> Color(0xFF22C55E)
                    2 -> Color(0xFFEF4444)
                    else -> Color(0xFF475569)
                }
                val connLabel = when (commaConnectionState) {
                    1 -> localized("已连接", "Connected")
                    2 -> localized("异常", "Error")
                    else -> localized("等待", "Waiting")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(connDotColor))
                    Spacer(Modifier.width(3.dp))
                    Text(connLabel, color = connDotColor, fontSize = 8.sp, fontWeight = FontWeight.Medium)
                }
            }
            Spacer(Modifier.height(4.dp))
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Surface800.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                // 根据屏幕方向动态设置行列数
                val configuration = androidx.compose.ui.platform.LocalConfiguration.current
                val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                val cols = if (isLandscape) 5 else 3
                val rows = (17 + cols - 1) / cols   // 17个按钮（15个原有 + 新增重启/关机）
                val btnSize = if (isLandscape) 56.dp else 72.dp
                val gridSpacing = if (isLandscape) 4.dp else 10.dp
                Column(
                    modifier = Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(gridSpacing)
                ) {
                    var buttonIndex = 0
                    for (row in 0 until rows) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            for (col in 0 until cols) {
                                buttonIndex++
                                val bn = buttonIndex
                                // 按钮位置对调：1↔10, 2↔11, 3↔12（功能不变，仅布局位置交换）
                                val mappedBn = when (bn) { 1 -> 10; 2 -> 11; 3 -> 12; 10 -> 1; 11 -> 2; 12 -> 3; else -> bn }
                                when (mappedBn) {
                                    1 -> {
                                Button(
                                    onClick = { showAboutDialog = true },
                                    modifier = Modifier.size(btnSize).shadow(4.dp, RoundedCornerShape(14.dp)),
                                    colors = ButtonDefaults.buttonColors(containerColor = ButtonHelp),
                                    contentPadding = PaddingValues(0.dp),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                        Icon(Icons.Default.Info, "关于", Modifier.size(24.dp), tint = Color.White)
                                        Text(localized("关于", "About"), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
                            }
                            2 -> {
                                Button(
                                    onClick = { onHomeNavClick() },
                                    modifier = Modifier.size(btnSize).shadow(4.dp, RoundedCornerShape(14.dp)),
                                    colors = ButtonDefaults.buttonColors(containerColor = if (homeAddressSet) Color(0xFFF97316) else Color(0xFF475569)),
                                    contentPadding = PaddingValues(0.dp), shape = RoundedCornerShape(14.dp)
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                        Text("🏠", fontSize = 18.sp)
                                        Text(
                                            text = if (homeAddressSet) localized("回家", "Home") else localized("设家", "Set Home"),
                                            fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                            color = if (homeAddressSet) Color.White else Color.White.copy(alpha = 0.5f)
                                        )
                                    }
                                }
                            }
                            3 -> {
                                val overtakeModeNames = arrayOf(localized("禁止\n超车", "No\nOvertake"), localized("拨杆\n超车", "Signal\nOvertake"), localized("自动\n超车", "Auto\nOvertake"))
                                val overtakeModeColors = arrayOf(OvertakeDisabled, OvertakeManual, OvertakeAuto)
                                Button(
                                    onClick = {
                                        if (!isOvertakeModeLoading) {
                                            isOvertakeModeLoading = true
                                            coroutineScope.launch {
                                                val nextMode = (overtakeMode + 1) % 3
                                                context.getSharedPreferences("CarrotAmap", android.content.Context.MODE_PRIVATE).edit().putInt("overtake_mode", nextMode).apply()
                                                kotlinx.coroutines.delay(300)
                                                overtakeMode = nextMode
                                                isOvertakeModeLoading = false
                                            }
                                        }
                                    },
                                    modifier = Modifier.size(btnSize).shadow(4.dp, RoundedCornerShape(14.dp)),
                                    colors = ButtonDefaults.buttonColors(containerColor = if (isOvertakeModeLoading) Surface500 else overtakeModeColors[overtakeMode]),
                                    contentPadding = PaddingValues(0.dp), shape = RoundedCornerShape(14.dp),
                                    enabled = !isOvertakeModeLoading
                                ) {
                                    Text(if (isOvertakeModeLoading) localized("切换\n中...", "Switch\ning...") else overtakeModeNames[overtakeMode],
                                        fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 12.sp)
                                }
                            }
                            4 -> {
                                Button(
                                    onClick = { playSound(R.raw.left, "左变道"); onSendCommand("LANECHANGE", "LEFT"); onDismiss?.invoke() },
                                    modifier = Modifier.size(btnSize).shadow(4.dp, RoundedCornerShape(14.dp)),
                                    colors = ButtonDefaults.buttonColors(containerColor = ButtonLaneChange),
                                    contentPadding = PaddingValues(0.dp), shape = RoundedCornerShape(14.dp)
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                        Icon(Icons.Default.KeyboardArrowLeft, "左变道", Modifier.size(24.dp), tint = Color.White)
                                        Text(localized("变道", "Lane"), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
                            }
                            5 -> {
                                Button(
                                    onClick = { onSearchClick?.invoke(); onDismiss?.invoke() },
                                    modifier = Modifier.size(btnSize).shadow(4.dp, RoundedCornerShape(14.dp)),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                    contentPadding = PaddingValues(0.dp), shape = RoundedCornerShape(14.dp)
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                        Icon(Icons.Default.Search, "搜索", Modifier.size(24.dp), tint = Color.White)
                                        Text(localized("搜索", "Search"), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
                            }
                            6 -> {
                                Button(
                                    onClick = { playSound(R.raw.right, "右变道"); onSendCommand("LANECHANGE", "RIGHT"); onDismiss?.invoke() },
                                    modifier = Modifier.size(btnSize).shadow(4.dp, RoundedCornerShape(14.dp)),
                                    colors = ButtonDefaults.buttonColors(containerColor = ButtonLaneChange),
                                    contentPadding = PaddingValues(0.dp), shape = RoundedCornerShape(14.dp)
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                        Icon(Icons.Default.KeyboardArrowRight, "右变道", Modifier.size(24.dp), tint = Color.White)
                                        Text(localized("变道", "Lane"), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
                            }
                            7 -> {
                                Button(
                                    onClick = { onShow7706Debug?.invoke(); onDismiss?.invoke() },
                                    modifier = Modifier.size(btnSize).shadow(4.dp, RoundedCornerShape(14.dp)),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                                    contentPadding = PaddingValues(0.dp), shape = RoundedCornerShape(14.dp)
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                        Icon(Icons.Default.Info, "调试", Modifier.size(24.dp), tint = Color.White)
                                        Text(localized("调试", "Debug"), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
                            }
                            8 -> {
                                Button(
                                    onClick = { onCompanyNavClick() },
                                    modifier = Modifier.size(btnSize).shadow(4.dp, RoundedCornerShape(14.dp)),
                                    colors = ButtonDefaults.buttonColors(containerColor = if (companyAddressSet) Color(0xFF3B82F6) else Color(0xFF475569)),
                                    contentPadding = PaddingValues(0.dp), shape = RoundedCornerShape(14.dp)
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                        Text("🏢", fontSize = 18.sp)
                                        Text(
                                            text = if (companyAddressSet) localized("公司", "Work") else localized("设公司", "Set Work"),
                                            fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                            color = if (companyAddressSet) Color.White else Color.White.copy(alpha = 0.5f)
                                        )
                                    }
                                }
                            }
                            9 -> {
                                Button(
                                    onClick = { onPageChange(Page.Experiment); onDismiss?.invoke() },
                                    modifier = Modifier.size(btnSize).shadow(4.dp, RoundedCornerShape(14.dp)),
                                    colors = ButtonDefaults.buttonColors(containerColor = ButtonExperiment),
                                    contentPadding = PaddingValues(0.dp), shape = RoundedCornerShape(14.dp)
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                        Icon(CustomIcons.BugReport, "实验", Modifier.size(24.dp), tint = Color.White)
                                        Text(localized("实验", "Exp"), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
                            }
                            10 -> {
                                // 🔵 车速环 — 打开 GitHub
                                val speed = carrotManFields.vEgoKph.toInt()
                                Button(
                                    onClick = { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/jixiexiaoge/navipilot")).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) },
                                    modifier = Modifier.size(btnSize).shadow(4.dp, RoundedCornerShape(14.dp)),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                                    contentPadding = PaddingValues(0.dp), shape = RoundedCornerShape(14.dp)
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                        Text("$speed", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        Text(localized("车速", "Speed"), fontSize = 8.sp, color = Color.White.copy(alpha = 0.8f))
                                    }
                                }
                            }
                            11 -> {
                                // ⛔ 限速值
                                val limit = carrotManFields.nRoadLimitSpeed
                                Button(
                                    onClick = { },
                                    modifier = Modifier.size(btnSize).shadow(4.dp, RoundedCornerShape(14.dp)),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFBBF24)),
                                    contentPadding = PaddingValues(0.dp), shape = RoundedCornerShape(14.dp)
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                        Text(if (limit > 0) "$limit" else "--", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if (limit > 0) Color(0xFFFBBF24) else Color.White.copy(alpha = 0.5f))
                                        Text(localized("限速", "Limit"), fontSize = 8.sp, color = Color.White.copy(alpha = 0.7f))
                                    }
                                }
                            }
                            12 -> {
                                // 🟢 巡航环 — 打开设备 Web 控制页
                                val cruise = carrotManFields.vCruiseKph.toInt()
                                Button(
                                    onClick = {
                                        val ip = networkManager?.getCurrentDeviceIP()
                                        if (ip != null) {
                                            try {
                                                context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("http://$ip:7000")).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                                            } catch (_: Exception) { }
                                        }
                                    },
                                    modifier = Modifier.size(btnSize).shadow(4.dp, RoundedCornerShape(14.dp)),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E)),
                                    contentPadding = PaddingValues(0.dp), shape = RoundedCornerShape(14.dp)
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                        Text("$cruise", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        Text(localized("巡航", "Cruise"), fontSize = 8.sp, color = Color.White.copy(alpha = 0.8f))
                                    }
                                }
                            }
                            13 -> {
                                // 🧪 实验模式切换
                                Button(
                                    onClick = { onExperimentClick() },
                                    modifier = Modifier.size(btnSize).shadow(4.dp, RoundedCornerShape(14.dp)),
                                    colors = ButtonDefaults.buttonColors(containerColor = if (isOpenpilotActive) Color(0xFF8B5CF6) else Color(0xFF475569)),
                                    contentPadding = PaddingValues(0.dp), shape = RoundedCornerShape(14.dp)
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                        Text("🧪", fontSize = 18.sp)
                                        Text(localized("实验", "Exp"), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (isOpenpilotActive) Color(0xFF8B5CF6) else Color.White.copy(alpha = 0.5f))
                                    }
                                }
                            }
                            14 -> {
                                // 🚦 红绿灯
                                val tColor = when (trafficLightState) {
                                    0 -> Color(0xFF22C55E); 1 -> Color(0xFFEF4444); 2 -> Color(0xFFFBBF24)
                                    else -> Color(0xFF64748B)
                                }
                                val dirLabel = when (trafficLightDir) {
                                    1 -> "←"; 2 -> "→"; 3 -> "↩"; 4 -> "↑"; 5 -> "↪"; else -> ""
                                }
                                Button(
                                    onClick = { },
                                    modifier = Modifier.size(btnSize).shadow(4.dp, RoundedCornerShape(14.dp)),
                                    colors = ButtonDefaults.buttonColors(containerColor = if (trafficLightState >= 0 && trafficLightCountdown > 0) tColor else Color(0xFF475569)),
                                    contentPadding = PaddingValues(0.dp), shape = RoundedCornerShape(14.dp)
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(modifier = Modifier.size(8.dp).clip(androidx.compose.foundation.shape.CircleShape).background(tColor))
                                            if (dirLabel.isNotEmpty()) { Spacer(Modifier.width(2.dp)); Text(dirLabel, fontSize = 9.sp, color = Color(0xFF94A3B8)) }
                                        }
                                        Text(text = if (trafficLightState >= 0 && trafficLightCountdown > 0) "${trafficLightCountdown}s" else "--",
                                            fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (trafficLightState >= 0) tColor else Color.White.copy(alpha = 0.5f))
                                        Text(localized("红绿灯", "Traffic"), fontSize = 7.sp, color = Color.White.copy(alpha = 0.6f))
                                    }
                                }
                            }
                            15 -> {
                                // 📤 数据分享
                                val isSharing = isShareDataEnabled == true
                                Button(
                                    onClick = { onShareDataClick() },
                                    modifier = Modifier.size(btnSize).shadow(4.dp, RoundedCornerShape(14.dp)),
                                    colors = ButtonDefaults.buttonColors(containerColor = if (isSharing) Color(0xFF22C55E) else Color(0xFF475569)),
                                    contentPadding = PaddingValues(0.dp), shape = RoundedCornerShape(14.dp)
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                        Text("📤", fontSize = 16.sp)
                                        Text(text = if (isSharing) localized("分发", "On") else localized("分享", "Share"),
                                            fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (isSharing) Color(0xFF22C55E) else Color.White.copy(alpha = 0.5f))
                                    }
                                }
                            }
                            16 -> {
                                // 🔄 重启设备 — 点击后弹确认框，手动确认才发送（防误触）
                                Button(
                                    onClick = { powerConfirmCommand = NetworkManager.CMD_REBOOT },
                                    modifier = Modifier.size(btnSize).shadow(4.dp, RoundedCornerShape(14.dp)),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF97316)),
                                    contentPadding = PaddingValues(0.dp), shape = RoundedCornerShape(14.dp)
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                        Icon(Icons.Default.Refresh, "重启", Modifier.size(24.dp), tint = Color.White)
                                        Text(localized("重启", "Reboot"), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
                            }
                            17 -> {
                                // ⏻ 关机设备 — 点击后弹确认框，手动确认才发送（防误触）
                                Button(
                                    onClick = { powerConfirmCommand = NetworkManager.CMD_SHUTDOWN },
                                    modifier = Modifier.size(btnSize).shadow(4.dp, RoundedCornerShape(14.dp)),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                    contentPadding = PaddingValues(0.dp), shape = RoundedCornerShape(14.dp)
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                        Icon(Icons.Default.PowerSettingsNew, "关机", Modifier.size(24.dp), tint = Color.White)
                                        Text(localized("关机", "Power Off"), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
                            }
                            else -> {}
                        }
                    }
                }
            }
                } // Column
            } // Card

            // 超车参数调节区域
            if (overtakeMode != 0) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = Surface600, thickness = 1.dp)
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OvertakeParameterRow(localized("最小超车速度", "Min Overtake Speed"), "", 70f, 50f, 120f, 5f, "overtake_param_min_speed_kph", context)
                    OvertakeParameterRow(localized("速度差阈值", "Speed Diff Threshold"), "", 10f, 5f, 30f, 1f, "overtake_param_speed_diff_kph", context)
                }
            }
        }

        // 关于弹窗
        if (showAboutDialog) {
            Dialog(onDismissRequest = { showAboutDialog = false }) {
                Card(
                    modifier = Modifier.fillMaxWidth(0.78f).widthIn(max = 340.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DialogBackground),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("🚗 CP搭子3.0", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text(localized("兼容 openpilot 全系 CP 用户", "Compatible with all openpilot CP users"), fontSize = 12.sp, color = TextSecondary)
                        Text(localized("含欧尚 Z6 等车型", "Including Oushan Z6 and more"), fontSize = 11.sp, color = Color(0xFF94A3B8))
                        Box(modifier = Modifier.fillMaxWidth().clickable { showAboutDialog = false }.padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
                            Image(painter = painterResource(id = R.drawable.sponsor), contentDescription = localized("赞助图片", "Sponsor image"),
                                modifier = Modifier.fillMaxWidth(0.9f).heightIn(max = 160.dp), contentScale = ContentScale.Fit)
                        }
                        HorizontalDivider(color = Surface600)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            AboutFeatureItem("🗺️", localized("高德车机版导航联动", "AMap Auto Integration"))
                            AboutFeatureItem("🚗", localized("openpilot 驾驶辅助", "openpilot Driving Assist"))
                            AboutFeatureItem("📊", localized("车道感知与盲区监测", "Lane Awareness & Blindspot"))
                            AboutFeatureItem("🔄", localized("自动超车与变道", "Auto Overtake & Lane Change"))
                            AboutFeatureItem("📤", localized("数据分享与分发", "Data Sharing & Distribution"))
                        }
                        Spacer(Modifier.height(4.dp))
                        Button(onClick = { showAboutDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = PrimaryLight), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Text(localized("知道了", "Got it"), fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }

        // 重启/关机二次确认弹窗（防误触：确认后才通过 UDP 7706 发送系统命令）
        powerConfirmCommand?.let { cmd ->
            val isReboot = cmd == NetworkManager.CMD_REBOOT
            Dialog(onDismissRequest = { powerConfirmCommand = null }) {
                Card(
                    modifier = Modifier.fillMaxWidth(0.78f).widthIn(max = 340.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DialogBackground),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(if (isReboot) "🔄" else "⏻", fontSize = 28.sp)
                        Text(
                            localized(if (isReboot) "确认重启设备？" else "确认关机设备？", if (isReboot) "Reboot device?" else "Power off device?"),
                            fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary
                        )
                        Text(
                            localized(
                                if (isReboot) "设备将立即重启，openpilot 会中断运行" else "设备将立即关机，需重新上电才能启动",
                                if (isReboot) "The device will reboot immediately. openpilot will stop running." else "The device will power off immediately and needs to be powered on again to start."
                            ),
                            fontSize = 12.sp, color = TextSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { powerConfirmCommand = null },
                                colors = ButtonDefaults.buttonColors(containerColor = Surface600),
                                shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f)
                            ) {
                                Text(localized("取消", "Cancel"), color = TextPrimary, fontWeight = FontWeight.Medium)
                            }
                            Button(
                                onClick = {
                                    powerConfirmCommand = null
                                    android.widget.Toast.makeText(
                                        context,
                                        localized("命令已发送", "Command sent"),
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                    // 优先走 NetworkManager 专用通道（3次重发防丢包）；兜底走通用命令回调
                                    val nm = networkManager
                                    if (nm != null) nm.sendSystemCommand(cmd) else onSendCommand(cmd, "")
                                    onDismiss?.invoke()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = if (isReboot) Color(0xFFF97316) else Color(0xFFEF4444)),
                                shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f)
                            ) {
                                Text(localized("确认执行", "Confirm"), color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * 高阶功能弹窗 - 3x3 九宫格
     */
    @Composable
    fun AdvancedFunctionsDialog(
        onDismiss: () -> Unit,
        onSendCommand: (String, String) -> Unit,
        onSendRoadLimitSpeed: () -> Unit,
        onLaunchAmap: () -> Unit,
        onSendNavConfirmation: () -> Unit,
        onPageChange: (Page) -> Unit,
        isOpenpilotActive: Boolean,
        carrotManFields: CarrotManFields,
        networkManager: NetworkManager,
        context: android.content.Context
    ) {
        Dialog(onDismissRequest = onDismiss) {
            Card(
                modifier = Modifier
                    .width(60.dp * 3 + 10.dp * 2 + 12.dp * 2)
                    .wrapContentHeight()
                    .padding(0.dp),
                colors = CardDefaults.cardColors(containerColor = DialogBackground),
                elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AdvancedFunctionsContent(
                        onSendCommand = onSendCommand,
                        onSendRoadLimitSpeed = onSendRoadLimitSpeed,
                        onLaunchAmap = onLaunchAmap,
                        onSendNavConfirmation = onSendNavConfirmation,
                        onPageChange = onPageChange,
                        isOpenpilotActive = isOpenpilotActive,
                        carrotManFields = carrotManFields,
                        networkManager = networkManager,
                        context = context,
                        onDismiss = onDismiss,
                        onSearchClick = null,
                        onShow7706Debug = null,
                    )
                }
            }
        }
    }

    @Composable
    private fun AboutFeatureItem(icon: String, text: String) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(icon, fontSize = 16.sp)
            Text(text, fontSize = 13.sp, color = TextSecondary)
        }
    }
    
    /**
     * 🆕 超车参数调节行组件
     * 显示参数名称、当前值，并提供加减按钮
     */
    @Composable
    private fun OvertakeParameterRow(
        label: String,
        unit: String,
        defaultValue: Float,
        minValue: Float,
        maxValue: Float,
        step: Float,
        prefKey: String,
        context: android.content.Context,
        displayMultiplier: Float = 1f  // 显示倍数（用于百分比等）
    ) {
        val prefs = remember { context.getSharedPreferences("CarrotAmap", android.content.Context.MODE_PRIVATE) }
        var currentValue by remember { 
            mutableStateOf(prefs.getFloat(prefKey, defaultValue).coerceIn(minValue, maxValue))
        }
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 参数名称（左侧，不占用多余空间）
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = TextSecondary
            )
            
            // 减号按钮、数值、加号按钮（右侧，更紧凑排列）
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 减号按钮（更小）
                Button(
                    onClick = {
                        val newValue = (currentValue - step).coerceAtLeast(minValue)
                        currentValue = newValue
                        prefs.edit().putFloat(prefKey, newValue).apply()
                        android.util.Log.d("MainActivity", "🔧 调整参数 $label: $newValue")
                    },
                    modifier = Modifier.size(24.dp),
                    enabled = currentValue > minValue,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (currentValue > minValue) ButtonDecel else Surface400
                    ),
                    contentPadding = PaddingValues(0.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "−",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                // 当前值显示（移除单位，紧凑宽度）
                Text(
                    text = "${(currentValue * displayMultiplier).toInt()}${if (unit.isNotEmpty()) " $unit" else ""}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryLight,
                    modifier = Modifier.width(35.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Clip
                )

                // 加号按钮（更小）
                Button(
                    onClick = {
                        val newValue = (currentValue + step).coerceAtMost(maxValue)
                        currentValue = newValue
                        prefs.edit().putFloat(prefKey, newValue).apply()
                        android.util.Log.d("MainActivity", "🔧 调整参数 $label: $newValue")
                    },
                    modifier = Modifier.size(24.dp),
                    enabled = currentValue < maxValue,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (currentValue < maxValue) ButtonAccel else Surface400
                    ),
                    contentPadding = PaddingValues(0.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "+",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
    
    /**
     * 向高德地图车机版发送 POI 导航广播（KEY_TYPE 10038）
     */
    fun sendPoiNavigationToAmapAuto(
        context: Context,
        poiName: String,
        destLat: Double,
        destLon: Double
    ) {
        try {
            val launched = tryLaunchAmapAutoApp(context)
            val intent = Intent("AUTONAVI_STANDARD_BROADCAST_RECV").apply {
                putExtra("KEY_TYPE", 10038)
                putExtra("SOURCE_APP", "Navipilot")
                putExtra("POINAME", poiName)
                putExtra("LAT", destLat)
                putExtra("LON", destLon)
                putExtra("DEV", 0)
                putExtra("STYLE", 0)
                setPackage(AMAP_AUTO_PKG)
                flags = Intent.FLAG_INCLUDE_STOPPED_PACKAGES
            }
            sendAmapAutoBroadcastAfterLaunch(
                context,
                launched,
                intent,
                "✅ 高德车机版 POI 导航已发送: $poiName"
            )
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "❌ 高德车机版 POI 准备失败: ${e.message}", e)
        }
    }

    /**
     * 发送回家导航指令给高德地图
     */
    fun sendHomeNavigationToAmap(context: android.content.Context) {
        try {
            android.util.Log.i("MainActivity", "🏠 发送一键回家指令给高德地图")
            val launched = tryLaunchAmapAutoApp(context)
            val homeIntent = android.content.Intent("AUTONAVI_STANDARD_BROADCAST_RECV").apply {
                putExtra("KEY_TYPE", 10040)
                putExtra("SOURCE_APP", "Navipilot")
                putExtra("DEST", 0)
                putExtra("IS_START_NAVI", 0)
                setPackage(AMAP_AUTO_PKG)
                flags = android.content.Intent.FLAG_INCLUDE_STOPPED_PACKAGES
            }
            sendAmapAutoBroadcastAfterLaunch(
                context,
                launched,
                homeIntent,
                "✅ 一键回家导航广播已发送"
            )
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "❌ 发送一键回家指令失败: ${e.message}", e)
        }
    }
    
    /**
     * 发送导航到公司指令给高德地图
     */
    fun sendCompanyNavigationToAmap(context: android.content.Context) {
        try {
            android.util.Log.i("MainActivity", "🏢 发送导航到公司指令给高德地图")
            val launched = tryLaunchAmapAutoApp(context)
            val companyIntent = android.content.Intent("AUTONAVI_STANDARD_BROADCAST_RECV").apply {
                putExtra("KEY_TYPE", 10040)
                putExtra("SOURCE_APP", "Navipilot")
                putExtra("DEST", 1)
                putExtra("IS_START_NAVI", 0)
                setPackage(AMAP_AUTO_PKG)
                flags = android.content.Intent.FLAG_INCLUDE_STOPPED_PACKAGES
            }
            sendAmapAutoBroadcastAfterLaunch(
                context,
                launched,
                companyIntent,
                "✅ 导航到公司广播已发送"
            )
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "❌ 发送导航到公司指令失败: ${e.message}", e)
        }
    }

    /**
     * 启动模拟导航功能
     */
    fun startSimulatedNavigation(context: android.content.Context, carrotManFields: CarrotManFields) {
        try {
            android.util.Log.i("MainActivity", "🔧 启动模拟导航功能")
            
            val currentLat = when {
                carrotManFields.vpPosPointLat != 0.0 -> {
                    android.util.Log.i("MainActivity", "✅ 使用实时GPS坐标（vpPosPointLat）: ${carrotManFields.vpPosPointLat}")
                    carrotManFields.vpPosPointLat
                }
                carrotManFields.latitude != 0.0 -> {
                    android.util.Log.i("MainActivity", "✅ 使用备用GPS坐标（latitude）: ${carrotManFields.latitude}")
                    carrotManFields.latitude
                }
                else -> {
                    val fallbackLat = getCurrentLocationLatitude(context)
                    android.util.Log.w("MainActivity", "⚠️ GPS坐标不可用，使用SharedPreferences坐标: $fallbackLat")
                    fallbackLat
                }
            }
            
            val currentLon = when {
                carrotManFields.vpPosPointLon != 0.0 -> {
                    android.util.Log.i("MainActivity", "✅ 使用实时GPS坐标（vpPosPointLon）: ${carrotManFields.vpPosPointLon}")
                    carrotManFields.vpPosPointLon
                }
                carrotManFields.longitude != 0.0 -> {
                    android.util.Log.i("MainActivity", "✅ 使用备用GPS坐标（longitude）: ${carrotManFields.longitude}")
                    carrotManFields.longitude
                }
                else -> {
                    val fallbackLon = getCurrentLocationLongitude(context)
                    android.util.Log.w("MainActivity", "⚠️ GPS坐标不可用，使用SharedPreferences坐标: $fallbackLon")
                    fallbackLon
                }
            }
            
            if (currentLat == 0.0 || currentLon == 0.0) {
                android.util.Log.w("MainActivity", "⚠️ GPS坐标无效，使用默认起点坐标（北京）")
                // 目的地：无锡硕放国际机场 (31.4944°N, 120.4290°E)
                sendSimulatedNavigationIntent(context, 39.9042, 116.4074, 31.4944, 120.4290)
                return
            }
            
            // 目的地：无锡硕放国际机场
            val destLat = 31.4944
            val destLon = 120.4290
            
            if (kotlin.math.abs(currentLat - destLat) < 0.001 && kotlin.math.abs(currentLon - destLon) < 0.001) {
                android.util.Log.w("MainActivity", "⚠️ 起点和终点坐标过于接近，调整目的地位置（使用苏州）")
                // 备用目的地：苏州市中心
                sendSimulatedNavigationIntent(context, currentLat, currentLon, 31.2989, 120.5853)
            } else {
                sendSimulatedNavigationIntent(context, currentLat, currentLon, destLat, destLon)
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "❌ 启动模拟导航失败: ${e.message}", e)
        }
    }
    
    /**
     * 发送模拟导航Intent
     */
    private fun sendSimulatedNavigationIntent(
        context: android.content.Context,
        startLat: Double, 
        startLon: Double, 
        destLat: Double, 
        destLon: Double
    ) {
        try {
            val launched = tryLaunchAmapAutoApp(context)
            val intent = android.content.Intent("AUTONAVI_STANDARD_BROADCAST_RECV").apply {
                putExtra("KEY_TYPE", 10076)
                putExtra("SOURCE_APP", "Navipilot")
                putExtra("EXTRA_SLAT", startLat)
                putExtra("EXTRA_SLON", startLon)
                putExtra("EXTRA_SNAME", localized("当前位置", "Current Location"))
                putExtra("EXTRA_DLAT", destLat)
                putExtra("EXTRA_DLON", destLon)
                putExtra("EXTRA_DNAME", localized("无锡硕放国际机场", "Wuxi Shuofang Intl Airport"))
                putExtra("EXTRA_DEV", 0)
                putExtra("EXTRA_M", 0)
                putExtra("KEY_RECYLE_SIMUNAVI", true)
                setPackage(AMAP_AUTO_PKG)
                flags = android.content.Intent.FLAG_INCLUDE_STOPPED_PACKAGES
            }
            sendAmapAutoBroadcastAfterLaunch(
                context,
                launched,
                intent,
                "✅ 模拟导航广播已发送给高德地图车机版"
            )
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "❌ 发送模拟导航广播失败: ${e.message}", e)
        }
    }
    
    /**
     * 获取当前位置纬度
     */
    private fun getCurrentLocationLatitude(context: android.content.Context): Double {
        return try {
            val carrotPrefs = context.getSharedPreferences("CarrotAmap", android.content.Context.MODE_PRIVATE)
            val devicePrefs = context.getSharedPreferences("device_prefs", android.content.Context.MODE_PRIVATE)
            // 🔧 修复：使用CoordinatePreferences读取坐标，避免Float精度损失
            var lat = com.jixiexiaoge.drivingassist.utils.CoordinatePreferences.getCoordinate(carrotPrefs, "vpPosPointLat", 0.0)
            if (lat == 0.0) {
                lat = com.jixiexiaoge.drivingassist.utils.CoordinatePreferences.getCoordinate(devicePrefs, "vpPosPointLat", 0.0)
            }
            if (lat != 0.0) {
                android.util.Log.i("MainActivity", "✅ 获取到当前位置纬度: $lat")
                lat
            } else {
                android.util.Log.w("MainActivity", "⚠️ 未找到当前位置，使用默认起点坐标（北京）")
                39.9042
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "❌ 获取当前位置纬度失败: ${e.message}", e)
            39.9042
        }
    }
    
    /**
     * 获取当前位置经度
     */
    private fun getCurrentLocationLongitude(context: android.content.Context): Double {
        return try {
            val carrotPrefs = context.getSharedPreferences("CarrotAmap", android.content.Context.MODE_PRIVATE)
            val devicePrefs = context.getSharedPreferences("device_prefs", android.content.Context.MODE_PRIVATE)
            // 🔧 修复：使用CoordinatePreferences读取坐标，避免Float精度损失
            var lon = com.jixiexiaoge.drivingassist.utils.CoordinatePreferences.getCoordinate(carrotPrefs, "vpPosPointLon", 0.0)
            if (lon == 0.0) {
                lon = com.jixiexiaoge.drivingassist.utils.CoordinatePreferences.getCoordinate(devicePrefs, "vpPosPointLon", 0.0)
            }
            if (lon != 0.0) {
                android.util.Log.i("MainActivity", "✅ 获取到当前位置经度: $lon")
                lon
            } else {
                android.util.Log.w("MainActivity", "⚠️ 未找到当前位置，使用默认起点坐标（北京）")
                116.4074
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "❌ 获取当前位置经度失败: ${e.message}", e)
            116.4074
        }
    }
}

/**
 * 速度圆环Compose组件（紧凑版，优化字体）
 */
@Composable
fun SpeedIndicatorCompose(
    value: Int,
    color: Color,
    label: String,
    onClick: (() -> Unit)? = null
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(48.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(42.dp)
                .clickable(enabled = onClick != null) { onClick?.invoke() }
        ) {
            Canvas(
                modifier = Modifier.fillMaxSize()
            ) {
                val radius = size.minDimension / 2f - 5.dp.toPx()
                drawCircle(
                    color = Color.White,
                    radius = radius,
                    center = center
                )
                drawCircle(
                    color = color,
                    radius = radius,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 5.dp.toPx())
                )
            }

            Text(
                text = value.toString(),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }

        if (label.isNotEmpty()) {
            Text(
                text = label,
                fontSize = 10.sp,
                color = TextTertiary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 12.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}



/**
 * 速度圆环组件（优化版 - 增强无障碍支持）
 */
@Composable
fun SpeedRing(
    speed: Int,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = "$label $speed ${localized("公里每小时", "kilometers per hour")}"
            }
            .background(color.copy(alpha = 0.15f), androidx.compose.foundation.shape.CircleShape)
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "$speed",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = label,
                fontSize = 10.sp,
                color = color.copy(alpha = 0.8f)
            )
        }
    }
}
