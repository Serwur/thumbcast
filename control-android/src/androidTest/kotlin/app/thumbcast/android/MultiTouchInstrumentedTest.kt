package app.thumbcast.android

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.thumbcast.android.host.AppActionHandler
import app.thumbcast.android.host.HostConnection
import app.thumbcast.android.host.HostPadReporter
import app.thumbcast.android.overlay.ThumbcastOverlay
import app.thumbcast.model.ButtonControl
import app.thumbcast.model.Output
import app.thumbcast.model.PadButton
import app.thumbcast.model.Profile
import app.thumbcast.model.ProfileMode
import app.thumbcast.model.Rect
import app.thumbcast.model.StickControl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * SZKIC testu instrumentacyjnego multitouch (uruchamiany na emulatorze / urządzeniu przez
 * `:control-android:connectedDebugAndroidTest`, workflow `emulator-tests.yml`).
 *
 * Co sprawdza:
 * 1. Dwa palce jednocześnie: lewy na joysticku, prawy na przycisku A -> pad dostaje flagę A (0x1000)
 *    i niezerową lewą gałkę w tym samym stanie (prawdziwy multitouch w jednym View, w przeciwieństwie
 *    do upstreamowego OSC, który ignoruje `getActionIndex() != 0`).
 * 2. Podniesienie palca na A (ACTION_POINTER_UP) zwalnia tylko A; gałka trzyma wychylenie.
 * 3. ACTION_CANCEL -> ostatni stan pada jest zerowy (releaseAll).
 *
 * Zdarzenia są budowane ręcznie (`MotionEvent.obtain` z PointerProperties/PointerCoords) i podawane
 * bezpośrednio do `overlay.dispatchTouchEvent`, bez okna i bez Espresso. Overlay ma ręcznie
 * wymuszony rozmiar przez `measure/layout`, co wywołuje `onSizeChanged` i tworzy silnik.
 *
 * Wymaga: aliasów androidx.test (runner, ext-junit) w version catalog, patrz build.gradle.kts.
 * Asercje opierają się na semantyce silnika (ENGINE): stick w trybie PAD_PLUS wysyła oś lewej gałki,
 * przycisk HOLD ustawia flagę na czas trzymania. Szczegóły (martwa strefa, krzywa) mogą wymagać
 * dopasowania wartości w asercjach.
 */
@RunWith(AndroidJUnit4::class)
class MultiTouchInstrumentedTest {

    private class Recorder : HostPadReporter, HostConnection {
        val pad: ArrayList<IntArray> = ArrayList()

        override fun reportOscState(
            buttonFlags: Int, leftStickX: Short, leftStickY: Short,
            rightStickX: Short, rightStickY: Short, leftTrigger: Byte, rightTrigger: Byte,
        ) {
            pad.add(intArrayOf(buttonFlags, leftStickX.toInt(), leftStickY.toInt(), rightStickX.toInt(), rightStickY.toInt()))
        }

        override fun sendKeyboardInput(keyMap: Short, keyDirection: Byte, modifier: Byte, flags: Byte) {}
        override fun sendMouseMove(deltaX: Short, deltaY: Short) {}
        override fun sendMousePosition(x: Short, y: Short, referenceWidth: Short, referenceHeight: Short) {}
        override fun sendMouseButtonDown(mouseButton: Byte) {}
        override fun sendMouseButtonUp(mouseButton: Byte) {}
        override fun sendMouseHighResScroll(scrollAmount: Short) {}
    }

    private val width: Int = 2000
    private val height: Int = 1000

    private fun profile(): Profile = Profile(
        schema = 1,
        id = "test",
        name = "test",
        mode = ProfileMode.PAD_PLUS,
        controls = listOf(
            StickControl(id = "move", rect = Rect(0f, 0.4f, 0.35f, 0.6f), floating = false),
            ButtonControl(id = "a", rect = Rect(0.85f, 0.7f, 0.1f, 0.2f), output = Output.Pad(PadButton.A)),
        ),
    )

    private fun ev(action: Int, downTime: Long, ids: IntArray, xs: FloatArray, ys: FloatArray): MotionEvent {
        val n: Int = ids.size
        val props = arrayOfNulls<MotionEvent.PointerProperties>(n)
        val coords = arrayOfNulls<MotionEvent.PointerCoords>(n)
        var i = 0
        while (i < n) {
            val p = MotionEvent.PointerProperties()
            p.id = ids[i]
            p.toolType = MotionEvent.TOOL_TYPE_FINGER
            props[i] = p
            val c = MotionEvent.PointerCoords()
            c.x = xs[i]
            c.y = ys[i]
            c.pressure = 1f
            c.size = 1f
            coords[i] = c
            i++
        }
        return MotionEvent.obtain(
            downTime, SystemClock.uptimeMillis(), action, n, props, coords,
            0, 0, 1f, 1f, 0, 0, android.view.InputDevice.SOURCE_TOUCHSCREEN, 0,
        )
    }

    private fun pointerAction(masked: Int, index: Int): Int =
        masked or (index shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)

    @Test
    fun twoFingersStickAndButtonAtOnce() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val rec = Recorder()
        var overlay: ThumbcastOverlay? = null
        instrumentation.runOnMainSync {
            val o = ThumbcastOverlayFactory.create(
                instrumentation.targetContext, profile(), rec, rec, AppActionHandler { },
            )
            o.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
            )
            o.layout(0, 0, width, height)
            overlay = o
        }
        val o: ThumbcastOverlay = overlay!!
        val t0: Long = SystemClock.uptimeMillis()
        // Środek joysticka: (350, 700); przycisk A: (1800, 800).
        instrumentation.runOnMainSync {
            o.dispatchTouchEvent(ev(MotionEvent.ACTION_DOWN, t0, intArrayOf(0), floatArrayOf(350f), floatArrayOf(700f)))
            o.dispatchTouchEvent(ev(MotionEvent.ACTION_MOVE, t0, intArrayOf(0), floatArrayOf(500f), floatArrayOf(700f)))
            o.dispatchTouchEvent(
                ev(pointerAction(MotionEvent.ACTION_POINTER_DOWN, 1), t0,
                    intArrayOf(0, 1), floatArrayOf(500f, 1800f), floatArrayOf(700f, 800f)),
            )
        }
        val withA: IntArray = rec.pad.last()
        assertEquals("flaga A", 0x1000, withA[0] and 0x1000)
        assertTrue("lewa gałka wychylona w prawo", withA[1] > 0)

        instrumentation.runOnMainSync {
            o.dispatchTouchEvent(
                ev(pointerAction(MotionEvent.ACTION_POINTER_UP, 1), t0,
                    intArrayOf(0, 1), floatArrayOf(500f, 1800f), floatArrayOf(700f, 800f)),
            )
        }
        val afterUp: IntArray = rec.pad.last()
        assertEquals("A zwolnione", 0, afterUp[0] and 0x1000)
        assertTrue("gałka nadal wychylona", afterUp[1] > 0)

        instrumentation.runOnMainSync {
            o.dispatchTouchEvent(ev(MotionEvent.ACTION_CANCEL, t0, intArrayOf(0), floatArrayOf(500f), floatArrayOf(700f)))
        }
        val afterCancel: IntArray = rec.pad.last()
        assertEquals(0, afterCancel[0])
        assertEquals(0, afterCancel[1])
        assertEquals(0, afterCancel[2])
    }
}
