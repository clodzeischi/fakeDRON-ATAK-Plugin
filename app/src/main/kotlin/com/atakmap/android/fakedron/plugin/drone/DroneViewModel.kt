package com.atakmap.android.fakedron.plugin.drone

import com.atakmap.android.fakedron.plugin.DroneSimulator
import com.atakmap.android.fakedron.plugin.mapgraphics.MapGraphicsManager
import com.atakmap.android.fakedron.plugin.mapgraphics.MapTargetingController
import com.atakmap.android.maps.MapView
import com.atakmap.android.util.ATAKUtilities
import com.atakmap.coremap.conversions.CoordinateFormat
import com.atakmap.coremap.conversions.CoordinateFormatUtilities
import com.atakmap.coremap.maps.coords.GeoPoint
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DroneViewModel(
    private val mapView: MapView,
    private val graphics: MapGraphicsManager
) {
    private val viewModelScope = MainScope()

    private val _state = MutableStateFlow(DroneState())
    val state: StateFlow<DroneState> = _state.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    private lateinit var targetingController: MapTargetingController

    fun initTargeting(mapView: MapView) {
        targetingController = MapTargetingController(
            mapView              = mapView,
            graphics             = graphics,
            onRallyPointSet      = { point ->
                simulator.updateRallyPoint(point)    // ← simulator now knows
            },
            onTargetingModeChanged = { isTargeting ->
                _state.value = _state.value.copy(isTargeting = isTargeting)
            }
        )
    }

    private val simulator = DroneSimulator(
        scope         = viewModelScope,
        onStateUpdate = { status, altitude, position, rallyCleared ->
            _state.value = _state.value.copy(
                status         = status,
                actualAltitude = altitude,
                location       = position?.let { toMGRS(it) }
            )

            position?.let {
                if (status == FlightStatus.IDLE) {
                    graphics.removeDroneMarker()
                    graphics.clearRallyPoint()
                } else {
                    graphics.updateDroneMarker(it, altitude)
                }
            }

            if (rallyCleared) {
                graphics.clearRallyPoint()
            }
        }
    )

    fun onLaunchLand() {
        when (_state.value.status) {
            FlightStatus.IDLE -> {
                val self = ATAKUtilities.findSelf(mapView)
                if (self == null || !self.point.isValid) {
                    _toastMessage.value = "Operator position unknown"
                    return
                }
                val spawnPoint = simulator.spawnOffset(self.point)
                graphics.spawnDroneMarker(spawnPoint)
                simulator.launch(_state.value.targetAltitude, spawnPoint)
            }

            FlightStatus.FLYING,
            FlightStatus.LAUNCHING,
            FlightStatus.RTH -> {      // ← RTH also responds to LAND
                simulator.land()
                simulator.clearRallyPoint()
                graphics.clearRallyPoint()
                if (_state.value.isTargeting) {
                    targetingController.toggle()
                }
            }

            else -> Unit
        }
    }

    fun onRth() {
        val self = ATAKUtilities.findSelf(mapView)
        if (self == null || !self.point.isValid) {
            _toastMessage.value = "Operator position unknown"
            return
        }
        simulator.startRTH(self.point)
        graphics.setRallyPoint(self.point)
    }

    fun onFlyToMapPoint() {
        targetingController.toggle()
    }

    fun onRallyPointSet(point: GeoPoint) {
        // if in RTH, tapping map cancels it silently
        if (_state.value.status == FlightStatus.RTH) {
            simulator.cancelRTH()
        }
        simulator.updateRallyPoint(point)
        graphics.setRallyPoint(point)
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

    private fun toMGRS(point: GeoPoint): String {
        return try {
            val mgrs = CoordinateFormatUtilities.formatToString(point, CoordinateFormat.MGRS)
            mgrs.toString()
        } catch (e: Exception) {
            "---"
        }
    }

    fun onDestroy() {
        targetingController.cleanup()
        simulator.stop()
        viewModelScope.cancel()
    }
}