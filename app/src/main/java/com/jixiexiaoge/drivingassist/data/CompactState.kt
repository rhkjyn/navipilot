package com.jixiexiaoge.drivingassist.data

import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Carrot compact_state 协议解析器（CVS1/CVB1）。
 *
 * 与 web 驾驶页 Carrot Vision 使用同一数据源：/ws/compact_state。
 * 帧格式:
 *   CVS1: "CVS1" + serviceId(u8) + flags(u8) + sequence(u16 LE) + body
 *   CVB1: "CVB1" + count(u16) + [u32 len + frame] x count
 * body 按 compact_state.py SERVICE_SCHEMAS 顺序定长编码
 * （标量 LE；列表先 u16/u8 长度前缀；struct 内联；struct_list 前有 u8 数量）。
 */

// ---------- 服务 ID ----------
object CompactServices {
    const val CAR_STATE = 1
    const val CONTROLS_STATE = 2
    const val DEVICE_STATE = 3
    const val PERIPHERAL_STATE = 4
    const val CARROT_MAN = 5
    const val SELFDRIVE_STATE = 6
    const val GPS = 7
    const val LONGITUDINAL_PLAN = 8
    const val MODEL_V2 = 9
    const val LIVE_CALIBRATION = 10
    const val ROAD_CAMERA_STATE = 11
    const val LATERAL_PLAN = 12
    const val RADAR_STATE = 13
    const val CAR_CONTROL = 14
    const val LIVE_DELAY = 15
    const val LIVE_TORQUE = 16
    const val LIVE_PARAMS = 17
    const val LIVE_TRACKS = 18
    const val CAMERA_ODOMETRY = 19
    const val LIVE_POSE = 20

    /** 网页 Carrot Vision 请求的全部服务（HUD+OVERLAY+TRACK+AR 分组） */
    val ALL = listOf(
        "carState", "controlsState", "deviceState", "peripheralState", "carrotMan",
        "selfdriveState", "gpsLocationExternal", "longitudinalPlan",
        "modelV2", "liveCalibration", "roadCameraState", "lateralPlan",
        "radarState", "carControl", "liveDelay", "liveTorqueParameters",
        "liveParameters", "liveTracks", "cameraOdometry", "livePose",
    )
}

// ---------- 场景数据结构 ----------
data class RadarLead(
    val dRel: Float = 0f, val yRel: Float = 0f, val vLead: Float = 0f,
    val aLead: Float = 0f, val modelProb: Float = 0f, val status: Boolean = false,
    val fcw: Boolean = false,
)

data class TrackPoint(val id: Long = 0, val dRel: Float = 0f, val yRel: Float = 0f, val vRel: Float = 0f)

data class LeadV3(
    val prob: Float = 0f,
    val x: FloatArray = FloatArray(0), // 距离轨迹 m
    val y: FloatArray = FloatArray(0), // 横向偏移 m
    val v: FloatArray = FloatArray(0), // 速度 m/s
)

