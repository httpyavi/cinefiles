package com.example.cinefiles.ui.diary

import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.text.SpannableStringBuilder
import android.text.format.DateFormat
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.example.cinefiles.CineFilesApp
import com.example.cinefiles.R
import com.example.cinefiles.databinding.FragmentDiaryEditorBinding
import com.example.cinefiles.databinding.ItemDiaryInlinePhotoBinding
import com.example.cinefiles.logic.DiaryCover
import com.example.cinefiles.logic.DiaryDocument
import com.example.cinefiles.logic.DiaryFont
import com.example.cinefiles.logic.DiarySegment
import com.example.cinefiles.logic.DiaryText
import com.example.cinefiles.logic.DiaryTextSize
import com.example.cinefiles.logic.DiaryTheme
import com.example.cinefiles.logic.ImageSource
import com.example.cinefiles.logic.TextAlign
import com.example.cinefiles.util.EmptyAction
import com.example.cinefiles.util.ImageUrls
import com.example.cinefiles.util.applySystemBarsPadding
import com.example.cinefiles.util.loadImage
import com.example.cinefiles.util.openMovieDetails
import com.example.cinefiles.util.show
import com.example.cinefiles.util.showMessage
import com.example.cinefiles.util.showWindowMessage
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Date

/**
 * The Diary editor: a clean document-style editor inspired by Apple Notes. At the top the movie's name (tap it for Details),
 * the date and the rating; then a continuous flow of rich-text paragraphs and inline photos.
 *
 *  - The strip at the bottom has bold, italic, underline, heading, pull-quote, alignments, and Add Photo.
 *  - "Style" (DiaryStyleSheet) changes the font, the text size and the canvas theme, live.
 *  - Photos are inserted directly at the cursor position and saved in exact document order.
 */
class DiaryEditorFragment : Fragment() {

    private var _binding: FragmentDiaryEditorBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DiaryEditorViewModel by viewModels()

    private var documentFilled = false
    private var suppressTextWatchers = false
    private var activeEditor: RichEditText? = null
    private var pendingInsertEditor: RichEditText? = null
    private var pendingInsertPos: Int = 0

    private var movieId = 0
    private var font = DiaryFont.DEFAULT
    private var size = DiaryTextSize.DEFAULT
    private var align = TextAlign.DEFAULT
    private var theme = DiaryTheme.DEFAULT

    private val flushBody = Runnable { _binding?.let { flush() } }

