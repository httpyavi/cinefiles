package com.example.cinefiles.ui.diary

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatEditText

/**
 * The body of a diary page: an ordinary EditText that also tells its screen when the cursor or the selection moves, so the bold, italic,
 * underline, heading and quote buttons can show what applies where the cursor is ([onSelectionChangedListener]).
 */
class RichEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.appcompat.R.attr.editTextStyle
) : AppCompatEditText(context, attrs, defStyleAttr) {

    /** Called with the selection's start and end every time they change. */
    var onSelectionChangedListener: ((start: Int, end: Int) -> Unit)? = null

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        onSelectionChangedListener?.invoke(selStart, selEnd)
    }
}
