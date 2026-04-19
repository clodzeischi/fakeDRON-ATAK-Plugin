package com.atakmap.android.fakedron.plugin.mapgraphics

import com.atakmap.android.drawing.mapItems.DrawingCircle
import com.atakmap.android.maps.MapView
import com.atakmap.android.maps.Marker
import com.atakmap.coremap.maps.coords.GeoPoint
import com.atakmap.coremap.maps.coords.GeoPointMetaData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sin

class MapGraphicsManager(private val mapView: MapView) {

    companion object {
        // rally point pulse
        const val RALLY_BASE_RADIUS_M   = 300.0
        const val RALLY_PULSE_AMPLITUDE = 80.0   // expands ± this many metres
        const val RALLY_PULSE_SPEED     = 2.0    // higher = faster pulse
        const val RALLY_STROKE_COLOR    = 0xFFFF6D00.toInt()
        const val RALLY_FILL_COLOR      = 0x33FF6D00.toInt()
        const val TICK_MS               = 16L    // ~60fps
    }

    private val managerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val mapGroup = mapView.rootGroup.addGroup("fakeDRON")
    private var rallyCircle: DrawingCircle? = null
    private var droneMarker: Marker? = null
    private var pulseJob: Job? = null

    // ── Rally Point ─────────────────────────────────────────────────────────
    fun setRallyPoint(point: GeoPoint) {
        if (rallyCircle == null) {
            spawnRallyCircle(point)
        } else {
            rallyCircle!!.setCenterPoint(GeoPointMetaData(point))
        }
    }

    fun clearRallyPoint() {
        pulseJob?.cancel()
        pulseJob = null
        rallyCircle?.let { mapGroup.removeItem(it) }
        rallyCircle = null
    }

    private fun spawnRallyCircle(point: GeoPoint) {
        rallyCircle = DrawingCircle(mapView, "fakeDRON-rally").apply {
            setCenterPoint(GeoPointMetaData(point))
            radius       = RALLY_BASE_RADIUS_M
            strokeColor  = RALLY_STROKE_COLOR
            fillColor    = RALLY_FILL_COLOR
            strokeWeight = 2.0
            radiusMarker = null
        }
        mapGroup.addItem(rallyCircle)
        startPulse()
    }

    private fun startPulse() {
        pulseJob?.cancel()
        pulseJob = managerScope.launch {
            val startTime = System.currentTimeMillis()
            while (isActive) {
                delay(TICK_MS)
                val elapsed = (System.currentTimeMillis() - startTime) / 1000.0  // seconds
                val pulse   = sin(elapsed * RALLY_PULSE_SPEED * Math.PI)
                rallyCircle?.radius = RALLY_BASE_RADIUS_M + RALLY_PULSE_AMPLITUDE * pulse
            }
        }
    }

    // ── Drone Marker ─────────────────────────────────────────────────────────
    fun spawnDroneMarker(point: GeoPoint) {
        droneMarker = Marker(point, "fakeDRON-01").apply {
            type       = "a-f-A-M-F-Q"
            title      = "fakeDRON-01"
            setMetaString("how", "m-g")
            updateLabel(0)
        }
        mapGroup.addItem(droneMarker)
    }

    fun updateDroneMarker(point: GeoPoint, altitude: Int) {
        droneMarker?.apply {
            this.point = GeoPoint(point.latitude, point.longitude, altitude.toDouble())
            updateLabel(altitude)
        }
    }

    fun removeDroneMarker() {
        droneMarker?.let { mapGroup.removeItem(it) }
        droneMarker = null
    }

    private fun Marker.updateLabel(altitude: Int) {
        setMetaString("callsign", "fakeDRON-01\n${altitude}m")
    }

    fun cleanup() {
        managerScope.cancel()
        mapGroup.clearItems()
        mapView.rootGroup.removeGroup(mapGroup)
    }
}