package com.example.chronik

import android.content.ClipboardManager
import android.content.Context
import android.os.SystemClock
import android.view.View
import androidx.annotation.IdRes
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.CoordinatesProvider
import androidx.test.espresso.action.GeneralLocation
import androidx.test.espresso.action.GeneralSwipeAction
import androidx.test.espresso.action.Press
import androidx.test.espresso.action.Swipe
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.matcher.ViewMatchers.hasFocus
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.chronik.data.Note
import com.example.chronik.data.NotesStore
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.TypeSafeMatcher
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    private lateinit var context: Context
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(
            context.getSharedPreferences("quick_notes", Context.MODE_PRIVATE)
                .edit().clear().commit(),
        )
    }

    @After
    fun tearDown() {
        scenario?.close()
        context.getSharedPreferences("quick_notes", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun addingAndEditingNotesPersistsTextAndNewestFirstOrderAfterRecreation() {
        val firstText = "A quick thought\nKeep the exact spacing.  "
        val newestText = "Newest thought — café ☕"
        val activity = launch()
        onView(withId(R.id.empty_state)).check(matches(isDisplayed()))

        onView(withId(R.id.add_note)).perform(click())
        onView(noteChild(0, R.id.note_text))
            .check(matches(hasFocus()))
            .check(matches(withText("")))
            .perform(replaceText(firstText), closeSoftKeyboard())

        onView(withId(R.id.add_note)).perform(click())
        onView(noteChild(0, R.id.note_text))
            .check(matches(hasFocus()))
            .check(matches(withText("")))
            .perform(replaceText(newestText), closeSoftKeyboard())

        activity.recreate()

        assertCount(2)
        assertNoteText(0, newestText)
        assertNoteText(1, firstText)
        assertEquals(listOf(newestText, firstText), NotesStore(context).load().map(Note::text))
    }

    @Test
    fun copyPlacesTheCompleteNoteTextOnTheClipboard() {
        val text = "Copy every line\n\"Quotes\", café ☕\n  and spaces  "
        NotesStore(context).save(listOf(Note("copy-me", text)))
        val activity = launch()

        clickNoteAction(0, R.id.copy_note)

        // Clipboard access is checked while the activity owns the foreground window.
        activity.onActivity { currentActivity ->
            val clipboard = currentActivity.getSystemService(Context.CLIPBOARD_SERVICE)
                as ClipboardManager
            val clip = clipboard.primaryClip
            assertNotNull(clip)
            assertEquals(1, clip!!.itemCount)
            assertEquals(text, clip.getItemAt(0).coerceToText(currentActivity).toString())
        }
    }

    @Test
    fun deletingThenUndoingRestoresTheNoteAtItsOriginalPosition() {
        val original = listOf(
            Note("first", "Keep me first"),
            Note("second", "Keep me second"),
        )
        NotesStore(context).save(original)
        val activity = launch()

        clickNoteAction(0, R.id.delete_note)

        assertCount(1)
        onView(noteChild(0, R.id.note_text)).check(matches(withText("Keep me second")))
        onView(withText(R.string.undo)).perform(click())

        activity.recreate()

        assertCount(2)
        assertNoteText(0, "Keep me first")
        assertNoteText(1, "Keep me second")
        assertEquals(original, NotesStore(context).load())
    }

    @Test
    fun moveMenuReordersNotesInBothDirectionsAndPersistsTheirOrder() {
        val original = listOf(
            Note("first", "First note"),
            Note("second", "Second note"),
            Note("third", "Third note"),
        )
        NotesStore(context).save(original)
        val activity = launch()

        clickNoteAction(0, R.id.drag_handle)
        onView(withText(R.string.move_note_down)).perform(click())
        activity.recreate()

        val moved = listOf(original[1], original[0], original[2])
        assertEquals(moved, NotesStore(context).load())
        assertNoteText(0, "Second note")
        assertNoteText(1, "First note")

        clickNoteAction(1, R.id.drag_handle)
        onView(withText(R.string.move_note_up)).perform(click())
        activity.recreate()

        assertEquals(original, NotesStore(context).load())
        assertNoteText(0, "First note")
        assertNoteText(1, "Second note")
        assertCount(3)
    }

    @Test
    fun draggingTheHandleMovesTheNoteAndPersistsItsOrder() {
        val original = listOf(
            Note("drag-first", "Drag this note down"),
            Note("drag-second", "This note becomes first"),
        )
        NotesStore(context).save(original)
        val activity = launch()
        scrollToNote(0)

        onView(noteChild(0, R.id.drag_handle)).perform(
            GeneralSwipeAction(
                Swipe.SLOW,
                GeneralLocation.CENTER,
                CoordinatesProvider { handle ->
                    val list = handle.rootView.findViewById<RecyclerView>(R.id.notes_list)
                    val nextCard = requireNotNull(list.findViewHolderForAdapterPosition(1)) {
                        "The next note must be laid out before dragging"
                    }.itemView
                    // Move the card fully past the next slot before lifting the finger.
                    GeneralLocation.CENTER.calculateCoordinates(nextCard)
                },
                Press.FINGER,
            ),
        )

        val moved = listOf(original[1], original[0])
        assertEquals(moved, NotesStore(context).load())
        activity.recreate()
        assertEquals(moved, NotesStore(context).load())
        assertNoteText(0, "This note becomes first")
        assertNoteText(1, "Drag this note down")
    }

    @Test
    fun editingAfterReorderingUpdatesTheMovedNoteByItsStableIdentity() {
        val original = listOf(
            Note("edit-first", "Original first note"),
            Note("edit-second", "Original second note"),
            Note("edit-third", "Original third note"),
        )
        val editedText = "Edited after moving\nStill the same note."
        NotesStore(context).save(original)
        val activity = launch()

        // A real handle tap also protects the menu alternative from drag interception.
        clickNoteAction(0, R.id.drag_handle)
        onView(withText(R.string.move_note_down)).perform(click())
        scrollToNote(1)
        onView(noteChild(1, R.id.note_text))
            .perform(click(), replaceText(editedText), closeSoftKeyboard())

        val expected = listOf(original[1], original[0].copy(text = editedText), original[2])
        assertEquals(expected, NotesStore(context).load())
        activity.recreate()
        assertEquals(expected, NotesStore(context).load())
        assertNoteText(0, "Original second note")
        assertNoteText(1, editedText)
        assertNoteText(2, "Original third note")
    }

    @Test
    fun tappingTheCountUnfocusesTheEditorDismissesTheKeyboardAndKeepsExactText() {
        val text = "Keep this exact text\n  including spaces and café ☕  "
        val activity = launch()
        onView(withId(R.id.add_note)).perform(click())
        onView(noteChild(0, R.id.note_text))
            .check(matches(hasFocus()))
            .perform(replaceText(text))
        assertImeVisibility(true)

        onView(withId(R.id.note_count)).perform(click())

        onView(noteChild(0, R.id.note_text)).check(matches(not(hasFocus())))
        assertImeVisibility(false)
        assertEquals(listOf(text), NotesStore(context).load().map(Note::text))
        activity.recreate()
        assertNoteText(0, text)
    }

    @Test
    fun tappingTheCardNumberUnfocusesTheEditorAndDismissesTheKeyboard() {
        val original = Note("outside-card", "Before editing")
        val text = "Edited before tapping the card header\nSecond line"
        NotesStore(context).save(listOf(original))
        launch()
        onView(noteChild(0, R.id.note_text)).perform(click(), replaceText(text))
        assertImeVisibility(true)

        onView(noteChild(0, R.id.note_number)).perform(click())

        onView(noteChild(0, R.id.note_text)).check(matches(not(hasFocus())))
        assertImeVisibility(false)
        assertEquals(listOf(original.copy(text = text)), NotesStore(context).load())
    }

    @Test
    fun tappingAnotherEditorTransfersFocusKeepsTheKeyboardAndPreservesBothNotes() {
        val original = listOf(
            Note("focus-first", "First note"),
            Note("focus-second", "Second note"),
        )
        val firstText = "Edited first note"
        val secondText = "Edited second note"
        NotesStore(context).save(original)
        val activity = launch()
        onView(noteChild(0, R.id.note_text)).perform(click(), replaceText(firstText))
        assertImeVisibility(true)

        scrollToNote(1)
        onView(noteChild(1, R.id.note_text)).perform(click(), replaceText(secondText))

        onView(noteChild(1, R.id.note_text)).check(matches(hasFocus()))
        assertImeVisibility(true)
        val expected = listOf(
            original[0].copy(text = firstText),
            original[1].copy(text = secondText),
        )
        assertEquals(expected, NotesStore(context).load())
        activity.onActivity { currentActivity ->
            val list = currentActivity.findViewById<RecyclerView>(R.id.notes_list)
            val secondEditor = list.findViewHolderForAdapterPosition(1)
                ?.itemView?.findViewById<View>(R.id.note_text)
            assertNotNull(secondEditor)
            assertEquals(secondEditor, currentActivity.currentFocus)
        }
    }

    @Test
    fun addNoteSitsBelowTheListAndTheScreenHasNoTitleSubtitleOrReorderHint() {
        NotesStore(context).save(
            listOf(Note("layout-first", "One"), Note("layout-second", "Two")),
        )
        val activity = launch()
        onView(withId(R.id.add_note)).check(matches(isDisplayed()))
        activity.onActivity { currentActivity ->
            val list = currentActivity.findViewById<View>(R.id.notes_list)
            val addButton = currentActivity.findViewById<View>(R.id.add_note)
            val listPosition = IntArray(2)
            val buttonPosition = IntArray(2)
            list.getLocationOnScreen(listPosition)
            addButton.getLocationOnScreen(buttonPosition)
            assertTrue("The notes list must have usable space", list.height > 0)
            assertTrue(
                "Add note must be below the notes list",
                buttonPosition[1] >= listPosition[1] + list.height,
            )
        }
        onView(withText("chronik")).check(doesNotExist())
        onView(withText("Your thoughts, within reach.")).check(doesNotExist())
        onView(withText("Drag the handle to reorder")).check(doesNotExist())
    }

    private fun launch(): ActivityScenario<MainActivity> =
        ActivityScenario.launch(MainActivity::class.java).also { scenario = it }

    private fun assertCount(count: Int) {
        onView(withId(R.id.note_count)).check(
            matches(withText(context.resources.getQuantityString(R.plurals.note_count, count, count))),
        )
    }

    private fun assertNoteText(position: Int, text: String) {
        scrollToNote(position)
        onView(noteChild(position, R.id.note_text)).check(matches(withText(text)))
    }

    private fun clickNoteAction(position: Int, @IdRes childId: Int) {
        scrollToNote(position)
        onView(noteChild(position, childId)).perform(click())
    }

    private fun assertImeVisibility(expected: Boolean) {
        onView(isRoot()).perform(object : ViewAction {
            override fun getConstraints(): Matcher<View> = isRoot()

            override fun getDescription(): String = "Wait for IME visibility to become $expected"

            override fun perform(uiController: UiController, view: View) {
                val deadline = SystemClock.uptimeMillis() + 3_000
                do {
                    uiController.loopMainThreadForAtLeast(50)
                    val insets = ViewCompat.getRootWindowInsets(view)
                    if (insets?.isVisible(WindowInsetsCompat.Type.ime()) == expected) return
                } while (SystemClock.uptimeMillis() < deadline)

                val insets = ViewCompat.getRootWindowInsets(view)
                assertNotNull("Window insets must be available", insets)
                assertEquals(
                    "IME visibility after tapping",
                    expected,
                    insets!!.isVisible(WindowInsetsCompat.Type.ime()),
                )
            }
        })
    }

    private fun scrollToNote(position: Int) {
        onView(withId(R.id.notes_list)).perform(object : ViewAction {
            override fun getConstraints(): Matcher<View> = isAssignableFrom(RecyclerView::class.java)

            override fun getDescription(): String = "Scroll to note at position $position"

            override fun perform(uiController: UiController, view: View) {
                (view as RecyclerView).scrollToPosition(position)
                uiController.loopMainThreadUntilIdle()
            }
        })
    }

    /** Selects one recycled card's child without an Espresso Contrib dependency. */
    private fun noteChild(position: Int, @IdRes childId: Int): Matcher<View> =
        object : TypeSafeMatcher<View>() {
            override fun describeTo(description: Description) {
                description.appendText("view $childId inside note at adapter position $position")
            }

            override fun matchesSafely(view: View): Boolean {
                if (view.id != childId) return false
                var item = view
                while (item.parent is View && item.parent !is RecyclerView) {
                    item = item.parent as View
                }
                val list = item.parent as? RecyclerView ?: return false
                return list.id == R.id.notes_list && list.getChildAdapterPosition(item) == position
            }
        }
}
