package com.example.cinefiles.ui.picks

import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.example.cinefiles.R
import com.example.cinefiles.data.Movie
import com.example.cinefiles.databinding.FragmentPicksBinding
import com.example.cinefiles.ui.components.PosterAdapter
import com.example.cinefiles.ui.components.PosterStyle
import com.example.cinefiles.ui.mood.WatchFlowViewModel
import com.example.cinefiles.util.EmptyAction
import com.example.cinefiles.util.ImageUrls
import com.example.cinefiles.util.animationsEnabled
import com.example.cinefiles.util.applySystemBarsPadding
import com.example.cinefiles.util.loadImage
import com.example.cinefiles.util.openMovieDetails
import com.example.cinefiles.util.show
import com.example.cinefiles.util.showRatingStar
import com.example.cinefiles.util.showUndo

/**
 * Step 4 of the journey: YOUR PICKS. This class only draws what the shared WatchFlowViewModel says and forwards taps.
 * Which movies are picked is decided by logic/RecommendationEngine.kt; the candidates come from MovieRepository.
 *
 * First a short "building your picks" moment (about a second, skipped when the phone's animations are off),
 * then the page: the Top Match card, "You liked", and "More for you". Done goes back to Home.
 */
class PicksFragment : Fragment() {

    // ViewBinding: only valid between onCreateView and onDestroyView
    private var _binding: FragmentPicksBinding? = null
    private val binding get() = _binding!!

    private val watchFlow: WatchFlowViewModel by activityViewModels()

    private lateinit var likedAdapter: PosterAdapter
    private lateinit var moreAdapter: PicksAdapter

    // What the page shows right now, so the Top Match button can follow the watchlist
    private var topMovie: Movie? = null
    private var watchlistIds: Set<Int> = emptySet()