data class CompactState(
    // carState
    var vEgo: Float = 0f,
    var vCruise: Float = 0f,
    var steerDeg: Float = 0f,
    var brakeHold: Boolean = false,
    var softHoldActive: Int = 0,
    var carrotCruise: Int = 0,
    var leftBlindspot: Boolean = false,
    var rightBlindspot: Boolean = false,
    var leftBlinker: Boolean = false,
    var rightBlinker: Boolean = false,
    var gear: Int = 0,
    var fuel: Float = 0f,
    // controlsState
    var engaged: Boolean = false,
    // selfdriveState
    var alertType: String = "",
    var alertText1: String = "",
    var alertText2: String = "",
    var alertStatus: Int = 0,
    // deviceState / peripheral
    var cpuTemp: Float = 0f,
    var voltage: Long = 0,
    // carrotMan 导航信息
    var roadLimitKph: Int = 0,
    var turnInfo: Int = 0,
    var distToTurn: Int = 0,
    var tbtText: String = "",
    var posRoadName: String = "",
    var vehicleNaviActive: Boolean = false,
    // gpsLocationExternal
    var gpsHasFix: Boolean = false,
    // modelV2
    var modelFrameId: Long = 0,
    var frameDropPerc: Float = 0f,
    var path: List<FloatArray> = emptyList(), // 主路径 position (x,y,z) 米, 相机系 z 向上
    var laneLines: List<List<FloatArray>> = emptyList(), // 4 条 x N 个 (x,y,z) 米, 相机系
    var laneLineProbs: FloatArray = FloatArray(0),
    var roadEdges: List<List<FloatArray>> = emptyList(),
    var leadsV3: List<LeadV3> = emptyList(),
    // liveCalibration
    var calStatus: Int = 0,           // 1 = calibrated
    var rpyCalib: FloatArray = FloatArray(0), // [roll, pitch, yaw] 弧度
    var height: FloatArray = FloatArray(0),
    // lateralPlan
    var laneChangeState: Int = 0,     // 1 = preLaneChange
    var laneChangeDirection: Int = 0, // 1 = left, 2 = right
    // longitudinalPlan
    var planSpeed: Float = 0f,        // speeds[0] m/s（目标车速）
    var tFollow: Float = 0f,
    var desiredDistance: Float = 0f,
    var myDrivingMode: Int = 0,       // 1 ECO / 2 SAFE / 3 NORM / 4 FAST
    var xState: Int = 0,
    var cruiseTarget: Float = 0f,     // eco 巡航目标 m/s
    // radarState
    var leadOne: RadarLead = RadarLead(),
    var leadTwo: RadarLead = RadarLead(),
    var leadRight: RadarLead = RadarLead(),
    var leadLeft: RadarLead = RadarLead(),
    // liveTracks
    var tracks: List<TrackPoint> = emptyList(),
    // 时间戳
    var recvAt: Long = System.currentTimeMillis(),
) {
    fun lead() = if (leadOne.status) leadOne else null
}

// ---------- 顺序读取器 ----------
private class FieldReader(data: ByteArray) {
    private val dis = DataInputStream(ByteArrayInputStream(data))
    fun bool(): Boolean = dis.readUnsignedByte() != 0
    fun i8(): Int = dis.readByte().toInt()
    fun u8(): Int = dis.readUnsignedByte()
    fun i16(): Int = dis.readShort().toInt()
    fun u16(): Int = dis.readUnsignedShort()
    fun i32(): Int = dis.readInt()
    fun u32(): Long = dis.readInt().toLong() and 0xFFFFFFFFL
    fun u64(): Long = dis.readLong()
    fun f32(): Float = dis.readFloat()
    fun f64(): Double = dis.readDouble()
    fun text(): String {
        val len = dis.readUnsignedShort()
        val bytes = ByteArray(len)
        dis.readFully(bytes)
        return String(bytes, Charsets.UTF_8)
    }
    fun f32List(): FloatArray {
        val n = dis.readUnsignedShort()
        if (n == 0) return FloatArray(0)
        val out = FloatArray(n)
        for (i in 0 until n) out[i] = dis.readFloat()
        return out
    }
    fun cm16List(): FloatArray { // u16, 单位 cm → m
        val n = dis.readUnsignedShort()
        if (n == 0) return FloatArray(0)
        val out = FloatArray(n)
        for (i in 0 until n) out[i] = dis.readUnsignedShort() / 100f
        return out
    }
    fun cmi16List(): FloatArray { // i16 cm → m
        val n = dis.readUnsignedShort()
        if (n == 0) return FloatArray(0)
        val out = FloatArray(n)
        for (i in 0 until n) out[i] = dis.readShort() / 100f
        return out
    }
    fun mmi16List(): FloatArray { // i16 mm → m
        val n = dis.readUnsignedShort()
        if (n == 0) return FloatArray(0)
        val out = FloatArray(n)
        for (i in 0 until n) out[i] = dis.readShort() / 1000f
        return out
    }
    fun f32FirstList(): FloatArray { // 只有 [0] 一个值
        val n = dis.readUnsignedShort()
        if (n == 0) return FloatArray(0)
        return floatArrayOf(dis.readFloat())
    }
    fun skipBytes(n: Int) {
        for (i in 0 until n) dis.readByte()
    }
    fun remaining(): Int = dis.available()
}

// ---------- 解析器 ----------
object CompactStateParser {
    private const val FRAME_MAGIC = 0x31535643 // "CVS1" LE
    private const val BATCH_MAGIC = 0x31425643 // "CVB1" LE

