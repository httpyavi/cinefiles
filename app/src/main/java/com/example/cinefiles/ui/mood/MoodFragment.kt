package com.example.cinefiles.ui.mood

import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.example.cinefiles.R
import com.example.cinefiles.databinding.FragmentMoodBinding
import com.example.cinefiles.logic.MAX_MOODS
import com.example.cinefiles.logic.Mood
import com.example.cinefiles.logic.MoodSelection
import com.example.cinefiles.logic.Moods
import com.example.cinefiles.logic.ToggleResult
import com.example.cinefiles.util.applySystemBarsPadding
import com.example.cinefiles.util.openScreen
import com.example.cinefiles.util.shake
import com.google.android.material.chip.Chip

/**
 * Step 1 of 3: "What are you feeling today?"
 *
 * Users explore and choose moods via a 2-column, 5-row grid of the exact 10 moods.
 * Regular moods allow choosing up to 3 moods. Surprise Me is mutually exclusive with regular moods.
 */
class MoodFragment : Fragment() {

    private var _binding: FragmentMoodBinding? = null
    private val binding get() = _binding!!

    private val watchFlow: WatchFlowViewModel by activityViewModels()
    private val moods = Moods.grid
    private lateinit var gridAdapter: MoodGridAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMoodBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.moodTopBar.applySystemBarsPadding(top = true, bottom = false)
        binding.moodFooter.applySystemBarsPadding(top = false, bottom = true)

        binding.moodGlow.strength = GLOW_STRENGTH
        binding.moodGlow.focusY = GLOW_FOCUS_Y

        setUpGrid()
        setUpButtons()
        observeViewModel()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setUpGrid() {
        val initialSelection = watchFlow.selection.value ?: MoodSelection()
        gridAdapter = MoodGridAdapter(moods, initialSelection) { mood, itemView ->
            onMoodTileTapped(mood, itemView)
        }

        binding.moodGrid.apply {
            layoutManager = GridLayoutManager(requireContext(), 2)
            adapter = gridAdapter
            isNestedScrollingEnabled = false
        }
    }

    private fun setUpButtons() {
        binding.moodClose.setOnClickListener {
            findNavController().popBackStack(R.id.homeFragment, false)
        }

        binding.continueButton.setOnClickListener {
            findNavController().openScreen(R.id.preferencesFragment, slide = true)
        }
    }

    private fun onMoodTileTapped(mood: Mood, tileView: View) {
        val result = watchFlow.onMoodTapped(mood)
        when (result) {
            ToggleResult.SELECTED -> {
                confirmHaptic(tileView)
            }
            ToggleResult.DESELECTED -> {
                tickHaptic(tileView)
            }
            ToggleResult.LIMIT_REACHED -> {
                tileView.shake()
                binding.moodCounter.shake()
                tileView.announceForAccessibility(getString(R.string.mood_limit_reached))
            }
        }
    }

    private fun observeViewModel() {
        watchFlow.selection.observe(viewLifecycleOwner) { showSelection(it) }
    }

    private fun showSelection(selection: MoodSelection) {
        gridAdapter.updateSelection(selection)

        // 1. Counter text
        binding.moodCounter.text = when {
            selection.count == 0 -> getString(R.string.mood_hint)
            selection.ids.contains(Moods.SURPRISE_ID) -> getString(R.string.mood_surprise_selected)
            else -> getString(R.string.mood_selected_count, selection.count, MAX_MOODS)
        }

        // 2. Continue button state
        binding.continueButton.isEnabled = selection.canContinue

        // 3. Ambient glow
        if (selection.ids.isNotEmpty()) {
            val lastMoodId = selection.ids.last()
            val mood = moods.firstOrNull { it.id == lastMoodId }
            if (mood != null) {
                val color = ContextCompat.getColor(requireContext(), mood.tint)
                binding.moodGlow.animateTint(color)
            }
        } else {
            binding.moodGlow.animateTint(ContextCompat.getColor(requireContext(), R.color.accent))
        }

        // 4. Chosen mood chips in footer
        binding.moodChips.removeAllViews()
        for (id in selection.ids) {
            val mood = moods.firstOrNull { it.id == id } ?: continue
            binding.moodChips.addView(makeChip(mood))
        }
        binding.moodChipsScroll.visibility = if (selection.ids.isNotEmpty()) View.VISIBLE else View.GONE
    }

    private fun makeChip(mood: Mood): Chip {
        val chip = layoutInflater.inflate(R.layout.item_mood_chip, binding.moodChips, false) as Chip
        val label = getString(mood.shortLabel)
        chip.text = label
        chip.chipStrokeColor = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), mood.tint))
        chip.closeIconContentDescription = getString(R.string.mood_remove_description, label)
        chip.setOnCloseIconClickListener { watchFlow.onMoodTapped(mood) }
        chip.setOnClickListener { watchFlow.onMoodTapped(mood) }
        return chip
    }

    private fun tickHaptic(view: View) {
        val feedback = if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CLOCK_TICK else HapticFeedbackConstants.CONTEXT_CLICK
        view.performHapticFeedback(feedback)
    }

    private fun confirmHaptic(view: View) {
        val feedback = if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CONTEXT_CLICK
        view.performHapticFeedback(feedback)
    }

    private companion object {
        const val GLOW_STRENGTH = 0.35f
        const val GLOW_FOCUS_Y = 0.32f
    }
}
