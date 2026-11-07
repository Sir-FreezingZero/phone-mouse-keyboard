package io.github.phonemousekeyboard.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Simple line icons for things the built-in Material icon set doesn't include. Drawn on a
 * 24 × 24 grid with a 1.8 unit stroke, so they sit well next to Material icons. The colour
 * comes from the `Icon` tint.
 */
object AppIcons {

    /** A mouse seen from above: a rounded body with a line between the two buttons. */
    val Mouse: ImageVector by lazy {
        lineIcon("Mouse") {
            // Body: a tall rounded shape, 10 wide and 16 high.
            moveTo(7f, 9f)
            arcTo(5f, 5f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 17f, y1 = 9f)
            verticalLineTo(15f)
            arcTo(5f, 5f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 7f, y1 = 15f)
            close()
            // Line between the buttons.
            moveTo(12f, 4f)
            verticalLineTo(9f)
        }
    }

    /** A keyboard: an outline with two rows of keys and a space bar. */
    val Keyboard: ImageVector by lazy {
        lineIcon("Keyboard") {
            // Outline.
            moveTo(4f, 6f)
            horizontalLineTo(20f)
            arcTo(1.5f, 1.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 21.5f, y1 = 7.5f)
            verticalLineTo(16.5f)
            arcTo(1.5f, 1.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 20f, y1 = 18f)
            horizontalLineTo(4f)
            arcTo(1.5f, 1.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 2.5f, y1 = 16.5f)
            verticalLineTo(7.5f)
            arcTo(1.5f, 1.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 4f, y1 = 6f)
            close()
            // Two rows of keys, drawn as short dashes.
            for (row in listOf(9.5f, 12.5f)) {
                for (column in listOf(6f, 9.33f, 12.67f, 16f)) {
                    moveTo(column, row)
                    horizontalLineTo(column + 2f)
                }
            }
            // Space bar.
            moveTo(8f, 15.5f)
            horizontalLineTo(16f)
        }
    }

    /** A phone with motion waves coming off its top corner, for the Air mouse. */
    val AirMouse: ImageVector by lazy {
        lineIcon("Air mouse") {
            // Phone outline.
            moveTo(5.5f, 8f)
            horizontalLineTo(11.5f)
            arcTo(1.5f, 1.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 13f, y1 = 9.5f)
            verticalLineTo(19.5f)
            arcTo(1.5f, 1.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 11.5f, y1 = 21f)
            horizontalLineTo(5.5f)
            arcTo(1.5f, 1.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 4f, y1 = 19.5f)
            verticalLineTo(9.5f)
            arcTo(1.5f, 1.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 5.5f, y1 = 8f)
            close()
            // Two motion arcs, centred on the phone's top-right corner.
            moveTo(15f, 6f)
            arcTo(3f, 3f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 18f, y1 = 9f)
            moveTo(15f, 3f)
            arcTo(6f, 6f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 21f, y1 = 9f)
        }
    }

    /** A window with an arrow pointing out of it, for links that open in a browser. */
    val Link: ImageVector by lazy {
        lineIcon("Link") {
            // Window: three sides of a rounded square, open at the top right for the arrow.
            moveTo(10f, 5f)
            horizontalLineTo(6.5f)
            arcTo(1.5f, 1.5f, 0f, isMoreThanHalf = false, isPositiveArc = false, x1 = 5f, y1 = 6.5f)
            verticalLineTo(17.5f)
            arcTo(1.5f, 1.5f, 0f, isMoreThanHalf = false, isPositiveArc = false, x1 = 6.5f, y1 = 19f)
            horizontalLineTo(17.5f)
            arcTo(1.5f, 1.5f, 0f, isMoreThanHalf = false, isPositiveArc = false, x1 = 19f, y1 = 17.5f)
            verticalLineTo(14f)
            // Arrow pointing up and to the right.
            moveTo(13f, 5f)
            horizontalLineTo(19f)
            verticalLineTo(11f)
            moveTo(19f, 5f)
            lineTo(11f, 13f)
        }
    }

    /** The Bluetooth rune. */
    val Bluetooth: ImageVector by lazy {
        lineIcon("Bluetooth") {
            moveTo(7f, 7.5f)
            lineTo(16.5f, 16.5f)
            lineTo(12f, 21f)
            verticalLineTo(3f)
            lineTo(16.5f, 7.5f)
            lineTo(7f, 16.5f)
        }
    }

    /** Angle brackets, for "source code". */
    val Code: ImageVector by lazy {
        lineIcon("Code") {
            moveTo(9f, 7f)
            lineTo(4f, 12f)
            lineTo(9f, 17f)
            moveTo(15f, 7f)
            lineTo(20f, 12f)
            lineTo(15f, 17f)
        }
    }

    /** Three sliders, for settings pages. */
    val Tune: ImageVector by lazy {
        lineIcon("Tune") {
            for ((row, knob) in listOf(6f to 15f, 12f to 8f, 18f to 13f)) {
                moveTo(4f, row)
                horizontalLineTo(20f)
                // A short upright line marks each slider's knob.
                moveTo(knob, row - 2f)
                verticalLineTo(row + 2f)
            }
        }
    }

    private fun lineIcon(name: String, pathData: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = pathData,
        ).build()
}
