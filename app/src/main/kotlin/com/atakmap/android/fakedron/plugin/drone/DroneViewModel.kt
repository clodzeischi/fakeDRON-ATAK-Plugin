package com.atakmap.android.fakedron.plugin.drone

import com.atakmap.android.fakedron.plugin.DroneSimulator
import com.atakmap.android.fakedron.plugin.mapgraphics.MapGraphicsManager
import com.atakmap.android.fakedron.plugin.mapgraphics.MapTargetingController
import com.atakmap.android.maps.MapView
import com.atakmap.android.util.ATAKUtilities
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DroneViewModel(
    private val mapView: MapView
) {
    private val viewModelScope = MainScope()

    private val _state = MutableStateFlow(DroneState())
    val state: StateFlow<DroneState> = _state.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    private lateinit var targetingController: MapTargetingController

    fun initTargeting(mapView: MapView, graphics: MapGraphicsManager) {
        targetingController = MapTargetingController(mapView, graphics) { isTargeting ->
            _state.value = _state.value.copy(isTargeting = isTargeting)
        }
    }

    private val simulator = DroneSimulator(
        scope = viewModelScope,
        onStateUpdate = { status, altitude ->
            _state.value = _state.value.copy(
                status = status,
                actualAltitude = altitude
            )
        }
    )

    fun onLaunchLand() {
        val self = ATAKUtilities.findSelf(mapView)
        if (self == null || !self.point.isValid) {
            _toastMessage.value = "Operator position unknown"
            return
        }

        when (_state.value.status) {
            FlightStatus.IDLE    -> simulator.launch(_state.value.targetAltitude)
            FlightStatus.FLYING,
            FlightStatus.LAUNCHING -> simulator.land()
            else                 -> Unit  // ignore taps during LANDING
        }
    }

    fun onFlyToMapPoint() {
        targetingController.toggle()
    }

    fun onRth() {
        _state.value = _state.value.copy(status = FlightStatus.RTH)
    }

    fun onTargetAltitudeChanged(altitude: Int) {
        _state.value = _state.value.copy(targetAltitude = altitude)
        // if airborne, feed new target to simulator immediately
        if (_state.value.status == FlightStatus.FLYING) {
            simulator.updateTargetAltitude(altitude)
        }
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    fun onDestroy() {
        targetingController.cleanup()
        simulator.stop()
        viewModelScope.cancel()
    }
}