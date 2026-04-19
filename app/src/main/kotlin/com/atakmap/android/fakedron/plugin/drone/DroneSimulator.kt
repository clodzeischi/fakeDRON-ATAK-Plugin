package com.atakmap.android.fakedron.plugin

import com.atakmap.android.fakedron.plugin.drone.FlightStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class DroneSimulator(
    private val scope: CoroutineScope,
    private val onStateUpdate: (FlightStatus, Int) -> Unit
) {
    companion object {
        const val ALTITUDE_STEP_M = 3
        const val TICK_MS = 50L
        const val LAUNCH_THRESHOLD_M = 100
    }

    private var simulationJob: Job? = null
    private var actualAltitude = 0
    private var targetAltitude = 0

    fun launch(target: Int) {
        targetAltitude = target
        actualAltitude = 0
        startLoop()
    }

    fun land() {
        targetAltitude = 0
        // don't restart loop if already running — just redirect target
    }

    fun updateTargetAltitude(target: Int) {
        targetAltitude = target
    }

    fun stop() {
        simulationJob?.cancel()
        simulationJob = null
        actualAltitude = 0
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
        // converge actual altitude toward target
        actualAltitude = when {
            actualAltitude < targetAltitude -> (actualAltitude + ALTITUDE_STEP_M)
                .coerceAtMost(targetAltitude)

            actualAltitude > targetAltitude -> (actualAltitude - ALTITUDE_STEP_M)
                .coerceAtLeast(targetAltitude)

            else -> actualAltitude
        }

        // determine status from altitude and target
        val newStatus = when {
            targetAltitude == 0 && actualAltitude == 0 -> FlightStatus.IDLE
            targetAltitude == 0 && actualAltitude > 0 -> FlightStatus.LANDING
            actualAltitude < LAUNCH_THRESHOLD_M -> FlightStatus.LAUNCHING
            else -> FlightStatus.FLYING
        }

        onStateUpdate(newStatus, actualAltitude)

        // stop the loop once we've landed
        if (newStatus == FlightStatus.IDLE) {
            simulationJob?.cancel()
        }
    }
}