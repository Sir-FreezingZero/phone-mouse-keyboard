package io.github.phonemousekeyboard.ui

/**
 * Every page in the app.
 *
 * @param title shown in the header while the page is open.
 * @param isMainPage whether the page is one of the app's main abilities (Mouse, Keyboard),
 *   which the user moves between by swiping the header title. Other pages are opened from the
 *   menu.
 */
enum class Screen(val title: String, val isMainPage: Boolean) {
    MOUSE(title = "Mouse", isMainPage = true),
    KEYBOARD(title = "Keyboard", isMainPage = true),
    BLUETOOTH(title = "Bluetooth", isMainPage = false);

    companion object {
        /** The main pages, in the order they sit side by side in the header. */
        val mainPages: List<Screen> = entries.filter { it.isMainPage }
    }
}
