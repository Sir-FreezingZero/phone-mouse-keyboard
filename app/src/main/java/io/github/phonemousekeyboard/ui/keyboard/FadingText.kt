package io.github.phonemousekeyboard.ui.keyboard

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Remembers when each character in the text box was typed, so characters can fade out and be
 * removed once they have been on screen for a while.
 *
 * Typing happens at the end of the text, so the oldest characters are always at the start.
 */
internal class CharacterAges {

    /** Typing time of each character, in [android.os.SystemClock.uptimeMillis] time. */
    private val typedAtMillis = ArrayList<Long>()

    /** Updates the ages after the text changed from [oldText] to [newText]. */
    fun onTextChanged(oldText: String, newText: String, nowMillis: Long) {
        if (typedAtMillis.size != oldText.length) {
            // Out of step (e.g. after the screen rotated): treat everything as just typed.
            reset(newText.length, nowMillis)
            return
        }
        val unchanged = oldText.commonPrefixWith(newText).length
        while (typedAtMillis.size > unchanged) typedAtMillis.removeAt(typedAtMillis.lastIndex)
        repeat(newText.length - unchanged) { typedAtMillis.add(nowMillis) }
    }

    fun reset(length: Int, nowMillis: Long) {
        typedAtMillis.clear()
        repeat(length) { typedAtMillis.add(nowMillis) }
    }

    /** Forgets the first [count] characters, after they've been removed from the text. */
    fun dropFirst(count: Int) {
        typedAtMillis.subList(0, count.coerceAtMost(typedAtMillis.size)).clear()
    }

    /** Opacity of the character at [index]: 1 while fresh, falling to 0 as it fades. */
    fun opacityAt(index: Int, nowMillis: Long, fade: FadeTiming): Float {
        val typedAt = typedAtMillis.getOrNull(index) ?: return 1f
        val fadingFor = nowMillis - typedAt - fade.delayMillis
        if (fadingFor <= 0) return 1f
        return (1f - fadingFor.toFloat() / fade.durationMillis).coerceIn(0f, 1f)
    }

    /** How many characters at the start have completely faded and can be removed. */
    fun fadedPrefixLength(nowMillis: Long, fade: FadeTiming): Int =
        typedAtMillis.indices.firstOrNull { opacityAt(it, nowMillis, fade) > 0f }
            ?: typedAtMillis.size
}

/** When characters start fading, and how long the fade takes. */
internal data class FadeTiming(val delayMillis: Int, val durationMillis: Int)

/**
 * Draws each typed character with its current opacity; the text itself is unchanged. The
 * text box starts with the invisible [ANCHOR], which [ages] doesn't track.
 */
internal fun fadingTextTransformation(
    ages: CharacterAges,
    nowMillis: Long,
    fade: FadeTiming,
    textColor: Color,
) = VisualTransformation { text ->
    val contentStart = if (text.text.startsWith(ANCHOR)) ANCHOR.length else 0
    val styled = buildAnnotatedString {
        append(text)
        for (index in contentStart until text.length) {
            val opacity = ages.opacityAt(index - contentStart, nowMillis, fade)
            if (opacity < 1f) {
                addStyle(SpanStyle(color = textColor.copy(alpha = opacity)), index, index + 1)
            }
        }
    }
    TransformedText(styled, OffsetMapping.Identity)
}

/**
 * Removes the first [count] typed characters (just after the [ANCHOR]), keeping the cursor
 * and any word the keyboard is still composing in the same place relative to the rest.
 */
internal fun TextFieldValue.removeOldestCharacters(count: Int): TextFieldValue {
    val contentStart = if (hasAnchor) ANCHOR.length else 0
    val removedEnd = contentStart + count
    fun Int.shifted() = if (this >= removedEnd) this - count else minOf(this, contentStart)
    fun TextRange.shifted() = TextRange(this.start.shifted(), this.end.shifted())
    return TextFieldValue(
        annotatedString = AnnotatedString(text.removeRange(contentStart, removedEnd)),
        selection = selection.shifted(),
        composition = composition?.shifted(),
    )
}
