package com.example.cinefiles.ui.diary

import android.annotation.SuppressLint
import android.graphics.Typeface
import android.text.Editable
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.core.content.getSystemService
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.cinefiles.R
import com.example.cinefiles.data.DiaryImageStore
import com.example.cinefiles.databinding.ItemDiaryBlockImageBinding
import com.example.cinefiles.databinding.ItemDiaryBlockTextBinding
import com.example.cinefiles.logic.BlockType
import com.example.cinefiles.logic.DiaryBlock
import com.example.cinefiles.logic.DiaryBlocks
import com.example.cinefiles.logic.DiaryFont
import com.example.cinefiles.logic.DiaryTheme
import com.example.cinefiles.logic.ImageSource
import com.example.cinefiles.logic.TextAlign
import com.example.cinefiles.util.ImageUrls

/**
 * The body of a diary page, written like an article (no boxes): runs of paragraphs (a [TextHolder], just text on the page) with pictures
 * between them (an [ImageHolder]: the picture and a small quiet pill of tools in its corner). The page always ends with a text run, so the
 * user can always keep writing below a picture.
 *
 * This adapter OWNS the list while the page is edited and tells [Listener.onChanged] about every change (a letter typed, a style switched, a
 * picture moved...), so the screen's ViewModel always has the latest page. It is not a ListAdapter on purpose: a drag has to move a picture the
 * moment the finger asks, and typing must never rebuild the paragraph under the cursor.
 *
 * Formatting ([toggleBold], [toggleItalic], [setAlignment]) acts on the paragraph run that has the cursor. The look of the page ([setStyle]) is
 * its font and theme.
 */