    /** 解析 CVB1 批帧（或单帧兜底），合并各服务字段为一份快照返回 */
    fun parseBatch(data: ByteArray): CompactState? {
        if (data.size < 8) return null
        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        if (buf.int != BATCH_MAGIC) {
            return parseSingle(data)
        }
        val count = buf.short.toInt() and 0xFFFF
        val merged = CompactState()
        var any = false
        for (i in 0 until count) {
            if (buf.remaining() < 4) break
            val len = buf.int
            if (len <= 0 || len > buf.remaining()) break
            val frameBytes = ByteArray(len)
            buf.get(frameBytes)
            if (applyFrame(frameBytes, merged)) any = true
        }
        if (!any) return null
        merged.recvAt = System.currentTimeMillis()
        return merged
    }

    /** 解析单个 CVS1 帧（无合并语义，仅该服务的字段） */
    fun parseSingle(data: ByteArray): CompactState? {
        val state = CompactState()
        if (!applyFrame(data, state)) return null
        state.recvAt = System.currentTimeMillis()
        return state
    }

    /** 把单个 CVS1 帧的字段写入给定快照（批帧合并的关键：逐服务叠加，不覆盖其它服务） */
    private fun applyFrame(data: ByteArray, state: CompactState): Boolean {
        if (data.size < 8) return false
        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        if (buf.int != FRAME_MAGIC) return false
        val serviceId = buf.get().toInt() and 0xFF
        buf.get() // flags
        buf.short // sequence
        val body = ByteArray(buf.remaining())
        buf.get(body)

        when (serviceId) {
            CompactServices.CAR_STATE -> readCarState(body, state)
            CompactServices.CONTROLS_STATE -> readControlsState(body, state)
            CompactServices.DEVICE_STATE -> readDeviceState(body, state)
            CompactServices.PERIPHERAL_STATE -> readPeripheralState(body, state)
            CompactServices.CARROT_MAN -> readCarrotMan(body, state)
            CompactServices.SELFDRIVE_STATE -> readSelfdriveState(body, state)
            CompactServices.GPS -> readGps(body, state)
            CompactServices.LONGITUDINAL_PLAN -> readLongitudinalPlan(body, state)
            CompactServices.MODEL_V2 -> readModelV2(body, state)
            CompactServices.LIVE_CALIBRATION -> readLiveCalibration(body, state)
            CompactServices.LATERAL_PLAN -> readLateralPlan(body, state)
            CompactServices.RADAR_STATE -> readRadarState(body, state)
            CompactServices.CAR_CONTROL -> readCarControl(body, state)
            CompactServices.LIVE_TRACKS -> readLiveTracks(body, state)
            else -> return false // 其它服务本页不展示，跳过
        }
        return true
    }

    // ---- carState ----
    private fun readCarState(data: ByteArray, s: CompactState) {
        val r = FieldReader(data)
        s.vEgo = r.f32()
        r.f32() // aEgo
        r.f32() // vEgoCluster
        s.vCruise = r.f32()
        s.steerDeg = r.f32()
        s.brakeHold = r.bool()
        s.softHoldActive = r.i16()
        s.carrotCruise = r.i16()
        r.i16() // gearStep
        r.f32() // useLaneLineSpeed
        r.bool() // brakeLights
        s.leftBlindspot = r.bool()
        s.rightBlindspot = r.bool()
        r.i16() // leftLaneLine
        r.i16() // rightLaneLine
        s.gear = r.u8() // gearShifter enum
        s.leftBlinker = r.bool()
        s.rightBlinker = r.bool()
        s.fuel = r.f32()
        r.f32() // ureaGauge
        r.skipBytes(16) // tpms struct (4xf32)
        r.bool() // evModeValid
        r.bool() // evModeActive
    }

    // ---- controlsState ----
    private fun readControlsState(data: ByteArray, s: CompactState) {
        val r = FieldReader(data)
        s.engaged = r.bool()
        s.vCruise = r.f32()
        r.bool() // activeLaneLine
        r.f32() // curvature
        r.f32() // desiredCurvature
        r.f32() // actualLateralAccel
        r.f32() // desiredLateralAccel
        r.f32() // lateralOutput
    }

    // ---- deviceState ----
    private fun readDeviceState(data: ByteArray, s: CompactState) {
        val r = FieldReader(data)
        r.i8() // memoryUsagePercent
        r.f32() // freeSpacePercent
        val temps = r.f32List()
        if (temps.isNotEmpty()) s.cpuTemp = temps.maxOrNull() ?: 0f
        r.u8() // deviceType
        r.bool() // started
    }

