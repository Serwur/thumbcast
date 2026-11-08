package app.thumbcast.android.sink

import app.thumbcast.android.host.AppActionHandler
import app.thumbcast.android.host.HostProtocol
import app.thumbcast.android.host.WindowsVkKeyTranslator
import app.thumbcast.model.KeyCodes
import app.thumbcast.model.MouseBtn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testy adapterów InputSink na czystym JVM. Stałe `KeyEvent.KEYCODE_*` są stałymi kompilacji
 * (Kotlin je wkleja), więc tabela kodów działa bez Robolectric.
 */
class SinksTest {

    private val zeroS: Short = 0
    private val zeroB: Byte = 0

    // ---------------- PadSink ----------------

    @Test
    fun padSinkForwardsStateInOrder() {
        val rep = RecordingPadReporter()
        val sink = PadSink(rep)
        val lsx: Short = 100
        val lsy: Short = -200
        val rsx: Short = 300
        val rsy: Short = -400
        val lt: Byte = 0xFF.toByte()
        sink.padState(0x1000, lsx, lsy, rsx, rsy, lt, zeroB)
        assertEquals(listOf("pad 0x1000 ls=100,-200 rs=300,-400 lt=255 rt=0"), rep.calls)
    }

    @Test
    fun padSinkSkipsIdenticalState() {
        val rep = RecordingPadReporter()
        val sink = PadSink(rep)
        assertTrue(sink.padState(0x1000, zeroS, zeroS, zeroS, zeroS, zeroB, zeroB))
        assertFalse(sink.padState(0x1000, zeroS, zeroS, zeroS, zeroS, zeroB, zeroB))
        assertEquals(1, rep.calls.size)
    }

    @Test
    fun padSinkResetSendsZeroOnlyAfterNonZeroState() {
        val rep = RecordingPadReporter()
        val sink = PadSink(rep)
        // Profil KBM: nic nie wysłano, reset nie może utworzyć pada na hoście.
        sink.reset()
        assertTrue(rep.calls.isEmpty())
        sink.padState(0x2000, zeroS, zeroS, zeroS, zeroS, zeroB, zeroB)
        assertTrue(sink.isActive)
        sink.reset()
        sink.reset()
        assertEquals(2, rep.calls.size)
        assertEquals("pad 0x0 ls=0,0 rs=0,0 lt=0 rt=0", rep.calls[1])
        assertFalse(sink.isActive)
    }

    // ---------------- KeyboardSink ----------------

    @Test
    fun keyboardSinkTranslatesLetterToVk() {
        val conn = RecordingHostConnection()
        val sink = KeyboardSink(conn, WindowsVkKeyTranslator)
        sink.key("M", true)
        sink.key("M", false)
        // VK 'M' = 0x4D, prefiks 0x80 w starszym bajcie.
        assertEquals(
            listOf(
                "key 804d dir=" + HostProtocol.KEY_DOWN + " mod=0 flags=0",
                "key 804d dir=" + HostProtocol.KEY_UP + " mod=0 flags=0",
            ),
            conn.calls,
        )
    }

    @Test
    fun keyboardSinkTracksModifierBits() {
        val conn = RecordingHostConnection()
        val sink = KeyboardSink(conn, WindowsVkKeyTranslator)
        sink.key("SHIFT", true)
        sink.key("1", true)
        sink.key("1", false)
        sink.key("SHIFT", false)
        assertEquals("key 80a0 dir=3 mod=1 flags=0", conn.calls[0])
        assertEquals("key 8031 dir=3 mod=1 flags=0", conn.calls[1])
        assertEquals("key 8031 dir=4 mod=1 flags=0", conn.calls[2])
        assertEquals("key 80a0 dir=4 mod=0 flags=0", conn.calls[3])
    }

    @Test
    fun keyboardSinkDropsUnknownAndDuplicates() {
        val conn = RecordingHostConnection()
        val sink = KeyboardSink(conn, WindowsVkKeyTranslator)
        assertFalse(sink.key("NOPE", true))
        assertEquals(1, sink.droppedCount)
        assertTrue(sink.key("A", true))
        assertFalse(sink.key("A", true))
        assertFalse(sink.key("B", false))
        assertEquals(1, conn.calls.size)
    }

