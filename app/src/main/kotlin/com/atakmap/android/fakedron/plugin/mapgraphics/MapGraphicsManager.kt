package com.atakmap.android.fakedron.plugin.mapgraphics

import android.util.Log
import com.atakmap.android.drawing.mapItems.DrawingCircle
import com.atakmap.android.maps.MapView
import com.atakmap.coremap.maps.coords.GeoPointMetaData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MapGraphicsManager(private val mapView: MapView) {

    companion object {
        const val PING_DURATION_MS = 400L          // faster, snappier
         const val PING_TICK_MS = 16L
        const val PING_COLOR = 0xFFFF6D00.toInt()
        const val PING_FILL_COLOR = 0x33FF6D00.toInt()
    }

    private val managerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val mapGroup = mapView.rootGroup
        .addGroup("fakeDRON")

    fun spawnPing(point: GeoPointMetaData) {
        
        val circle = DrawingCircle(mapView, "fakeDRON-ping-${System.currentTimeMillis()}")
        circle.setCenterPoint(point)
        circle.radius = 10.0
        circle.strokeColor = PING_COLOR
        circle.fillColor = PING_FILL_COLOR
        circle.strokeWeight = 2.0
        mapGroup.addItem(circle)

        managerScope.launch {
            val startTime = System.currentTimeMillis()

            while (isActive) {
                delay(PING_TICK_MS)
                val elapsed = System.currentTimeMillis() - startTime
                val progress = (elapsed.toFloat() / PING_DURATION_MS).coerceIn(0f, 1f)

                // ease out — fast at start, slows toward end
                val eased = 1f - (1f - progress) * (1f - progress)

                // fade out
                val alpha = ((1f - eased) * 255).toInt()
                val fadedStroke = (alpha shl 24) or (PING_COLOR and 0x00FFFFFF)
                val fadedFill   = (alpha shl 24) or (PING_FILL_COLOR and 0x00FFFFFF)
                circle.strokeColor = fadedStroke
                circle.fillColor   = fadedFill

                if (progress >= 1f) {
                    mapGroup.removeItem(circle)
                    break
                }
            }
        }
    }

    // ── future ──────────────────────────────────────────
    // fun spawnDroneMarker(point: GeoPoint): Marker
    // fun updateDroneMarker(point: GeoPoint, altitude: Int)
    // fun removeDroneMarker()
    // ────────────────────────────────────────────────────

    fun cleanup() {
        managerScope.cancel()
        mapGroup.clearItems()
        mapView.rootGroup.removeGroup(mapGroup)
    }
}