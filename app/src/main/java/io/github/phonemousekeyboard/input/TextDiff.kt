package io.github.phonemousekeyboard.input

/**
 * The edit needed to turn one string into another, expressed as keyboard actions:
 * press Backspace [deletions] times, then type [insertion].
 *
 * Phone keyboards don't report individual key presses — they replace text (autocorrect,
 * suggestions, swipe typing). Comparing the text before and after each change lets us
 * replay that change on the host as ordinary key presses.
 */
data class TextDiff(val deletions: Int, val insertion: String) {

    companion object {
        /**
         * Computes the diff assuming the cursor is at the end of the text, which is how the
         * keyboard text box is used. Everything after the shared prefix is deleted and retyped.
         */
        fun between(oldText: String, newText: String): TextDiff {
            val sharedPrefix = oldText.commonPrefixWith(newText).length
            return TextDiff(
                deletions = oldText.length - sharedPrefix,
                insertion = newText.substring(sharedPrefix),
            )
        }
    }
}
