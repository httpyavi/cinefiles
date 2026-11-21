package com.example.cinefiles.ui.swipe

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.cinefiles.R
import com.example.cinefiles.databinding.FragmentSwipeBinding
import com.example.cinefiles.logic.SwipeDirection
import com.example.cinefiles.ui.mood.WatchFlowViewModel
import com.example.cinefiles.util.applySystemBarsPadding
import com.example.cinefiles.util.openMovieDetails
import com.example.cinefiles.util.openScreen
import com.google.android.material.snackbar.Snackbar

/**
 * Step 3 of the journey: swiping through the movie cards.
 * This class draws what SwipeViewModel says and forwards taps. The card movement is SwipeDeckView's job,
 * and the rules (what is liked, how far we are, undo) are in logic/SwipeDeck.kt.
 */
class SwipeFragment : Fragment() {

    // ViewBinding: only valid between onCreateView and onDestroyView
    private var _binding: FragmentSwipeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SwipeViewModel by viewModels()

    // The moods chosen on the Mood screen (shared with Mood and Preferences)
    private val watchFlow: WatchFlowViewModel by activityViewModels()

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

        // Only the first call creates the deck. Coming back to this screen keeps it as it was.
        viewModel.start(watchFlow.selection.value?.ids.orEmpty())
        binding.swipeCaption.text = viewModel.moodCaption

        setUpTopBar()
        setUpDeck()
        setUpButtons()
        setUpStateScreens()

        viewModel.screen.observe(viewLifecycleOwner) { render(it) }
    }

    override fun onDestroyView() {
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
        // These two callbacks do not touch the screen's views: they only talk to the ViewModel
        binding.swipeDeck.listener = object : SwipeDeckView.Listener {
            override fun onSwiped(direction: SwipeDirection) = viewModel.onSwiped(direction)
            override fun onCardTapped() = openDetailsOfTopCard()
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

    // ---------- Drawing what the ViewModel says ----------

    private fun render(state: SwipeScreenState) {
        val ready = state as? SwipeScreenState.Ready

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

        showEndOfDeck(ready)
    }

    /** The "You've seen them all" screen, or "Nothing caught your eye?" when nothing was liked. */
    private fun showEndOfDeck(ready: SwipeScreenState.Ready) {
        val end = binding.swipeEnd
        end.root.isVisible = ready.isFinished
        if (!ready.isFinished) return

        if (ready.likedCount > 0) {
            end.swipeEndIcon.setImageResource(R.drawable.ic_check)
            end.swipeEndTitle.setText(R.string.swipe_end_title)
            end.swipeEndMessage.text =
                resources.getQuantityString(R.plurals.swipe_end_liked, ready.likedCount, ready.likedCount)
            end.swipeEndPrimary.setText(R.string.swipe_end_show_picks)
            end.swipeEndPrimary.setOnClickListener { openPicks() }
            end.swipeEndSecondary.setText(R.string.swipe_end_keep_swiping)
            end.swipeEndSecondary.setOnClickListener { viewModel.keepSwiping() }
        } else {
            end.swipeEndIcon.setImageResource(R.drawable.ic_search)
            end.swipeEndTitle.setText(R.string.swipe_end_zero_title)
            end.swipeEndMessage.setText(R.string.swipe_end_zero_message)
            end.swipeEndPrimary.setText(R.string.swipe_end_try_new_cards)
            end.swipeEndPrimary.setOnClickListener { viewModel.keepSwiping() }
            end.swipeEndSecondary.setText(R.string.swipe_end_change_mood)
            end.swipeEndSecondary.setOnClickListener { changeMood() }
        }
    }

    /** A button that cannot be used is dimmed too, so it does not look tappable. */
    private fun setActionEnabled(button: View, enabled: Boolean) {
        button.isEnabled = enabled
        button.alpha = if (enabled) 1f else DISABLED_ALPHA
    }

    private companion object {
        const val DISABLED_ALPHA = 0.38f
    }
}
