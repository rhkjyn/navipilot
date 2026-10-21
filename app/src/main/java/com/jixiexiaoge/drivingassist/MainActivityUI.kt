package com.jixiexiaoge.drivingassist

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.jixiexiaoge.drivingassist.utils.CoordinatePreferences
import android.content.SharedPreferences
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.jixiexiaoge.drivingassist.ui.theme.Surface700
import com.jixiexiaoge.drivingassist.ui.theme.Surface800
import com.jixiexiaoge.drivingassist.ui.theme.Surface900
import com.jixiexiaoge.drivingassist.ui.theme.TextSecondary
import com.jixiexiaoge.drivingassist.ui.theme.TextTertiary
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jixiexiaoge.drivingassist.ui.components.AutoSwitchExperimentPage
import com.jixiexiaoge.drivingassist.ui.components.CameraVisionPage
import com.jixiexiaoge.drivingassist.ui.components.Carrot7706JsonDebugOverlay
import com.jixiexiaoge.drivingassist.ui.components.DeviceWebPage
import com.jixiexiaoge.drivingassist.ui.components.SearchResult
import com.jixiexiaoge.drivingassist.ui.components.SearchProvider
import com.jixiexiaoge.drivingassist.ui.components.searchPlaces
import com.jixiexiaoge.drivingassist.ui.theme.NavipilotTheme
import com.jixiexiaoge.drivingassist.ui.utils.localized
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.text.input.ImeAction

/**
 * MainActivity UI组件管理类
 * 负责所有UI组件的定义和界面逻辑
 */
