package com.atakmap.android.fakedron.plugin.mapgraphics

import com.atakmap.android.maps.MapEvent
import com.atakmap.android.maps.MapEventDispatcher
import com.atakmap.android.maps.MapView
import com.atakmap.coremap.maps.coords.GeoPoint
import com.atakmap.map.AtakMapView

class MapTargetingController(
    private val mapView: MapView,
    private val graphics: MapGraphicsManager,
    private val onRallyPointSet: (GeoPoint) -> Unit,
    private val onTargetingModeChanged: (Boolean) -> Unit
) {
    private var isTargeting = false

    private val mapClickListener = MapEventDispatcher.MapEventDispatchListener { event ->
        val geoPoint = mapView.inverse(event.pointF)
        geoPoint?.let {
            graphics.setRallyPoint(it)
            onRallyPointSet(it.get())
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