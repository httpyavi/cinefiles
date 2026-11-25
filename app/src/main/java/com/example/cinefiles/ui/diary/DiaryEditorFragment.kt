package com.example.cinefiles.ui.diary

import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.example.cinefiles.CineFilesApp
import com.example.cinefiles.R
import com.example.cinefiles.databinding.FragmentDiaryEditorBinding
import com.example.cinefiles.logic.DiaryBlock
import com.example.cinefiles.logic.DiaryFont
import com.example.cinefiles.logic.DiaryTheme
import com.example.cinefiles.logic.PopcornRating
import com.example.cinefiles.logic.TextAlign
import com.example.cinefiles.util.EmptyAction
import com.example.cinefiles.util.applySystemBarsPadding
import com.example.cinefiles.util.show
import com.example.cinefiles.util.showMessage
import com.example.cinefiles.util.showWindowMessage
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import java.util.Date

/**
 * The Diary editor: a page written like an article, with no boxes. The movie is the headline, then paragraphs and pictures flow down the page.
 * It only draws what DiaryEditorViewModel says and forwards taps; DiaryBlockAdapter edits the page.
 *
 *  - "Paragraph" starts a new paragraph run at the end; "Picture" puts a picture where the cursor is (in the middle of a run it splits it).
 *    A picture comes from the phone's photo picker (copied into the app, so it never breaks) or from the movie's own stills on TMDB (DiaryStillsSheet).
 *  - While a paragraph run has the cursor, a strip above the buttons offers bold, italic and left / center / right (the buttons show what applies
 *    at the cursor).
 *  - A picture can be dragged (press its handle), moved with its arrows, or deleted (with Undo). An empty run is deleted with Backspace.
 *  - "Style" (DiaryStyleSheet) changes the page's font and background theme, live.
 *  - The page is saved when you leave it, when the app goes to the background and with the Save button.
 */
class DiaryEditorFragment : Fragment(), DiaryBlockAdapter.Listener {

    private var _binding: FragmentDiaryEditorBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DiaryEditorViewModel by viewModels()

    private lateinit var headerAdapter: DiaryHeaderAdapter
    private lateinit var adapter: DiaryBlockAdapter
    private lateinit var touchHelper: ItemTouchHelper

    private var listFilled = false // the adapter has the page's blocks (reset with the view)
    private var currentFont = DiaryFont.SANS
    private var currentTheme = DiaryTheme.MIDNIGHT

    /** The phone's own photo picker (it falls back to the system file picker on phones that do not have one). */
    private val pickPhoto = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.importImage(uri)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDiaryEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.editorTopBar.applySystemBarsPadding(top = true, bottom = false)
        binding.editorAddBar.applySystemBarsPadding(top = false, bottom = true)

        setUpList()
        setUpFormatBar()

