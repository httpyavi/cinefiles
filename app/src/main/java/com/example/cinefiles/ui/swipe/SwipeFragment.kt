package com.example.cinefiles.ui.swipe

import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.core.content.ContextCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.cinefiles.R
import com.example.cinefiles.data.Movie
import com.example.cinefiles.databinding.FragmentSwipeBinding
import com.example.cinefiles.logic.Achievement
import com.example.cinefiles.logic.SwipeDirection
import com.example.cinefiles.ui.mood.WatchFlowViewModel
import com.example.cinefiles.util.PosterTint
import com.example.cinefiles.util.applySystemBarsPadding
import com.example.cinefiles.util.openMovieDetails
import com.example.cinefiles.util.openScreen
import com.google.android.material.snackbar.Snackbar

/**
 * Step 3 of the journey: swiping through the movie cards.
 * This class draws what SwipeViewModel says and forwards taps. The card movement is SwipeDeckView's job,
 * the rules (what is liked, how far we are, undo) are in logic/SwipeDeck.kt, and which movies come up is
 * decided by logic/Discovery.kt.
 *
 * The light behind the cards (AmbientGlowView) is the screen's mood: it takes the color of the poster on top,
 * leans red while you drag toward "like" and cool slate toward "pass".
 */
class SwipeFragment : Fragment() {

    // ViewBinding: only valid between onCreateView and onDestroyView
    private var _binding: FragmentSwipeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SwipeViewModel by viewModels()

    // The moods and preferences chosen on the first two screens (shared with them)
    private val watchFlow: WatchFlowViewModel by activityViewModels()

    private var shownTopId: Int? = null                 // the movie whose color the light shows
    private var latest: SwipeScreenState.Ready? = null  // the newest deck state, to know the NEXT card

    private val bannerQueue = ArrayDeque<Achievement>()
    private var bannerShowing = false
    private val hideBannerRunnable = Runnable { hideBanner() }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSwipeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // The top bar sits under the status bar; the buttons stay above the gesture bar
        binding.swipeTopBar.applySystemBarsPadding(top = true, bottom = false)
        binding.swipeButtons.applySystemBarsPadding(top = false, bottom = true)
        binding.swipeBannerHost.applySystemBarsPadding(top = true, bottom = false)

        // Only the first call creates the deck. Coming back to this screen keeps it as it was.
        viewModel.start(watchFlow.selection.value?.ids.orEmpty(), watchFlow.preferences)
        binding.swipeCaption.text = viewModel.moodCaption

        binding.swipeGlow.strength = GLOW_STRENGTH
        binding.swipeGlow.setTint(ContextCompat.getColor(requireContext(), R.color.accent_deep))

        setUpTopBar()
        setUpDeck()
        setUpButtons()
        setUpStateScreens()