    /** The phone's own multi-photo picker (capped at [DiaryText.MAX_PHOTOS]). */
    private val pickPhotos = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(DiaryText.MAX_PHOTOS)) { uris ->
        if (uris.isNotEmpty()) viewModel.importCovers(uris)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDiaryEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.editorTopBar.applySystemBarsPadding(top = true, bottom = false)
        binding.formatBar.applySystemBarsPadding(top = false, bottom = true)

        setUpFormatBar()

        binding.documentContainer.setOnClickListener {
            val last = currentEditors().lastOrNull() ?: return@setOnClickListener
            last.requestFocus()
            last.setSelection(last.text?.length ?: 0)
        }

        binding.editorBack.setOnClickListener { leave() }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = leave()
        })
        binding.editorStyle.setOnClickListener { DiaryStyleSheet.show(childFragmentManager) }
        binding.editorSave.setOnClickListener { save() }
        binding.movieLink.setOnClickListener { findNavController().openMovieDetails(movieId, returnIfOpenBelow = true) }

        viewModel.state.observe(viewLifecycleOwner) { render(it) }
        viewModel.font.observe(viewLifecycleOwner) { font = it; applyStyle() }
        viewModel.size.observe(viewLifecycleOwner) { size = it; applyStyle() }
        viewModel.align.observe(viewLifecycleOwner) { align = it; applyStyle(); refreshFormatState() }
        viewModel.theme.observe(viewLifecycleOwner) { theme = it; applyStyle() }
        viewModel.attachments.observe(viewLifecycleOwner) { updatePhotoLimitState(it.size) }
        viewModel.photosToInsert.observe(viewLifecycleOwner) { event ->
            event.getIfNotHandled()?.let { insertPhotos(it) }
        }
        viewModel.message.observe(viewLifecycleOwner) { event -> event.getIfNotHandled()?.let { showMessage(it) } }
        viewModel.importing.observe(viewLifecycleOwner) { binding.editorImporting.isVisible = it }
    }

    override fun onStop() {
        super.onStop()
        flush()
        viewModel.saveIfNeeded() // the app went to the background: a page must never be lost
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.root.removeCallbacks(flushBody)
        documentFilled = false
        activeEditor = null
        pendingInsertEditor = null
        _binding = null
    }

    // ---------- Setup ----------

    /** The strip: bold, italic, underline, heading, pull-quote, alignments, and Add Photo. */
    private fun setUpFormatBar() {
        binding.formatBold.setOnClickListener { toggleInline(Typeface.BOLD) }
        binding.formatItalic.setOnClickListener { toggleInline(Typeface.ITALIC) }
        binding.formatUnderline.setOnClickListener { toggleInline(RichText.UNDERLINE) }
        binding.formatUnderline.paint.isUnderlineText = true
        binding.formatHeading.setOnClickListener { toggleBlock(RichText.Block.HEADING) }
        binding.formatQuote.setOnClickListener { toggleBlock(RichText.Block.QUOTE) }
        binding.alignLeft.setOnClickListener { setParagraphAlign(RichText.ParagraphAlign.LEFT) }
        binding.alignCenter.setOnClickListener { setParagraphAlign(RichText.ParagraphAlign.CENTER) }
        binding.alignRight.setOnClickListener { setParagraphAlign(RichText.ParagraphAlign.RIGHT) }
        binding.formatPhoto.setOnClickListener { chooseCoverSource() }
    }

    // ---------- Formatting ----------

    private fun targetEditor(): RichEditText? =
        activeEditor?.takeIf { it.parent === binding.documentContainer } ?: currentEditors().firstOrNull()

    private fun currentEditors(): List<RichEditText> =
        binding.documentContainer.children.filterIsInstance<RichEditText>().toList()

    private fun currentCovers(): List<DiaryCover> =
        binding.documentContainer.children.mapNotNull { it.tag as? DiaryCover }.toList()

    private fun toggleInline(style: Int) {
        val body = targetEditor() ?: return
        val text = body.text ?: return
        body.requestFocus()
        RichText.toggle(text, style, body.selectionStart.coerceAtLeast(0), body.selectionEnd.coerceAtLeast(0))
        refreshFormatState()
        scheduleFlush()
    }

    private fun toggleBlock(block: RichText.Block) {
        val body = targetEditor() ?: return
        val text = body.text ?: return
        body.requestFocus()
        RichText.toggleBlock(
            text, block, body.selectionStart.coerceAtLeast(0), body.selectionEnd.coerceAtLeast(0),
            quoteColor(), resources.getDimensionPixelSize(R.dimen.diary_quote_bar), resources.getDimensionPixelSize(R.dimen.diary_quote_gap)
        )
        refreshFormatState()
        scheduleFlush()
    }

    private fun setParagraphAlign(paragraphAlign: RichText.ParagraphAlign) {
        val body = targetEditor() ?: return
        val text = body.text ?: return
        body.requestFocus()
        RichText.setAlignment(text, paragraphAlign, body.selectionStart.coerceAtLeast(0), body.selectionEnd.coerceAtLeast(0))
        refreshFormatState()
        scheduleFlush()
    }

    /** The buttons light up for what applies where the cursor is. */
    private fun refreshFormatState() {
        val b = _binding ?: return
        val editor = targetEditor() ?: return
        val text = editor.text ?: return
        val start = editor.selectionStart.coerceAtLeast(0)
        val end = editor.selectionEnd.coerceAtLeast(0)
        b.formatBold.isSelected = RichText.isActive(text, Typeface.BOLD, start, end)
        b.formatItalic.isSelected = RichText.isActive(text, Typeface.ITALIC, start, end)
        b.formatUnderline.isSelected = RichText.isActive(text, RichText.UNDERLINE, start, end)
        val block = RichText.blockAt(text, start)
        b.formatHeading.isSelected = block == RichText.Block.HEADING
        b.formatQuote.isSelected = block == RichText.Block.QUOTE
        val paragraphAlign = RichText.alignmentAt(text, start)
        b.alignLeft.isSelected = paragraphAlign == RichText.ParagraphAlign.LEFT
        b.alignCenter.isSelected = paragraphAlign == RichText.ParagraphAlign.CENTER
        b.alignRight.isSelected = paragraphAlign == RichText.ParagraphAlign.RIGHT
    }

    private fun scheduleFlush() {
        binding.root.removeCallbacks(flushBody)
        binding.root.postDelayed(flushBody, FLUSH_DELAY_MS)
    }

    /** Serializes the document blocks (text + inline photos in order) and hands them to the ViewModel. */
    private fun flush() {
        val b = _binding ?: return
        if (!documentFilled) return
        val segments = ArrayList<DiarySegment>()
        val covers = ArrayList<DiaryCover>()
        for (child in b.documentContainer.children) {
            when {
                child is RichEditText -> {
                    val editable = child.text ?: continue
                    segments += DiarySegment.Text(RichText.toHtml(editable))
                }
                child.tag is DiaryCover -> {
                    val cover = child.tag as DiaryCover
                    segments += DiarySegment.Photo(cover)
                    covers += cover
                }
            }
        }
        viewModel.onBodyChanged(DiaryDocument.encode(segments))
        viewModel.syncAttachments(covers)
    }

    // ---------- Drawing what the ViewModel says ----------

    private fun render(state: EditorState) {
        val ready = state as? EditorState.Ready
        binding.editorStates.isVisible = ready == null
        binding.stateLoading.root.isVisible = state is EditorState.Loading
        binding.stateMissing.root.isVisible = state is EditorState.Missing || state is EditorState.Blocked
        binding.editorScroll.isVisible = ready != null
        binding.formatBar.isVisible = ready != null
        binding.editorSave.isEnabled = ready != null

        if (state is EditorState.Missing || state is EditorState.Blocked) {
            val blocked = state is EditorState.Blocked
            binding.stateMissing.show(
                icon = R.drawable.ic_book,
                title = if (blocked) R.string.diary_blocked_title else R.string.diary_missing_title,
                message = if (blocked) R.string.diary_blocked_message else R.string.diary_movie_missing,
                action = EmptyAction(R.string.action_back) { findNavController().popBackStack() }
            )
        }
        if (ready == null) return

        movieId = ready.movieId
        binding.movieTitle.text = ready.movieTitle
        binding.movieLink.contentDescription = getString(R.string.diary_open_movie_description, ready.movieTitle)
        binding.moviePoster.loadImage(ImageUrls.poster(ready.posterPath))
        binding.pageDate.text = DateFormat.getMediumDateFormat(requireContext()).format(Date(ready.date))
        binding.pageRating.isVisible = ready.ratingSnapshot != null
        binding.pageRating.rating = ready.ratingSnapshot

        if (!documentFilled) {
            populateDocument(viewModel.currentBodyHtml(), viewModel.currentCoverUri())
            documentFilled = true
        }
        applyStyle()
        refreshFormatState()
    }

    private fun populateDocument(bodyHtml: String, legacyCoverUri: String?) {
        binding.documentContainer.removeAllViews()
        val segments = DiaryDocument.parse(bodyHtml, legacyCoverUri)
        val hasPhotos = segments.any { it is DiarySegment.Photo }
        var firstEditorAdded = false
        for (segment in segments) {
            when (segment) {
                is DiarySegment.Text -> {
                    val spanned = RichText.toSpanned(segment.html, quoteColor(), quoteBar(), quoteGap())
                    val editor = createEditor(spanned, showHint = !firstEditorAdded && !hasPhotos)
                    binding.documentContainer.addView(editor)
                    if (!firstEditorAdded) {
                        activeEditor = editor
                        firstEditorAdded = true
                    }
                }
                is DiarySegment.Photo -> {
                    binding.documentContainer.addView(createPhotoView(segment.cover))
                }
            }
        }
    }

    private fun createEditor(initialText: CharSequence, showHint: Boolean): RichEditText {
        val editor = RichEditText(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            background = null
            setPadding(0, 0, 0, 0)
            gravity = Gravity.TOP or Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            isHorizontalScrollBarEnabled = false
            setText(initialText)
            if (showHint) setHint(R.string.diary_text_hint)
        }
        applyStyleToEditor(editor)
        editor.doAfterTextChanged {
            if (documentFilled && !suppressTextWatchers) {
                scheduleFlush()
            }
        }
        editor.onSelectionChangedListener = { _, _ ->
            if (editor.isFocused || activeEditor == editor) refreshFormatState()
        }
        editor.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                activeEditor = editor
                refreshFormatState()
            }
        }
        return editor
    }

    private fun createPhotoView(cover: DiaryCover): View {
        val item = ItemDiaryInlinePhotoBinding.inflate(layoutInflater, binding.documentContainer, false)
        item.root.tag = cover
        val diaryImages = (requireActivity().application as CineFilesApp).diaryImages
        val model: Any? = when (cover.source) {
            ImageSource.TMDB -> ImageUrls.backdrop(cover.ref)
            ImageSource.LOCAL -> diaryImages.file(cover.ref)
        }
        Glide.with(item.inlinePhotoImage).load(model).centerCrop().error(R.drawable.bg_poster_fallback).into(item.inlinePhotoImage)
        item.inlinePhotoRemove.setOnClickListener { removePhotoView(item.root) }
        return item.root
    }

    private fun insertPhotos(covers: List<DiaryCover>) {
        if (covers.isEmpty() || _binding == null) return
        val target = pendingInsertEditor?.takeIf { it.parent === binding.documentContainer }
            ?: activeEditor?.takeIf { it.parent === binding.documentContainer }
            ?: currentEditors().lastOrNull()
            ?: createEditor("", showHint = false).also { binding.documentContainer.addView(it) }

        val editable = target.text ?: SpannableStringBuilder()
        val pos = (if (target === pendingInsertEditor) pendingInsertPos else target.selectionStart).coerceIn(0, editable.length)

        val before = SpannableStringBuilder(editable.subSequence(0, pos))
        val after = SpannableStringBuilder(editable.subSequence(pos, editable.length))
        // Trim a single newline right at the split boundary so inserted photos sit cleanly between paragraphs
        if (before.endsWith('\n')) before.delete(before.length - 1, before.length)
        if (after.startsWith('\n')) after.delete(0, 1)

        suppressTextWatchers = true
        target.setText(before)
        target.hint = null
        suppressTextWatchers = false

        var insertIndex = binding.documentContainer.indexOfChild(target) + 1
        var lastInsertedEditor: RichEditText = target
        covers.forEachIndexed { idx, cover ->
            val photoView = createPhotoView(cover)
            binding.documentContainer.addView(photoView, insertIndex++)
            val trailingText: CharSequence = if (idx == covers.lastIndex) after else ""
            val nextEditor = createEditor(trailingText, showHint = false)
            binding.documentContainer.addView(nextEditor, insertIndex++)
            lastInsertedEditor = nextEditor
        }

        lastInsertedEditor.requestFocus()
        lastInsertedEditor.setSelection(0)
        activeEditor = lastInsertedEditor
        pendingInsertEditor = lastInsertedEditor
        pendingInsertPos = 0
        refreshFormatState()
        flush()
    }

    private fun removePhotoView(photoView: View) {
        val container = binding.documentContainer
        val index = container.indexOfChild(photoView)
        if (index < 0) return

        val prev = container.getChildAt(index - 1) as? RichEditText
        val next = container.getChildAt(index + 1) as? RichEditText
        container.removeViewAt(index)

        if (prev != null && next != null) {
            val prevText = prev.text ?: SpannableStringBuilder()
            val nextText = next.text ?: SpannableStringBuilder()
            val joinPos = prevText.length
            val merged = SpannableStringBuilder(prevText)
            if (prevText.isNotEmpty() && nextText.isNotEmpty()) {
                merged.append('\n')
            }
            merged.append(nextText)

            suppressTextWatchers = true
            prev.setText(merged)
            suppressTextWatchers = false

            container.removeView(next)
            prev.requestFocus()
            prev.setSelection(joinPos.coerceIn(0, prev.text?.length ?: 0))
            activeEditor = prev
        }

        updateFirstEditorHint()
        refreshFormatState()
        flush()
    }

    private fun updateFirstEditorHint() {
        val hasPhotos = currentCovers().isNotEmpty()
        val editors = currentEditors()
        editors.forEachIndexed { index, editor ->
            if (index == 0 && !hasPhotos) {
                editor.setHint(R.string.diary_text_hint)
            } else {
                editor.hint = null
            }
        }
    }

    private fun updatePhotoLimitState(photoCount: Int) {
        val b = _binding ?: return
        val atLimit = photoCount >= DiaryText.MAX_PHOTOS
        b.formatPhoto.alpha = if (atLimit) 0.45f else 1f
    }

    /** The page's font, size, alignment and theme: the screen's background, the movie link, and all text blocks. */
    private fun applyStyle() {
        val b = _binding ?: return
        val context = requireContext()
        b.editorRoot.setBackgroundColor(context.themeColor(theme.background))

        b.movieTitle.applyDiaryText(font, theme, baseSp = MOVIE_SP)
        b.movieTitle.setTextColor(context.themeColor(theme.accent))
        b.movieTitle.compoundDrawablesRelative[2]?.setTint(context.themeColor(theme.accent))
        b.pageDate.applyDiaryText(font, theme, baseSp = META_SP, secondary = true)

        for (editor in currentEditors()) {
            applyStyleToEditor(editor)
        }
    }

    private fun applyStyleToEditor(editor: RichEditText) {
        editor.applyDiaryText(font, theme, baseSp = size.bodySp)
        editor.gravity = Gravity.TOP or Gravity.START
        editor.text?.let { RichText.restyleQuotes(it, quoteColor(), quoteBar(), quoteGap()) }
        editor.setLineSpacing(0f, BODY_LINE_SPACING)
    }

    private fun quoteColor() = requireContext().themeColor(theme.accent)
    private fun quoteBar() = resources.getDimensionPixelSize(R.dimen.diary_quote_bar)
    private fun quoteGap() = resources.getDimensionPixelSize(R.dimen.diary_quote_gap)

    // ---------- Actions ----------

    /** The Save button: saves and goes back, unless there is nothing to save. */
    private fun save() {
        flush()
        when {
            viewModel.isEmptiedPage -> askToDeleteEmptyPage()
            viewModel.isBlankPage -> showMessage(R.string.diary_nothing_to_save)
            else -> viewModel.saveIfNeeded {
                if (_binding != null) {
                    showWindowMessage(R.string.diary_saved)
                    findNavController().popBackStack()
                }
            }
        }
    }

    /** Saves the page (when there is something to save) and goes back. A saved page that was emptied asks first. */
    private fun leave() {
        flush()
        if (viewModel.isEmptiedPage) {
            askToDeleteEmptyPage()
        } else {
            viewModel.saveIfNeeded {
                if (_binding != null) findNavController().popBackStack()
            }
        }
    }

    /** A page that was written before and now has nothing on it: saving it empty would erase it, so the user decides. */
    private fun askToDeleteEmptyPage() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.diary_empty_page_title)
            .setMessage(R.string.diary_empty_page_message)
            .setNegativeButton(R.string.diary_empty_page_keep, null)
            .setPositiveButton(R.string.diary_empty_page_delete) { _, _ ->
                viewModel.deletePage {
                    if (_binding != null) {
                        showWindowMessage(R.string.diary_deleted)
                        findNavController().popBackStack()
                    }
                }
            }
            .show()
    }

    /** "Add photos": from the phone's photos, or stills of the movie (up to 10 photos). */
    private fun chooseCoverSource() {
        val currentEditor = targetEditor()
        pendingInsertEditor = currentEditor
        pendingInsertPos = currentEditor?.selectionStart?.coerceAtLeast(0) ?: 0

        if (currentCovers().size >= DiaryText.MAX_PHOTOS) {
            showMessage(R.string.diary_photos_limit_reached)
            return
        }
        val options = arrayOf(getString(R.string.diary_image_from_phone), getString(R.string.diary_image_from_movie))
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.diary_image_source_title)
            .setItems(options) { _, which ->
                if (which == 0) {
                    pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                } else {
                    DiaryStillsSheet.show(childFragmentManager)
                }
            }
            .show()
    }

    private companion object {
        const val FLUSH_DELAY_MS = 350L
        const val MOVIE_SP = 16f
        const val META_SP = 13f
        const val BODY_LINE_SPACING = 1.2f
    }
}
