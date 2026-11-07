package io.github.phonemousekeyboard.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/*
 * Shared spacing for pages and settings sheets. Kept tight on purpose: on a phone, every
 * bit of height given to margins is taken from the trackpad, text box or buttons.
 */

/** Space between a page's content and the edges of the screen (below the header). */
val PageContentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)

/** Applies [PageContentPadding]. */
fun Modifier.pagePadding(): Modifier = padding(PageContentPadding)

/** Space between groups on a page or in a settings sheet. */
val GroupSpacing = 6.dp

/** Space around a settings sheet's content, below its drag handle. */
val SheetContentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = 8.dp)

/**
 * A small drag handle for bottom sheets: the standard one has 22 dp of space above and below
 * it, which is a lot on a phone.
 */
@Composable
fun CompactDragHandle() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .wrapContentWidth()
            .size(width = 32.dp, height = 4.dp)
            .background(MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp)),
    )
}
