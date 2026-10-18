package com.example.chronik

import android.content.Context
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.chronik.data.Note
import com.example.chronik.data.NotesStore
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotesStoreTest {
    private lateinit var context: Context
    private lateinit var preferences: SharedPreferences
    private lateinit var store: NotesStore

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        preferences = context.getSharedPreferences("quick_notes", Context.MODE_PRIVATE)
        assertTrue(preferences.edit().clear().commit())
        store = NotesStore(context)
    }

    @After
    fun tearDown() {
        preferences.edit().clear().commit()
    }

    @Test
    fun roundTripPreservesTextIdsAndOrder() {
        val notes = listOf(
            Note("third", "First line\nSecond line\n\"Quoted\" and \\backslash"),
            Note("first", "Заметка — café ☕ 📝"),
            Note("second", ""),
            Note("fourth", "  \n\t  "),
        )

        store.save(notes)

        assertEquals(notes, NotesStore(context).load())
    }

    @Test
    fun savingEmptyCollectionClearsExistingNotes() {
        store.save(listOf(Note("existing", "Remove me")))

        store.save(emptyList())

        assertTrue(NotesStore(context).load().isEmpty())
    }

    @Test
    fun corruptJsonLoadsAsEmptyWithoutThrowing() {
        assertTrue(preferences.edit().putString("notes", "{invalid JSON").commit())

        assertTrue(store.load().isEmpty())
    }

    @Test
    fun incorrectPreferenceTypeLoadsAsEmptyWithoutThrowing() {
        assertTrue(preferences.edit().putInt("notes", 42).commit())

        assertTrue(store.load().isEmpty())
    }

    @Test
    fun duplicateAndMissingIdsBecomeUniqueWithoutLosingTextOrOrder() {
        val entries = JSONArray()
            .put(JSONObject().put("id", "same").put("text", "First"))
            .put(JSONObject().put("id", "same").put("text", "Second"))
            .put(JSONObject().put("text", ""))
        assertTrue(preferences.edit().putString("notes", entries.toString()).commit())

        val notes = store.load()

        assertEquals(listOf("First", "Second", ""), notes.map(Note::text))
        assertEquals("same", notes.first().id)
        assertEquals(notes.size, notes.map(Note::id).toSet().size)
        assertTrue(notes.all { it.id.isNotBlank() })
        store.save(notes)
        assertEquals(notes, NotesStore(context).load())
    }

    @Test
    fun malformedEntriesAreSkippedWhileValidNotesRemain() {
        val entries = JSONArray()
            .put(JSONObject().put("id", "first").put("text", "Keep first"))
            .put("not an object")
            .put(JSONObject().put("id", "missing-text"))
            .put(JSONObject().put("id", "number-text").put("text", 7))
            .put(JSONObject().put("id", "null-text").put("text", JSONObject.NULL))
            .put(JSONObject().put("id", "last").put("text", ""))
        assertTrue(preferences.edit().putString("notes", entries.toString()).commit())

        assertEquals(
            listOf(Note("first", "Keep first"), Note("last", "")),
            store.load(),
        )
    }
}
