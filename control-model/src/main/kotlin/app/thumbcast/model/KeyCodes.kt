package app.thumbcast.model

/**
 * Stały słownik dozwolonych kodów klawiszy dla Output.Key i StickKeys.
 * Mapowanie na kody hosta (VK) robi :control-android; tu są tylko nazwy logiczne.
 */
object KeyCodes {
    val LETTERS: Set<String> = ('A'..'Z').map { it.toString() }.toSet()
    val DIGITS: Set<String> = ('0'..'9').map { it.toString() }.toSet()
    val FUNCTION: Set<String> = (1..12).map { "F$it" }.toSet()
    val MODIFIERS: Set<String> = setOf("SHIFT", "CTRL", "ALT")
    val SPECIAL: Set<String> = setOf("SPACE", "ESC", "TAB", "ENTER", "-", "=")
    val ARROWS: Set<String> = setOf("UP", "DOWN", "LEFT", "RIGHT")

    val ALL: Set<String> = LETTERS + DIGITS + FUNCTION + MODIFIERS + SPECIAL + ARROWS

    fun isValid(code: String): Boolean = code in ALL
    fun isModifier(code: String): Boolean = code in MODIFIERS
}
