package com.example.cinefiles.ui.diary

import android.graphics.Typeface
import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.example.cinefiles.CineFilesApp
import com.example.cinefiles.R
import com.example.cinefiles.databinding.FragmentDiaryEditorBinding
import com.example.cinefiles.logic.DiaryCover
import com.example.cinefiles.logic.DiaryFont
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
 * The Diary editor: a page written like a post on Substack, with no boxes and no cells. At the top the movie's name (tap it for the movie's
 * Details), the date and the rating; then an optional headline, an optional cover picture and ONE continuous body. It only draws what
 * DiaryEditorViewModel says and forwards taps; the two text fields ARE the editor.
 *
 *  - The strip at the bottom has bold, italic, underline, heading, pull-quote and left / center / right / justify (RichText.kt). The first five
 *    work on the words (or the paragraph) with the cursor, the alignment is the whole page's. "Style" (DiaryStyleSheet) changes the font, the
 *    text size and the canvas theme, live.
 *  - The cover is a picture from the phone's photos (copied into the app, so it never breaks) or a still of the movie from TMDB (DiaryStillsSheet).
 *  - The page is saved when you leave it, when the app goes to the background and with the Save button. A page with nothing on it is never
 *    saved; leaving a saved page that was emptied asks whether to delete it.
 */
class DiaryEditorFragment : Fragment() {

    private var _binding: FragmentDiaryEditorBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DiaryEditorViewModel by viewModels()

    private var textFilled = false // the two text fields hold the page's words (reset with the view)
    private var movieId = 0
    private var font = DiaryFont.DEFAULT
    private var size = DiaryTextSize.DEFAULT
    private var align = TextAlign.DEFAULT
    private var theme = DiaryTheme.DEFAULT

    private val flushBody = Runnable { _binding?.let { flush() } }

    /** The phone's own photo picker (it falls back to the system file picker on phones that do not have one). */
    private val pickPhoto = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.importCover(uri)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDiaryEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.editorTopBar.applySystemBarsPadding(top = true, bottom = false)
        binding.formatBar.applySystemBarsPadding(top = false, bottom = true)

        // A headline can be long: it wraps over a few lines (a text field that is not multi-line scrolls sideways unless told not to)
        binding.headlineInput.setHorizontallyScrolling(false)
        binding.headlineInput.maxLines = HEADLINE_MAX_LINES

        setUpFormatBar()
        setUpTexts()