    // ---- peripheralState ----
    private fun readPeripheralState(data: ByteArray, s: CompactState) {
        s.voltage = FieldReader(data).u32()
    }

    // ---- carrotMan ----
    private fun readCarrotMan(data: ByteArray, s: CompactState) {
        val r = FieldReader(data)
        r.i32() // activeCarrot
        s.roadLimitKph = r.i32()
        r.i32() // xSpdType
        r.i32() // xSpdLimit
        r.i32() // xSpdDist
        r.i32() // xSpdCountDown
        s.turnInfo = r.i32()
        s.distToTurn = r.i32()
        r.i32() // xTurnCountDown
        r.text() // atcType
        s.posRoadName = r.text()
        s.tbtText = r.text()
        r.i32() // desiredSpeed
        r.f32() // xPosLat
        r.f32() // xPosLon
        r.f32() // xPosAngle
        r.f32() // xPosSpeed
        r.i32() // trafficState
        r.i32() // nGoPosDist
        r.i32() // nGoPosTime
        r.text() // szSdiDescr
        r.text() // naviPaths
        r.text() // desiredSource
        s.vehicleNaviActive = r.bool()
        r.i32() // vehicleNaviSpeed
        r.bool() // vehicleNaviSectionActive
        r.bool() // vehicleNaviAvailable
    }

    // ---- selfdriveState ----
    private fun readSelfdriveState(data: ByteArray, s: CompactState) {
        val r = FieldReader(data)
        r.bool() // enabled
        r.u8() // personality
        s.alertStatus = r.u8()
        r.u8() // alertSize
        s.alertType = r.text()
        s.alertText1 = r.text()
        s.alertText2 = r.text()
    }

    // ---- modelV2 ----
    private fun readModelV2(data: ByteArray, s: CompactState) {
        readModelPolys(data, s)
    }

    /** modelV2 变长字段统一读取：position/velocity/laneLines/roadEdges/leadsV3/laneLineProbs 等 */
    private fun readModelPolys(data: ByteArray, s: CompactState) {
        val r = FieldReader(data)
        r.u32() // frameId
        r.u32() // frameIdExtra
        s.path = readPoly(r) // position (x cm16, y mm16, z mm16)
        readPoly(r) // velocity (仅 x)
        // laneLines: struct_list (u8 count) of xyz
        val laneCount = r.u8()
        val lanes = mutableListOf<List<FloatArray>>()
        for (i in 0 until laneCount) {
            val x = r.cm16List()
            val y = r.mmi16List()
            val z = r.mmi16List()
            val n = minOf(x.size, y.size, z.size)
            val pts = ArrayList<FloatArray>(n)
            for (j in 0 until n) pts.add(floatArrayOf(x[j], y[j], z[j]))
            lanes.add(pts)
        }
        s.laneLines = lanes
        s.laneLineProbs = r.f32List()
        // roadEdges
        val edgeCount = r.u8()
        val edges = mutableListOf<List<FloatArray>>()
        for (i in 0 until edgeCount) {
            val x = r.cm16List()
            val y = r.mmi16List()
            val z = r.mmi16List()
            val n = minOf(x.size, y.size, z.size)
            val pts = ArrayList<FloatArray>(n)
            for (j in 0 until n) pts.add(floatArrayOf(x[j], y[j], z[j]))
            edges.add(pts)
        }
        s.roadEdges = edges
        r.f32List() // roadEdgeStds
        // leadsV3
        val leadCount = r.u8()
        val leads = mutableListOf<LeadV3>()
        for (i in 0 until leadCount) {
            val prob = r.f32()
            val x = r.cm16List()
            val y = r.f32FirstList()
            val v = r.f32FirstList()
            leads.add(LeadV3(prob, x, y, v))
        }
        s.leadsV3 = leads
        r.f32List() // laneLineStds
        r.i32() // frameAge
        s.frameDropPerc = r.f32()
        r.f32() // modelExecutionTime
    }

    private fun readPoly(r: FieldReader): List<FloatArray> {
        val x = r.cm16List()
        val y = r.mmi16List()
        val z = r.mmi16List()
        val n = minOf(x.size, y.size, z.size)
        val pts = ArrayList<FloatArray>(n)
        for (j in 0 until n) pts.add(floatArrayOf(x[j], y[j], z[j]))
        return pts
    }

