package app.thumbcast.android.host

/**
 * Callback UI dla akcji lokalnych (`Output.App`): "menu", "soft_keyboard", "toggle_overlay".
 * Nic z tego nie trafia do hosta. Wołane na wątku UI.
 *
 * `fun interface`, żeby w `Game.java` dało się podać lambdę: `action -> { ... }`.
 */
fun interface AppActionHandler {
    fun onAppAction(action: String)
}