        binding.editorBack.setOnClickListener { leave() }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = leave()
        })
        binding.editorStyle.setOnClickListener { DiaryStyleSheet.show(childFragmentManager) }
        binding.editorSave.setOnClickListener { save() }
        binding.movieLink.setOnClickListener { findNavController().openMovieDetails(movieId, returnIfOpenBelow = true) }
        binding.coverAdd.setOnClickListener { chooseCoverSource() }
        binding.coverReplace.setOnClickListener { chooseCoverSource() }
        binding.coverRemove.setOnClickListener { viewModel.removeCover() }

        viewModel.state.observe(viewLifecycleOwner) { render(it) }
        viewModel.font.observe(viewLifecycleOwner) { font = it; applyStyle() }
        viewModel.size.observe(viewLifecycleOwner) { size = it; applyStyle() }
        viewModel.align.observe(viewLifecycleOwner) { align = it; applyStyle(); refreshFormatState() }
        viewModel.theme.observe(viewLifecycleOwner) { theme = it; applyStyle() }
        viewModel.cover.observe(viewLifecycleOwner) { showCover(it) }
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
        textFilled = false
        _binding = null
    }

    // ---------- Setup ----------

    private fun setUpTexts() {
        binding.headlineInput.doAfterTextChanged { if (textFilled) viewModel.onHeadlineChanged(it?.toString().orEmpty()) }
        binding.bodyInput.doAfterTextChanged {
            if (!textFilled) return@doAfterTextChanged
            // Turning the words into HTML is a little work: do it a moment after the typing pauses (and always before saving)
            binding.root.removeCallbacks(flushBody)
            binding.root.postDelayed(flushBody, FLUSH_DELAY_MS)
        }
        binding.bodyInput.onSelectionChangedListener = { _, _ -> refreshFormatState() }
    }

    /** The strip: bold, italic, underline, heading and pull-quote act on the body's cursor or selection; the alignments on the page. */
    private fun setUpFormatBar() {
        binding.formatBold.setOnClickListener { toggleInline(Typeface.BOLD) }
        binding.formatItalic.setOnClickListener { toggleInline(Typeface.ITALIC) }
        binding.formatUnderline.setOnClickListener { toggleInline(RichText.UNDERLINE) }
        binding.formatUnderline.paint.isUnderlineText = true
        binding.formatHeading.setOnClickListener { toggleBlock(RichText.Block.HEADING) }
        binding.formatQuote.setOnClickListener { toggleBlock(RichText.Block.QUOTE) }
        binding.alignLeft.setOnClickListener { viewModel.setAlign(TextAlign.LEFT) }
        binding.alignCenter.setOnClickListener { viewModel.setAlign(TextAlign.CENTER) }
        binding.alignRight.setOnClickListener { viewModel.setAlign(TextAlign.RIGHT) }
        binding.alignJustify.setOnClickListener { viewModel.setAlign(TextAlign.JUSTIFY) }
    }

    // ---------- Formatting ----------

    private fun toggleInline(style: Int) {
        val body = binding.bodyInput
        val text = body.text ?: return
        body.requestFocus()
        RichText.toggle(text, style, body.selectionStart.coerceAtLeast(0), body.selectionEnd.coerceAtLeast(0))
        refreshFormatState()
        scheduleFlush()
    }

    private fun toggleBlock(block: RichText.Block) {
        val body = binding.bodyInput
        val text = body.text ?: return
        body.requestFocus()
        RichText.toggleBlock(
            text, block, body.selectionStart.coerceAtLeast(0), body.selectionEnd.coerceAtLeast(0),
            quoteColor(), resources.getDimensionPixelSize(R.dimen.diary_quote_bar), resources.getDimensionPixelSize(R.dimen.diary_quote_gap)
        )
        refreshFormatState()
        scheduleFlush()
    }

    /** The buttons light up for what applies where the cursor is. */
    private fun refreshFormatState() {
        val b = _binding ?: return
        val text = b.bodyInput.text ?: return
        val start = b.bodyInput.selectionStart.coerceAtLeast(0)
        val end = b.bodyInput.selectionEnd.coerceAtLeast(0)
        b.formatBold.isSelected = RichText.isActive(text, Typeface.BOLD, start, end)
        b.formatItalic.isSelected = RichText.isActive(text, Typeface.ITALIC, start, end)
        b.formatUnderline.isSelected = RichText.isActive(text, RichText.UNDERLINE, start, end)
        val block = RichText.blockAt(text, start)
        b.formatHeading.isSelected = block == RichText.Block.HEADING
        b.formatQuote.isSelected = block == RichText.Block.QUOTE
        b.alignLeft.isSelected = align == TextAlign.LEFT
        b.alignCenter.isSelected = align == TextAlign.CENTER
        b.alignRight.isSelected = align == TextAlign.RIGHT
        b.alignJustify.isSelected = align == TextAlign.JUSTIFY
    }

    private fun scheduleFlush() {
        binding.root.removeCallbacks(flushBody)
        binding.root.postDelayed(flushBody, FLUSH_DELAY_MS)
    }

    /** Hands the ViewModel what the body says right now (as HTML). */
    private fun flush() {
        val b = _binding ?: return
        if (!textFilled) return
        viewModel.onBodyChanged(RichText.toHtml(b.bodyInput.text ?: return))
        viewModel.onHeadlineChanged(b.headlineInput.text?.toString().orEmpty())
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

        // The two fields are filled once per screen with the words the ViewModel holds (after a screen turn, the latest ones)
        if (!textFilled) {
            binding.headlineInput.setText(viewModel.currentHeadline())
            binding.bodyInput.setText(RichText.toSpanned(viewModel.currentBodyHtml(), quoteColor(), quoteBar(), quoteGap()))
            textFilled = true
        }
        applyStyle()
        refreshFormatState()
    }

    /** The page's font, size, alignment and theme: the screen's background, the movie link, the headline, the cover tools and the body. */
    private fun applyStyle() {
        val b = _binding ?: return
        val context = requireContext()
        b.editorRoot.setBackgroundColor(context.themeColor(theme.background))

        b.movieTitle.applyDiaryText(font, theme, baseSp = MOVIE_SP)
        b.movieTitle.setTextColor(context.themeColor(theme.accent))
        b.movieTitle.compoundDrawablesRelative[2]?.setTint(context.themeColor(theme.accent))
        b.pageDate.applyDiaryText(font, theme, baseSp = META_SP, secondary = true)
        b.headerRule.setBackgroundColor(context.themeColor(theme.divider))

        b.headlineInput.applyDiaryText(font, theme, baseSp = size.headlineSp)
        b.headlineInput.paint.isFakeBoldText = true
        b.headlineInput.applyAlignment(align)

        b.coverAdd.setTextColor(context.themeColor(theme.secondaryText))
        b.coverAdd.iconTint = android.content.res.ColorStateList.valueOf(context.themeColor(theme.secondaryText))

        b.bodyInput.applyDiaryText(font, theme, baseSp = size.bodySp)
        b.bodyInput.applyAlignment(align)
        b.bodyInput.text?.let { RichText.restyleQuotes(it, quoteColor(), quoteBar(), quoteGap()) }
        b.bodyInput.setLineSpacing(0f, BODY_LINE_SPACING)
    }

    private fun showCover(cover: DiaryCover?) {
        val b = _binding ?: return
        b.coverBox.isVisible = cover != null
        b.coverAdd.isVisible = cover == null
        if (cover == null) {
            Glide.with(b.coverImage).clear(b.coverImage)
            return
        }
        val model: Any? = when (cover.source) {
            ImageSource.TMDB -> ImageUrls.backdrop(cover.ref)
            ImageSource.LOCAL -> (requireActivity().application as CineFilesApp).diaryImages.file(cover.ref)
        }
        Glide.with(b.coverImage).load(model).centerCrop().error(R.drawable.bg_poster_fallback).into(b.coverImage)
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

    /** "Add a cover picture": from the phone's photos, or a still of the movie. */
    private fun chooseCoverSource() {
        val options = arrayOf(getString(R.string.diary_image_from_phone), getString(R.string.diary_image_from_movie))
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.diary_image_source_title)
            .setItems(options) { _, which ->
                if (which == 0) {
                    pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                } else {
                    DiaryStillsSheet.show(childFragmentManager)
                }
            }
            .show()
    }

    private companion object {
        const val HEADLINE_MAX_LINES = 4
        const val FLUSH_DELAY_MS = 350L
        const val MOVIE_SP = 16f
        const val META_SP = 13f
        const val BODY_LINE_SPACING = 1.2f
    }
}
