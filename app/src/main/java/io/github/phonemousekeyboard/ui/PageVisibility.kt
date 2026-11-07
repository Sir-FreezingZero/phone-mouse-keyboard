package io.github.phonemousekeyboard.ui

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Remembers which main pages are hidden from the header's swipeable title, set with the
 * switches next to them in the menu. A hidden page can still be opened from the menu.
 */
class PageVisibilityStore(context: Context) {

    private val preferences =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private val _hiddenPages = MutableStateFlow(load())

    /** Main pages left out of the header. Never all of them. */
    val hiddenPages: StateFlow<Set<Screen>> = _hiddenPages.asStateFlow()

    /** Shows or hides [page] in the header. Hiding the last shown page is ignored. */
    fun setShown(page: Screen, isShown: Boolean) {
        val hidden = _hiddenPages.value
        val updated = if (isShown) hidden - page else hidden + page
        if (Screen.mainPages.all { it in updated }) return

        _hiddenPages.value = updated
        preferences.edit { putStringSet(KEY_HIDDEN, updated.map { it.name }.toSet()) }
    }

    private fun load(): Set<Screen> {
        val names = preferences.getStringSet(KEY_HIDDEN, null).orEmpty()
        // Ignores names of pages that no longer exist.
        return Screen.mainPages.filter { it.name in names }.toSet()
    }

    private companion object {
        const val PREFERENCES_NAME = "pages"
        const val KEY_HIDDEN = "hidden_pages"
    }
}

/** The main pages shown in the header, in order: all of them except [hiddenPages]. */
fun shownMainPages(hiddenPages: Set<Screen>): List<Screen> =
    Screen.mainPages.filterNot { it in hiddenPages }