class MainActivityUI(
    private val core: MainActivityCore
) {

    /**
     * 设置用户界面
     */
    @Composable
    fun SetupUserInterface() {
        NavipilotTheme {
            val appContext = LocalContext.current

            
            // 不使用 Scaffold 的 bottomBar，改为手动叠加，让导航栏浮在内容上方
            Box(modifier = Modifier.fillMaxSize()) {
                // 拦截返回键：正常返回
                BackHandler(enabled = true) {
                    if (core.currentPage !is Page.Home) {
                        core.currentPage = Page.Home
                    }
                    // 主页时什么都不做，防止退出应用
                }

                // 主内容区域（占满全屏）
                Box(modifier = Modifier.fillMaxSize()) {
                    // 根据当前页面显示不同内容
                    when (core.currentPage) {
                        is Page.Home -> HomePage(
                            userType = core.userType.value,
                            carrotManFields = core.carrotManFields.value,
                            wsConnected = core.wsConnected.value,
                            wsDataTimeout = core.wsDataTimeout.value,
                            onSendCommand = { command, arg -> core.sendCarrotCommand(command, arg) },
                            onSendRoadLimitSpeed = { core.sendCurrentRoadLimitSpeed() },
                            onLaunchAmap = { core.launchAmapAuto() },
                            onSendNavConfirmation = { core.sendNavigationConfirmationManually() },
                            onPageChange = { page ->
                                core.currentPage = page
                            }
                        )
                        is Page.Experiment -> AutoSwitchExperimentPage(
                            onBack = {
                                core.currentPage = Page.Home
                            },
                            conditionalExperimentManager = core.getConditionalExperimentManagerSafely(),
                            carrotParamClient = core.getCarrotParamClientSafely(),
                            carrotManFields = core.carrotManFields
                        )
                        is Page.DeviceWeb -> {
                            val ip = core.networkManager?.getCurrentDeviceIP()
                            DeviceWebPage(
                                url = if (ip != null) "http://$ip:7000" else "",
                                onBack = { core.currentPage = Page.Home }
                            )
                        }
                        is Page.CameraVision -> {
                            val ip = core.networkManager?.getCurrentDeviceIP()
                            CameraVisionPage(
                                deviceIp = ip,
                                wsClient = core.carrotWsClient,
                                onBack = { core.currentPage = Page.Home }
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * 主页组件
     */
    @Composable
    private fun HomePage(
        userType: Int,
        carrotManFields: CarrotManFields,
        wsConnected: Boolean,
        wsDataTimeout: Boolean,
        onSendCommand: (String, String) -> Unit,
        onSendRoadLimitSpeed: () -> Unit,
        onLaunchAmap: () -> Unit,
        onSendNavConfirmation: () -> Unit,
        onPageChange: (Page) -> Unit, // 页面切换回调
    ) {
        val scrollState = rememberScrollState()
        val data by core.xiaogeData
        // 数据卡片展开/折叠状态
        var isDataCardExpanded by remember { mutableStateOf(true) }
        var isVideoExpanded by remember { mutableStateOf(false) }
        // 高阶功能对话框状态
        var showAdvancedDialog by remember { mutableStateOf(false) }
        // 点击首页预览条：全屏 7706 JSON 调试
        var show7706JsonDebug by remember { mutableStateOf(false) }
        val carrotFieldsLive by core.carrotManFields

        // ===== 动作触发器（面板按钮点击时递增，OsmMapView 监听执行内部逻辑）=====
        var searchShowTrigger by remember { mutableIntStateOf(0) }
        var homeNavTrigger by remember { mutableIntStateOf(0) }
        var homeNavLongTrigger by remember { mutableIntStateOf(0) }
        var companyNavTrigger by remember { mutableIntStateOf(0) }
        var companyNavLongTrigger by remember { mutableIntStateOf(0) }

        val mapContext = LocalContext.current

        // ===== 面板显示用状态 =====
        var homeAddressSet by remember { mutableStateOf(false) }
        var companyAddressSet by remember { mutableStateOf(false) }

        // 搜索对话框状态
        var showSearchDialog by remember { mutableStateOf(false) }
        var searchQuery by remember { mutableStateOf("") }
        var searchResults by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
        var isSearching by remember { mutableStateOf(false) }
        var selectedProvider by remember { mutableStateOf(SearchProvider.GAODE) }
        var searchServiceName by remember { mutableStateOf("") }
        val searchScope = rememberCoroutineScope()

        // 监听搜索触发（必须在 showSearchDialog 声明之后）
        LaunchedEffect(searchShowTrigger) {
            if (searchShowTrigger > 0) showSearchDialog = true
        }

        // 监听回家导航触发
        LaunchedEffect(homeNavTrigger) {
            if (homeNavTrigger > 0) {
                val prefs = mapContext.getSharedPreferences("map_addresses", Context.MODE_PRIVATE)
                val name = prefs.getString("home_name", "") ?: ""
                val lat = try { prefs.getFloat("home_lat", 0f).toDouble() } catch (_: ClassCastException) { prefs.getString("home_lat", "0")?.toDoubleOrNull() ?: 0.0 }
                val lon = try { prefs.getFloat("home_lon", 0f).toDouble() } catch (_: ClassCastException) { prefs.getString("home_lon", "0")?.toDoubleOrNull() ?: 0.0 }
                if (name.isNotEmpty() && lat != 0.0 && lon != 0.0) {
                    MainActivityUIComponents.sendPoiNavigationToAmapAuto(mapContext, name, lat, lon)
                }
            }
        }
        // 监听清除回家地址（长按）
        LaunchedEffect(homeNavLongTrigger) {
            if (homeNavLongTrigger > 0) {
                mapContext.getSharedPreferences("map_addresses", Context.MODE_PRIVATE).edit()
                    .remove("home_name").remove("home_lat").remove("home_lon").apply()
                homeAddressSet = false
            }
        }
        // 监听回公司导航触发
        LaunchedEffect(companyNavTrigger) {
            if (companyNavTrigger > 0) {
                val prefs = mapContext.getSharedPreferences("map_addresses", Context.MODE_PRIVATE)
                val name = prefs.getString("company_name", "") ?: ""
                val lat = try { prefs.getFloat("company_lat", 0f).toDouble() } catch (_: ClassCastException) { prefs.getString("company_lat", "0")?.toDoubleOrNull() ?: 0.0 }
                val lon = try { prefs.getFloat("company_lon", 0f).toDouble() } catch (_: ClassCastException) { prefs.getString("company_lon", "0")?.toDoubleOrNull() ?: 0.0 }
                if (name.isNotEmpty() && lat != 0.0 && lon != 0.0) {
                    MainActivityUIComponents.sendPoiNavigationToAmapAuto(mapContext, name, lat, lon)
                }
            }
        }
        // 监听清除公司地址（长按）
        LaunchedEffect(companyNavLongTrigger) {
            if (companyNavLongTrigger > 0) {
                mapContext.getSharedPreferences("map_addresses", Context.MODE_PRIVATE).edit()
                    .remove("company_name").remove("company_lat").remove("company_lon").apply()
                companyAddressSet = false
            }
        }

        val mapService = core.userSelectedMode
        val gpsAccuracy = carrotManFields.accuracy
        val positionMode = when {
            gpsAccuracy < 3.0 -> "RTK"
            gpsAccuracy < 10.0 -> "DGPS"
            else -> "GPS"
        }

        // 导航起点与 OsmMapView 一致：优先 WGS84 的 latitude/longitude，否则用 X 系列车位（避免 0,0 起点导致「起终点参数错误」）
        val currentNavStartLat = when {
            carrotManFields.latitude != 0.0 && carrotManFields.longitude != 0.0 -> carrotManFields.latitude
            carrotManFields.vpPosPointLat != 0.0 && carrotManFields.vpPosPointLon != 0.0 -> carrotManFields.vpPosPointLat
            else -> 0.0
        }
        val currentNavStartLon = when {
            carrotManFields.latitude != 0.0 && carrotManFields.longitude != 0.0 -> carrotManFields.longitude
            carrotManFields.vpPosPointLat != 0.0 && carrotManFields.vpPosPointLon != 0.0 -> carrotManFields.vpPosPointLon
            else -> 0.0
        }

        // comma 设备连接状态：0=未连接, 1=已连接, 2=异常
        val commaConnectionState = core.getNetworkClientSafely()?.let { client ->
            when {
                core.wsDataTimeout.value -> 2
                client.isRunning() && client.getCurrentDevice() != null -> 1
                else -> 0
            }
        } ?: 0

        // ===== 垂直布局：数据面板 + 底部控制栏 =====
        val cruiseSetSpeed = try { carrotManFields.vCruiseKph.toInt() } catch (_: Exception) { 0 }
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶部：数据面板（占据主要空间），无地图时纯深色背景
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Surface900)
            ) {
                // 数据面板（居中显示）
                HomeControlPanel(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    carrotManFields = carrotManFields,
                    userType = userType,
                    cruiseSetSpeed = cruiseSetSpeed,
                    carCruiseSpeed = try { carrotManFields.carcruiseSpeed.toInt() } catch (_: Exception) { 0 },
                    carrotParamClient = core.getCarrotParamClientSafely(),
                    homeAddressSet = homeAddressSet,
                    companyAddressSet = companyAddressSet,
                    commaConnectionState = commaConnectionState,
                    onShowAdvancedDialog = { showAdvancedDialog = true },
                    onPageChange = { page: Page -> core.currentPage = page },
                    onSearchClick = { searchShowTrigger++ },
                    onHomeNavClick = { homeNavTrigger++ },
                    onHomeNavLongClick = { homeNavLongTrigger++ },
                    onCompanyNavClick = { companyNavTrigger++ },
                    onCompanyNavLongClick = { companyNavLongTrigger++ },
                    onSendCommand = onSendCommand,
                    onSendRoadLimitSpeed = onSendRoadLimitSpeed,
                    onLaunchAmap = onLaunchAmap,
                    onSendNavConfirmation = onSendNavConfirmation,
                    onLanePanelClick = { show7706JsonDebug = true },
                    vehicleData = data?.let { vd ->
                        com.jixiexiaoge.drivingassist.data.VehicleData(
                            carState = vd.carState?.let { cs ->
                                com.jixiexiaoge.drivingassist.data.CarState(
                                    vEgo = cs.vEgo,
                                    steeringAngleDeg = cs.steeringAngleDeg,
                                    leftLatDist = cs.leftLatDist,
                                    leftBlindspot = cs.leftBlindspot,
                                    rightBlindspot = cs.rightBlindspot
                                )
                            },
                            modelV2 = vd.modelV2?.let { mv ->
                                com.jixiexiaoge.drivingassist.data.ModelV2(
                                    leadX = mv.lead0?.x ?: 0f,
                                    leadV = mv.lead0?.v ?: 0f,
                                    leadProb = mv.lead0?.prob ?: 0f,
                                    laneLineProbs = mv.laneLineProbs,
                                    leftDist = mv.meta?.distanceToRoadEdgeLeft ?: 0f,
                                    rightDist = mv.meta?.distanceToRoadEdgeRight ?: 0f
                                )
                            }
                        )
                    }
                )
            }

        }

        // 7706 JSON 调试面板（点击车道盲区面板触发）
        if (show7706JsonDebug) {
            Carrot7706JsonDebugOverlay(
                fields = carrotFieldsLive,
                networkClient = core.getNetworkClientSafely(),
                v2ClientSnapshot = core.naviV2Client?.debugSnapshot(),
                v2StreamSnapshot = core.naviStreamManager?.debugSnapshot(),
                xiaogeLatestPacket = core.xiaogeTcpClient?.latestPacket,
                xiaogeLatestData = core.xiaogeTcpClient?.latestData,
                xiaogeConnected = core.xiaogeTcpClient?.isConnected ?: false,
                xiaogePackets = core.xiaogeTcpClient?.packetsReceived ?: 0,
                onDismiss = { show7706JsonDebug = false }
            )
        }

        // 搜索对话框
        var navHistory by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
        val navHistoryScope = rememberCoroutineScope()
        LaunchedEffect(Unit) {
            withContext(kotlinx.coroutines.Dispatchers.IO) {
                navHistory = MainActivityUISearch.loadNavHistory(mapContext)
            }
        }
        if (showSearchDialog) {
            AlertDialog(
                onDismissRequest = { showSearchDialog = false; searchQuery = ""; searchResults = emptyList() },
                title = {
                    Column {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text(localized("搜索地点...", "Search places..."), fontSize = 13.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                if (searchQuery.isNotBlank()) {
                                    isSearching = true
                                    searchScope.launch {
                                        val response = searchPlaces(searchQuery, selectedProvider, mapContext)
                                        searchResults = response.results
                                        searchServiceName = response.serviceName
                                        isSearching = false
                                    }
                                }
                            }),
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = ""; searchResults = emptyList() }) {
                                        Icon(Icons.Default.Clear, localized("清除", "Clear"), modifier = Modifier.size(22.dp))
                                    }
                                }
                            },
                            leadingIcon = {
                                if (isSearching) {
                                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.Search, localized("搜索", "Search"), modifier = Modifier.size(22.dp))
                                }
                            }
                        )
                        // 搜索引擎选择
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SearchProvider.entries.forEach { provider ->
                                FilterChip(
                                    selected = selectedProvider == provider,
                                    onClick = {
                                        selectedProvider = provider
                                        searchServiceName = ""
                                        searchResults = emptyList()
                                    },
                                    label = { Text(provider.labelCn, fontSize = 11.sp) },
                                    leadingIcon = if (selectedProvider == provider) {
                                        { Icon(Icons.Default.Check, null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                                    } else null
                                )
                            }
                        }
                    }
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                        if (searchResults.isNotEmpty()) {
                            // 搜索服务标签
                            if (searchServiceName.isNotEmpty()) {
                                val badgeColor = when (searchServiceName) {
                                    "高德地图" -> Color(0xFFFF6B00)
                                    else -> Color(0xFF10B981)
                                }
                                Surface(color = badgeColor.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp)) {
                                    Text("🔍 $searchServiceName", fontSize = 10.sp, color = badgeColor,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                                Spacer(Modifier.height(4.dp))
                            }
                            searchResults.forEach { result ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        MainActivityUIComponents.sendPoiNavigationToAmapAuto(mapContext, result.name, result.lat, result.lon)
                                        navHistoryScope.launch {
                                            MainActivityUISearch.saveNavHistory(mapContext, result.name, result.lon, result.lat)
                                            navHistory = MainActivityUISearch.loadNavHistory(mapContext)
                                        }
                                        showSearchDialog = false; searchQuery = ""; searchResults = emptyList()
                                    }.padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(result.name, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                        if (result.address.isNotBlank()) Text(result.address, fontSize = 11.sp, color = Color(0xFF94A3B8), maxLines = 1)
                                    }
                                    // 保存到家/公司按钮
                                    if (!homeAddressSet) {
                                        TextButton(onClick = { navHistoryScope.launch { MainActivityUISearch.saveAddress(mapContext, "home", result.name, result.lon, result.lat) }; homeAddressSet = true },
                                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
                                            Text("🏠", fontSize = 14.sp)
                                        }
                                    }
                                    if (!companyAddressSet) {
                                        TextButton(onClick = { navHistoryScope.launch { MainActivityUISearch.saveAddress(mapContext, "company", result.name, result.lon, result.lat) }; companyAddressSet = true },
                                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
                                            Text("🏢", fontSize = 14.sp)
                                        }
                                    }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            }
                        } else if (!isSearching && searchQuery.isNotEmpty()) {
                            Text(localized("无搜索结果", "No results"), fontSize = 13.sp, color = Color(0xFF94A3B8),
                                modifier = Modifier.padding(vertical = 20.dp))
                        } else if (!isSearching && searchQuery.isEmpty() && navHistory.isNotEmpty()) {
                            Text(localized("🕐 最近导航", "🕐 Recent"), fontSize = 11.sp, color = TextSecondary,
                                fontWeight = FontWeight.Medium, modifier = Modifier.padding(vertical = 4.dp))
                            navHistory.forEach { hist ->
                                ListItem(
                                    headlineContent = { Text(hist.name, fontSize = 13.sp, fontWeight = FontWeight.Medium) },
                                    modifier = Modifier.clickable {
                                        MainActivityUIComponents.sendPoiNavigationToAmapAuto(mapContext, hist.name, hist.lat, hist.lon)
                                        navHistoryScope.launch {
                                            MainActivityUISearch.saveNavHistory(mapContext, hist.name, hist.lon, hist.lat)
                                            navHistory = MainActivityUISearch.loadNavHistory(mapContext)
                                        }
                                        showSearchDialog = false
                                        searchQuery = ""
                                    }
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSearchDialog = false; searchQuery = ""; searchResults = emptyList() }) {
                        Text(localized("关闭", "Close"))
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp)
            )
        }
    }

    /** 首页控制台：仅圆形图标（无障碍用语见 contentDescription，无底部文字） */
    @Composable
    private fun HomeControlPanelCircleIcon(
        modifier: Modifier = Modifier,
        background: Color,
        icon: ImageVector,
        contentDescription: String,
        iconTint: Color = Color.White,
        onClick: () -> Unit
    ) {
        val boxDp = 48.dp
        val iconDp = 22.dp
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(boxDp)
                    .clip(CircleShape)
                    .background(background)
                    .clickable { onClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription, Modifier.size(iconDp), tint = iconTint)
            }
        }
    }

    /** 家/公司：仅 emoji 圆形按钮；短按导航、长按清除地址（无障碍用语见 contentDescription） */
    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun HomeControlPanelEmojiAddress(
        modifier: Modifier = Modifier,
        emoji: String,
        accessibilityLabel: String,
        addressSet: Boolean,
        onShortClick: () -> Unit,
        onLongClick: () -> Unit
    ) {
        val bg = if (addressSet) Color(0xFF1E293B).copy(alpha = 0.75f) else Color(0xFF334155).copy(alpha = 0.85f)
        val boxDp = 48.dp
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(boxDp)
                    .semantics { contentDescription = accessibilityLabel }
                    .clip(CircleShape)
                    .background(bg)
                    .combinedClickable(onClick = onShortClick, onLongClick = onLongClick),
                contentAlignment = Alignment.Center
            ) {
                Text(text = emoji, fontSize = 17.sp)
            }
        }
    }

    /** 速度环（无底部文字；modifier 可传 weight(1f) 等分） */
    @Composable
    private fun HomePanelSpeedRing(
        modifier: Modifier = Modifier,
        value: Int,
        color: Color,
        onClick: () -> Unit
    ) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            com.jixiexiaoge.drivingassist.ui.components.SpeedRingButton(
                value = value,
                color = color,
                onClick = onClick,
                diameter = 48.dp,
                valueTextSize = 11.sp
            )
        }
    }

    /** 将用户类型数字转为可读文本（与 ProfilePage 一致） */
    private fun userTypeDisplayName(userType: Int): String = when (userType) {
        -1 -> localized("管理员", "Admin")
        0 -> localized("未知用户", "Unknown")
        1 -> localized("新用户", "New User")
        2 -> localized("支持者", "Supporter")
        3 -> localized("赞助者", "Sponsor")
        4 -> localized("铁粉", "Super Fan")
        else -> localized("未知类型", "Unknown Type")
    }

    @Composable
    private fun CollapsibleCard(
        title: String,
        icon: String,
        initiallyExpanded: Boolean = false,
        content: @Composable () -> Unit
    ) {
        var expanded by remember { mutableStateOf(initiallyExpanded) }
        Card(
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = Surface800.copy(alpha = 0.85f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(icon, fontSize = 12.sp)
                        Spacer(Modifier.width(4.dp))
                        Text(title, fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                    }
                    Text(
                        text = if (expanded) "▲" else "▼",
                        fontSize = 9.sp,
                        color = TextTertiary
                    )
                }
                AnimatedVisibility(visible = expanded) {
                    content()
                }
            }
        }
    }

    /** 搜索对话框底部：待提取完成时此处为完整搜索实现 */
    // 已提取到 MainActivityUISearch.kt:
    //   saveNavHistory()、saveAddress()、loadNavHistory()

    /**
     * 首页功能控制面板
     */
    @Composable
    private fun HomeControlPanel(
        modifier: Modifier = Modifier,
        carrotManFields: CarrotManFields,
        userType: Int,
        cruiseSetSpeed: Int,
        carCruiseSpeed: Int,
        carrotParamClient: CarrotParamClient?,
        homeAddressSet: Boolean,
        companyAddressSet: Boolean,
        commaConnectionState: Int = 0,
        onShowAdvancedDialog: () -> Unit,
        onPageChange: (Page) -> Unit,
        onSearchClick: () -> Unit,
        onHomeNavClick: () -> Unit,
        onHomeNavLongClick: () -> Unit,
        onCompanyNavClick: () -> Unit,
        onCompanyNavLongClick: () -> Unit,
        onSendCommand: (String, String) -> Unit = { _, _ -> },
        onSendRoadLimitSpeed: () -> Unit = {},
        onLaunchAmap: () -> Unit = {},
        onSendNavConfirmation: () -> Unit = {},
        vehicleData: com.jixiexiaoge.drivingassist.data.VehicleData? = null,
        deviceStatus: com.jixiexiaoge.drivingassist.data.DeviceStatus? = null,
        onLanePanelClick: () -> Unit = {},
    ) {
        val panelContext = LocalContext.current
        val scrollState = rememberScrollState()

        // 解析设备端 ExperimentalMode 参数（与旧 SecondarySection 一致）
        fun parseExperimentalMode(value: Any?): Boolean? {
            return when (value) {
                is Boolean -> value
                is Int -> value != 0
                is Long -> value != 0L
                is Double -> value != 0.0
                is Float -> value != 0f
                is String -> value == "1" || value.equals("true", ignoreCase = true)
                else -> null
            }
        }

        val coroutineScope = rememberCoroutineScope()
        val tileBg = Surface800.copy(alpha = 0.72f)
        val searchBgTarget = when (commaConnectionState) {
            1 -> Color(0xFF10B981).copy(alpha = 0.9f)
            2 -> Color(0xFFEF4444).copy(alpha = 0.9f)
            else -> tileBg
        }
        val searchBg by animateColorAsState(
            targetValue = searchBgTarget,
            animationSpec = tween(durationMillis = 500),
            label = "searchBgColor"
        )
        var isExperimentalMode by remember(carrotParamClient) { mutableStateOf<Boolean?>(null) }

        LaunchedEffect(carrotParamClient) {
            if (carrotParamClient == null) {
                isExperimentalMode = null
                return@LaunchedEffect
            }
            val result = carrotParamClient.getParams("ExperimentalMode")
            isExperimentalMode = result.getOrNull()?.get("ExperimentalMode")?.let(::parseExperimentalMode)
        }

        // 从设备读取 ShareData 参数值
        var isShareDataEnabled by remember(carrotParamClient) { mutableStateOf<Boolean?>(null) }

        LaunchedEffect(carrotParamClient) {
            if (carrotParamClient == null) {
                isShareDataEnabled = null
                return@LaunchedEffect
            }
            val result = carrotParamClient.getParams("ShareData")
            isShareDataEnabled = result.getOrNull()?.get("ShareData")?.let(::parseExperimentalMode)
        }

        val experimentBg = when (isExperimentalMode) {
            true -> Color(0xFF8B5CF6).copy(alpha = 0.92f)
            false -> Color(0xFF06B6D4).copy(alpha = 0.92f)
            null -> tileBg
        }

        val onCruiseSetClick: () -> Unit = {
            onShowAdvancedDialog()
        }

        val onExperimentClick: () -> Unit = {
            if (carrotParamClient == null) {
                android.widget.Toast.makeText(
                    panelContext,
                    localized("设备未连接", "Device not connected"),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            } else {
                coroutineScope.launch {
                    val currentMode = isExperimentalMode ?: carrotParamClient
                        .getParams("ExperimentalMode")
                        .getOrNull()
                        ?.get("ExperimentalMode")
                        ?.let(::parseExperimentalMode)
                        ?: false
                    val targetMode = !currentMode
                    val result = carrotParamClient.setExperimentalMode(targetMode)
                    if (result.isSuccess) {
                        isExperimentalMode = targetMode
                        android.widget.Toast.makeText(
                            panelContext,
                            if (targetMode) localized("已切换实验模式", "Exp Mode ON")
                            else localized("已切换Chill模式", "Chill Mode ON"),
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        android.widget.Toast.makeText(
                            panelContext,
                            result.exceptionOrNull()?.message
                                ?: localized("切换失败", "Switch failed"),
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }

        val onShareDataClick: () -> Unit = {
            if (carrotParamClient == null) {
                android.widget.Toast.makeText(
                    panelContext,
                    localized("设备未连接", "Device not connected"),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            } else {
                coroutineScope.launch {
                    val currentMode = isShareDataEnabled ?: carrotParamClient
                        .getParams("ShareData")
                        .getOrNull()
                        ?.get("ShareData")
                        ?.let(::parseExperimentalMode)
                        ?: false
                    val targetMode = !currentMode
                    val result = carrotParamClient.setParam("ShareData", if (targetMode) 1 else 0)
                    if (result.isSuccess) {
                        isShareDataEnabled = targetMode
                        android.widget.Toast.makeText(
                            panelContext,
                            if (targetMode) localized("数据分发已开启", "Share ON")
                            else localized("数据分发已关闭", "Share OFF"),
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        android.widget.Toast.makeText(
                            panelContext,
                            result.exceptionOrNull()?.message
                                ?: localized("切换失败", "Switch failed"),
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }

        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(colors = listOf(Surface900, Surface800)))
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 高级功能（嵌入到主页面板，置顶显示）
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Surface800.copy(alpha = 0.85f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        MainActivityUIComponents.AdvancedFunctionsContent(
                            onSendCommand = onSendCommand,
                            onSendRoadLimitSpeed = onSendRoadLimitSpeed,
                            onLaunchAmap = onLaunchAmap,
                            onSendNavConfirmation = onSendNavConfirmation,
                            onPageChange = onPageChange,
                            isOpenpilotActive = carrotManFields.active,
                            carrotManFields = carrotManFields,
                            networkManager = try { core.networkManager } catch (_: Exception) { null },
                            context = panelContext,
                            onDismiss = null,
                            onSearchClick = { onSearchClick() },
                            onShow7706Debug = { onLanePanelClick() },
                            commaConnectionState = commaConnectionState,
                            onHomeNavClick = onHomeNavClick,
                            onHomeNavLongClick = onHomeNavLongClick,
                            onCompanyNavClick = onCompanyNavClick,
                            onCompanyNavLongClick = onCompanyNavLongClick,
                            homeAddressSet = homeAddressSet,
                            companyAddressSet = companyAddressSet,
                            onExperimentClick = { onExperimentClick() },
                            onShareDataClick = { onShareDataClick() },
                            isShareDataEnabled = isShareDataEnabled,
                            trafficLightState = carrotManFields.trafficLightState,
                            trafficLightCountdown = carrotManFields.trafficLightCountdown,
                            trafficLightDir = carrotManFields.amap_traffic_light_dir,
                        )
                    }
                }

                }  // Column 结束
        }
    }

    /** 导航状态面板（H 120 ↑ 样式） */
    @Composable
    private fun NavStatusPanel(
        roadName: String,
        limitSpeed: Int,
        nTBTTurnType: Int
    ) {
        val turnArrow = when (nTBTTurnType) {
            1 -> "↑"   // 直行
            2 -> "→"   // 右转
            3 -> "←"   // 左转
            4 -> "↻"   // 环岛
            else -> "—"
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1E293B).copy(alpha = 0.9f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (roadName.isNotEmpty()) {
                    Text(
                        text = roadName.take(2),
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (limitSpeed > 0) limitSpeed.toString() else "—",
                    color = Color(0xFFFBBF24),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = turnArrow,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    /** 道路信息卡片（道路名 + 限速 + 前车距离） */
    @Composable
    private fun RoadInfoCard(
        roadName: String,
        limitSpeed: Int,
        leadDist: Float,
        leadProb: Float,
        modifier: Modifier = Modifier
    ) {
        Row(
            modifier = modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF1E293B).copy(alpha = 0.9f))
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 道路名 + 限速
            Column(horizontalAlignment = Alignment.Start) {
                Text(
                    text = if (roadName.isNotEmpty()) roadName else localized("未知道路", "Unknown Rd"),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("⛔", fontSize = 11.sp)
                    Spacer(Modifier.width(2.dp))
                    Text(
                        text = if (limitSpeed > 0) "${limitSpeed} km/h" else localized("-- km/h", "-- km/h"),
                        color = Color(0xFFFBBF24),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            // 前车距离
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = localized("🚘 前车", "🚘 Lead"),
                    fontSize = 9.sp,
                    color = Color(0xFF94A3B8)
                )
                Text(
                    text = if (leadProb > 0.3f) "${leadDist.toInt()} m" else localized("-- m", "-- m"),
                    color = if (leadProb > 0.3f) Color(0xFF22C55E) else Color(0xFF64748B),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    /** 车道感知仪表盘 - 俯视三车道视图 */
    @Composable
    private fun LaneBlindspotPanel(
        modifier: Modifier = Modifier,
        laneLineProbs: List<Float>,
        leftDist: Float,
        rightDist: Float,
        leftBlindspot: Boolean,
        rightBlindspot: Boolean,
        leftLatDist: Float,
        leadX: Float = 0f,
        leadProb: Float = 0f,
        positionMode: String = "GPS",
        gpsAccuracy: Float = 0f
    ) {
        val ll0 = laneLineProbs.getOrNull(0) ?: 0.5f  // 左路缘
        val ll1 = laneLineProbs.getOrNull(1) ?: 0.5f  // 左车道线
        val ll2 = laneLineProbs.getOrNull(2) ?: 0.5f  // 右车道线
        val ll3 = laneLineProbs.getOrNull(3) ?: 0.5f  // 右路缘
        val hasLead = leadProb > 0.3f && leadX > 0

        fun laneColor(p: Float) = if (p > 0.6f) Color(0xFF60A5FA) else Color(0xFF334155)
        fun dashColor(p: Float) = if (p > 0.6f) Color(0xFF94A3B8) else Color(0xFF334155)

        Column(
            modifier = modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Surface800.copy(alpha = 0.85f))
                .border(1.dp, Surface700.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .padding(6.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Text(localized("🚦 车道感知", "🚦 Lane"), fontSize = 9.sp, color = Color(0xFF64748B))
                // GPS 定位精度徽章（右上角）
                val modeColor = when (positionMode) {
                    "RTK" -> Color(0xFF22C55E)
                    "DGPS" -> Color(0xFFFBBF24)
                    else -> Color(0xFF64748B)
                }
                val modeLabel = when (positionMode) {
                    "RTK" -> "RTK"
                    "DGPS" -> "DGPS"
                    else -> "GPS"
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .background(modeColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                        .border(0.5.dp, modeColor.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = "📡 $modeLabel",
                        fontSize = 8.sp,
                        color = modeColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.height(4.dp))

            // 俯视道路图
            Box(modifier = Modifier.fillMaxWidth().height(36.dp)) {
                // 道路底色
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width; val h = size.height
                    val laneW = w / 3f         // 每车道宽度
                    val leftEdge = 0f
                    val rightEdge = w

                    // 道路沥青底色
                    drawRect(Color(0xFF141E33))

                    // 路缘线（第0条和第3条）
                    drawLine(laneColor(ll0), androidx.compose.ui.geometry.Offset(leftEdge + 2f, 0f), androidx.compose.ui.geometry.Offset(leftEdge + 2f, h), 3f)
                    drawLine(laneColor(ll3), androidx.compose.ui.geometry.Offset(rightEdge - 2f, 0f), androidx.compose.ui.geometry.Offset(rightEdge - 2f, h), 3f)

                    // 车道虚线（第1条和第2条）
                    fun drawDashed(x: Float, color: Color) {
                        var y = 0f
                        while (y < h) { drawLine(color, androidx.compose.ui.geometry.Offset(x, y), androidx.compose.ui.geometry.Offset(x, (y + 10f).coerceAtMost(h)), 2f); y += 18f }
                    }
                    drawDashed(laneW, dashColor(ll1))
                    drawDashed(laneW * 2f, dashColor(ll2))

                    // 侧方车辆 - 左右盲区
                    fun drawSideCar(cx: Float, blind: Boolean) {
                        if (blind) {
                            for (i in 1..2) drawCircle(Color(0xFFEF4444).copy(alpha = 0.15f), 18f + i * 5f, androidx.compose.ui.geometry.Offset(cx, h * 0.35f))
                            drawRoundRect(Color(0xFF6B7280), androidx.compose.ui.geometry.Offset(cx - 7f, h * 0.35f - 10f), androidx.compose.ui.geometry.Size(14f, 20f), androidx.compose.ui.geometry.CornerRadius(3f))
                            drawLine(Color(0xFFEF4444), androidx.compose.ui.geometry.Offset(cx, h * 0.35f + 10f), androidx.compose.ui.geometry.Offset(cx, h * 0.45f), 1f)
                        }
                    }
                    drawSideCar(laneW / 2f, leftBlindspot)
                    drawSideCar(laneW * 2.5f, rightBlindspot)

                    // 前车（中间车道顶部）
                    if (hasLead) {
                        val s = (leadX.coerceIn(15f, 80f) / 80f).coerceIn(0.3f, 1f)
                        val carW = (16f / s).coerceIn(14f, 26f)
                        val carH = carW * 1.5f
                        val cx = laneW * 1.5f
                        val cy = h * 0.08f + carH / 2f
                        for (i in 1..3) drawCircle(Color(0xFF3B82F6).copy(alpha = 0.08f), carW * 0.6f + i * 5f, androidx.compose.ui.geometry.Offset(cx, cy))
                        // 前车车身 - 俯视
                        drawRoundRect(Color(0xFF94A3B8), androidx.compose.ui.geometry.Offset(cx - carW / 2, cy - carH / 2), androidx.compose.ui.geometry.Size(carW, carH), androidx.compose.ui.geometry.CornerRadius(3f))
                        drawRoundRect(Color(0xFF6B7280).copy(alpha = 0.5f), androidx.compose.ui.geometry.Offset(cx - carW * 0.3f, cy - carH / 3), androidx.compose.ui.geometry.Size(carW * 0.6f, carH * 0.2f), androidx.compose.ui.geometry.CornerRadius(1f))
                        // 连接到本车虚线
                        drawLine(Color(0xFF475569).copy(alpha = 0.5f), androidx.compose.ui.geometry.Offset(cx, cy + carH / 2), androidx.compose.ui.geometry.Offset(cx, h * 0.55f), 1f)
                    }

                    // 本车（中间车道底部）
                    val myCX = laneW * 1.5f
                    val myCY = h * 0.72f
                    val myW = 24f
                    val myH = 38f
                    // 车身蓝色光晕
                    for (i in 1..2) drawCircle(Color(0xFF3B82F6).copy(alpha = 0.08f), myW * 0.7f + i * 6f, androidx.compose.ui.geometry.Offset(myCX, myCY))
                    // 车身（深蓝）
                    drawRoundRect(Color(0xFF2563EB), androidx.compose.ui.geometry.Offset(myCX - myW / 2, myCY - myH / 2), androidx.compose.ui.geometry.Size(myW, myH), androidx.compose.ui.geometry.CornerRadius(5f))
                    // 车头（浅蓝高亮）
                    drawRoundRect(Color(0xFF60A5FA).copy(alpha = 0.4f), androidx.compose.ui.geometry.Offset(myCX - myW * 0.35f, myCY - myH / 2), androidx.compose.ui.geometry.Size(myW * 0.7f, myH * 0.25f), androidx.compose.ui.geometry.CornerRadius(2f))
                    // 挡风玻璃
                    drawRoundRect(Color(0xFF1E3A5F).copy(alpha = 0.7f), androidx.compose.ui.geometry.Offset(myCX - myW * 0.3f, myCY - myH * 0.15f), androidx.compose.ui.geometry.Size(myW * 0.6f, myH * 0.2f), androidx.compose.ui.geometry.CornerRadius(1f))
                    // 左大灯
                    drawCircle(Color(0xFFFBBF24).copy(alpha = 0.6f), 2f, androidx.compose.ui.geometry.Offset(myCX - myW * 0.35f, myCY - myH / 2 + 3f))
                    // 右大灯
                    drawCircle(Color(0xFFFBBF24).copy(alpha = 0.6f), 2f, androidx.compose.ui.geometry.Offset(myCX + myW * 0.35f, myCY - myH / 2 + 3f))
                    // 尾灯
                    drawCircle(Color(0xFFEF4444).copy(alpha = 0.5f), 2f, androidx.compose.ui.geometry.Offset(myCX - myW * 0.3f, myCY + myH / 2 - 3f))
                    drawCircle(Color(0xFFEF4444).copy(alpha = 0.5f), 2f, androidx.compose.ui.geometry.Offset(myCX + myW * 0.3f, myCY + myH / 2 - 3f))
                }
            }

            Spacer(Modifier.height(4.dp))
            // 底部车道线置信度 + 盲区状态条
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                // 四条车道线概率条
                val probs = listOf(ll0, ll1, ll2, ll3)
                probs.forEachIndexed { i, p ->
                    val barColor = when {
                        p > 0.8f -> Color(0xFF22C55E)
                        p > 0.5f -> Color(0xFFFBBF24)
                        else -> Color(0xFF475569)
                    }
                    val label = when (i) { 0 -> "L0"; 1 -> "L1"; 2 -> "L2"; else -> "L3" }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Box(modifier = Modifier.fillMaxWidth().height(2.dp).clip(RoundedCornerShape(2.dp)).background(barColor.copy(alpha = p.coerceIn(0.2f, 1f))))
                        Text(label, fontSize = 6.sp, color = Color(0xFF64748B))
                    }
                }
                // 盲区指示
                Box(modifier = Modifier.width(1.dp).height(12.dp).background(Color(0xFF334155)))
                Text(if (leftBlindspot) "⚠" else "●", fontSize = 7.sp, color = if (leftBlindspot) Color(0xFFEF4444) else Color(0xFF22C55E))
                Text(if (rightBlindspot) "⚠" else "●", fontSize = 7.sp, color = if (rightBlindspot) Color(0xFFEF4444) else Color(0xFF22C55E))
            }
        }
    }

@Composable
private fun LaneIndicator(
    label: String,
    lineProb: Float,
    laneProb: Float,
    hasBlindspot: Boolean,
    isLeft: Boolean
) {
    val lineColor = if (lineProb > 0.5f) Color(0xFF60A5FA) else Color(0xFF374151)
    val laneColor = if (laneProb > 0.5f) Color(0xFF475569) else Color(0xFF1E293B)
    val activeColor = Color(0xFF3B82F6)
    val warnColor = Color(0xFFEF4444)

    Column(
        modifier = Modifier.width(36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 路缘线
        Box(
            modifier = Modifier
                .width(2.dp).height(20.dp)
                .background(lineColor)
        )
        // 车道区域（有盲区时变红）
        Box(
            modifier = Modifier
                .width(28.dp).height(36.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (hasBlindspot) warnColor.copy(alpha = 0.2f) else laneColor),
            contentAlignment = Alignment.Center
        ) {
            if (hasBlindspot) {
                // 盲区车辆
                Box(
                    modifier = Modifier
                        .size(14.dp, 18.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFF6B7280))
                )
            }
            // 车道线置信度指示条
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(20.dp)
                    .align(if (isLeft) Alignment.CenterEnd else Alignment.CenterStart)
                    .background(activeColor.copy(alpha = laneProb.coerceIn(0.2f, 1f)))
            )
        }
        Box(
            modifier = Modifier
                .width(2.dp).height(20.dp)
                .background(lineColor)
        )
    }
}

    /** 驾驶状态面板（openpilot 状态 + 巡航 + 红绿灯倒计时） */
    @Composable
    private fun DrivingStatusPanel(
        active: Boolean,
        vEgo: Int,
        vCruise: Int,
        isExperimental: Boolean?,
        trafficState: Int,
        trafficCountdown: Int,
        modifier: Modifier = Modifier
    ) {
        // trafficState: -1=无数据, 0=绿灯, 1=红灯, 2=黄灯
        val (tColor, tText) = when (trafficState) {
            0 -> Color(0xFF22C55E) to localized("绿灯", "Green")
            1 -> Color(0xFFEF4444) to localized("红灯", "Red")
            2 -> Color(0xFFFBBF24) to localized("黄灯", "Yellow")
            else -> Color(0xFF64748B) to localized("--", "--")
        }
        Row(
            modifier = modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF1E293B).copy(alpha = 0.9f))
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // openpilot 状态
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (active) "🟢" else "⚪",
                    fontSize = 16.sp
                )
                Spacer(Modifier.width(4.dp))
                Column {
                    Text(
                        text = if (active) localized("已激活", "Active")
                               else localized("待机", "Standby"),
                        fontSize = 11.sp,
                        color = if (active) Color(0xFF22C55E) else Color(0xFF94A3B8),
                        fontWeight = FontWeight.Bold
                    )
                    val expText = when (isExperimental) {
                        true -> localized("🧪 实验", "🧪 Exp")
                        false -> localized("❄️ Chill", "❄️ Chill")
                        null -> "--"
                    }
                    Text(expText, fontSize = 8.sp, color = Color(0xFF64748B))
                }
            }
            // 速度信息
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${vEgo}/${vCruise}",
                    fontSize = 14.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = localized("km/h", "km/h"),
                    fontSize = 8.sp,
                    color = Color(0xFF94A3B8)
                )
            }
            // 红绿灯倒计时
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(tColor)
                )
                Spacer(Modifier.width(4.dp))
                Column {
                    Text(
                        text = if (trafficCountdown > 0) "${trafficCountdown}s" else tText,
                        fontSize = 13.sp,
                        color = tColor,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = localized("🚥", "🚥"),
                        fontSize = 8.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }
        }
    }

    /**
     * 状态信息卡片（小型数据展示单元）
     */
    @Composable
    private fun StatusCard(
        label: String,
        value: String,
        unit: String
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                color = Color(0xFF64748B),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = value,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                if (unit.isNotEmpty()) {
                    Text(
                        text = unit,
                        color = Color(0xFF94A3B8),
                        fontSize = 10.sp,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
            }
        }
    }
}