    private var revealed = false          // the fade-in has played for this view
    private var lines: List<String> = emptyList()
    private var lineIndex = 0
    private val nextLine = object : Runnable {
        override fun run() {
            val line = _binding?.picksBuilding?.buildingLine ?: return
            lineIndex = (lineIndex + 1) % lines.size
            line.animate().alpha(0f).setDuration(LINE_FADE_MS).withEndAction {
                line.text = lines[lineIndex]
                line.animate().alpha(1f).setDuration(LINE_FADE_MS).start()
            }.start()
            line.postDelayed(this, LINE_EVERY_MS)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPicksBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.picksContent.applySystemBarsPadding(top = true, bottom = false)
        binding.picksScroll.applySystemBarsPadding(top = false, bottom = true)
        binding.picksGlow.strength = GLOW_STRENGTH
        binding.picksGlow.setTint(ContextCompat.getColor(requireContext(), R.color.accent))

        binding.picksStateHeader.applySystemBarsPadding(top = true, bottom = false)
        // Done goes back to Home, from the page itself and from the empty and error states
        val done = View.OnClickListener { findNavController().popBackStack(R.id.homeFragment, false) }
        binding.picksDone.setOnClickListener(done)
        binding.picksStateDone.setOnClickListener(done)

        likedAdapter = PosterAdapter(PosterStyle.ROW, onMovieClick = { movie -> findNavController().openMovieDetails(movie.id) })
        binding.likedList.adapter = likedAdapter
        binding.likedList.setHasFixedSize(true)

        moreAdapter = PicksAdapter(
            onPickClick = { pick -> QuickViewSheet.show(childFragmentManager, pick.movie.id) },
            onBookmarkClick = { movie -> watchFlow.toggleWatchlist(movie) }
        )
        binding.moreGrid.adapter = moreAdapter
        binding.moreGrid.itemAnimator = null

        // The empty state: nothing new to suggest. Two ways on.
        binding.stateEmpty.show(
            icon = R.drawable.ic_search,
            title = R.string.picks_empty_title,
            message = R.string.picks_empty_message,
            action = EmptyAction(R.string.swipe_end_keep_swiping) { findNavController().popBackStack(R.id.swipeFragment, false) },
            secondaryAction = EmptyAction(R.string.swipe_end_change_mood) { findNavController().popBackStack(R.id.moodFragment, false) }
        )
        binding.stateError.errorRetry.setOnClickListener { watchFlow.retryPicks() }

        watchFlow.picks.observe(viewLifecycleOwner) { render(it) }
        watchFlow.watchlistIds.observe(viewLifecycleOwner) { ids ->
            watchlistIds = ids
            moreAdapter.setWatchlistIds(ids)
            showTopWatchlistButton()
        }
        watchFlow.undo.observe(viewLifecycleOwner) { event ->
            event.getIfNotHandled()?.let { info -> showUndo(info) { watchFlow.undo(it) } }
        }
    }

    override fun onDestroyView() {
        binding.picksBuilding.buildingLine.removeCallbacks(nextLine)
        binding.picksBuilding.buildingLine.animate().cancel()
        super.onDestroyView()
        revealed = false
        _binding = null
    }

    // ---------- Drawing what the ViewModel says ----------

    private fun render(state: PicksState) {
        binding.picksBuilding.root.isVisible = state is PicksState.Building
        binding.picksScroll.isVisible = state is PicksState.Ready
        // "Idle" means the screen was opened with nothing to show (for example after the app was restored): say so kindly
        binding.stateEmpty.root.isVisible = state is PicksState.Empty || state is PicksState.Idle
        binding.stateError.root.isVisible = state is PicksState.Error
        binding.picksStateHeader.isVisible = state is PicksState.Empty || state is PicksState.Idle || state is PicksState.Error

        if (state is PicksState.Building) startBuildingMoment() else stopBuildingMoment()
        if (state is PicksState.Error) binding.stateError.show(state.error)
        if (state is PicksState.Ready) showPicks(state)
    }

    // ---------- "Building your picks" ----------

    private fun startBuildingMoment() {
        val building = binding.picksBuilding
        val moods = watchFlow.moodNames(" + ")
        lines = listOf(
            getString(if (watchFlow.picksLikedCount > 0) R.string.picks_building_likes else R.string.picks_building_mood, moods),
            getString(R.string.picks_building_passed),
            getString(R.string.picks_building_top)
        )
        lineIndex = 0
        building.buildingLine.removeCallbacks(nextLine)
        building.buildingLine.text = lines[0]
        building.buildingLine.alpha = 1f

        if (!requireContext().animationsEnabled()) {
            // No waiting for decoration: the picks come as soon as they are ready, so the bar just stands full
            building.buildingProgress.progress = BAR_FULL
            return
        }
        building.buildingLine.postDelayed(nextLine, LINE_EVERY_MS)
        ObjectAnimator.ofInt(building.buildingProgress, "progress", 0, BAR_FULL)
            .setDuration(BAR_MS).apply { interpolator = DecelerateInterpolator() }.start()
    }

    private fun stopBuildingMoment() {
        binding.picksBuilding.buildingLine.removeCallbacks(nextLine)
    }

    // ---------- The page ----------

    private fun showPicks(ready: PicksState.Ready) {
        binding.picksSummary.text = ready.summary
        showTopMatch(ready)

        likedAdapter.submitList(ready.liked)
        binding.likedSection.isVisible = ready.liked.isNotEmpty()

        moreAdapter.submitList(ready.more)
        binding.moreSection.isVisible = ready.more.isNotEmpty()

        if (!revealed) {
            revealed = true
            reveal()
        }
    }

    private fun showTopMatch(ready: PicksState.Ready) {
        val pick = ready.top
        val movie = pick.movie
        topMovie = movie

        binding.topBackdrop.loadImage(ImageUrls.backdrop(movie.backdropPath ?: movie.posterPath))
        binding.topMatchPill.text = getString(R.string.picks_match_percent, pick.matchPercent)
        binding.topTitle.text = movie.title

        // "7.6 · 2017 · Science Fiction, Drama", with a small star in front of the rating
        binding.topMeta.text = pickMeta(movie)
        binding.topMeta.showRatingStar(movie.voteAverage != null)

        // Two lines about the movie; when TMDB has no story, the reason it was picked
        binding.topDescription.text = movie.overview.ifBlank { requireContext().reasonText(pick.reason) }

        binding.topDetails.setOnClickListener { findNavController().openMovieDetails(movie.id) }
        binding.topMatchCard.setOnClickListener { findNavController().openMovieDetails(movie.id) }
        binding.topAddWatchlist.setOnClickListener { watchFlow.toggleWatchlist(movie) }
        showTopWatchlistButton()
    }

    /** The Top Match's watchlist button says what a tap will do, and follows the database. */
    private fun showTopWatchlistButton() {
        val movie = topMovie ?: return
        val onList = movie.id in watchlistIds
        binding.topAddWatchlist.setText(if (onList) R.string.detail_in_watchlist else R.string.action_add_watchlist)
        binding.topAddWatchlist.setIconResource(if (onList) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark)
    }

    /** The page appears with a short fade and a very small grow. (Nothing to wait for when animations are off.) */
    private fun reveal() {
        if (!requireContext().animationsEnabled()) return
        val content = binding.picksContent
        content.alpha = 0f
        content.scaleX = REVEAL_START_SCALE
        content.scaleY = REVEAL_START_SCALE
        content.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(REVEAL_MS).setInterpolator(DecelerateInterpolator()).start()
    }

    private companion object {
        const val GLOW_STRENGTH = 0.5f
        const val BAR_FULL = 100
        const val BAR_MS = 1_000L          // the thin bar fills in about the time the moment lasts
        const val LINE_EVERY_MS = 450L     // one line of text at a time, a new one every 0.45 s
        const val LINE_FADE_MS = 120L
        const val REVEAL_MS = 350L
        const val REVEAL_START_SCALE = 0.96f
    }
}
