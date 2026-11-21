package com.example.cinefiles.ui.mood

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.example.cinefiles.R
import com.example.cinefiles.databinding.FragmentMoodBinding
import com.example.cinefiles.logic.MAX_MOODS
import com.example.cinefiles.logic.Mood
import com.example.cinefiles.logic.MoodSelection
import com.example.cinefiles.logic.ToggleResult
import com.example.cinefiles.util.applySystemBarsPadding
import com.example.cinefiles.util.openScreen
import com.example.cinefiles.util.shake

/**
 * Step 1 of 3: "What are you feeling today?"
 * It draws the cards and forwards taps. The rules (max three, Surprise Me alone) are in
 * logic/MoodSelection.kt, and the choice is kept in the shared WatchFlowViewModel.
 */
class MoodFragment : Fragment() {

    // ViewBinding: only valid between onCreateView and onDestroyView
    private var _binding: FragmentMoodBinding? = null
    private val binding get() = _binding!!

    // The SAME instance is shared by Mood, Preferences, Swipe and Picks
    private val watchFlow: WatchFlowViewModel by activityViewModels()

    private lateinit var adapter: MoodAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMoodBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // The top bar sits under the status bar; the footer stays above the gesture bar
        binding.moodTopBar.applySystemBarsPadding(top = true, bottom = false)
        binding.moodFooter.applySystemBarsPadding(top = false, bottom = true)

        setUpButtons()
        setUpGrid()
        observeViewModel()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setUpButtons() {
        // X: leave the flow and go back to Home
        binding.moodClose.setOnClickListener {
            findNavController().popBackStack(R.id.homeFragment, false)
        }
        // Continue: on to the Preferences screen
        binding.continueButton.setOnClickListener {
            findNavController().openScreen(R.id.preferencesFragment)
        }
    }

    private fun setUpGrid() {
        adapter = MoodAdapter(::onMoodClicked)

        // Two columns. "Surprise Me" is the last card and takes the full width (both columns).
        val layoutManager = GridLayoutManager(requireContext(), 2)
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                val isSurprise = adapter.currentList.getOrNull(position)?.mood?.isSurprise == true
                return if (isSurprise) 2 else 1
            }
        }

        binding.moodGrid.layoutManager = layoutManager
        binding.moodGrid.adapter = adapter
        // No cross-fade when a card changes: the card animates itself (outline, check, grow)
        binding.moodGrid.itemAnimator = null
    }

    private fun onMoodClicked(mood: Mood, cardView: View) {
        val result = watchFlow.onMoodTapped(mood)
        if (result == ToggleResult.LIMIT_REACHED) {
            cardView.shake() // "no": already three moods
            // People who cannot see the shake hear the reason instead
            cardView.announceForAccessibility(getString(R.string.mood_limit_reached))
        }
    }

    private fun observeViewModel() {
        watchFlow.moodCards.observe(viewLifecycleOwner) { adapter.submitList(it) }
        watchFlow.selection.observe(viewLifecycleOwner) { showSelection(it) }
    }

    /** The footer: "2 of 3 selected", and Continue only works with at least one mood. */
    private fun showSelection(selection: MoodSelection) {
        binding.moodCounter.text = getString(R.string.mood_selected_count, selection.count, MAX_MOODS)
        binding.continueButton.isEnabled = selection.canContinue
    }
}
