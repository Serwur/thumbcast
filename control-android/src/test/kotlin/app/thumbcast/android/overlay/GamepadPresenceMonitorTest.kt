package app.thumbcast.android.overlay

import android.view.InputDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reguła „fizyczny pad” i powiadomienia tylko przy zmianie (bez InputManagera). */
class GamepadPresenceMonitorTest {

    private val q = 29

    @Test
    fun physicalGamepadRule() {
        val pad = InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_KEYBOARD
        val stick = InputDevice.SOURCE_JOYSTICK
        assertTrue(GamepadPresenceMonitor.isPhysicalGamepad(pad, isVirtual = false, isExternal = true, sdkInt = q))
        assertTrue(GamepadPresenceMonitor.isPhysicalGamepad(stick, isVirtual = false, isExternal = true, sdkInt = q))
        assertFalse("wirtualny", GamepadPresenceMonitor.isPhysicalGamepad(pad, isVirtual = true, isExternal = true, sdkInt = q))
        assertFalse("wbudowany (API 29+)", GamepadPresenceMonitor.isPhysicalGamepad(pad, isVirtual = false, isExternal = false, sdkInt = q))
        assertTrue("przed API 29 brak isExternal", GamepadPresenceMonitor.isPhysicalGamepad(pad, isVirtual = false, isExternal = false, sdkInt = 28))
        assertFalse("klawiatura", GamepadPresenceMonitor.isPhysicalGamepad(InputDevice.SOURCE_KEYBOARD, isVirtual = false, isExternal = true, sdkInt = q))
        assertFalse("ekran dotykowy", GamepadPresenceMonitor.isPhysicalGamepad(InputDevice.SOURCE_TOUCHSCREEN, isVirtual = false, isExternal = true, sdkInt = q))
    }

    @Test
    fun callbackOnlyOnChange() {
        var connected = false
        val events = ArrayList<Boolean>()
        val m = GamepadPresenceMonitor(null, { events.add(it) }, { connected })
        m.start()
        assertTrue("start bez pada: brak zmiany", events.isEmpty())
        connected = true
        m.refresh()
        m.refresh()
        assertEquals(listOf(true), events)
        assertTrue(m.isPresent)
        connected = false
        m.refresh()
        assertEquals(listOf(true, false), events)
    }

    @Test
    fun startStopIdempotentAndStartRefreshes() {
        val events = ArrayList<Boolean>()
        val m = GamepadPresenceMonitor(null, { events.add(it) }, { true })
        m.start()
        m.start()
        assertEquals(listOf(true), events)
        assertTrue(m.isStarted)
        m.stop()
        m.stop()
        assertFalse(m.isStarted)
        assertTrue("ostatni znany stan zostaje", m.isPresent)
    }
}
