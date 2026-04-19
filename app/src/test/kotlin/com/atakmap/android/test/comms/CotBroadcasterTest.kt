package com.atakmap.android.fakedron.plugin.comms

import com.atakmap.android.cot.CotMapComponent
import com.atakmap.comms.CotDispatcher
import com.atakmap.coremap.cot.event.CotEvent
import com.atakmap.coremap.maps.coords.GeoCalculations
import com.atakmap.coremap.maps.coords.GeoPoint
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import io.mockk.just
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import junit.framework.TestCase.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class CotBroadcasterTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    lateinit var mockDispatcher: CotDispatcher

    private lateinit var broadcaster: CotBroadcaster

    @Before
    fun setup() {
        mockkStatic(CotMapComponent::class)
        mockkStatic(GeoCalculations::class)

        every { CotMapComponent.getExternalDispatcher() } returns mockDispatcher
        every { mockDispatcher.dispatch(any()) } just runs
        every { GeoCalculations.distanceTo(any(), any()) } returns 0.0

        // no MapView needed — just pass the uid string directly
        broadcaster = CotBroadcaster("test-device-uid")
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun `first tick always broadcasts`() {
        broadcaster.onTick(GeoPoint(35.0, -117.0), 100, 0.0, 0.0)
        verify(exactly = 1) { mockDispatcher.dispatch(any()) }
    }

    @Test
    fun `second tick at same position under 60s does not broadcast`() {
        val position = GeoPoint(35.0, -117.0)
        broadcaster.onTick(position, 100, 0.0, 0.0)
        broadcaster.onTick(position, 100, 0.0, 0.0)
        verify(exactly = 1) { mockDispatcher.dispatch(any()) }
    }

    @Test
    fun `tick over 100m distance broadcasts regardless of time`() {
        // stub BEFORE any tick calls
        every { GeoCalculations.distanceTo(any(), any()) } returns 150.0

        broadcaster.onTick(GeoPoint(35.0, -117.0), 100, 0.0, 0.0)   // first — fires (null lastPos)
        broadcaster.onTick(GeoPoint(35.0, -117.0), 100, 0.0, 0.0)   // second — 150m > 100m, fires

        verify(exactly = 2) { mockDispatcher.dispatch(any()) }
    }

    @Test
    fun `tick under 100m distance does not broadcast`() {
        broadcaster.onTick(GeoPoint(35.0, -117.0), 100, 0.0, 0.0)
        broadcaster.onTick(GeoPoint(35.00001, -117.0), 100, 0.0, 0.0)
        verify(exactly = 1) { mockDispatcher.dispatch(any()) }
    }

    @Test
    fun `broadcast uid is prefixed with fakeDRON`() {
        val capturedEvent = slot<CotEvent>()
        every { mockDispatcher.dispatch(capture(capturedEvent)) } just runs

        broadcaster.onTick(GeoPoint(35.0, -117.0), 100, 0.0, 0.0)

        assertEquals("fakeDRON-test-device-uid", capturedEvent.captured.uid)
    }

    @Test
    fun `broadcast event type is correct CoT type`() {
        val capturedEvent = slot<CotEvent>()
        every { mockDispatcher.dispatch(capture(capturedEvent)) } just runs

        broadcaster.onTick(GeoPoint(35.0, -117.0), 100, 0.0, 0.0)

        assertEquals("a-f-A-M-F-Q", capturedEvent.captured.type)
    }

    @Test
    fun `onLanded sends delete event with correct type`() {
        val capturedEvent = slot<CotEvent>()
        every { mockDispatcher.dispatch(capture(capturedEvent)) } just runs

        broadcaster.onLanded()

        assertEquals("t-x-d-d", capturedEvent.captured.type)
    }

    @Test
    fun `onLanded resets gate so next tick broadcasts immediately`() {
        broadcaster.onTick(GeoPoint(35.0, -117.0), 100, 0.0, 0.0)
        broadcaster.onLanded()
        broadcaster.onTick(GeoPoint(35.0, -117.0), 0, 0.0, 0.0)
        verify(exactly = 3) { mockDispatcher.dispatch(any()) }
    }

    @Test
    fun `null dispatcher does not crash on onTick`() {
        every { CotMapComponent.getExternalDispatcher() } returns null
        broadcaster.onTick(GeoPoint(35.0, -117.0), 100, 0.0, 0.0)
    }

    @Test
    fun `null dispatcher does not crash on onLanded`() {
        every { CotMapComponent.getExternalDispatcher() } returns null
        broadcaster.onLanded()
    }
}