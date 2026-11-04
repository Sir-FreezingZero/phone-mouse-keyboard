package io.github.phonemousekeyboard.ui

/**
 * Every page in the app.
 *
 * @param title shown in the header while the page is open.
 * @param isTab whether the page has a tab in the bottom bar. Other pages are opened from the menu.
 */
enum class Screen(val title: String, val isTab: Boolean) {
    MOUSE(title = "Mouse", isTab = true),
    KEYBOARD(title = "Keyboard", isTab = true),
    BLUETOOTH(title = "Bluetooth", isTab = false);

    companion object {
        val tabs: List<Screen> = entries.filter { it.isTab }
    }
}
