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

        viewModel.ratedCount.observe(viewLifecycleOwner) { count ->
            binding.statRated.text = count.formatted()
            binding.navRatingsCount.text = count.formatted()
        }

        viewModel.diaryCount.observe(viewLifecycleOwner) { count ->
            binding.statDiary.text = count.formatted()
            binding.navDiaryCount.text = count.formatted()
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

        binding.navRatingsRow.setOnClickListener {
            showRatingsDialog()
        }

        binding.navDiaryRow.setOnClickListener {
            showDiaryDialog()
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
        binding.profileTaste.text = when {
            taste.likedCount == 0 -> getString(R.string.profile_taste_none)
            taste.topGenre == null -> likedMovies(taste.likedCount)
            else -> getString(R.string.profile_taste_value, taste.topGenre, likedMovies(taste.likedCount))
        }
    }

    private fun likedMovies(count: Int): String =
        resources.getQuantityString(R.plurals.profile_taste_liked, count, count)

    // ---------- Ratings & Diary Dialogs ----------

    private fun showRatingsDialog() {
        val dialogBinding = DialogProfileListBinding.inflate(layoutInflater)
        dialogBinding.dialogTitle.text = getString(R.string.profile_nav_ratings)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .create()

        dialogBinding.dialogCloseButton.setOnClickListener { dialog.dismiss() }

        val movies = viewModel.ratedMovies.value.orEmpty()
        dialogBinding.dialogSubtitle.text = getString(R.string.detail_rating_format, "${movies.size} movies")
        dialogBinding.dialogEmptyMessage.text = getString(R.string.profile_no_ratings)
        dialogBinding.dialogEmptyMessage.isVisible = movies.isEmpty()
        dialogBinding.dialogRecycler.isVisible = movies.isNotEmpty()

        if (movies.isNotEmpty()) {
            dialogBinding.dialogRecycler.layoutManager = LinearLayoutManager(requireContext())
            dialogBinding.dialogRecycler.adapter = RatedMoviesAdapter(movies) { movie ->
                dialog.dismiss()
                findNavController().openMovieDetails(movie.id)
            }
        }

        dialog.show()
    }

    private fun showDiaryDialog() {
        val dialogBinding = DialogProfileListBinding.inflate(layoutInflater)
        dialogBinding.dialogTitle.text = getString(R.string.profile_nav_diary)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .create()

        dialogBinding.dialogCloseButton.setOnClickListener { dialog.dismiss() }

        val entries = viewModel.diaryEntries.value.orEmpty()
        dialogBinding.dialogSubtitle.text = "${entries.size} entries"
        dialogBinding.dialogEmptyMessage.text = getString(R.string.profile_no_diary)
        dialogBinding.dialogEmptyMessage.isVisible = entries.isEmpty()
        dialogBinding.dialogRecycler.isVisible = entries.isNotEmpty()

        if (entries.isNotEmpty()) {
            dialogBinding.dialogRecycler.layoutManager = LinearLayoutManager(requireContext())
            dialogBinding.dialogRecycler.adapter = DiaryEntriesAdapter(entries) { entry ->
                dialog.dismiss()
                findNavController().openMovieDetails(entry.movieId.toInt())
            }
        }

        dialog.show()
    }

    // ---------- Edit Name & Clear Data Dialogs ----------

    private fun showEditNameDialog() {
        val dialogViews = DialogEditNameBinding.inflate(layoutInflater)
        dialogViews.nameInput.setText(viewModel.name.value)
        dialogViews.nameInput.setSelection(dialogViews.nameInput.length())

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.profile_edit_title)
            .setView(dialogViews.root)
            .setNegativeButton(R.string.profile_cancel, null)
            .setPositiveButton(R.string.profile_edit_save, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                if (viewModel.rename(dialogViews.nameInput.text.toString())) {
                    dialog.dismiss()
                } else {
                    dialogViews.nameLayout.error = getString(R.string.profile_edit_error)
                }
            }
        }
        dialog.show()
    }

    private fun showClearDataDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.profile_clear_title)
            .setMessage(R.string.profile_clear_message)
            .setNegativeButton(R.string.profile_cancel, null)
            .setPositiveButton(R.string.profile_clear_confirm) { _, _ -> viewModel.clearMyData() }
            .show()
    }

    // ---------- Adapters for Dialogs ----------

    private class RatedMoviesAdapter(
        private val movies: List<Movie>,
        private val onClick: (Movie) -> Unit
    ) : RecyclerView.Adapter<RatedMoviesAdapter.ViewHolder>() {

        class ViewHolder(val binding: ItemProfileRatedMovieBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemProfileRatedMovieBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun getItemCount(): Int = movies.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val movie = movies[position]
            holder.binding.movieTitle.text = movie.title
            holder.binding.movieYear.text = movie.releaseDate?.take(4) ?: ""
            val rating = movie.personalRating ?: 0f
            holder.binding.movieRating.text = "★ ${String.format(Locale.US, "%.1f", rating)} / 5.0"
            holder.binding.moviePoster.loadImage(ImageUrls.poster(movie.posterPath))
            holder.binding.root.setOnClickListener { onClick(movie) }
        }
    }

    private class DiaryEntriesAdapter(
        private val entries: List<DiaryEntry>,
        private val onClick: (DiaryEntry) -> Unit
    ) : RecyclerView.Adapter<DiaryEntriesAdapter.ViewHolder>() {

        private val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

        class ViewHolder(val binding: ItemProfileDiaryEntryBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemProfileDiaryEntryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun getItemCount(): Int = entries.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val entry = entries[position]
            holder.binding.diaryMovieTitle.text = entry.movieTitle
            holder.binding.diaryDate.text = dateFormat.format(Date(entry.dateWatched))
            holder.binding.diaryRewatch.isVisible = entry.isRewatch

            if (entry.rating != null && entry.rating > 0f) {
                holder.binding.diaryRating.isVisible = true
                holder.binding.diaryRating.text = "★ ${String.format(Locale.US, "%.1f", entry.rating)}"
            } else {
                holder.binding.diaryRating.isVisible = false
            }

            if (!entry.notes.isNullOrBlank()) {
                holder.binding.diaryNotes.isVisible = true
                holder.binding.diaryNotes.text = entry.notes
            } else {
                holder.binding.diaryNotes.isVisible = false
            }

            holder.binding.diaryPoster.loadImage(ImageUrls.poster(entry.posterPath))
            holder.binding.root.setOnClickListener { onClick(entry) }
        }
    }

    private companion object {
        const val GLOW_STRENGTH = 0.55f
    }
}
