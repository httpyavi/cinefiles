package com.example.cinefiles.ui.swipe

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.example.cinefiles.R
import com.example.cinefiles.databinding.FragmentTempScreenBinding
import com.example.cinefiles.logic.Moods
import com.example.cinefiles.ui.mood.WatchFlowViewModel
import com.example.cinefiles.util.applySystemBarsPadding

// TEMP: stand-in for the swipe screen, which is built in Batch 4 (that batch rewrites this file).
// For now it only shows what the user chose on the Mood and Preferences screens, read from the
// shared WatchFlowViewModel, to prove the choices arrive. It reuses the stand-in layout.
// Listed in TEMP.md.
class SwipeFragment : Fragment() {

    private var _binding: FragmentTempScreenBinding? = null
    private val binding get() = _binding!!

    private val watchFlow: WatchFlowViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTempScreenBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.tempRoot.applySystemBarsPadding()

        binding.tempTitle.setText(R.string.temp_swipe_title)
        binding.tempMessage.setText(R.string.temp_swipe_message)
        binding.tempBack.isVisible = true
        binding.tempBack.setOnClickListener { findNavController().navigateUp() }

        binding.tempExtra.isVisible = true
        binding.tempExtra.text = describeChoices()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /** A few lines that list the moods and preferences the user chose. */
    private fun describeChoices(): String {
        val none = getString(R.string.temp_swipe_none)
        val moodIds = watchFlow.selection.value?.ids.orEmpty()
        val preferences = watchFlow.preferences

        val moodNames = Moods.all.filter { it.id in moodIds }
            .joinToString { getString(it.label) }
            .ifEmpty { none }
        val genreIds = Moods.genreIdsFor(moodIds)
            .joinToString()
            .ifEmpty { none }

        return getString(
            R.string.temp_swipe_summary,
            moodNames,
            genreIds,
            getString(preferences.releasePeriod.label),
            getString(preferences.minRating.label),
            getString(preferences.length.label),
            getString(preferences.language.label)
        )
    }
}
