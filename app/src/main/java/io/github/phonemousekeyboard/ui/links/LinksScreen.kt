package io.github.phonemousekeyboard.ui.links

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.phonemousekeyboard.input.LinkTarget
import io.github.phonemousekeyboard.ui.components.GroupTitleButton
import io.github.phonemousekeyboard.ui.components.pagePadding
import io.github.phonemousekeyboard.ui.theme.ActiveBlue

/** Everything the Links page can ask for, grouped to keep the parameter list short. */
data class LinksActions(
    val onOpen: (Link) -> Unit,
    val onSave: (Link) -> Unit,
    val onDelete: (Link) -> Unit,
    val onMove: (link: Link, offset: Int) -> Unit,
)

private val TileShape = RoundedCornerShape(4.dp)

/** Height of one link's row: compact, so many links fit on screen. */
private val RowHeight = 36.dp

/**
 * The Links page: saved web pages. Tapping one opens it in the computer's browser (see
 * [io.github.phonemousekeyboard.input.LinkOpener]); each row's ⋮ edits, moves or deletes it.
 *
 *   SAVED LINKS                    + Add
 *   ────────────────────────────────────    underline, a little short of the screen edges
 *   [G] GitHub  github.com              ⋮
 *   [C] claude.ai                       ⋮
 *
 *   Opens in the default browser · Tap a link to open it
 */
@Composable
fun LinksScreen(
    links: List<Link>,
    target: LinkTarget,
    isConnected: Boolean,
    openingAddress: String?,
    actions: LinksActions,
    modifier: Modifier = Modifier,
) {
    // The link being added or edited, or null while the dialog is closed.
    var editing by remember { mutableStateOf<Link?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .pagePadding(),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
                modifier = Modifier.padding(start = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "SAVED LINKS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                GroupTitleButton(
                    label = "+ Add",
                    onClick = { editing = Link(name = "", address = "") },
                )
            }
            // The page's side padding keeps a small gap between this line and the screen edges.
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            if (links.isEmpty()) {
                Text(
                    text = "No links yet. Tap + Add to save a web page, then tap it to open " +
                        "it on the computer.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                )
            }
            links.forEachIndexed { index, link ->
                LinkRow(
                    link = link,
                    isOpening = link.address == openingAddress,
                    isFirst = index == 0,
                    isLast = index == links.lastIndex,
                    onOpen = { actions.onOpen(link) },
                    onEdit = { editing = link },
                    onMove = { offset -> actions.onMove(link, offset) },
                    onDelete = { actions.onDelete(link) },
                )
            }
        }

        Text(
            text = if (isConnected) {
                "${targetSummary(target)} · Tap a link to open it"
            } else {
                "Not connected. Connect to a computer to open links."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp),
        )
    }

    editing?.let { link ->
        LinkEditDialog(
            link = link,
            onSave = { saved ->
                actions.onSave(saved)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

/** Where links open, in a few words, for the line at the bottom of the page. */
private fun targetSummary(target: LinkTarget): String = when (target) {
    LinkTarget.DEFAULT_BROWSER -> "Opens in the default browser"
    LinkTarget.NEW_TAB -> "Opens in a new tab"
    LinkTarget.NEW_WINDOW -> "Opens in a new window"
}

/**
 * One saved link on a single compact line: a letter tile, its name, the address in grey when
 * it has a separate name, and ⋮ for the rest. Tap to open. While it's opening, "Opening…"
 * replaces the address.
 */
@Composable
private fun LinkRow(
    link: Link,
    isOpening: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onMove: (offset: Int) -> Unit,
    onDelete: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme

    // The address is only worth repeating when the name is something else.
    val detail = when {
        isOpening -> "Opening…"
        link.name.isNotBlank() -> link.address.substringAfter("://").removeSuffix("/")
        else -> null
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(RowHeight)
            .clickable(role = Role.Button, onClick = onOpen)
            .padding(start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LetterTile(link.displayName)
        // Name and address share the space up to the ⋮ button; a long one is cut short with "…".
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = link.displayName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isOpening) ActiveBlue else colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
        LinkMenu(
            canMoveUp = !isFirst,
            canMoveDown = !isLast,
            onEdit = onEdit,
            onMove = onMove,
            onDelete = onDelete,
        )
    }
}

/** A small square with the first letter of [name], so links are easy to tell apart at a glance. */
@Composable
private fun LetterTile(name: String) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(20.dp)
            .background(colors.surfaceVariant, TileShape)
            .border(1.dp, colors.outline, TileShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "•",
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun LinkMenu(
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onEdit: () -> Unit,
    onMove: (offset: Int) -> Unit,
    onDelete: () -> Unit,
) {
    var isOpen by remember { mutableStateOf(false) }

    Box {
        // Smaller than a standard icon button, to fit the compact row.
        IconButton(onClick = { isOpen = true }, modifier = Modifier.size(RowHeight)) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Link options",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        DropdownMenu(expanded = isOpen, onDismissRequest = { isOpen = false }) {
            DropdownMenuItem(
                text = { Text("Edit") },
                onClick = {
                    isOpen = false
                    onEdit()
                },
            )
            DropdownMenuItem(
                text = { Text("Move up") },
                enabled = canMoveUp,
                onClick = {
                    isOpen = false
                    onMove(-1)
                },
            )
            DropdownMenuItem(
                text = { Text("Move down") },
                enabled = canMoveDown,
                onClick = {
                    isOpen = false
                    onMove(1)
                },
            )
            DropdownMenuItem(
                text = { Text("Delete") },
                onClick = {
                    isOpen = false
                    onDelete()
                },
            )
        }
    }
}

/** Adds a new link or edits [link]. Saving tidies the address with [normalizeAddress]. */
@Composable
private fun LinkEditDialog(
    link: Link,
    onSave: (Link) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(link.name) }
    var address by rememberSaveable { mutableStateOf(link.address) }

    val isNew = link.address.isEmpty()
    val canSave = address.isNotBlank()
    val save = { onSave(link.copy(name = name.trim(), address = normalizeAddress(address))) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Add link" else "Edit link") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Address") },
                    placeholder = { Text("example.com") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Next,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name (optional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!canTypeAddress(address.trim())) {
                    Text(
                        text = "Some characters in this address can't be typed on the " +
                            "computer and will be skipped.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = save, enabled = canSave) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
