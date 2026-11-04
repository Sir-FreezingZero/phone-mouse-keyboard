package io.github.phonemousekeyboard.ui.keyboard

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/*
 * Backspace with nothing to delete.
 *
 * Phone keyboards only report Backspace by deleting text. In an empty text box (or one whose
 * text has faded away) there is nothing to delete, so pressing Backspace would do nothing,
 * even though there may be text on the computer to delete.
 *
 * To catch it, the text box always starts with one invisible character, the anchor, and the
 * cursor is kept after it. Backspace in an "empty" box deletes the anchor; the app notices,
 * sends Backspace to the computer and puts the anchor back. Everything else ignores the
 * anchor: only the text after it (the "content") is ever sent.
 */

/** A zero-width space: takes no room and isn't visible. */
internal const val ANCHOR = "​"

/** A text box with nothing in it but the anchor, cursor after it. */
internal fun emptyTextBox() = TextFieldValue(ANCHOR, TextRange(ANCHOR.length))

internal val TextFieldValue.hasAnchor: Boolean get() = text.startsWith(ANCHOR)

/** The typed text, without the anchor. */
internal val TextFieldValue.content: String get() = text.removePrefix(ANCHOR)

/**
 * Makes sure the anchor is in place and the cursor (and any selection or word being composed)
 * stays after it.
 */
internal fun TextFieldValue.withAnchor(): TextFieldValue {
    val anchored = if (hasAnchor) this else insertAnchor()
    fun TextRange.afterAnchor() = TextRange(
        start.coerceAtLeast(ANCHOR.length),
        end.coerceAtLeast(ANCHOR.length),
    )
    return TextFieldValue(
        text = anchored.text,
        selection = anchored.selection.afterAnchor(),
        composition = anchored.composition?.afterAnchor(),
    )
}

private fun TextFieldValue.insertAnchor(): TextFieldValue {
    fun TextRange.shifted() = TextRange(start + ANCHOR.length, end + ANCHOR.length)
    return TextFieldValue(
        text = ANCHOR + text,
        selection = selection.shifted(),
        composition = composition?.shifted(),
    )
}
