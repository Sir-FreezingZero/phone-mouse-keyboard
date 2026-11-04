package io.github.phonemousekeyboard.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

/** How visible the titles either side of the current one are. */
private const val GHOST_ALPHA = 0.3f

/** How large the titles either side of the current one are. */
private const val GHOST_SCALE = 0.75f

/**
 * Distance between the centres of neighbouring titles. Kept just wide enough for the longest
 * title next to a shrunken neighbour, so the faded names sit close to the current one.
 */
private val TitleSpacing = 100.dp

/** Width over which titles fade out at the left and right edges. */
private val EdgeFadeWidth = 32.dp

/**
 * The header title on main pages. The current page's name is in the middle and the names of
 * the pages either side peek in, faded, so it's clear there's more to find:
 *
 *            Keyboard   Mouse   Keyboard
 *            (faded)            (faded)
 *
 * Swiping the title moves between pages; tapping a faded name goes straight to it.
 */
@Composable
fun TitleSwitcher(
    pages: List<Screen>,
    currentPage: Screen,
    onPageSelected: (Screen) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(initialPage = pages.indexOf(currentPage).coerceAtLeast(0)) {
        pages.size
    }
    val scope = rememberCoroutineScope()
    val currentOnPageSelected by rememberUpdatedState(onPageSelected)

    // A swipe that comes to rest on another title switches to that page.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { index ->
            currentOnPageSelected(pages[index])
        }
    }

    // The page changed some other way: bring its title to the middle.
    LaunchedEffect(currentPage) {
        val index = pages.indexOf(currentPage)
        if (index >= 0 && index != pagerState.settledPage) {
            pagerState.animateScrollToPage(index)
        }
    }

    BoxWithConstraints(modifier = modifier.fadeEdges()) {
        val sidePadding = ((maxWidth - TitleSpacing) / 2).coerceAtLeast(0.dp)

        HorizontalPager(
            state = pagerState,
            pageSize = PageSize.Fixed(TitleSpacing),
            contentPadding = PaddingValues(horizontal = sidePadding),
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize(),
        ) { index ->
            val distance = pagerState.distanceFromCentre(index)
            val scale = lerp(1f, GHOST_SCALE, distance)

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(role = Role.Tab) {
                        scope.launch { pagerState.animateScrollToPage(index) }
                    }
                    .graphicsLayer {
                        alpha = lerp(1f, GHOST_ALPHA, distance)
                        scaleX = scale
                        scaleY = scale
                    },
            ) {
                PageTitle(title = pages[index].title)
            }
        }
    }
}

/** A page's name as shown in the header. Never cut off, even if wider than its slot. */
@Composable
fun PageTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        maxLines = 1,
        modifier = modifier.wrapContentWidth(unbounded = true),
    )
}

/** 0 for the page in the middle, rising to 1 for a page one position away (or further). */
private fun PagerState.distanceFromCentre(page: Int): Float =
    ((currentPage - page) + currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)

/** The value [fraction] of the way from [start] to [stop]. */
private fun lerp(start: Float, stop: Float, fraction: Float): Float = start + (stop - start) * fraction

/** Fades content out towards the left and right edges, so cut-off titles look intentional. */
private fun Modifier.fadeEdges(): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val fade = EdgeFadeWidth.toPx() / size.width
        drawRect(
            brush = Brush.horizontalGradient(
                0f to Color.Transparent,
                fade to Color.Black,
                1f - fade to Color.Black,
                1f to Color.Transparent,
                startX = 0f,
                endX = size.width,
            ),
            topLeft = Offset.Zero,
            blendMode = BlendMode.DstIn,
        )
    }
