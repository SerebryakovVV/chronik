package com.example.chronik.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.UUID

/** Stores the notes together so their on-screen order survives an app restart. */
class NotesStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun load(): List<Note> {
        val savedNotes = try {
            preferences.getString(NOTES_KEY, null)
        } catch (_: ClassCastException) {
            null
        } ?: return emptyList()

        val entries = try {
            JSONArray(savedNotes)
        } catch (_: JSONException) {
            return emptyList()
        }

        val usedIds = mutableSetOf<String>()
        return buildList {
            for (index in 0 until entries.length()) {
                val entry = entries.opt(index) as? JSONObject ?: continue
                // A blank note is valid; a missing or non-string text field is not.
                val text = entry.opt(TEXT_KEY) as? String ?: continue
                val savedId = entry.opt(ID_KEY) as? String
                val id = if (!savedId.isNullOrBlank() && usedIds.add(savedId)) {
                    savedId
                } else {
                    UUID.randomUUID().toString().also(usedIds::add)
                }
                add(Note(id, text))
            }
        }
    }

    fun save(notes: List<Note>) {
        val entries = JSONArray()
        notes.forEach { note ->
            entries.put(
                JSONObject()
                    .put(ID_KEY, note.id)
                    .put(TEXT_KEY, note.text),
            )
        }
        preferences.edit().putString(NOTES_KEY, entries.toString()).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "quick_notes"
        const val NOTES_KEY = "notes"
        const val ID_KEY = "id"
        const val TEXT_KEY = "text"
    }
}
