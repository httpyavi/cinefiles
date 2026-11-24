package com.example.cinefiles.ui.detail

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.doOnPreDraw
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.cinefiles.R
import com.example.cinefiles.data.Movie
import com.example.cinefiles.data.Reaction
import com.example.cinefiles.databinding.FragmentDetailBinding
import com.example.cinefiles.logic.canLike
import com.example.cinefiles.logic.splitRuntime
import com.example.cinefiles.logic.watchlistButtonFor
import com.example.cinefiles.ui.components.PosterAdapter
import com.example.cinefiles.ui.components.PosterStyle
import com.example.cinefiles.util.ImageUrls
import com.example.cinefiles.util.applySystemBarsPadding
import com.example.cinefiles.util.genresText
import com.example.cinefiles.util.loadImage
import com.example.cinefiles.util.openMovieDetails
import com.example.cinefiles.util.ratingText
import com.example.cinefiles.util.shake
import com.example.cinefiles.util.show
import com.example.cinefiles.util.showRatingStar
import com.example.cinefiles.util.showMessage

/**
 * The Movie Details screen for one movie. It only draws what DetailViewModel says and forwards taps.
 * The movie's id arrives as the navigation argument "movieId" (see openMovieDetails in util/NavigationExtensions.kt).
 * The three buttons change the database; the screen follows the database, so it is always up to date.
 */
class DetailFragment : Fragment() {

    // ViewBinding: only valid between onCreateView and onDestroyView
    private var _binding: FragmentDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DetailViewModel by viewModels()

    private lateinit var similarAdapter: PosterAdapter

    // Remembered between drawings, so a toggle does not redo work that did not change
    private var shownBackdropUrl: String? = null
    private var shownOverview: String? = null
    private var overviewExpanded = false
    private var trailerKey: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // The backdrop runs under the status bar, so only the floating back button and the states are moved down
        binding.detailTopBar.applySystemBarsPadding(top = true, bottom = false)
        binding.detailStates.applySystemBarsPadding()
        binding.detailScroll.applySystemBarsPadding(top = false, bottom = true)

        binding.detailBack.setOnClickListener { findNavController().popBackStack() }

        binding.watchlistButton.setOnClickListener { viewModel.toggleWatchlist() }
        binding.likeButton.setOnClickListener { viewModel.toggleLike() }
        binding.watchedButton.setOnClickListener { viewModel.toggleWatched() }
        binding.favoriteButton.setOnClickListener { viewModel.toggleFavorite() }
        binding.trailerButton.setOnClickListener { trailerKey?.let { openTrailer(it) } }
        binding.overviewToggle.setOnClickListener {
            overviewExpanded = !overviewExpanded
            applyOverviewLines()
        }

        // Similar movies: the same poster row as Home. Tapping one opens ITS details on top of this one.
        similarAdapter = PosterAdapter(PosterStyle.ROW, onMovieClick = { movie -> findNavController().openMovieDetails(movie.id) })
        binding.similarList.adapter = similarAdapter
        binding.similarList.setHasFixedSize(true)

        binding.stateError.errorRetry.setOnClickListener { viewModel.load() }
        binding.loadErrorRetry.setOnClickListener { viewModel.load() }

