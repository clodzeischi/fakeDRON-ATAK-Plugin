package com.atakmap.android.fakedron.plugin.drone

import com.atakmap.coremap.maps.coords.GeoPoint

enum class FlightStatus { IDLE, LAUNCHING, FLYING, LANDING }

data class DroneState(
    val status: FlightStatus = FlightStatus.IDLE,
    val targetAltitude: Int = 100,
    val actualAltitude: Int? = null,
    val location: String? = null,
    val isTargeting: Boolean = false,
    val rallyPoint: GeoPoint? = null
)