        binding.editorBack.setOnClickListener { leave() }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = leave()
        })
        binding.editorStyle.setOnClickListener { DiaryStyleSheet.show(childFragmentManager) }
        binding.editorSave.setOnClickListener {
            viewModel.saveIfNeeded {
                if (_binding != null) {
                    showWindowMessage(R.string.diary_saved)
                    findNavController().popBackStack()
                }
            }
        }
        binding.addText.setOnClickListener { showAndFocus(adapter.addParagraph()) }
        binding.addImage.setOnClickListener { chooseImageSource() }

        viewModel.state.observe(viewLifecycleOwner) { render(it) }
        viewModel.font.observe(viewLifecycleOwner) { currentFont = it; applyStyle() }
        viewModel.theme.observe(viewLifecycleOwner) { currentTheme = it; applyStyle() }
        viewModel.newBlock.observe(viewLifecycleOwner) { event ->
            event.getIfNotHandled()?.let { picture -> scrollTo(adapter.insertPicture(picture)) }
        }
        viewModel.message.observe(viewLifecycleOwner) { event -> event.getIfNotHandled()?.let { showMessage(it) } }
        viewModel.importing.observe(viewLifecycleOwner) { binding.editorImporting.isVisible = it }
    }

    override fun onStop() {
        super.onStop()
        viewModel.saveIfNeeded() // the app went to the background: a page must never be lost
    }

    override fun onDestroyView() {
        super.onDestroyView()
        listFilled = false
        _binding = null
    }

    // ---------- Setup ----------

    private fun setUpList() {
        val app = requireActivity().application as CineFilesApp
        headerAdapter = DiaryHeaderAdapter()
        adapter = DiaryBlockAdapter(app.diaryImages, this)
        adapter.headerCount = 1
        binding.blockList.adapter = ConcatAdapter(headerAdapter, adapter)
        binding.blockList.itemAnimator = null

        touchHelper = ItemTouchHelper(object : ItemTouchHelper.Callback() {
            // Only a picture can be dragged (by its handle); the headline and the paragraph runs stay where they are
            override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int =
                if (viewHolder is DiaryBlockAdapter.ImageHolder) makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) else 0

            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean =
                isPageBlock(target) && adapter.move(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)

            override fun canDropOver(recyclerView: RecyclerView, current: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean =
                isPageBlock(target)

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

            override fun isLongPressDragEnabled(): Boolean = false // a long press belongs to the text: only the handle drags
        })
        touchHelper.attachToRecyclerView(binding.blockList)
    }

    /** The headline belongs to another adapter, so its holder's place must never be mistaken for a place on the page. */
    private fun isPageBlock(holder: RecyclerView.ViewHolder) =
        holder is DiaryBlockAdapter.TextHolder || holder is DiaryBlockAdapter.ImageHolder

    /** The strip above the buttons: bold, italic and the three alignments, acting on the paragraph run with the cursor. */
    private fun setUpFormatBar() {
        binding.formatBold.setOnClickListener { adapter.toggleBold() }
        binding.formatItalic.setOnClickListener { adapter.toggleItalic() }
        binding.alignLeft.setOnClickListener { adapter.setAlignment(TextAlign.LEFT) }
        binding.alignCenter.setOnClickListener { adapter.setAlignment(TextAlign.CENTER) }
        binding.alignRight.setOnClickListener { adapter.setAlignment(TextAlign.RIGHT) }
    }

    // ---------- What the page tells us (DiaryBlockAdapter.Listener) ----------

    override fun onChanged(blocks: List<DiaryBlock>) {
        viewModel.onBlocksChanged(blocks)
    }

    override fun onStartDrag(holder: RecyclerView.ViewHolder) {
        touchHelper.startDrag(holder)
    }

    override fun onPictureDeleted(block: DiaryBlock, index: Int) {
        // Deleting is easy to do by accident, so it offers Undo
        val root = _binding?.root ?: return
        Snackbar.make(root, R.string.diary_block_deleted, Snackbar.LENGTH_LONG)
            .setAction(R.string.action_undo) {
                if (_binding != null) adapter.insert(index, block)
            }
            .show()
    }

    override fun onFormatChanged(state: DiaryBlockAdapter.FormatState?) {
        val b = _binding ?: return
        b.formatBar.isVisible = state != null
        if (state == null) return
        b.formatBold.isSelected = state.bold
        b.formatItalic.isSelected = state.italic
        b.alignLeft.isSelected = state.align == TextAlign.LEFT
        b.alignCenter.isSelected = state.align == TextAlign.CENTER
        b.alignRight.isSelected = state.align == TextAlign.RIGHT
    }

    // ---------- Drawing what the ViewModel says ----------

    private fun render(state: EditorState) {
        val ready = state as? EditorState.Ready
        binding.editorStates.isVisible = ready == null
        binding.stateLoading.root.isVisible = state is EditorState.Loading
        binding.stateMissing.root.isVisible = state is EditorState.Missing || state is EditorState.Blocked
        binding.blockList.isVisible = ready != null
        binding.editorAddBar.isVisible = ready != null
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

        val date = DateFormat.getMediumDateFormat(requireContext()).format(Date(ready.date))
        val meta = ready.ratingSnapshot?.let { getString(R.string.diary_meta_rated, date, PopcornRating.format(it)) } ?: date
        headerAdapter.show(ready.movieTitle, meta)

        // The adapter is filled once per screen with the blocks the ViewModel holds (after a screen turn, the latest ones)
        if (!listFilled) {
            listFilled = true
            adapter.submit(viewModel.currentBlocks())
        }
        applyStyle()
    }

    /** The page's font and theme: the screen's background, the headline and every paragraph. */
    private fun applyStyle() {
        val b = _binding ?: return
        b.editorRoot.setBackgroundColor(requireContext().themeColor(currentTheme.background))
        headerAdapter.setStyle(currentFont, currentTheme)
        adapter.setStyle(currentFont, currentTheme)
    }

    // ---------- Actions ----------

    /** Saves the page (when there is something to save) and goes back. */
    private fun leave() {
        viewModel.saveIfNeeded {
            if (_binding != null) findNavController().popBackStack()
        }
    }

    /** "Add picture": from the phone's photos, or a still of the movie. */
    private fun chooseImageSource() {
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

    /** Scrolls to the block at [position] of the page (the headline is one place above it in the list). */
    private fun scrollTo(position: Int) {
        if (position >= 0) binding.blockList.scrollToPosition(position + adapter.headerCount)
    }

    /** Scrolls to a paragraph run and puts the cursor in it, with the keyboard. */
    private fun showAndFocus(position: Int) {
        scrollTo(position)
        binding.blockList.post { _binding?.let { adapter.focusParagraph(position) } }
    }
}