    // ---- gpsLocationExternal ----
    private fun readGps(data: ByteArray, s: CompactState) {
        val r = FieldReader(data)
        r.f64() // latitude
        r.f64() // longitude
        r.f32() // speed
        r.f32() // bearingDeg
        r.f32() // bearingAccuracyDeg
        r.f32() // speedAccuracy
        s.gpsHasFix = r.bool()
        r.f64() // altitude
        r.f32() // horizontalAccuracy
        r.f32() // verticalAccuracy
        r.f64() // unixTimestampMillis
    }

    // ---- longitudinalPlan ----
    private fun readLongitudinalPlan(data: ByteArray, s: CompactState) {
        val r = FieldReader(data)
        r.f32FirstList() // accels
        val speeds = r.f32FirstList()
        if (speeds.isNotEmpty()) s.planSpeed = speeds[0]
        r.f32FirstList() // jerks
        s.tFollow = r.f32()
        s.desiredDistance = r.f32()
        s.myDrivingMode = r.i32()
        s.xState = r.i32()
        r.i32() // trafficState
        r.u8() // longitudinalPlanSource enum
        s.cruiseTarget = r.f32() // 尾部追加字段（schema evolution）
    }

    // ---- liveCalibration ----
    private fun readLiveCalibration(data: ByteArray, s: CompactState) {
        val r = FieldReader(data)
        s.calStatus = r.u8() // enum: 1 = calibrated
        r.i32() // calCycle
        r.i8() // calPerc
        r.i32() // validBlocks
        s.rpyCalib = r.f32List()
        s.height = r.f32List()
    }

    // ---- lateralPlan ----
    private fun readLateralPlan(data: ByteArray, s: CompactState) {
        val r = FieldReader(data)
        r.bool() // useLaneLines
        r.text() // latDebugText
        readPoly(r) // position
        r.f32List() // distances
        s.laneChangeState = r.u8() // enum: 1 = preLaneChange
        s.laneChangeDirection = r.u8() // enum: 1 = left, 2 = right
    }

    // ---- radarState ----
    private fun readRadarState(data: ByteArray, s: CompactState) {
        val r = FieldReader(data)
        s.leadOne = readRadarLead(r)
        s.leadTwo = readRadarLead(r)
        s.leadRight = readRadarLead(r)
        s.leadLeft = readRadarLead(r)
        skipLeadList(r)
        skipLeadList(r)
        skipLeadList(r)
        skipLeadList(r)
        skipLeadList(r)
        skipLeadList(r)
    }

    private fun readRadarLead(r: FieldReader): RadarLead {
        val dRel = r.f32()
        val yRel = r.f32()
        val vRel = r.f32()
        val aRel = r.f32()
        val vLead = r.f32()
        val aLead = r.f32()
        r.f32() // dPath
        r.f32() // vLat
        r.f32() // vLeadK
        r.f32() // aLeadK
        val fcw = r.bool()
        val status = r.bool()
        r.f32() // aLeadTau
        val modelProb = r.f32()
        r.bool() // radar
        r.i32() // radarTrackId
        r.f32() // jLead
        r.f32() // score
        return RadarLead(dRel, yRel, vLead, aLead, modelProb, status, fcw)
    }

    private fun skipLeadList(r: FieldReader) {
        val count = r.u8()
        for (i in 0 until count) readRadarLead(r)
    }

    // ---- carControl ----
    private fun readCarControl(data: ByteArray, s: CompactState) {
        val r = FieldReader(data)
        r.bool() // latActive
        r.bool() // longActive
        r.skipBytes(12) // actuators struct (3xf32)
    }

    // ---- liveTracks ----
    private fun readLiveTracks(data: ByteArray, s: CompactState) {
        val r = FieldReader(data)
        val count = r.u8()
        val tracks = mutableListOf<TrackPoint>()
        for (i in 0 until count) {
            val id = r.u32()
            val dRel = r.f32()
            val yRel = r.f32()
            val vRel = r.f32()
            r.bool() // measured
            r.u8() // radarSource
            tracks.add(TrackPoint(id, dRel, yRel, vRel))
        }
        s.tracks = tracks
    }
}
