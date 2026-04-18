package com.atakmap.android.fakedron.plugin.drone

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DroneViewModel {

    private val _state = MutableStateFlow(DroneState())
    val state: StateFlow<DroneState> = _state.asStateFlow()

    fun onLaunchLand() {
        val current = _state.value
        _state.value = when (current.status) {
            FlightStatus.IDLE     -> current.copy(status = FlightStatus.LAUNCHING)
            FlightStatus.FLYING   -> current.copy(status = FlightStatus.LANDING)
            else                  -> current
        }
    }

    fun onRth() {
        _state.value = _state.value.copy(status = FlightStatus.RTH)
    }

    fun onTargetAltitudeChanged(altitude: Int) {
        _state.value = _state.value.copy(targetAltitude = altitude)
    }
}