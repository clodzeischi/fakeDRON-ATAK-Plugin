package com.atakmap.android.test.drone

import com.atakmap.android.fakedron.plugin.DroneSimulator
import com.atakmap.android.fakedron.plugin.comms.CotBroadcaster
import com.atakmap.android.fakedron.plugin.drone.FlightStatus
import com.atakmap.coremap.maps.coords.GeoPoint
import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class DroneSimulatorTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    lateinit var mockBroadcaster: CotBroadcaster

    private lateinit var simulator: DroneSimulator

    private var lastStatus: FlightStatus = FlightStatus.IDLE
    private var lastAltitude: Int = 0
    private var lastPosition: GeoPoint? = null
    private var lastRallyCleared: Boolean = false

    private val spawnPoint = GeoPoint(35.0, -117.0)
    private val rallyPoint = GeoPoint(35.01, -117.0)

    // ── Builder helper ────────────────────────────────────────────────────────

    private fun buildSimulator(
        distanceTo: Double = 500.0,
        bearing: Double    = 90.0,
        nextPoint: GeoPoint = rallyPoint
    ) = DroneSimulator(
        scope              = TestScope(),
        broadcaster        = mockBroadcaster,
        onStateUpdate      = { status: FlightStatus, altitude: Int, position: GeoPoint?, rallyCleared: Boolean ->
            lastStatus       = status
            lastAltitude     = altitude
            lastPosition     = position
            lastRallyCleared = rallyCleared
        },
        geoDistanceTo      = { _, _ -> distanceTo },
        geoBearingTo       = { _, _ -> bearing },
        geoPointAtDistance = { _, _, _ -> nextPoint }
    )

    @Before
    fun setup() {
        every { mockBroadcaster.onTick(any(), any(), any(), any()) } just runs
        every { mockBroadcaster.onLanded() } just runs
        simulator = buildSimulator()
    }

    // ── Altitude convergence ──────────────────────────────────────────────────

    @Test
    fun `altitude climbs toward target each tick`() {
        simulator.launch(100, spawnPoint)
        simulator.tick()
        assertEquals(ALTITUDE_STEP_M, lastAltitude)
    }

    @Test
    fun `altitude does not overshoot target`() {
        simulator.launch(3, spawnPoint)
        simulator.tick()
        assertEquals(3, lastAltitude)
    }

    @Test
    fun `altitude descends toward zero when landing`() {
        simulator.launch(100, spawnPoint)
        repeat(34) { simulator.tick() }
        simulator.land()
        simulator.tick()
        assertTrue(lastAltitude < 100)
    }

    @Test
    fun `altitude does not go below zero`() {
        simulator.launch(3, spawnPoint)
        simulator.tick()
        simulator.land()
        simulator.tick()
        assertEquals(0, lastAltitude)
    }

    // ── Status derivation ─────────────────────────────────────────────────────

    @Test
    fun `status is IDLE when target and actual are both zero`() {
        simulator.tick()
        assertEquals(FlightStatus.IDLE, lastStatus)
    }

    @Test
    fun `status is LAUNCHING when climbing below threshold`() {
        simulator.launch(200, spawnPoint)
        simulator.tick()
        assertEquals(FlightStatus.LAUNCHING, lastStatus)
    }

    @Test
    fun `status is FLYING when altitude reaches threshold`() {
        simulator.launch(200, spawnPoint)
        repeat(34) { simulator.tick() }
        assertEquals(FlightStatus.FLYING, lastStatus)
    }

    @Test
    fun `status is LANDING when target is zero but altitude is above zero`() {
        simulator.launch(200, spawnPoint)
        repeat(34) { simulator.tick() }
        simulator.land()
        simulator.tick()
        assertEquals(FlightStatus.LANDING, lastStatus)
    }

    // ── Rally arrival ─────────────────────────────────────────────────────────

    @Test
    fun `rallyCleared is true when within arrival radius`() {
        simulator = buildSimulator(distanceTo = 5.0)

        simulator.launch(200, spawnPoint)
        repeat(34) { simulator.tick() }
        simulator.updateRallyPoint(rallyPoint)
        simulator.tick()

        assertTrue(lastRallyCleared)
    }

    @Test
    fun `rallyCleared is false when outside arrival radius`() {
        simulator = buildSimulator(distanceTo = 500.0)

        simulator.launch(200, spawnPoint)
        repeat(34) { simulator.tick() }
        simulator.updateRallyPoint(rallyPoint)
        simulator.tick()

        assertFalse(lastRallyCleared)
    }

    @Test
    fun `position does not change during LAUNCHING`() {
        val pointAtDistanceCalled = mutableListOf<Boolean>()

        simulator = DroneSimulator(
            scope              = TestScope(),
            broadcaster        = mockBroadcaster,
            onStateUpdate      = { status: FlightStatus, altitude: Int, position: GeoPoint?, rallyCleared: Boolean ->
                lastStatus = status; lastAltitude = altitude
                lastPosition = position; lastRallyCleared = rallyCleared
            },
            geoDistanceTo      = { _, _ -> 500.0 },
            geoBearingTo       = { _, _ -> 90.0 },
            geoPointAtDistance = { _, _, _ ->
                pointAtDistanceCalled.add(true)
                rallyPoint
            }
        )

        simulator.launch(200, spawnPoint)
        simulator.updateRallyPoint(rallyPoint)
        simulator.tick()  // still LAUNCHING

        assertTrue(pointAtDistanceCalled.isEmpty())
    }

    @Test
    fun `position does not change during LANDING`() {
        val pointAtDistanceCalled = mutableListOf<Boolean>()

        simulator = DroneSimulator(
            scope              = TestScope(),
            broadcaster        = mockBroadcaster,
            onStateUpdate      = { status: FlightStatus, altitude: Int, position: GeoPoint?, rallyCleared: Boolean ->
                lastStatus = status; lastAltitude = altitude
                lastPosition = position; lastRallyCleared = rallyCleared
            },
            geoDistanceTo      = { _, _ -> 500.0 },
            geoBearingTo       = { _, _ -> 90.0 },
            geoPointAtDistance = { _, _, _ ->
                pointAtDistanceCalled.add(true)
                rallyPoint
            }
        )

        simulator.launch(200, spawnPoint)
        repeat(34) { simulator.tick() }
        simulator.land()
        simulator.updateRallyPoint(rallyPoint)

        pointAtDistanceCalled.clear()  // clear any calls from climb phase
        simulator.tick()

        assertTrue(pointAtDistanceCalled.isEmpty())
    }

    @Test
    fun `position moves toward rally when FLYING and outside radius`() {
        val pointAtDistanceCalled = mutableListOf<Boolean>()

        simulator = DroneSimulator(
            scope              = TestScope(),
            broadcaster        = mockBroadcaster,
            onStateUpdate      = { status: FlightStatus, altitude: Int, position: GeoPoint?, rallyCleared: Boolean ->
                lastStatus = status; lastAltitude = altitude
                lastPosition = position; lastRallyCleared = rallyCleared
            },
            geoDistanceTo      = { _, _ -> 500.0 },
            geoBearingTo       = { _, _ -> 90.0 },
            geoPointAtDistance = { _, _, _ ->
                pointAtDistanceCalled.add(true)
                rallyPoint
            }
        )

        simulator.launch(200, spawnPoint)
        repeat(34) { simulator.tick() }
        simulator.updateRallyPoint(rallyPoint)

        pointAtDistanceCalled.clear()  // clear climb phase calls
        simulator.tick()

        assertEquals(1, pointAtDistanceCalled.size)
    }

    // ── Stop resets all state ─────────────────────────────────────────────────

    @Test
    fun `stop() resets altitude to zero`() {
        simulator.launch(200, spawnPoint)
        repeat(10) { simulator.tick() }
        assertTrue(simulator.actualAltitude > 0)

        simulator.stop()
        assertEquals(0, simulator.actualAltitude)
    }

    @Test
    fun `stop() resets position to null`() {
        simulator.launch(200, spawnPoint)
        simulator.tick()
        simulator.stop()
        simulator.tick()
        assertNull(lastPosition)
    }

    @Test
    fun `IDLE status triggers onLanded on broadcaster`() {
        simulator.launch(3, spawnPoint)
        simulator.tick()
        simulator.land()
        simulator.tick()

        verify(exactly = 1) { mockBroadcaster.onLanded() }
    }

    companion object {
        const val ALTITUDE_STEP_M = DroneSimulator.ALTITUDE_STEP_M
        const val MOVEMENT_STEP_M = DroneSimulator.MOVEMENT_STEP_M
    }
}