package io.github.phonemousekeyboard.ui.links

import android.content.Context
import androidx.core.content.edit
import io.github.phonemousekeyboard.input.UsKeyboardLayout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * A saved web page.
 *
 * @param name what the user called it; may be blank, see [displayName].
 * @param address the full address, e.g. "https://example.com", see [normalizeAddress].
 */
data class Link(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val address: String,
) {
    /** The name, or the address without "https://" when there's no name. */
    val displayName: String
        get() = name.ifBlank { address.substringAfter("://").removeSuffix("/") }
}

/**
 * Tidies an address as typed by the user: trims spaces and adds "https://" when no scheme is
 * given ("example.com" → "https://example.com"). The Run box on Windows needs the scheme to
 * know it's a web address; browsers accept it either way.
 */
fun normalizeAddress(typed: String): String {
    val address = typed.trim()
    val hasScheme = "://" in address || address.startsWith("about:") || address.startsWith("mailto:")
    return if (address.isEmpty() || hasScheme) address else "https://$address"
}

/** Whether every character of [address] can be typed on the computer's (US) keyboard. */
fun canTypeAddress(address: String): Boolean =
    address.all { UsKeyboardLayout.keyStrokeFor(it) != null }

/** Saves the user's links, in their chosen order, between launches. */
class LinksStore(context: Context) {

    private val preferences =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private val _links = MutableStateFlow(load())
    val links: StateFlow<List<Link>> = _links.asStateFlow()

    /** Adds [link] at the end, or replaces the link with the same id. */
    fun save(link: Link) = update { links ->
        if (links.any { it.id == link.id }) {
            links.map { if (it.id == link.id) link else it }
        } else {
            links + link
        }
    }

    fun delete(link: Link) = update { links -> links.filterNot { it.id == link.id } }

    /** Moves [link] [offset] places: -1 moves it up, 1 down. Stops at the ends of the list. */
    fun move(link: Link, offset: Int) = update { links ->
        val from = links.indexOfFirst { it.id == link.id }
        if (from == -1) return@update links
        val to = (from + offset).coerceIn(0, links.lastIndex)
        links.toMutableList().apply { add(to, removeAt(from)) }
    }

    private fun update(change: (List<Link>) -> List<Link>) {
        val updated = change(_links.value)
        _links.value = updated
        preferences.edit { putString(KEY_LINKS, toJson(updated)) }
    }

    private fun load(): List<Link> {
        val json = preferences.getString(KEY_LINKS, null) ?: return emptyList()
        // A damaged list is dropped rather than crashing the app.
        return runCatching { fromJson(json) }.getOrDefault(emptyList())
    }

    private companion object {
        const val PREFERENCES_NAME = "links"
        const val KEY_LINKS = "links"

        const val FIELD_ID = "id"
        const val FIELD_NAME = "name"
        const val FIELD_ADDRESS = "address"

        fun toJson(links: List<Link>): String = JSONArray().apply {
            links.forEach { link ->
                put(
                    JSONObject()
                        .put(FIELD_ID, link.id)
                        .put(FIELD_NAME, link.name)
                        .put(FIELD_ADDRESS, link.address),
                )
            }
        }.toString()

        fun fromJson(json: String): List<Link> {
            val array = JSONArray(json)
            return List(array.length()) { index ->
                val item = array.getJSONObject(index)
                Link(
                    id = item.getString(FIELD_ID),
                    name = item.optString(FIELD_NAME),
                    address = item.getString(FIELD_ADDRESS),
                )
            }
        }
    }
}
