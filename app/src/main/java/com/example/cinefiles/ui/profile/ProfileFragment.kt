package com.example.cinefiles.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.example.cinefiles.BuildConfig
import com.example.cinefiles.R
import com.example.cinefiles.data.FavoriteResult
import com.example.cinefiles.data.Movie
import com.example.cinefiles.databinding.DialogEditNameBinding
import com.example.cinefiles.databinding.FragmentProfileBinding
import com.example.cinefiles.logic.CollectionTab
import com.example.cinefiles.logic.MAX_FAVORITES
import com.example.cinefiles.logic.Taste
import com.example.cinefiles.logic.TastePersonality
import com.example.cinefiles.ui.collection.CollectionViewModel
import com.example.cinefiles.ui.mood.WatchFlowViewModel
import com.example.cinefiles.util.applySystemBarsPadding
import com.example.cinefiles.util.formatted
import com.example.cinefiles.util.openDiaryList
import com.example.cinefiles.util.openMovieDetails
import com.example.cinefiles.util.openTab
import com.example.cinefiles.util.showMessage
import com.example.cinefiles.util.showUndo
import com.example.cinefiles.util.showWindowMessage
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * The Profile tab: your name, your 4 favorites (press and hold a poster, then drag to reorder; tap an empty place to pick a
 * watched movie), your numbers and taste, quick access to Liked, Watchlist, Watched and your Movie Diary, About and Clear my data.
 */
class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ProfileViewModel by viewModels()
    private val collectionViewModel: CollectionViewModel by activityViewModels()
    private val watchFlow: WatchFlowViewModel by activityViewModels()

    private lateinit var favoritesAdapter: FavoritesAdapter

    /** The phone's own photo picker (it falls back to the system file picker on phones that do not have one). */
    private val pickPhoto = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.setPhoto(uri)
    }

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
        binding.profileAvatarFrame.setOnClickListener { showPhotoChooser() }
        binding.clearData.setOnClickListener { showClearDataDialog() }

        setUpFavorites()
        setUpNavigationRows()
        observeData()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun observeData() {
        viewModel.name.observe(viewLifecycleOwner) { binding.profileName.text = it }
        viewModel.avatarLook.observe(viewLifecycleOwner) { binding.profileAvatar.show(it.letter, it.photo) }
        viewModel.photoFailed.observe(viewLifecycleOwner) { event ->
            event.getIfNotHandled()?.let { showMessage(R.string.profile_photo_failed, aboveTabBar = true) }
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

        viewModel.diaryCount.observe(viewLifecycleOwner) { binding.navDiaryCount.text = it.formatted() }

        binding.btnExploreMovies.setOnClickListener {
            findNavController().openTab(R.id.discoverFragment)
        }

        viewModel.favorites.observe(viewLifecycleOwner) { favorites ->
            favoritesAdapter.submit(favorites)
            binding.favoritesHint.setText(
                if (favorites.isEmpty()) R.string.profile_favorites_empty else R.string.profile_favorites_reorder_hint
            )
        }
        viewModel.undo.observe(viewLifecycleOwner) { event ->
            event.getIfNotHandled()?.let { info -> showUndo(info, aboveTabBar = true) { viewModel.restore(it) } }
        }
        viewModel.favoriteRefused.observe(viewLifecycleOwner) { event ->
            event.getIfNotHandled()?.let { result ->
                showMessage(
                    if (result == FavoriteResult.LIMIT_REACHED) R.string.favorite_limit_reached else R.string.favorite_blocked_message,
                    aboveTabBar = true
                )
            }
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

        binding.navDiaryRow.setOnClickListener { findNavController().openDiaryList() }
    }

    /**
     * The four favorite places: a poster opens its Details, an empty place opens the picker (watched movies only), the x
     * removes it (with Undo), and a long press then a drag moves it. The order is saved when the poster is dropped.
     */
    private fun setUpFavorites() {
        favoritesAdapter = FavoritesAdapter(
            onMovieClick = { movie -> findNavController().openMovieDetails(movie.id) },
            onEmptyClick = { FavoritePickerSheet.show(childFragmentManager) },
            onRemoveClick = { movie -> viewModel.removeFavorite(movie) },
            onOrderChanged = { ordered: List<Movie> -> viewModel.reorderFavorites(ordered.map { it.id }) }
        )
        binding.favoritesList.layoutManager = GridLayoutManager(requireContext(), MAX_FAVORITES)
        binding.favoritesList.adapter = favoritesAdapter
        binding.favoritesList.itemAnimator = null
        favoritesAdapter.touchHelper.attachToRecyclerView(binding.favoritesList)
    }

    private fun showTaste(taste: Taste) {
        if (!taste.hasEnoughData) {
            binding.tasteEmptyContainer.isVisible = true
            binding.tasteContentContainer.isVisible = false
            return
        }
        binding.tasteEmptyContainer.isVisible = false
        binding.tasteContentContainer.isVisible = true
        binding.profileTaste.text = getString(personalityStringRes(taste.personality!!))
        binding.profileTasteSupport.text = if (taste.secondaryGenre != null && taste.topGenre != null) {
            getString(R.string.profile_taste_support_pair, taste.topGenre, taste.secondaryGenre)
        } else {
            val genre = taste.topGenre.orEmpty()
            resources.getQuantityString(R.plurals.profile_taste_support_single, taste.topGenreCount, taste.topGenreCount, genre)
        }
    }

    private fun personalityStringRes(personality: TastePersonality): Int = when (personality) {
        TastePersonality.ROMANTIC -> R.string.profile_taste_personality_romantic
        TastePersonality.DRAMA_ROMANCE -> R.string.profile_taste_personality_drama_romance
        TastePersonality.MIND_BENDING -> R.string.profile_taste_personality_mind_bending
        TastePersonality.THRILL_SEEKER -> R.string.profile_taste_personality_thrill_seeker
        TastePersonality.DEEP_FEELER -> R.string.profile_taste_personality_deep_feeler
        TastePersonality.COMEDY_LOVER -> R.string.profile_taste_personality_comedy_lover
        TastePersonality.ROMCOM_FAN -> R.string.profile_taste_personality_romcom_fan
        TastePersonality.SUSPENSE_FAN -> R.string.profile_taste_personality_suspense_fan
        TastePersonality.HORROR_FAN -> R.string.profile_taste_personality_horror_fan
        TastePersonality.DREAMER -> R.string.profile_taste_personality_dreamer
        TastePersonality.CURIOUS_MIND -> R.string.profile_taste_personality_curious_mind
        TastePersonality.MUSIC_SOUL -> R.string.profile_taste_personality_music_soul
        TastePersonality.FRONTIER_SPIRIT -> R.string.profile_taste_personality_frontier_spirit
        TastePersonality.ECLECTIC -> R.string.profile_taste_personality_eclectic
    }

    // ---------- Profile photo, Edit Name & Clear Data Dialogs ----------

    /** A small chooser: a picture from the gallery, and (when there is a photo) going back to the first letter of the name. */
    private fun showPhotoChooser() {
        val hasPhoto = viewModel.avatarLook.value?.photo != null
        val options = buildList {
            add(getString(R.string.profile_photo_gallery))
            if (hasPhoto) add(getString(R.string.profile_photo_remove))
        }.toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.profile_photo_title)
            .setItems(options) { _, which ->
                if (which == 0) pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) else viewModel.removePhoto()
            }
            .show()
    }

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
