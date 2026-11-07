package io.github.phonemousekeyboard.ui.links

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
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
import io.github.phonemousekeyboard.ui.components.pagePadding

/** Everything the Links page can ask for, grouped to keep the parameter list short. */
data class LinksActions(
    val onOpen: (Link) -> Unit,
    val onSave: (Link) -> Unit,
    val onDelete: (Link) -> Unit,
    val onMove: (link: Link, offset: Int) -> Unit,
)

/**
 * The Links page: saved web pages. Tapping one opens it in the computer's browser (see
 * [io.github.phonemousekeyboard.input.LinkOpener]); each row's ⋮ edits, moves or deletes it.
 *
 *   SAVED LINKS                      Add
 *   ─────────────────────────────────────
 *   GitHub                             ⋮
 *   github.com
 *   ─────────────────────────────────────
 *   …
 *
 *   Tap a link to open it on the computer
 */
@Composable
fun LinksScreen(
    links: List<Link>,
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Saved links",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { editing = Link(name = "", address = "") }) {
                Text("Add")
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        Box(modifier = Modifier.weight(1f)) {
            if (links.isEmpty()) {
                Text(
                    text = "No links yet. Tap Add to save a web page, then tap it to open " +
                        "it in the computer's browser.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else {
                LazyColumn {
                    items(links, key = { it.id }) { link ->
                        LinkRow(
                            link = link,
                            isFirst = link == links.first(),
                            isLast = link == links.last(),
                            onOpen = { actions.onOpen(link) },
                            onEdit = { editing = link },
                            onMove = { offset -> actions.onMove(link, offset) },
                            onDelete = { actions.onDelete(link) },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }

        Text(
            text = when {
                !isConnected -> "Not connected. Connect to a computer to open links."
                openingAddress != null -> "Opening ${openingAddress.substringAfter("://")}…"
                else -> "Tap a link to open it on the computer."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
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

/** One saved link: its name and address. Tap to open; ⋮ for the rest. */
@Composable
private fun LinkRow(
    link: Link,
    isFirst: Boolean,
    isLast: Boolean,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onMove: (offset: Int) -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onOpen)
            .padding(start = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = link.displayName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Only worth repeating the address when the name is something else.
            if (link.name.isNotBlank()) {
                Text(
                    text = link.address.substringAfter("://"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
        IconButton(onClick = { isOpen = true }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Link options",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
