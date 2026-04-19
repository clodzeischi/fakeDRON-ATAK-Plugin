package com.atakmap.android.fakedron.plugin

import com.atakmap.android.fakedron.plugin.comms.CotBroadcaster
import com.atakmap.android.fakedron.plugin.drone.FlightStatus
import com.atakmap.coremap.maps.coords.GeoCalculations
import com.atakmap.coremap.maps.coords.GeoPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class DroneSimulator(
    private val scope: CoroutineScope,
    private val broadcaster: CotBroadcaster,
    private val onStateUpdate: (FlightStatus, Int, GeoPoint?, Boolean) -> Unit,
    // We have to inject these lambda wrappers here, otherwise we cannot test
    private val geoDistanceTo: (GeoPoint, GeoPoint) -> Double = { a, b ->
        GeoCalculations.distanceTo(a, b)
    },
    private val geoBearingTo: (GeoPoint, GeoPoint) -> Double = { a, b ->
        GeoCalculations.bearingTo(a, b)
    },
    private val geoPointAtDistance: (GeoPoint, Double, Double) -> GeoPoint = { point, bearing, distance ->
        GeoCalculations.pointAtDistance(point, bearing, distance)
    }
) {
    companion object {
        const val ALTITUDE_STEP_M  = 3
        const val MOVEMENT_STEP_M  = 3.0
        const val TICK_MS          = 16L
        const val LAUNCH_THRESHOLD = 100
        const val ARRIVAL_RADIUS_M = 20.0
        const val SPAWN_OFFSET_M   = 50.0
    }

    private var simulationJob: Job? = null
    internal var actualAltitude      = 0
    private var targetAltitude      = 0
    private var currentPosition: GeoPoint? = null
    private var lastPosition: GeoPoint? = null
    private var rallyPoint: GeoPoint?      = null

    private var isRTH = false

    fun launch(target: Int, spawnPoint: GeoPoint) {
        targetAltitude  = target
        actualAltitude  = 0
        currentPosition = spawnPoint
        rallyPoint      = null
        startLoop()
    }

    fun land() {
        targetAltitude = 0
        isRTH          = false  // landing always clears RTH
    }

    fun startRTH(operatorPosition: GeoPoint) {
        isRTH      = true
        rallyPoint = operatorPosition
    }

    fun cancelRTH() {
        isRTH = false
    }

    fun updateTargetAltitude(target: Int) {
        targetAltitude = target
    }

    fun updateRallyPoint(point: GeoPoint) {
        rallyPoint = point
    }

    fun clearRallyPoint() {
        rallyPoint = null
    }

    fun stop() {
        simulationJob?.cancel()
        simulationJob   = null
        actualAltitude  = 0
        currentPosition = null
        lastPosition    = null    // ← add this
        rallyPoint      = null
        isRTH           = false
    }

    private fun startLoop() {
        simulationJob?.cancel()
        simulationJob = scope.launch {
            while (isActive) {
                delay(TICK_MS)
                tick()
            }
        }
    }

    internal fun tick() {
        // ── Altitude ─────────────────────────────────────────────────────────
        actualAltitude = when {
            actualAltitude < targetAltitude ->
                (actualAltitude + ALTITUDE_STEP_M).coerceAtMost(targetAltitude)
            actualAltitude > targetAltitude ->
                (actualAltitude - ALTITUDE_STEP_M).coerceAtLeast(targetAltitude)
            else -> actualAltitude
        }

        // ── Status ───────────────────────────────────────────────────────────
        val newStatus = when {
            targetAltitude == 0 && actualAltitude == 0 -> FlightStatus.IDLE
            targetAltitude == 0 && actualAltitude > 0  -> FlightStatus.LANDING
            actualAltitude < LAUNCH_THRESHOLD          -> FlightStatus.LAUNCHING
            isRTH                                      -> FlightStatus.RTH
            else                                       -> FlightStatus.FLYING
        }

        // ── Horizontal movement (FLYING only) ────────────────────────────────
        var rallyCleared = false
        if (newStatus == FlightStatus.FLYING || newStatus == FlightStatus.RTH) {
            val rally = rallyPoint
            val pos   = currentPosition
            if (rally != null && pos != null) {
                val dist = geoDistanceTo(pos, rally)
                if (dist <= ARRIVAL_RADIUS_M) {
                    rallyPoint   = null
                    rallyCleared = true
                    if (isRTH) {
                        // arrival during RTH triggers automatic landing
                        isRTH          = false
                        targetAltitude = 0
                    }
                } else {
                    val bearing     = geoBearingTo(pos, rally)
                    currentPosition = geoPointAtDistance(
                        pos, bearing, MOVEMENT_STEP_M
                    )
                }
            }
        }

        broadcastTick(newStatus, currentPosition, actualAltitude)

        onStateUpdate(newStatus, actualAltitude, currentPosition, rallyCleared)

        // ── Stop loop on landing ──────────────────────────────────────────────
        if (newStatus == FlightStatus.IDLE) {
            broadcaster.onLanded()
            simulationJob?.cancel()
        }
    }

    private fun broadcastTick(status: FlightStatus, position: GeoPoint?, altitude: Int) {
        if (position == null) return
        if (status == FlightStatus.IDLE || status == FlightStatus.LANDING) return

        val last   = lastPosition
        val dist   = if (last != null) geoDistanceTo(last, position) else 0.0
        val course = if (last != null && dist > 0.01)
            geoBearingTo(last, position)
        else 0.0
        val speed  = if (last != null && dist > 0.01)
            dist / (TICK_MS / 1000.0)
        else 0.0

        broadcaster.onTick(position, altitude, speed, course)
        lastPosition = position
    }

    // 50m north of operator using ATAK native bearing/distance
    fun spawnOffset(origin: GeoPoint): GeoPoint {
        return geoPointAtDistance(origin, 0.0, SPAWN_OFFSET_M)
    }
}