package com.example.cinefiles.ui.diary

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.cinefiles.R
import com.example.cinefiles.data.DiaryEntry
import com.example.cinefiles.databinding.FragmentDiaryListBinding
import com.example.cinefiles.util.EmptyAction
import com.example.cinefiles.util.applySystemBarsPadding
import com.example.cinefiles.util.openDiaryEditor
import com.example.cinefiles.util.openMovieDetails
import com.example.cinefiles.util.openTab
import com.example.cinefiles.util.shake
import com.example.cinefiles.util.show
import com.example.cinefiles.util.showMessage
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * "My Diary": the diary pages as a list of cards, newest first. With a movie (opened from its Details) only that movie's pages are
 * listed and "New page" starts another one. It only draws what DiaryListViewModel says and forwards taps.
 */
class DiaryListFragment : Fragment() {

    private var _binding: FragmentDiaryListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DiaryListViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDiaryListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.diaryContent.applySystemBarsPadding()
        binding.diaryGlow.strength = GLOW_STRENGTH
        binding.diaryGlow.setTint(ContextCompat.getColor(requireContext(), R.color.accent))

        binding.diaryBack.setOnClickListener { findNavController().popBackStack() }

        val forOneMovie = viewModel.movieId != 0
        binding.diaryNewPage.isVisible = forOneMovie
        binding.diaryNewPage.setOnClickListener { viewModel.requestNewPage() }
        viewModel.newPage.observe(viewLifecycleOwner) { event ->
            event.getIfNotHandled()?.let { allowed ->
                if (allowed) {
                    findNavController().openDiaryEditor(movieId = viewModel.movieId)
                } else {
                    binding.diaryNewPage.shake() // THE WATCH RULE: no new page for a movie that is not watched
                    showMessage(R.string.diary_blocked_message)
                }
            }
        }

        val adapter = DiaryEntryAdapter(
            onOpen = { entry -> findNavController().openDiaryEditor(entryId = entry.id, movieId = entry.movieId) },
            onOpenMovie = { entry -> findNavController().openMovieDetails(entry.movieId, returnIfOpenBelow = true) },
            onDelete = { entry -> confirmDelete(entry) }
        )
        binding.diaryList.adapter = adapter
        binding.diaryList.itemAnimator = null

        // "Diary: Interstellar" for one movie, "My Diary" for all of them
        viewModel.movieTitle.observe(viewLifecycleOwner) { title ->
            binding.diaryTitle.text = if (title != null) getString(R.string.diary_list_movie_title, title) else getString(R.string.diary_list_title)
        }
        if (forOneMovie) {
            // "Diary: Interstellar" is a link to that movie's Details, like the movie's name on a page
            binding.diaryTitle.isClickable = true
            binding.diaryTitle.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_chevron_right, 0)
            binding.diaryTitle.setOnClickListener { findNavController().openMovieDetails(viewModel.movieId, returnIfOpenBelow = true) }
        }

        binding.stateEmpty.show(
            icon = R.drawable.ic_book,
            title = R.string.diary_empty_title,
            message = if (forOneMovie) R.string.diary_empty_movie_message else R.string.diary_empty_message,
            action = if (forOneMovie) {
                EmptyAction(R.string.diary_new_page) { viewModel.requestNewPage() }
            } else {
                EmptyAction(R.string.diary_empty_action) { findNavController().openTab(R.id.discoverFragment) }
            }
        )

        viewModel.entries.observe(viewLifecycleOwner) { entries ->
            adapter.submitList(entries)
            binding.diaryList.isVisible = entries.isNotEmpty()
            binding.stateEmpty.root.isVisible = entries.isEmpty()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /** Deleting a page also deletes its pictures and cannot be undone, so it asks first. */
    private fun confirmDelete(entry: DiaryEntry) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.diary_delete_title)
            .setMessage(R.string.diary_delete_message)
            .setNegativeButton(R.string.profile_cancel, null)
            .setPositiveButton(R.string.diary_delete) { _, _ ->
                viewModel.delete(entry)
                showMessage(R.string.diary_deleted)
            }
            .show()
    }

    private companion object {
        const val GLOW_STRENGTH = 0.6f
    }
}
