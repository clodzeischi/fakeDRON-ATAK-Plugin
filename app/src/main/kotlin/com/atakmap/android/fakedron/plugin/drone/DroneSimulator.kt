package com.atakmap.android.fakedron.plugin

import com.atakmap.android.fakedron.plugin.drone.FlightStatus
import com.atakmap.android.maps.MapView
import com.atakmap.coremap.maps.coords.GeoCalculations
import com.atakmap.coremap.maps.coords.GeoPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class DroneSimulator(
    private val scope: CoroutineScope,
    private val onStateUpdate: (FlightStatus, Int, GeoPoint?, Boolean) -> Unit
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
    private var actualAltitude      = 0
    private var targetAltitude      = 0
    private var currentPosition: GeoPoint? = null
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

    private fun tick() {
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
                val dist = GeoCalculations.distanceTo(pos, rally)
                if (dist <= ARRIVAL_RADIUS_M) {
                    rallyPoint   = null
                    rallyCleared = true
                    if (isRTH) {
                        // arrival during RTH triggers automatic landing
                        isRTH          = false
                        targetAltitude = 0
                    }
                } else {
                    val bearing     = GeoCalculations.bearingTo(pos, rally)
                    currentPosition = GeoCalculations.pointAtDistance(
                        pos, bearing, MOVEMENT_STEP_M
                    )
                }
            }
        }

        // ── Stop loop on landing ──────────────────────────────────────────────
        if (newStatus == FlightStatus.IDLE) {
            simulationJob?.cancel()
        }

        onStateUpdate(newStatus, actualAltitude, currentPosition, rallyCleared)
    }

    // 50m north of operator using ATAK native bearing/distance
    fun spawnOffset(origin: GeoPoint): GeoPoint {
        return GeoCalculations.pointAtDistance(origin, 0.0, SPAWN_OFFSET_M)
    }
}