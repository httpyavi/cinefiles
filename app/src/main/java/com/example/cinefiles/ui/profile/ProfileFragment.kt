package com.example.cinefiles.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.example.cinefiles.R
import com.example.cinefiles.databinding.FragmentProfileBinding
import com.example.cinefiles.util.applySystemBarsPadding
import com.example.cinefiles.util.openScreen
import java.text.NumberFormat

/**
 * The Profile tab: who you are, three numbers, and your achievements.
 * It only draws what ProfileViewModel says and forwards taps.
 */
class ProfileFragment : Fragment() {

    // ViewBinding: only valid between onCreateView and onDestroyView
    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ProfileViewModel by viewModels()
    private lateinit var adapter: AchievementAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // The content starts below the status bar; the scrolling area needs room above the tab bar
        binding.profileContent.applySystemBarsPadding(top = true, bottom = false)
        binding.profileScroll.applySystemBarsPadding(top = false, bottom = true)

        binding.profileGlow.strength = GLOW_STRENGTH
        binding.profileGlow.setTint(ContextCompat.getColor(requireContext(), R.color.accent))

        val name = getString(R.string.home_user_name)
        binding.profileName.text = name
        binding.profileAvatar.text = name.take(1).uppercase()

        adapter = AchievementAdapter { viewModel.select(it.achievement.id) }
        binding.achievementGrid.layoutManager = GridLayoutManager(requireContext(), COLUMNS)
        binding.achievementGrid.adapter = adapter
        binding.achievementGrid.itemAnimator = null

        // TEMP: keeps the Batch 1 design gallery reachable (removed with it in Batch 8)
        binding.openGallery.setOnClickListener { findNavController().openScreen(R.id.designGalleryFragment) }

        viewModel.state.observe(viewLifecycleOwner) { show(it) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun show(state: ProfileState) {
        val numbers = NumberFormat.getIntegerInstance()
        binding.statSwiped.text = numbers.format(state.swiped)
        binding.statLiked.text = numbers.format(state.liked)
        binding.statPassed.text = numbers.format(state.passed)
        binding.achievementCount.text =
            getString(R.string.profile_achievements_count, state.unlockedCount, state.items.size)

        adapter.submitList(state.items)
        showFocus(state.focus)

        // Badges earned since the last visit get their unlock animation now, once
        val toCelebrate = state.items.filter { it.celebrate }.map { it.achievement.id }.toSet()
        if (toCelebrate.isNotEmpty()) binding.root.post { _binding?.let { viewModel.markCelebrated(toCelebrate) } }
    }

    /** The panel above the grid. */
    private fun showFocus(focus: FocusState) {
        val item = focus.item
        if (item == null) {
            // Everything is earned
            binding.focusEyebrow.setText(R.string.profile_all_done)
            binding.focusName.text = ""
            binding.focusName.isVisible = false
            binding.focusDescription.isVisible = false
            binding.focusProgressRow.isVisible = false
            return
        }

        binding.focusName.isVisible = true
        binding.focusDescription.isVisible = true
        binding.focusProgressRow.isVisible = true

        val achievement = item.achievement
        binding.focusEyebrow.setText(if (focus.selectedByUser) R.string.profile_selected else R.string.profile_next_up)
        binding.focusName.setText(achievement.name)
        binding.focusDescription.setText(achievement.description)

        binding.focusProgress.max = achievement.goal
        binding.focusProgress.setProgressCompat(item.progress, true)
        binding.focusProgressText.text = if (item.unlocked) {
            getString(R.string.profile_unlocked_label)
        } else {
            getString(R.string.profile_progress, item.progress, achievement.goal)
        }
    }

    private companion object {
        const val COLUMNS = 3
        const val GLOW_STRENGTH = 0.55f
    }
}
