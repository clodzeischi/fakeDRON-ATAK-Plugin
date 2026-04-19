package com.atakmap.android.fakedron.plugin.drone

import com.atakmap.android.maps.MapView
import com.atakmap.android.util.ATAKUtilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DroneViewModel(
    private val mapView: MapView
) {

    private val _state = MutableStateFlow(DroneState())
    val state: StateFlow<DroneState> = _state.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    fun onLaunchLand() {
        val self = ATAKUtilities.findSelf(mapView)
        if (self == null || !self.point.isValid) {
            _toastMessage.value = "Operator position unknown"
            return
        }

        val current = _state.value
        _state.value = when (current.status) {
            FlightStatus.IDLE   -> current.copy(status = FlightStatus.LAUNCHING)
            FlightStatus.FLYING -> current.copy(status = FlightStatus.LANDING)
            else                -> current
        }
    }

    fun onRth() {
        _state.value = _state.value.copy(status = FlightStatus.RTH)
    }

    fun onTargetAltitudeChanged(altitude: Int) {
        _state.value = _state.value.copy(targetAltitude = altitude)
    }

    fun clearToast() {
        _toastMessage.value = null
    }
}