        viewModel.screen.observe(viewLifecycleOwner) { render(it) }
        viewModel.earned.observe(viewLifecycleOwner) { event ->
            event.getIfNotHandled()?.let { badges ->
                bannerQueue.addAll(badges)
                showNextBanner()
            }
        }
    }

    override fun onDestroyView() {
        binding.swipeBanner.root.removeCallbacks(hideBannerRunnable)
        binding.swipeBanner.root.animate().cancel()
        bannerQueue.clear()
        bannerShowing = false
        super.onDestroyView()
        _binding = null
    }

    // ---------- Setup: wire up taps ----------

    private fun setUpTopBar() {
        // X leaves the flow and goes back to Home
        binding.swipeClose.setOnClickListener {
            findNavController().popBackStack(R.id.homeFragment, false)
        }
        binding.swipePicksPill.setOnClickListener { openPicks() }

        // TEMP: long-press the moods line to step through the test modes (normal, loading, error, empty)
        binding.swipeCaption.setOnLongClickListener {
            val mode = viewModel.nextTestMode()
            Snackbar.make(binding.root, mode.label, Snackbar.LENGTH_SHORT).show()
            true
        }
    }

    private fun setUpDeck() {
        // The small line above a card's title: why this movie is in the deck
        binding.swipeDeck.eyebrowFor = { movie ->
            viewModel.reasonOf(movie.id)?.let { getString(it.label) }
        }

        // These callbacks only talk to the ViewModel and to the light behind the cards
        binding.swipeDeck.listener = object : SwipeDeckView.Listener {
            override fun onSwiped(direction: SwipeDirection) {
                viewModel.onSwiped(direction)
                popButton(direction)
            }

            override fun onCardTapped() = openDetailsOfTopCard()

            // The light leans toward the side the card is dragged to...
            override fun onDrag(progress: Float) = binding.swipeGlow.setEdge(progress)

            // ...and settles when the finger lets go without a swipe
            override fun onDragCancelled() = binding.swipeGlow.fadeEdge()

            // A card leaves: the edge light flashes (a button swipe) or fades (a finger swipe), and the
            // light starts turning into the color of the NEXT poster
            override fun onCardLeaving(direction: SwipeDirection) {
                binding.swipeGlow.releaseEdge(if (direction == SwipeDirection.LIKE) 1 else -1)
                latest?.second?.let { showLightFor(it) }
            }
        }
    }

    private fun setUpButtons() {
        // The buttons do exactly what the gestures do: the same animated swipe
        binding.actionPass.setOnClickListener { binding.swipeDeck.swipeTop(SwipeDirection.PASS) }
        binding.actionLike.setOnClickListener { binding.swipeDeck.swipeTop(SwipeDirection.LIKE) }
        binding.actionUndo.setOnClickListener { undo() }
        binding.actionInfo.setOnClickListener { openDetailsOfTopCard() }
    }

    private fun setUpStateScreens() {
        with(binding.stateEmpty) {
            emptyIcon.setImageResource(R.drawable.ic_search)
            emptyTitle.setText(R.string.swipe_empty_title)
            emptyMessage.setText(R.string.swipe_empty_message)
            emptyAction.setText(R.string.swipe_empty_action)
            emptyAction.isVisible = true
            emptyAction.setOnClickListener { changeMood() }
        }
        binding.stateError.errorRetry.setOnClickListener { viewModel.load() }
    }

    // ---------- What the buttons and taps do ----------

    private fun undo() {
        val last = viewModel.lastSwipe ?: return

        // From the end screen the end screen goes away first, so the card has room to come back
        val endScreenWasShowing = binding.swipeEnd.root.isVisible
        if (endScreenWasShowing) binding.swipeEnd.root.isVisible = false

        val started = binding.swipeDeck.bringBack(last.movie, last.direction) { viewModel.onUndone() }
        if (!started && endScreenWasShowing) binding.swipeEnd.root.isVisible = true
    }

    private fun openDetailsOfTopCard() {
        val movie = viewModel.topMovie ?: return
        findNavController().openMovieDetails(movie.id)
    }

    private fun openPicks() {
        findNavController().openScreen(R.id.picksFragment)
    }

    private fun changeMood() {
        findNavController().popBackStack(R.id.moodFragment, false)
    }

    private fun changePreferences() {
        findNavController().popBackStack(R.id.preferencesFragment, false)
    }

    // ---------- Drawing what the ViewModel says ----------

    private fun render(state: SwipeScreenState) {
        val ready = state as? SwipeScreenState.Ready
        latest = ready

        // Loading and Ready share the cards-and-buttons area; error and empty cover all of it
        binding.swipeColumn.isVisible = state is SwipeScreenState.Loading || ready != null
        binding.swipeLoading.isVisible = state is SwipeScreenState.Loading
        binding.swipeDeck.isVisible = ready != null
        binding.stateError.root.isVisible = state is SwipeScreenState.Error
        binding.stateEmpty.root.isVisible = state is SwipeScreenState.Empty

        // The progress only makes sense with a deck (invisible, not gone, so the top bar keeps its height)
        binding.swipeProgressText.isInvisible = ready == null
        binding.swipeProgress.isInvisible = ready == null

        if (ready != null) {
            showDeck(ready)
        } else {
            // Nothing to swipe yet
            for (button in listOf(binding.actionUndo, binding.actionPass, binding.actionLike, binding.actionInfo)) {
                setActionEnabled(button, false)
            }
            binding.swipePicksPill.isInvisible = true
        }
    }

    private fun showDeck(ready: SwipeScreenState.Ready) {
        binding.swipeDeck.setCards(ready.top, ready.second, ready.third)

        binding.swipeProgressText.text = getString(R.string.swipe_progress, ready.position, ready.total)
        val percent = if (ready.total == 0) 0 else ready.position * 100 / ready.total
        binding.swipeProgress.setProgressCompat(percent, true)

        // The Picks pill appears after the first like
        binding.swipePicksPill.isInvisible = ready.likedCount == 0
        binding.swipePicksPill.text = getString(R.string.swipe_picks_pill, ready.likedCount)

        setActionEnabled(binding.actionUndo, ready.canUndo)
        setActionEnabled(binding.actionPass, !ready.isFinished)
        setActionEnabled(binding.actionLike, !ready.isFinished)
        setActionEnabled(binding.actionInfo, !ready.isFinished)

        // The light follows the poster on top. The next two posters are looked at too, so their color
        // is ready when they get to the front.
        ready.top?.let { showLightFor(it) }
        ready.second?.let { PosterTint.load(requireContext(), it) { } }
        ready.third?.let { PosterTint.load(requireContext(), it) { } }

        showEndOfDeck(ready)
    }

    /** The light behind the cards turns into the color of this movie's poster (when it is known). */
    private fun showLightFor(movie: Movie) {
        if (shownTopId == movie.id) return
        PosterTint.cached(movie.id)?.let {
            shownTopId = movie.id
            binding.swipeGlow.animateTint(it)
            return
        }
        // Not known yet: it is worked out now, and the light changes when it arrives
        PosterTint.load(requireContext(), movie) { color ->
            if (_binding != null && shownTopId != movie.id) {
                shownTopId = movie.id
                binding.swipeGlow.animateTint(color)
            }
        }
    }

    /** One of the four end-of-deck screens (see logic notes in view_swipe_end.xml). */
    private fun showEndOfDeck(ready: SwipeScreenState.Ready) {
        val end = binding.swipeEnd
        end.root.isVisible = ready.isFinished
        if (!ready.isFinished) return

        val liked = ready.likedCount > 0
        when {
            // There is nothing new left to show: be honest about it
            !ready.hasMore -> {
                end.swipeEndIcon.setImageResource(R.drawable.ic_check)
                end.swipeEndTitle.setText(R.string.swipe_end_exhausted_title)
                if (liked) {
                    end.swipeEndMessage.text = resources.getQuantityString(
                        R.plurals.swipe_end_exhausted_liked, ready.likedCount, ready.likedCount
                    )
                    setEndButtons(R.string.swipe_end_show_picks, ::openPicks, R.string.swipe_end_change_mood, ::changeMood)
                } else {
                    end.swipeEndMessage.setText(R.string.swipe_end_exhausted_none)
                    setEndButtons(
                        R.string.swipe_end_change_mood, ::changeMood,
                        R.string.swipe_end_change_preferences, ::changePreferences
                    )
                }
            }
            liked -> {
                end.swipeEndIcon.setImageResource(R.drawable.ic_check)
                end.swipeEndTitle.setText(R.string.swipe_end_title)
                end.swipeEndMessage.text =
                    resources.getQuantityString(R.plurals.swipe_end_liked, ready.likedCount, ready.likedCount)
                setEndButtons(R.string.swipe_end_show_picks, ::openPicks, R.string.swipe_end_keep_swiping, viewModel::keepSwiping)
            }
            else -> {
                end.swipeEndIcon.setImageResource(R.drawable.ic_search)
                end.swipeEndTitle.setText(R.string.swipe_end_zero_title)
                end.swipeEndMessage.setText(R.string.swipe_end_zero_message)
                setEndButtons(R.string.swipe_end_try_new_cards, viewModel::keepSwiping, R.string.swipe_end_change_mood, ::changeMood)
            }
        }
    }

    private fun setEndButtons(primaryText: Int, primary: () -> Unit, secondaryText: Int, secondary: () -> Unit) {
        val end = binding.swipeEnd
        end.swipeEndPrimary.setText(primaryText)
        end.swipeEndPrimary.setOnClickListener { primary() }
        end.swipeEndSecondary.setText(secondaryText)
        end.swipeEndSecondary.setOnClickListener { secondary() }
    }

    /** A button that cannot be used is dimmed too, so it does not look tappable. */
    private fun setActionEnabled(button: View, enabled: Boolean) {
        button.isEnabled = enabled
        button.alpha = if (enabled) 1f else DISABLED_ALPHA
    }

    /** The button of the swipe that just landed gives a small spring: a quiet "got it". */
    private fun popButton(direction: SwipeDirection) {
        val button = if (direction == SwipeDirection.LIKE) binding.actionLike else binding.actionPass
        button.animate().cancel()
        button.animate().scaleX(BUTTON_POP).scaleY(BUTTON_POP).setDuration(BUTTON_POP_IN_MS).withEndAction {
            button.animate().scaleX(1f).scaleY(1f).setDuration(BUTTON_POP_OUT_MS)
                .setInterpolator(OvershootInterpolator(BUTTON_POP_OVERSHOOT)).start()
        }.start()
    }

    // ---------- Achievement banner ----------

    /** Shows the next earned badge (one at a time). It slides in, waits a moment and leaves by itself. */
    private fun showNextBanner() {
        if (bannerShowing) return
        val badge = bannerQueue.removeFirstOrNull() ?: return
        bannerShowing = true

        val banner = binding.swipeBanner
        banner.achievementBannerIcon.setImageResource(badge.icon)
        banner.achievementBannerName.setText(badge.name)
        val text = getString(R.string.ach_banner_title) + ". " + getString(badge.name)
        banner.root.contentDescription = text
        banner.root.announceForAccessibility(text)
        banner.root.setOnClickListener { hideBanner() }
        banner.root.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)

        val slide = resources.getDimension(R.dimen.banner_slide)
        banner.root.animate().cancel()
        banner.root.alpha = 0f
        banner.root.translationY = -slide
        banner.root.scaleX = BANNER_START_SCALE
        banner.root.scaleY = BANNER_START_SCALE
        banner.root.isVisible = true
        banner.root.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
            .setDuration(BANNER_IN_MS).setInterpolator(OvershootInterpolator(BANNER_OVERSHOOT))
            .withEndAction { _binding?.swipeBanner?.root?.postDelayed(hideBannerRunnable, BANNER_HOLD_MS) }
            .start()
    }

    private fun hideBanner() {
        val banner = _binding?.swipeBanner?.root ?: return
        if (!bannerShowing) return
        banner.removeCallbacks(hideBannerRunnable)
        banner.animate().cancel()
        val slide = resources.getDimension(R.dimen.banner_slide)
        banner.animate().alpha(0f).translationY(-slide).setDuration(BANNER_OUT_MS)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                banner.isVisible = false
                bannerShowing = false
                _binding?.let { showNextBanner() } // the next badge, if two were earned at once
            }
            .start()
    }

    private companion object {
        const val DISABLED_ALPHA = 0.38f
        const val GLOW_STRENGTH = 0.9f
        const val BUTTON_POP = 1.16f
        const val BUTTON_POP_IN_MS = 110L
        const val BUTTON_POP_OUT_MS = 280L
        const val BUTTON_POP_OVERSHOOT = 3f
        const val BANNER_START_SCALE = 0.92f
        const val BANNER_IN_MS = 420L
        const val BANNER_OUT_MS = 260L
        const val BANNER_HOLD_MS = 2800L
        const val BANNER_OVERSHOOT = 1.1f
    }
}
