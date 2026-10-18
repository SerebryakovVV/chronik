package com.example.chronik

import android.content.ClipData
import android.content.ClipboardManager
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.chronik.data.Note
import com.example.chronik.data.NotesStore
import com.google.android.material.card.MaterialCardView
import com.google.android.material.snackbar.Snackbar
import java.util.UUID
import kotlin.math.max

class MainActivity : AppCompatActivity() {
    private lateinit var store: NotesStore
    private val notes = mutableListOf<Note>()
    private lateinit var notesAdapter: NotesAdapter
    private lateinit var notesList: RecyclerView
    private lateinit var root: View
    private var deleteSnackbar: Snackbar? = null
    private var outsideEditorTap = false
    private var touchDownX = 0f
    private var touchDownY = 0f
    private val touchSlop by lazy { ViewConfiguration.get(this).scaledTouchSlop }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)
        root = findViewById(R.id.root)
        applyWindowInsets()

        store = NotesStore(this)
        notes.addAll(store.load())
        notesList = findViewById(R.id.notes_list)

        val dragHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun isLongPressDragEnabled() = false

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder,
            ): Boolean = moveNote(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null) {
                    (viewHolder.itemView as MaterialCardView).apply {
                        strokeColor = getColor(R.color.accent)
                        cardElevation = 8 * resources.displayMetrics.density
                    }
                    viewHolder.itemView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                }
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                (viewHolder.itemView as MaterialCardView).apply {
                    strokeColor = getColor(R.color.note_border)
                    cardElevation = 0f
                }
                // Refresh positional labels after the drag; editor text is left intact.
                notesAdapter.refreshPositions()
            }
        })

        notesAdapter = NotesAdapter(
            notes = notes,
            onTextChanged = ::updateNote,
            onCopy = ::copyNote,
            onDelete = ::deleteNote,
            onMove = { from, to ->
                clearEditorFocus()
                if (moveNote(from, to)) {
                    notesAdapter.refreshPositions()
                    notesList.scrollToPosition(to)
                }
            },
        )
        notesList.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = notesAdapter
            itemAnimator = null
        }
        attachHandleGestures(dragHelper)
        dragHelper.attachToRecyclerView(notesList)
        findViewById<View>(R.id.add_note).setOnClickListener { addNote() }
        updateScreenState()
    }

    private fun attachHandleGestures(dragHelper: ItemTouchHelper) {
        // Observe at the list level so scrolling cannot consume the handle's first move.
        // Stationary taps continue to the button and open its move menu.
        notesList.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
            private var candidate: RecyclerView.ViewHolder? = null
            private var downX = 0f
            private var downY = 0f

            override fun onInterceptTouchEvent(rv: RecyclerView, event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        candidate = null
                        downX = event.x
                        downY = event.y
                        val card = rv.findChildViewUnder(event.x, event.y)
                        val handle = card?.findViewById<View>(R.id.drag_handle)
                        if (notes.size > 1 && handle?.containsScreenPoint(event.rawX, event.rawY) == true) {
                            candidate = rv.getChildViewHolder(card)
                        }
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val holder = candidate
                        if (holder != null &&
                            (kotlin.math.abs(event.x - downX) > touchSlop ||
                                kotlin.math.abs(event.y - downY) > touchSlop)
                        ) {
                            candidate = null
                            clearEditorFocus()
                            dragHelper.startDrag(holder)
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> candidate = null
                }
                return false
            }
        })
    }

    private fun applyWindowInsets() {
        WindowInsetsControllerCompat(window, root).apply {
            val light = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK !=
                Configuration.UI_MODE_NIGHT_YES
            isAppearanceLightStatusBars = light
            isAppearanceLightNavigationBars = light
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.updatePadding(
                left = systemBars.left,
                top = systemBars.top,
                right = systemBars.right,
                bottom = max(systemBars.bottom, keyboard.bottom),
            )
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun addNote() {
        val note = Note(UUID.randomUUID().toString(), "")
        notes.add(0, note)
        store.save(notes)
        notesAdapter.requestFocus(note.id)
        notesAdapter.notifyItemInserted(0)
        notesAdapter.refreshPositions()
        updateScreenState()
        notesList.scrollToPosition(0)
        notesList.post {
            (notesList.findViewHolderForAdapterPosition(0) as? NotesAdapter.NoteHolder)?.focusIfRequested()
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchDownX = event.rawX
                touchDownY = event.rawY
                outsideEditorTap = currentFocus is EditText && !touchTargetsEditorOrAdd(event)
            }
            MotionEvent.ACTION_MOVE -> {
                if (kotlin.math.abs(event.rawX - touchDownX) > touchSlop ||
                    kotlin.math.abs(event.rawY - touchDownY) > touchSlop
                ) {
                    outsideEditorTap = false
                }
            }
            MotionEvent.ACTION_CANCEL -> outsideEditorTap = false
            MotionEvent.ACTION_UP -> {
                val dismissEditor = outsideEditorTap
                outsideEditorTap = false
                // Finish the target's click before resizing the window for the keyboard.
                val handled = super.dispatchTouchEvent(event)
                if (dismissEditor && currentFocus is EditText) clearEditorFocus()
                return handled
            }
        }
        return super.dispatchTouchEvent(event)
    }

    private fun touchTargetsEditorOrAdd(event: MotionEvent): Boolean {
        if (!::notesList.isInitialized) return false
        if (findViewById<View>(R.id.add_note).containsScreenPoint(event.rawX, event.rawY)) return true
        for (index in 0 until notesList.childCount) {
            val editor = notesList.getChildAt(index).findViewById<EditText>(R.id.note_text)
            if (editor?.containsScreenPoint(event.rawX, event.rawY) == true) return true
        }
        return false
    }

    private fun View.containsScreenPoint(x: Float, y: Float): Boolean {
        val bounds = Rect()
        if (!getGlobalVisibleRect(bounds)) return false
        // Visible bounds use the view root's origin; touch raw coordinates use the screen's.
        val rootLocation = IntArray(2)
        rootView.getLocationOnScreen(rootLocation)
        bounds.offset(rootLocation[0], rootLocation[1])
        return bounds.contains(x.toInt(), y.toInt())
    }

    private fun updateNote(id: String, text: String) {
        val index = notes.indexOfFirst { it.id == id }
        if (index < 0 || notes[index].text == text) return
        notes[index] = notes[index].copy(text = text)
        store.save(notes)
    }

    private fun copyNote(id: String) {
        val note = notes.find { it.id == id } ?: return
        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.clipboard_label), note.text))
        // Android 13+ supplies its own clipboard confirmation.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(this, R.string.note_copied, Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteNote(id: String) {
        val index = notes.indexOfFirst { it.id == id }
        if (index < 0) return
        deleteSnackbar?.dismiss()
        val holder = notesList.findViewHolderForAdapterPosition(index) as? NotesAdapter.NoteHolder
        if (holder?.editor?.hasFocus() == true) clearEditorFocus()
        val note = notes.removeAt(index)
        store.save(notes)
        notesAdapter.notifyItemRemoved(index)
        notesAdapter.refreshPositions()
        updateScreenState()
        deleteSnackbar = Snackbar.make(root, R.string.note_deleted, Snackbar.LENGTH_LONG)
            .setAnchorView(R.id.add_note)
            .setAction(R.string.undo) {
                val restoredIndex = index.coerceAtMost(notes.size)
                notes.add(restoredIndex, note)
                store.save(notes)
                notesAdapter.notifyItemInserted(restoredIndex)
                notesAdapter.refreshPositions()
                updateScreenState()
                notesList.scrollToPosition(restoredIndex)
            }
        deleteSnackbar?.show()
    }

    private fun moveNote(from: Int, to: Int): Boolean {
        if (from !in notes.indices || to !in notes.indices || from == to) return false
        notes.add(to, notes.removeAt(from))
        notesAdapter.notifyItemMoved(from, to)
        store.save(notes)
        return true
    }

    private fun clearEditorFocus() {
        root.requestFocus()
        WindowInsetsControllerCompat(window, root).hide(WindowInsetsCompat.Type.ime())
    }

    private fun updateScreenState() {
        findViewById<View>(R.id.empty_state).isVisible = notes.isEmpty()
        findViewById<TextView>(R.id.note_count).text =
            resources.getQuantityString(R.plurals.note_count, notes.size, notes.size)
    }
}