class DiaryBlockAdapter(
    private val imageStore: DiaryImageStore,
    private val listener: Listener
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    /** What the screen needs to hear from the page. */
    interface Listener {
        fun onChanged(blocks: List<DiaryBlock>)
        fun onStartDrag(holder: RecyclerView.ViewHolder)
        fun onPictureDeleted(block: DiaryBlock, index: Int)

        /** The paragraph run with the cursor changed or its selection moved ([state]), or no run has the cursor any more (null). */
        fun onFormatChanged(state: FormatState?)
    }

    /** How the paragraph with the cursor looks where the cursor is: for the bold, italic and alignment buttons. */
    data class FormatState(val bold: Boolean, val italic: Boolean, val align: TextAlign)

    private val items = ArrayList<DiaryBlock>()
    private var font = DiaryFont.SANS
    private var theme = DiaryTheme.MIDNIGHT
    private var focused: TextHolder? = null

    // ---------- Changing the page ----------

    /** Shows [blocks] (the page as it was loaded, or restored after a screen turn). */
    @SuppressLint("NotifyDataSetChanged")
    fun submit(blocks: List<DiaryBlock>) {
        items.clear()
        items.addAll(blocks)
        ensureTrailingText()
        notifyDataSetChanged()
        listener.onChanged(items.toList())
    }

    fun setStyle(newFont: DiaryFont, newTheme: DiaryTheme) {
        if (newFont == font && newTheme == theme) return
        font = newFont
        theme = newTheme
        notifyItemRangeChanged(0, items.size, PAYLOAD_STYLE)
    }

    /** Puts a new, empty paragraph run at the end of the page (when the last one is already empty, that one is used). Returns its place. */
    fun addParagraph(): Int {
        val last = items.lastOrNull()
        if (last != null && last.type == BlockType.TEXT && last.isBlank) return items.lastIndex
        items.add(DiaryBlocks.newText(html = true))
        notifyItemInserted(items.lastIndex)
        listener.onChanged(items.toList())
        return items.lastIndex
    }

    /**
     * Puts [picture] in the page where the cursor is: in the middle of a paragraph run it splits the run in two, around the picture; without a
     * cursor it goes to the end of the page. Returns the place of the picture.
     */
    fun insertPicture(picture: DiaryBlock): Int {
        val holder = focused
        val position = holder?.bindingAdapterPosition ?: RecyclerView.NO_POSITION
        if (holder == null || position !in items.indices || items[position].type != BlockType.TEXT) {
            // No cursor: the picture goes below the last paragraph. If that paragraph is empty, the picture takes its place and a new one follows.
            val last = items.lastOrNull()
            if (last != null && last.type == BlockType.TEXT && last.isBlank) {
                items.add(items.lastIndex, picture)
                notifyItemInserted(items.lastIndex - 1)
            } else {
                items.add(picture)
                notifyItemInserted(items.lastIndex)
                ensureTrailingText()
                notifyItemInserted(items.lastIndex)
            }
            listener.onChanged(items.toList())
            return items.indexOf(picture)
        }

        val edit = holder.binding.blockText
        val text = edit.text ?: Editable.Factory.getInstance().newEditable("")
        val cursor = edit.selectionStart.coerceIn(0, text.length)
        val before = SpannableStringBuilder(text.subSequence(0, cursor)).trimNewlines()
        val after = SpannableStringBuilder(text.subSequence(cursor, text.length)).trimNewlines()
        val align = items[position].align

        // New ids: the two halves are new paragraph runs, so their text fields are filled again
        items[position] = DiaryBlocks.newText(RichText.toHtml(before), html = true, align = align)
        items.add(position + 1, picture)
        items.add(position + 2, DiaryBlocks.newText(RichText.toHtml(after), html = true, align = align))
        notifyItemChanged(position)
        notifyItemRangeInserted(position + 1, 2)
        listener.onChanged(items.toList())
        return position + 1
    }

    /** Puts a deleted picture back where it was (Undo). */
    fun insert(index: Int, block: DiaryBlock) {
        val place = index.coerceIn(0, items.size)
        items.add(place, block)
        notifyItemInserted(place)
        listener.onChanged(items.toList())
    }

    /** Moves the block at [from] to [to] (the arrows move one place, a drag may move more). Only pictures are moved by the user. */
    fun move(from: Int, to: Int): Boolean {
        if (from !in items.indices || to !in items.indices || from == to) return false
        items.add(to, items.removeAt(from))
        notifyItemMoved(from, to)
        // The first and the last picture have a different "up" / "down" tool: draw the places they passed again
        notifyItemRangeChanged(minOf(from, to), kotlin.math.abs(from - to) + 1, PAYLOAD_STYLE)
        if (ensureTrailingText()) notifyItemInserted(items.lastIndex)
        listener.onChanged(items.toList())
        return true
    }

    private fun deletePicture(position: Int) {
        if (position !in items.indices || items[position].type != BlockType.IMAGE) return
        val block = items.removeAt(position)
        notifyItemRemoved(position)
        if (items.isNotEmpty()) notifyItemRangeChanged(0, items.size, PAYLOAD_STYLE)
        listener.onChanged(items.toList())
        listener.onPictureDeleted(block, position)
    }

    /**
     * Backspace at the very start of a paragraph run: an EMPTY run (not the last one of the page) is deleted and the cursor goes to the end of the
     * run before it, like in any editor. Returns true when it did that (then the key press is used up).
     */
    private fun removeEmptyRun(position: Int): Boolean {
        if (position !in items.indices || position == items.lastIndex) return false
        val block = items[position]
        if (block.type != BlockType.TEXT || !block.isBlank) return false
        items.removeAt(position)
        notifyItemRemoved(position)
        listener.onChanged(items.toList())
        (recyclerView?.findViewHolderForAdapterPosition(positionOnScreen(position - 1)) as? TextHolder)?.focus(atEnd = true)
        return true
    }

    /** Makes sure the page ends with a paragraph run. Returns true when it had to add one. */
    private fun ensureTrailingText(): Boolean {
        if (items.isNotEmpty() && items.last().type == BlockType.TEXT) return false
        items.add(DiaryBlocks.newText(html = true))
        return true
    }

    val blocks: List<DiaryBlock> get() = items

    // ---------- Formatting the paragraph with the cursor ----------

    fun toggleBold() = toggleStyle(Typeface.BOLD)

    fun toggleItalic() = toggleStyle(Typeface.ITALIC)

    private fun toggleStyle(style: Int) {
        val holder = focused ?: return
        val edit = holder.binding.blockText
        val text = edit.text ?: return
        RichText.toggle(text, style, edit.selectionStart, edit.selectionEnd)
        holder.commit() // a style change is not a typed letter, so nothing told the page yet
        holder.announceFormat()
    }

    /** Lines the paragraph run with the cursor up on the left, in the center or on the right. */
    fun setAlignment(align: TextAlign) {
        val holder = focused ?: return
        val position = holder.bindingAdapterPosition
        if (position !in items.indices) return
        items[position] = items[position].copy(align = align)
        holder.binding.blockText.gravity = Gravity.TOP or align.gravity()
        listener.onChanged(items.toList())
        holder.announceFormat()
    }

    /** Puts the cursor in the paragraph run at [position] and opens the keyboard. */
    fun focusParagraph(position: Int) {
        (recyclerView?.findViewHolderForAdapterPosition(positionOnScreen(position)) as? TextHolder)?.focus(atEnd = true)
    }

    // ---------- Showing the page ----------

    private var recyclerView: RecyclerView? = null

    /** The adapter is joined with the headline's adapter, so a place in this list is one further down in the RecyclerView. */
    var headerCount = 0

    private fun positionOnScreen(position: Int) = position + headerCount

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        this.recyclerView = recyclerView
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        this.recyclerView = null
    }

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int = if (items[position].type == BlockType.TEXT) TEXT else IMAGE

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TEXT) TextHolder(ItemDiaryBlockTextBinding.inflate(inflater, parent, false)) else ImageHolder(ItemDiaryBlockImageBinding.inflate(inflater, parent, false))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val block = items[position]
        when (holder) {
            is TextHolder -> holder.bind(block, position)
            is ImageHolder -> holder.bind(block, position)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position)
            return
        }
        // Only the look or the tools changed: the text being typed and the picture stay as they are
        when (holder) {
            is TextHolder -> holder.style(position)
            is ImageHolder -> holder.style(position)
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        when (holder) {
            is ImageHolder -> Glide.with(holder.binding.root).clear(holder.binding.blockImage)
            is TextHolder -> if (focused === holder) {
                focused = null
                listener.onFormatChanged(null)
            }
        }
    }

    /** One run of paragraphs: just text on the page. */
    inner class TextHolder(val binding: ItemDiaryBlockTextBinding) : RecyclerView.ViewHolder(binding.root) {
        private var boundId: String? = null
        private var filling = false // true while the text is being set from code: that is not the user typing

        init {
            val edit = binding.blockText
            edit.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    if (!filling) commit()
                }
            })
            edit.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    focused = this
                    announceFormat()
                } else if (focused === this) {
                    focused = null
                    listener.onFormatChanged(null)
                }
            }
            edit.onSelectionChangedListener = { _, _ -> if (focused === this) announceFormat() }
            edit.onBackspaceAtStart = { removeEmptyRun(bindingAdapterPosition) }
        }

        fun bind(block: DiaryBlock, position: Int) {
            // A run is filled with its text once; a repeat bind of the same run (the look changed) must not disturb what is being typed
            if (boundId != block.id) {
                filling = true
                binding.blockText.setText(RichText.toSpanned(block))
                filling = false
                boundId = block.id
            }
            style(position)
        }

        fun style(position: Int) {
            val edit = binding.blockText
            edit.applyDiaryText(font, theme, baseSp = TEXT_SP)
            edit.gravity = Gravity.TOP or items.getOrNull(position)?.align.orDefault().gravity()
            edit.hint = if (position == 0) edit.context.getString(R.string.diary_text_hint) else null
        }

        /** Tells the page what the run says now (as HTML: bold and italic are kept). */
        fun commit() {
            val position = bindingAdapterPosition
            if (position !in items.indices) return
            items[position] = items[position].copy(text = RichText.toHtml(binding.blockText.text ?: return), html = true)
            listener.onChanged(items.toList())
        }

        /** Tells the screen how the text looks at the cursor, for the bold, italic and alignment buttons. */
        fun announceFormat() {
            val edit = binding.blockText
            val text = edit.text ?: return
            val align = items.getOrNull(bindingAdapterPosition)?.align.orDefault()
            listener.onFormatChanged(
                FormatState(
                    bold = RichText.isActive(text, Typeface.BOLD, edit.selectionStart, edit.selectionEnd),
                    italic = RichText.isActive(text, Typeface.ITALIC, edit.selectionStart, edit.selectionEnd),
                    align = align
                )
            )
        }

        /** Puts the cursor in this run (at its end) and opens the keyboard. */
        fun focus(atEnd: Boolean) {
            val edit = binding.blockText
            edit.requestFocus()
            if (atEnd) edit.setSelection(edit.length())
            edit.context.getSystemService<InputMethodManager>()?.showSoftInput(edit, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    /** A picture between paragraphs, with a quiet pill of tools in its corner. */
    inner class ImageHolder(val binding: ItemDiaryBlockImageBinding) : RecyclerView.ViewHolder(binding.root) {

        @SuppressLint("ClickableViewAccessibility")
        fun bind(block: DiaryBlock, position: Int) {
            val model: Any? = when (block.imageSource) {
                ImageSource.TMDB -> ImageUrls.backdrop(block.imageRef)
                ImageSource.LOCAL -> imageStore.file(block.imageRef)
            }
            Glide.with(binding.blockImage).load(model).fitCenter().error(R.drawable.bg_poster_fallback).into(binding.blockImage)
            binding.blockImage.contentDescription = binding.root.context.getString(R.string.diary_image_description)

            binding.blockUp.setOnClickListener { move(bindingAdapterPosition, bindingAdapterPosition - 1) }
            binding.blockDown.setOnClickListener { move(bindingAdapterPosition, bindingAdapterPosition + 1) }
            binding.blockDelete.setOnClickListener { deletePicture(bindingAdapterPosition) }
            binding.blockHandle.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) listener.onStartDrag(this)
                false
            }
            style(position)
        }

        fun style(position: Int) {
            // The first place has no "up" and the last picture has no "down" (the closing paragraph run is always below it)
            binding.blockUp.alpha = if (position == 0) DISABLED_ALPHA else 1f
            binding.blockDown.alpha = if (position >= items.lastIndex - 1) DISABLED_ALPHA else 1f
        }
    }

    private fun TextAlign?.orDefault(): TextAlign = this ?: TextAlign.LEFT

    private fun SpannableStringBuilder.trimNewlines(): SpannableStringBuilder {
        while (isNotEmpty() && this[length - 1] == '\n') delete(length - 1, length)
        while (isNotEmpty() && this[0] == '\n') delete(0, 1)
        return this
    }

    private companion object {
        const val TEXT = 0
        const val IMAGE = 1
        const val TEXT_SP = 17f
        const val DISABLED_ALPHA = 0.35f
        val PAYLOAD_STYLE = Any()
    }
}

/** The horizontal gravity of a paragraph run's alignment. */
fun TextAlign.gravity(): Int = when (this) {
    TextAlign.LEFT -> Gravity.START
    TextAlign.CENTER -> Gravity.CENTER_HORIZONTAL
    TextAlign.RIGHT -> Gravity.END
}
