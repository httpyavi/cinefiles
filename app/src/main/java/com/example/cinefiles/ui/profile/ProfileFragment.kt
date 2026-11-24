package com.example.cinefiles.ui.profile

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.cinefiles.BuildConfig
import com.example.cinefiles.R
import com.example.cinefiles.data.DiaryEntry
import com.example.cinefiles.data.Movie
import com.example.cinefiles.databinding.DialogEditNameBinding
import com.example.cinefiles.databinding.DialogProfileListBinding
import com.example.cinefiles.databinding.FragmentProfileBinding
import com.example.cinefiles.databinding.ItemProfileDiaryEntryBinding
import com.example.cinefiles.databinding.ItemProfileRatedMovieBinding
import com.example.cinefiles.databinding.ViewFavoriteSlotBinding
import com.example.cinefiles.logic.CollectionTab
import com.example.cinefiles.logic.Taste
import com.example.cinefiles.logic.initialOf
import com.example.cinefiles.ui.collection.CollectionViewModel
import com.example.cinefiles.ui.mood.WatchFlowViewModel
import com.example.cinefiles.util.ImageUrls
import com.example.cinefiles.util.applySystemBarsPadding
import com.example.cinefiles.util.formatted
import com.example.cinefiles.util.loadImage
import com.example.cinefiles.util.openMovieDetails
import com.example.cinefiles.util.openTab
import com.example.cinefiles.util.showMessage
import com.example.cinefiles.util.showWindowMessage
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Redesigned Profile tab: personal film space with 4 favorites, movie statistics,
 * and quick access to Liked Movies, Watchlist, Watched, Personal Ratings, and Diary.
 */
class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ProfileViewModel by viewModels()
    private val collectionViewModel: CollectionViewModel by activityViewModels()
    private val watchFlow: WatchFlowViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.profileContent.applySystemBarsPadding(top = true, bottom = false)
        binding.profileScroll.applySystemBarsPadding(top = false, bottom = true)

        binding.profileGlow.strength = GLOW_STRENGTH
        binding.profileGlow.setTint(ContextCompat.getColor(requireContext(), R.color.accent))

        binding.aboutVersion.text = getString(R.string.profile_about_version, BuildConfig.VERSION_NAME)
        binding.profileEdit.setOnClickListener { showEditNameDialog() }
        binding.clearData.setOnClickListener { showClearDataDialog() }

        setUpNavigationRows()
        observeData()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun observeData() {
        viewModel.name.observe(viewLifecycleOwner) { name ->
            binding.profileName.text = name
            binding.profileAvatar.text = initialOf(name)
        }

        viewModel.taste.observe(viewLifecycleOwner) { showTaste(it) }

        viewModel.counts.observe(viewLifecycleOwner) { counts ->
            binding.statWatched.text = counts.watched.formatted()
            binding.statLiked.text = counts.liked.formatted()
            binding.statWatchlist.text = counts.watchlist.formatted()

            binding.navLikedCount.text = counts.liked.formatted()
            binding.navWatchlistCount.text = counts.watchlist.formatted()
            binding.navWatchedCount.text = counts.watched.formatted()
        }

        binding.btnExploreMovies.setOnClickListener {
            findNavController().openTab(R.id.discoverFragment)
        }

        viewModel.favorites.observe(viewLifecycleOwner) { favorites ->
            renderFavorites(favorites)
        }

        viewModel.dataCleared.observe(viewLifecycleOwner) { event ->
            event.getIfNotHandled()?.let {
                watchFlow.reset()
                findNavController().openTab(R.id.homeFragment)
                showWindowMessage(R.string.profile_cleared)
            }
        }
    }

    private fun setUpNavigationRows() {
        binding.navLikedRow.setOnClickListener {
            collectionViewModel.selectTab(CollectionTab.LIKED)
            findNavController().openTab(R.id.collectionFragment)
        }

        binding.navWatchlistRow.setOnClickListener {
            collectionViewModel.selectTab(CollectionTab.WATCHLIST)
            findNavController().openTab(R.id.collectionFragment)
        }

        binding.navWatchedRow.setOnClickListener {
            collectionViewModel.selectTab(CollectionTab.WATCHED)
            findNavController().openTab(R.id.collectionFragment)
        }
    }

    private fun renderFavorites(favorites: List<Movie>) {
        val slots = listOf(binding.slot1, binding.slot2, binding.slot3, binding.slot4)

        binding.favoritesHint.isVisible = favorites.isEmpty()
        binding.favoritesCountLabel.text = getString(R.string.profile_favorites_subtitle)

        for (i in slots.indices) {
            val slot = slots[i]
            if (i < favorites.size) {
                val movie = favorites[i]
                slot.posterImage.isVisible = true
                slot.posterImage.loadImage(ImageUrls.poster(movie.posterPath))
                slot.placeholderContainer.isVisible = false
                slot.removeButton.isVisible = true
                slot.movieTitle.isVisible = true
                slot.movieTitle.text = movie.title

                slot.slotContainer.setOnClickListener {
                    findNavController().openMovieDetails(movie.id)
                }
                slot.removeButton.setOnClickListener {
                    viewModel.removeFavorite(movie.id)
                }
            } else {
                slot.posterImage.isVisible = false
                slot.placeholderContainer.isVisible = true
                slot.removeButton.isVisible = false
                slot.movieTitle.isVisible = false

                slot.slotContainer.setOnClickListener {
                    showMessage(R.string.profile_favorites_empty)
                }
            }
        }
    }

    private fun showTaste(taste: Taste) {
        if (taste.likedCount == 0) {
            binding.tasteEmptyContainer.isVisible = true
            binding.tasteContentContainer.isVisible = false
        } else {
            binding.tasteEmptyContainer.isVisible = false
            binding.tasteContentContainer.isVisible = true
            binding.profileTaste.text = if (taste.topGenre == null) {
                likedMovies(taste.likedCount)
            } else {
                getString(R.string.profile_taste_value, taste.topGenre, likedMovies(taste.likedCount))
            }
        }
    }

    private fun likedMovies(count: Int): String =
        resources.getQuantityString(R.plurals.profile_taste_liked, count, count)

    // ---------- Edit Name & Clear Data Dialogs ----------

    private fun showEditNameDialog() {
        val dialogBinding = DialogEditNameBinding.inflate(layoutInflater)
        dialogBinding.nameInput.setText(viewModel.name.value)
        dialogBinding.nameInput.setSelection(dialogBinding.nameInput.length())

        val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogBinding.btnCancel.setOnClickListener { dialog.dismiss() }
        dialogBinding.btnSave.setOnClickListener {
            val input = dialogBinding.nameInput.text.toString()
            if (viewModel.rename(input)) {
                dialog.dismiss()
            } else {
                dialogBinding.errorText.text = getString(R.string.profile_edit_error)
                dialogBinding.errorText.isVisible = true
            }
        }

        dialogBinding.nameInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                dialogBinding.btnSave.performClick()
                true
            } else false
        }

        dialog.show()
        dialogBinding.nameInput.requestFocus()
    }

    private fun showClearDataDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.profile_clear_title)
            .setMessage(R.string.profile_clear_message)
            .setNegativeButton(R.string.profile_cancel, null)
            .setPositiveButton(R.string.profile_clear_confirm) { _, _ -> viewModel.clearMyData() }
            .show()
    }

    private companion object {
        const val GLOW_STRENGTH = 0.55f
    }
}
