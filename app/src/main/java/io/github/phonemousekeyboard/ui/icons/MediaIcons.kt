package io.github.phonemousekeyboard.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icons for the media buttons, which the built-in Material icon set doesn't include. Solid
 * shapes (triangles, bars, a speaker) on a 24 × 24 grid, with thin lines for the volume signs.
 * The colour comes from the `Icon` tint.
 */
object MediaIcons {

    val PlayPause: ImageVector by lazy {
        mediaIcon("Play/pause") {
            triangle(3f, 6f, 11f, 12f, 3f, 18f)
            bar(left = 14f, right = 16.5f)
            bar(left = 18.5f, right = 21f)
        }
    }

    val Stop: ImageVector by lazy {
        mediaIcon("Stop") { rectangle(6f, 6f, 18f, 18f) }
    }

    val Previous: ImageVector by lazy {
        mediaIcon("Previous") {
            bar(left = 5.5f, right = 8f)
            triangle(19f, 6f, 9f, 12f, 19f, 18f)
        }
    }

    val Next: ImageVector by lazy {
        mediaIcon("Next") {
            triangle(5f, 6f, 15f, 12f, 5f, 18f)
            bar(left = 16f, right = 18.5f)
        }
    }

    val Rewind: ImageVector by lazy {
        mediaIcon("Rewind") {
            triangle(12f, 6f, 3f, 12f, 12f, 18f)
            triangle(21f, 6f, 12f, 12f, 21f, 18f)
        }
    }

    val FastForward: ImageVector by lazy {
        mediaIcon("Fast forward") {
            triangle(3f, 6f, 12f, 12f, 3f, 18f)
            triangle(12f, 6f, 21f, 12f, 12f, 18f)
        }
    }

    val Mute: ImageVector by lazy {
        mediaIcon("Mute", lines = {
            // A cross beside the speaker.
            moveTo(15f, 9f)
            lineTo(21f, 15f)
            moveTo(21f, 9f)
            lineTo(15f, 15f)
        }) { speaker() }
    }

    val VolumeDown: ImageVector by lazy {
        mediaIcon("Volume down", lines = {
            moveTo(15f, 12f)
            horizontalLineTo(21f)
        }) { speaker() }
    }

    val VolumeUp: ImageVector by lazy {
        mediaIcon("Volume up", lines = {
            moveTo(15f, 12f)
            horizontalLineTo(21f)
            moveTo(18f, 9f)
            verticalLineTo(15f)
        }) { speaker() }
    }

    /**
     * Builds an icon from solid [shapes], plus optional [lines] drawn with a round-capped
     * stroke on top.
     */
    private fun mediaIcon(
        name: String,
        lines: (PathBuilder.() -> Unit)? = null,
        shapes: PathBuilder.() -> Unit,
    ): ImageVector {
        val builder = ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).path(fill = SolidColor(Color.Black), pathBuilder = shapes)

        if (lines != null) {
            builder.path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                pathBuilder = lines,
            )
        }
        return builder.build()
    }

    private fun PathBuilder.triangle(
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        x3: Float,
        y3: Float,
    ) {
        moveTo(x1, y1)
        lineTo(x2, y2)
        lineTo(x3, y3)
        close()
    }

    private fun PathBuilder.rectangle(left: Float, top: Float, right: Float, bottom: Float) {
        moveTo(left, top)
        horizontalLineTo(right)
        verticalLineTo(bottom)
        horizontalLineTo(left)
        close()
    }

    /** An upright bar the height of the triangles, as in ⏮ and ⏸. */
    private fun PathBuilder.bar(left: Float, right: Float) = rectangle(left, 6f, right, 18f)

    /** A speaker on the left half of the icon, facing right. */
    private fun PathBuilder.speaker() {
        moveTo(3f, 9f)
        horizontalLineTo(7f)
        lineTo(12f, 5f)
        verticalLineTo(19f)
        lineTo(7f, 15f)
        horizontalLineTo(3f)
        close()
    }
}
