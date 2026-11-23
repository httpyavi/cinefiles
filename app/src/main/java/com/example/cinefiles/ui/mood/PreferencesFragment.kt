package com.example.cinefiles.ui.mood

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.example.cinefiles.R
import com.example.cinefiles.databinding.FragmentPreferencesBinding
import com.example.cinefiles.logic.Language
import com.example.cinefiles.logic.Length
import com.example.cinefiles.logic.MinRating
import com.example.cinefiles.logic.ReleasePeriod
import com.example.cinefiles.util.addChoiceChip
import com.example.cinefiles.util.applySystemBarsPadding
import com.example.cinefiles.util.openScreen
import com.google.android.material.chip.ChipGroup

/**
 * Step 2 of 3: "Make it yours". Four optional questions, each starting at "Any".
 * The choices go straight into the shared WatchFlowViewModel.
 */
class PreferencesFragment : Fragment() {

    // ViewBinding: only valid between onCreateView and onDestroyView
    private var _binding: FragmentPreferencesBinding? = null
    private val binding get() = _binding!!

    private val watchFlow: WatchFlowViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPreferencesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.prefsTopBar.applySystemBarsPadding(top = true, bottom = false)
        binding.prefsFooter.applySystemBarsPadding(top = false, bottom = true)
        binding.prefsGlow.strength = GLOW_STRENGTH

        setUpButtons()
        setUpChips()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setUpButtons() {
        binding.prefsBack.setOnClickListener { findNavController().navigateUp() }

        // Skip = start swiping with "Any" everywhere
        binding.prefsSkip.setOnClickListener {
            watchFlow.resetPreferences()
            startSwiping()
        }
        binding.startSwipingButton.setOnClickListener { startSwiping() }
    }

    private fun startSwiping() {
        findNavController().openScreen(R.id.swipeFragment, slide = true)
    }

    /** Fills the four chip groups from the option lists in logic/Preferences.kt. */
    private fun setUpChips() {
        val chosen = watchFlow.preferences // what was chosen earlier (also when coming back to this screen)

        fillChips(binding.periodChips, ReleasePeriod.values().toList(), chosen.releasePeriod,
            { it.label }, watchFlow::setReleasePeriod)
        fillChips(binding.ratingChips, MinRating.values().toList(), chosen.minRating,
            { it.label }, watchFlow::setMinRating)
        fillChips(binding.lengthChips, Length.values().toList(), chosen.length,
            { it.label }, watchFlow::setLength)
        fillChips(binding.languageChips, Language.values().toList(), chosen.language,
            { it.label }, watchFlow::setLanguage)
    }

    /**
     * Puts one chip per option into [group], checks the chip of [selected], and tells
     * [onPicked] whenever the user picks another one.
     */
    private fun <T> fillChips(
        group: ChipGroup,
        options: List<T>,
        selected: T,
        labelOf: (T) -> Int,
        onPicked: (T) -> Unit
    ) {
        group.removeAllViews()
        val optionOfChip = HashMap<Int, T>() // chip id -> its option

        for (option in options) {
            val chip = group.addChoiceChip(getString(labelOf(option)))
            optionOfChip[chip.id] = option
            if (option == selected) group.check(chip.id)
        }

        // Set after the first check above, so building the chips does not count as a pick
        group.setOnCheckedStateChangeListener { _, checkedIds ->
            val option = checkedIds.firstOrNull()?.let { optionOfChip[it] }
            if (option != null) onPicked(option)
        }
    }

    private companion object {
        const val GLOW_STRENGTH = 0.4f
    }
}
