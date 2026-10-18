package com.example.chronik

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.RecyclerView
import com.example.chronik.data.Note

internal class NotesAdapter(
    private val notes: List<Note>,
    private val onTextChanged: (String, String) -> Unit,
    private val onCopy: (String) -> Unit,
    private val onDelete: (String) -> Unit,
    private val onMove: (Int, Int) -> Unit,
) : RecyclerView.Adapter<NotesAdapter.NoteHolder>() {

    private val stableIds = mutableMapOf<String, Long>()
    private var nextStableId = 0L
    private var pendingFocusId: String? = null

    init {
        setHasStableIds(true)
        stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY
    }

    override fun getItemCount() = notes.size

    override fun getItemId(position: Int): Long =
        stableIds.getOrPut(notes[position].id) { nextStableId++ }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = NoteHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_note, parent, false)
    )

    override fun onBindViewHolder(holder: NoteHolder, position: Int) {
        holder.bind(notes[position], position)
    }

    override fun onBindViewHolder(holder: NoteHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty()) holder.bindPosition(position)
        else onBindViewHolder(holder, position)
    }

    override fun onViewAttachedToWindow(holder: NoteHolder) {
        super.onViewAttachedToWindow(holder)
        holder.focusIfRequested()
    }

    override fun onViewRecycled(holder: NoteHolder) {
        holder.unbind()
        super.onViewRecycled(holder)
    }

    fun requestFocus(noteId: String) {
        pendingFocusId = noteId
    }

    fun refreshPositions() {
        if (notes.isNotEmpty()) notifyItemRangeChanged(0, notes.size, POSITION_ONLY)
    }

    inner class NoteHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val editor: EditText = itemView.findViewById(R.id.note_text)
        private val number: TextView = itemView.findViewById(R.id.note_number)
        private val handle: ImageButton = itemView.findViewById(R.id.drag_handle)
        private var boundId: String? = null

        private fun configureActions() {
            handle.setOnClickListener { showMoveMenu() }
            itemView.findViewById<View>(R.id.copy_note).setOnClickListener {
                boundId?.let(onCopy)
            }
            itemView.findViewById<View>(R.id.delete_note).apply {
                setOnClickListener { boundId?.let(onDelete) }
                ViewCompat.setTooltipText(this, context.getString(R.string.delete_note))
            }
            ViewCompat.setTooltipText(handle, itemView.context.getString(R.string.reorder_note))
            editor.doAfterTextChanged { editable ->
                boundId?.let { onTextChanged(it, editable?.toString().orEmpty()) }
            }
        }

        init {
            configureActions()
        }

        fun bind(note: Note, position: Int) {
            boundId = null
            if (editor.text.toString() != note.text) {
                editor.setText(note.text)
            }
            boundId = note.id
            bindPosition(position)
        }

        fun bindPosition(position: Int) {
            val context = itemView.context
            number.text = context.getString(R.string.note_number, position + 1)
            ViewCompat.setStateDescription(handle, context.getString(R.string.note_position, position + 1, notes.size))
            ViewCompat.setAccessibilityDelegate(editor, object : androidx.core.view.AccessibilityDelegateCompat() {
                override fun onInitializeAccessibilityNodeInfo(
                    host: View,
                    info: androidx.core.view.accessibility.AccessibilityNodeInfoCompat,
                ) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.hintText = context.getString(R.string.note_editor_label, bindingAdapterPosition + 1)
                }
            })
            ViewCompat.removeAccessibilityAction(handle, AccessibilityActionCompat.ACTION_SCROLL_BACKWARD.id)
            ViewCompat.removeAccessibilityAction(handle, AccessibilityActionCompat.ACTION_SCROLL_FORWARD.id)
            if (position > 0) {
                ViewCompat.replaceAccessibilityAction(
                    handle, AccessibilityActionCompat.ACTION_SCROLL_BACKWARD,
                    context.getString(R.string.move_note_up),
                ) { _, _ -> moveBy(-1) }
            }
            if (position < notes.lastIndex) {
                ViewCompat.replaceAccessibilityAction(
                    handle, AccessibilityActionCompat.ACTION_SCROLL_FORWARD,
                    context.getString(R.string.move_note_down),
                ) { _, _ -> moveBy(1) }
            }
        }

        fun focusIfRequested() {
            val id = boundId ?: return
            if (pendingFocusId != id) return
            editor.post {
                if (boundId == id && pendingFocusId == id && editor.isAttachedToWindow) {
                    pendingFocusId = null
                    editor.requestFocus()
                    editor.setSelection(editor.text.length)
                    WindowInsetsControllerCompat(
                        (itemView.context as MainActivity).window, editor
                    ).show(WindowInsetsCompat.Type.ime())
                }
            }
        }

        fun unbind() {
            boundId = null
            editor.clearFocus()
        }

        private fun showMoveMenu() {
            val position = bindingAdapterPosition
            if (position == RecyclerView.NO_POSITION) return
            PopupMenu(itemView.context, handle).apply {
                menu.add(0, MOVE_UP, 0, R.string.move_note_up).isEnabled = position > 0
                menu.add(0, MOVE_DOWN, 1, R.string.move_note_down).isEnabled = position < notes.lastIndex
                setOnMenuItemClickListener {
                    moveBy(if (it.itemId == MOVE_UP) -1 else 1)
                }
                show()
            }
        }

        private fun moveBy(offset: Int): Boolean {
            val position = bindingAdapterPosition
            val destination = position + offset
            if (position == RecyclerView.NO_POSITION || destination !in notes.indices) return false
            onMove(position, destination)
            return true
        }
    }

    private companion object {
        const val POSITION_ONLY = "position"
        const val MOVE_UP = 1
        const val MOVE_DOWN = 2
    }
}