    @Test
    fun keyboardSinkReleaseAllReleasesInReverseOrder() {
        val conn = RecordingHostConnection()
        val sink = KeyboardSink(conn, WindowsVkKeyTranslator)
        sink.key("W", true)
        sink.key("D", true)
        conn.calls.clear()
        sink.releaseAll()
        assertEquals(listOf("key 8044 dir=4 mod=0 flags=0", "key 8057 dir=4 mod=0 flags=0"), conn.calls)
        assertTrue(sink.pressedKeys.isEmpty())
    }

    @Test
    fun everyModelKeyCodeHasAndroidAndHostMapping() {
        for (code in KeyCodes.ALL) {
            assertTrue("brak keycode Androida dla $code", AndroidKeyCodes.isKnown(code))
            val android: Int = AndroidKeyCodes.toAndroidKeyCode(code)
            val host: Short = WindowsVkKeyTranslator.toHostKeyCode(android)
            assertTrue("brak kodu hosta dla $code", host.toInt() != 0)
        }
    }

    @Test
    fun androidKeyCodeTableIsInjective() {
        val seen: HashMap<Int, String> = HashMap()
        for (code in AndroidKeyCodes.codes) {
            val k: Int = AndroidKeyCodes.toAndroidKeyCode(code)
            val prev: String? = seen.put(k, code)
            assertEquals("kolizja keycode $k: $prev i $code", null, prev)
        }
    }

    // ---------------- MouseSink ----------------

    @Test
    fun mouseSinkSplitsLargeRelativeMove() {
        val conn = RecordingHostConnection()
        val sink = MouseSink(conn)
        sink.mouseMoveRelative(40000, -5)
        assertEquals(listOf("move 32767,-5", "move 7233,0"), conn.calls)
    }

    @Test
    fun mouseSinkButtonsAreEdgeOnly() {
        val conn = RecordingHostConnection()
        val sink = MouseSink(conn)
        sink.mouseButton(MouseBtn.LEFT, true)
        sink.mouseButton(MouseBtn.LEFT, true)
        sink.mouseButton(MouseBtn.RIGHT, false)
        sink.mouseButton(MouseBtn.LEFT, false)
        assertEquals(listOf("mdown 1", "mup 1"), conn.calls)
    }

    @Test
    fun mouseSinkPositionIsClampedToReference() {
        val conn = RecordingHostConnection()
        val sink = MouseSink(conn)
        sink.mousePosition(-10, 2000, 1920, 1080)
        assertEquals(listOf("pos 0,1079/1920 x 1080"), conn.calls)
    }

    @Test
    fun mouseSinkWheelAndRelease() {
        val conn = RecordingHostConnection()
        val sink = MouseSink(conn)
        sink.mouseWheel(0)
        sink.mouseWheel(-240)
        sink.mouseButton(MouseBtn.RIGHT, true)
        sink.releaseAll()
        assertEquals(listOf("scroll -240", "mdown 3", "mup 3"), conn.calls)
    }

    // ---------------- CompositeInputSink ----------------

    @Test
    fun compositeRoutesAppActionsToUiOnly() {
        val conn = RecordingHostConnection()
        val rep = RecordingPadReporter()
        val actions: ArrayList<String> = ArrayList()
        val composite = CompositeInputSink(
            PadSink(rep),
            KeyboardSink(conn, WindowsVkKeyTranslator),
            MouseSink(conn),
            AppActionHandler { a -> actions.add(a) },
        )
        composite.app("soft_keyboard")
        assertEquals(listOf("soft_keyboard"), actions)
        assertTrue(conn.calls.isEmpty())
        assertTrue(rep.calls.isEmpty())
    }

    @Test
    fun compositeReleaseAllZeroesEverything() {
        val conn = RecordingHostConnection()
        val rep = RecordingPadReporter()
        val composite = CompositeInputSink(
            PadSink(rep),
            KeyboardSink(conn, WindowsVkKeyTranslator),
            MouseSink(conn),
            AppActionHandler { },
        )
        composite.key("W", true)
        composite.mouseButton(MouseBtn.LEFT, true)
        composite.padState(0x1000, zeroS, zeroS, zeroS, zeroS, zeroB, zeroB)
        conn.calls.clear()
        rep.calls.clear()
        composite.releaseAll()
        assertEquals(listOf("key 8057 dir=4 mod=0 flags=0", "mup 1"), conn.calls)
        assertEquals(listOf("pad 0x0 ls=0,0 rs=0,0 lt=0 rt=0"), rep.calls)
        assertEquals(0, composite.lastPadButtons)
    }
}
