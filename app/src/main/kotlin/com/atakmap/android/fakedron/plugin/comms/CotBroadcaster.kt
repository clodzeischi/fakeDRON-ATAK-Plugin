package com.atakmap.android.fakedron.plugin.comms

import android.util.Log
import com.atakmap.android.cot.CotMapComponent
import com.atakmap.android.maps.MapView
import com.atakmap.coremap.cot.event.CotDetail
import com.atakmap.coremap.cot.event.CotEvent
import com.atakmap.coremap.cot.event.CotPoint
import com.atakmap.coremap.maps.coords.GeoPoint
import com.atakmap.coremap.maps.time.CoordinatedTime

class CotBroadcaster(private val mapView: MapView) {

    companion object {
        const val BROADCAST_INTERVAL_MS  = 60_000L   // 60s max time gate
        const val BROADCAST_DISTANCE_M   = 100.0     // 100m delta gate
        const val STALE_OFFSET_MS        = 62_000   // 62s stale
        const val COT_TYPE               = "a-f-A-M-F-Q"
        const val COT_HOW                = "m-g"
        const val DELETE_TYPE            = "t-x-d-d"
    }

    val TAG = "CotBroadcaster"

    // uid is derived once from device UID — stable for lifetime of plugin
    private val uid = "fakeDRON-${mapView.selfMarker.uid}"

    private var lastBroadcastTime     = 0L
    private var lastBroadcastPosition: GeoPoint? = null

    // called every simulator tick
    fun onTick(position: GeoPoint, altitude: Int, speed: Double, course: Double) {
        if (!shouldBroadcast(position)) return
        broadcast(position, altitude, speed, course)
    }

    // called when drone lands — sends delete event
    fun onLanded() {
        sendDeleteEvent()
        reset()
    }

    private fun shouldBroadcast(position: GeoPoint): Boolean {
        val now          = System.currentTimeMillis()
        val timeElapsed  = now - lastBroadcastTime >= BROADCAST_INTERVAL_MS
        val lastPos      = lastBroadcastPosition
        val distanceMoved = if (lastPos != null) {
            com.atakmap.coremap.maps.coords.GeoCalculations
                .distanceTo(lastPos, position)
        } else {
            Double.MAX_VALUE  // first broadcast always fires
        }

        return timeElapsed || distanceMoved >= BROADCAST_DISTANCE_M
    }

    private fun broadcast(
        position: GeoPoint,
        altitude: Int,
        speed: Double,
        course: Double
    ) {
        val now   = CoordinatedTime()
        val stale = CoordinatedTime().addMilliseconds(STALE_OFFSET_MS)

        val event = CotEvent().apply {
            this.uid   = this@CotBroadcaster.uid
            type       = COT_TYPE
            how        = COT_HOW
            time       = now
            start      = now
            this.stale = stale
            setPoint(CotPoint(
                position.latitude,
                position.longitude,
                altitude.toDouble(),
                CotPoint.UNKNOWN,   // ce — circular error unknown
                CotPoint.UNKNOWN    // le — linear error unknown
            ))
            detail = buildDetail(speed, course)
        }

        CotMapComponent.getExternalDispatcher().dispatch(event)

        // update gate trackers
        lastBroadcastTime     = System.currentTimeMillis()
        lastBroadcastPosition = position
    }

    private fun sendDeleteEvent() {
        val now = CoordinatedTime()

        val event = CotEvent().apply {
            this.uid   = this@CotBroadcaster.uid
            type       = DELETE_TYPE
            how        = COT_HOW
            time       = now
            start      = now
            stale      = now   // immediately stale
            setPoint(CotPoint(
                CotPoint.UNKNOWN,
                CotPoint.UNKNOWN,
                CotPoint.UNKNOWN,
                CotPoint.UNKNOWN,
                CotPoint.UNKNOWN
            ))
            detail = CotDetail()
        }

        CotMapComponent.getExternalDispatcher().dispatch(event)

        Log.d(TAG, "sendDeleteEvent: $event")
    }

    private fun buildDetail(speed: Double, course: Double): CotDetail {
        val detail = CotDetail()

        val contact = CotDetail("contact")
        contact.setAttribute("callsign", "fakeDRON-01")
        detail.addChild(contact)

        val uid = CotDetail("uid")
        uid.setAttribute("Droid", "fakeDRON-01")
        detail.addChild(uid)

        val track = CotDetail("track")
        track.setAttribute("speed", speed.toString())
        track.setAttribute("course", course.toString())
        detail.addChild(track)

        return detail
    }

    private fun reset() {
        lastBroadcastTime     = 0L
        lastBroadcastPosition = null
    }
}