        viewModel.state.observe(viewLifecycleOwner) { render(it) }
        viewModel.likeBlocked.observe(viewLifecycleOwner) { event ->
            event.getIfNotHandled()?.let {
                // THE LIKE RULE: the button wiggles "no" and the Snackbar says what to do instead
                binding.likeButton.shake()
                showMessage(R.string.like_blocked_message)
            }
        }
        viewModel.favoriteBlocked.observe(viewLifecycleOwner) { event ->
            event.getIfNotHandled()?.let {
                binding.favoriteButton.shake()
                showMessage(R.string.favorite_limit_reached)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // The next view starts fresh, so everything must be drawn again
        shownBackdropUrl = null
        shownOverview = null
        _binding = null
    }

    // ---------- Drawing what the ViewModel says ----------

    private fun render(state: DetailState) {
        val content = state as? DetailState.Content

        binding.detailScroll.isVisible = content != null
        binding.detailStates.isVisible = content == null
        binding.stateLoading.root.isVisible = state is DetailState.Loading
        binding.stateError.root.isVisible = state is DetailState.Error
        if (state is DetailState.Error) binding.stateError.show(state.error)
        if (content == null) return

        showHero(content.movie)
        showButtons(content.movie)
        showOverview(content.movie.overview)

        // The trailer button only exists when TMDB lists a YouTube trailer
        trailerKey = content.trailerKey
        binding.trailerButton.isVisible = content.trailerKey != null

        // TMDB could not be reached: say so, but keep showing what we have
        binding.detailLoadError.isVisible = content.loadError != null
        content.loadError?.let { binding.loadErrorText.setText(it.message) }

        // Similar movies: placeholders while they load, the row when they are here, nothing if there are none
        val similarLoading = content.isLoadingMore && content.similar.isEmpty()
        binding.similarSection.isVisible = similarLoading || content.similar.isNotEmpty()
        binding.similarLoading.root.isVisible = similarLoading
        binding.similarList.isVisible = !similarLoading
        similarAdapter.submitList(content.similar)
    }

    /** The picture, title, "rating · year · length" and the genres. */
    private fun showHero(movie: Movie) {
        binding.detailTitle.text = movie.title

        // A movie without a backdrop uses its poster (cropped to the wide picture) instead of an empty block
        val url = ImageUrls.backdrop(movie.backdropPath ?: movie.posterPath)
        if (url != shownBackdropUrl) {
            shownBackdropUrl = url
            binding.detailBackdrop.loadImage(url)
        }

        val rating = movie.ratingText()
        val year = movie.releaseYear?.toString()
        val length = movie.runtimeMinutes?.let { runtimeText(it) }
        val meta = listOfNotNull(rating, year, length).joinToString(" · ")
        binding.detailMeta.text = meta
        binding.detailMeta.showRatingStar(rating != null)
        binding.detailMeta.isVisible = meta.isNotEmpty()
        // A screen reader says "Rated 8.4 out of 10" instead of "star 8.4"
        binding.detailMeta.contentDescription = rating?.let {
            listOfNotNull(getString(R.string.swipe_rating_description, it), year, length).joinToString(", ")
        }

        val genres = movie.genresText()
        binding.detailGenres.text = genres
        binding.detailGenres.isVisible = genres.isNotEmpty()
    }

    /** The red watchlist button and the Like and Watched toggles show what is saved right now. */
    private fun showButtons(movie: Movie) {
        val watchlist = watchlistButtonFor(movie)
        binding.watchlistButton.setText(watchlist.label)
        binding.watchlistButton.setIconResource(
            if (watchlist.isOnList) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark
        )

        val liked = movie.reaction == Reaction.LIKED
        binding.likeButton.setText(if (liked) R.string.status_liked else R.string.action_like)
        binding.likeButton.setIconResource(if (liked) R.drawable.ic_heart_filled else R.drawable.ic_heart)
        binding.likeButton.iconTint = tint(if (liked) R.color.like else R.color.text_primary)
        // On the watchlist, Like is unavailable (THE LIKE RULE): dimmed, but still tappable so that a tap can explain why
        binding.likeButton.alpha = if (liked || canLike(movie)) 1f else UNAVAILABLE_ALPHA

        val watched = movie.isWatched
        binding.watchedButton.setText(if (watched) R.string.status_watched else R.string.detail_mark_watched)
        binding.watchedButton.setIconResource(if (watched) R.drawable.ic_check_filled else R.drawable.ic_check)
        binding.watchedButton.iconTint = tint(if (watched) R.color.accent_soft else R.color.text_primary)

        val favorite = movie.isFavorite
        binding.favoriteButton.setText(if (favorite) R.string.detail_favorited else R.string.detail_favorite)
        binding.favoriteButton.setIconResource(R.drawable.ic_star_filled)
        binding.favoriteButton.iconTint = tint(if (favorite) R.color.accent else R.color.text_primary)
    }

    private fun tint(colorRes: Int): ColorStateList =
        ColorStateList.valueOf(ContextCompat.getColor(requireContext(), colorRes))

    /** The story, collapsed to three lines. "More" is only offered when there is more to show. */
    private fun showOverview(overview: String) {
        val hasText = overview.isNotBlank()
        binding.detailOverview.isVisible = hasText
        if (!hasText) {
            binding.overviewToggle.isVisible = false
            return
        }
        if (overview != shownOverview) {
            shownOverview = overview
            overviewExpanded = false
            binding.detailOverview.text = overview
            applyOverviewLines()
        }
    }

    private fun applyOverviewLines() {
        binding.detailOverview.maxLines = if (overviewExpanded) Int.MAX_VALUE else COLLAPSED_LINES
        binding.overviewToggle.setText(if (overviewExpanded) R.string.detail_less else R.string.detail_more)

        // Only after the text has been laid out can we know whether it was cut off
        binding.detailOverview.doOnPreDraw {
            val current = _binding ?: return@doOnPreDraw
            val layout = current.detailOverview.layout ?: return@doOnPreDraw
            val isCutOff = layout.lineCount > 0 && layout.getEllipsisCount(layout.lineCount - 1) > 0
            current.overviewToggle.isVisible = overviewExpanded || isCutOff
        }
    }

    private fun runtimeText(totalMinutes: Int): String {
        val (hours, minutes) = splitRuntime(totalMinutes)
        return when {
            hours > 0 && minutes > 0 -> getString(R.string.detail_runtime_hours_minutes, hours, minutes)
            hours > 0 -> getString(R.string.detail_runtime_hours, hours)
            else -> getString(R.string.detail_runtime_minutes, minutes)
        }
    }

    /** Opens the YouTube trailer: in the YouTube app if the phone has it, otherwise in the browser. */
    private fun openTrailer(key: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$key"))
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            showMessage(R.string.detail_trailer_error)
        }
    }

    private companion object {
        const val COLLAPSED_LINES = 3
        const val UNAVAILABLE_ALPHA = 0.5f
    }
}
