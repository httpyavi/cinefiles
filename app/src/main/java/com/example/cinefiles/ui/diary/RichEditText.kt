package com.example.cinefiles.ui.diary

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import androidx.appcompat.widget.AppCompatEditText

/**
 * The text field of one paragraph run of a diary page. It is an ordinary EditText that also tells its screen two things the editor needs:
 *  - [onSelectionChangedListener]: the cursor or the selection moved (the bold / italic buttons then show what applies there);
 *  - [onBackspaceAtStart]: Backspace was pressed with the cursor at the very start (an empty run is then deleted, like in any editor).
 *    Phones' keyboards send that either as a key press or as "delete one letter before the cursor", so both are caught.
 */
class RichEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.appcompat.R.attr.editTextStyle
) : AppCompatEditText(context, attrs, defStyleAttr) {

    /** Called with the selection's start and end every time they change. */
    var onSelectionChangedListener: ((start: Int, end: Int) -> Unit)? = null

    /** Called when Backspace is pressed at the start of the text. Return true when it was used (then nothing else is deleted). */
    var onBackspaceAtStart: (() -> Boolean)? = null

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        onSelectionChangedListener?.invoke(selStart, selEnd)
    }

    private fun atStart(): Boolean = selectionStart == 0 && selectionEnd == 0

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DEL && atStart() && onBackspaceAtStart?.invoke() == true) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val connection = super.onCreateInputConnection(outAttrs) ?: return null
        return object : InputConnectionWrapper(connection, true) {
            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                if (beforeLength == 1 && afterLength == 0 && atStart() && onBackspaceAtStart?.invoke() == true) return true
                return super.deleteSurroundingText(beforeLength, afterLength)
            }

            override fun sendKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_DEL && event.action == KeyEvent.ACTION_DOWN && atStart() && onBackspaceAtStart?.invoke() == true) return true
                return super.sendKeyEvent(event)
            }
        }
    }
}
