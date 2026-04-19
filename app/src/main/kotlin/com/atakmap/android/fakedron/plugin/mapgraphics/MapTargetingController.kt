package com.atakmap.android.fakedron.plugin.mapgraphics

import android.util.Log
import com.atakmap.android.maps.MapEvent
import com.atakmap.android.maps.MapEventDispatcher
import com.atakmap.android.maps.MapView

class MapTargetingController(
    private val mapView: MapView,
    private val graphics: MapGraphicsManager,
    private val onTargetingModeChanged: (Boolean) -> Unit
) {
    private var isTargeting = false

    private val mapClickListener = object : MapEventDispatcher.MapEventDispatchListener {
        override fun onMapEvent(event: MapEvent) {
            val geoPoint = mapView.inverse(event.pointF)
            geoPoint?.let { graphics.spawnPing(it) }
        }
    }

    fun toggle() {
        isTargeting = !isTargeting
        if (isTargeting) {
            mapView.mapEventDispatcher.addMapEventListener(
                MapEvent.MAP_CLICK, mapClickListener
            )
        } else {
            mapView.mapEventDispatcher.removeMapEventListener(
                MapEvent.MAP_CLICK, mapClickListener
            )
        }
        onTargetingModeChanged(isTargeting)
    }

    fun cleanup() {
        if (isTargeting) {
            mapView.mapEventDispatcher.removeMapEventListener(
                MapEvent.MAP_CLICK, mapClickListener
            )
        }
    }
}