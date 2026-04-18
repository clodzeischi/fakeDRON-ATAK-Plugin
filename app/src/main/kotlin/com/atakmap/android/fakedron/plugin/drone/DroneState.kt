package com.atakmap.android.fakedron.plugin.drone

enum class FlightStatus { IDLE, LAUNCHING, FLYING, LANDING, RTH }

data class DroneState(
    val status: FlightStatus = FlightStatus.IDLE,
    val targetAltitude: Int = 120,
    val actualAltitude: Int? = null,
    val location: String? = null        // MGRS string
)