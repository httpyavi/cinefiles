package com.example.cinefiles.ui.profile

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.example.cinefiles.BuildConfig
import com.example.cinefiles.R
import com.example.cinefiles.databinding.DialogEditNameBinding
import com.example.cinefiles.databinding.FragmentProfileBinding
import com.example.cinefiles.logic.Taste
import com.example.cinefiles.logic.initialOf
import com.example.cinefiles.ui.mood.WatchFlowViewModel
import com.example.cinefiles.util.applySystemBarsPadding
import com.example.cinefiles.util.formatted
import com.example.cinefiles.util.openTab
import com.example.cinefiles.util.showWindowMessage
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * The Profile tab: who you are (name with Edit), three numbers, your taste, your achievements, About CineFiles with
 * the TMDB credit, and Clear my data. It only draws what ProfileViewModel says and forwards taps.
 */
class ProfileFragment : Fragment() {

    // ViewBinding: only valid between onCreateView and onDestroyView
    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ProfileViewModel by viewModels()

    // Clear my data also ends any "watch something" journey in progress
    private val watchFlow: WatchFlowViewModel by activityViewModels()

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

        adapter = AchievementAdapter { viewModel.select(it.achievement.id) }
        binding.achievementGrid.layoutManager = GridLayoutManager(requireContext(), COLUMNS)
        binding.achievementGrid.adapter = adapter
        binding.achievementGrid.itemAnimator = null

        binding.aboutVersion.text = getString(R.string.profile_about_version, BuildConfig.VERSION_NAME)
        binding.profileEdit.setOnClickListener { showEditNameDialog() }
        binding.clearData.setOnClickListener { showClearDataDialog() }

        viewModel.state.observe(viewLifecycleOwner) { show(it) }
        viewModel.name.observe(viewLifecycleOwner) { name ->
            binding.profileName.text = name
            binding.profileAvatar.text = initialOf(name)
        }
        viewModel.taste.observe(viewLifecycleOwner) { showTaste(it) }
        viewModel.dataCleared.observe(viewLifecycleOwner) { event ->
            event.getIfNotHandled()?.let {
                watchFlow.reset()
                findNavController().openTab(R.id.homeFragment)
                showWindowMessage(R.string.profile_cleared)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ---------- Drawing what the ViewModel says ----------

    private fun show(state: ProfileState) {
        binding.statSwiped.text = state.swiped.formatted()
        binding.statLiked.text = state.liked.formatted()
        binding.statPassed.text = state.passed.formatted()
        binding.achievementCount.text =
            getString(R.string.profile_achievements_count, state.unlockedCount, state.items.size)

        adapter.submitList(state.items)
        showFocus(state.focus)

        // Badges earned since the last visit get their unlock animation now, once
        val toCelebrate = state.items.filter { it.celebrate }.map { it.achievement.id }.toSet()
        if (toCelebrate.isNotEmpty()) binding.root.post { _binding?.let { viewModel.markCelebrated(toCelebrate) } }
    }

    /** "Drama · 9 liked movies", or a dash while nothing is liked. */
    private fun showTaste(taste: Taste) {
        binding.profileTaste.text = when {
            taste.likedCount == 0 -> getString(R.string.profile_taste_none)
            taste.topGenre == null -> likedMovies(taste.likedCount)
            else -> getString(R.string.profile_taste_value, taste.topGenre, likedMovies(taste.likedCount))
        }
    }

    private fun likedMovies(count: Int): String =
        resources.getQuantityString(R.plurals.profile_taste_liked, count, count)

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

    // ---------- The two dialogs ----------

    /** A small dialog with one text field. A blank name keeps the dialog open and says so. */
    private fun showEditNameDialog() {
        val dialogViews = DialogEditNameBinding.inflate(layoutInflater)
        dialogViews.nameInput.setText(viewModel.name.value)
        dialogViews.nameInput.setSelection(dialogViews.nameInput.length())

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.profile_edit_title)
            .setView(dialogViews.root)
            .setNegativeButton(R.string.profile_cancel, null)
            .setPositiveButton(R.string.profile_edit_save, null) // set below, so a blank name does not close the dialog
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

    /** CLEAR MY DATA asks first: it cannot be undone. */
    private fun showClearDataDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.profile_clear_title)
            .setMessage(R.string.profile_clear_message)
            .setNegativeButton(R.string.profile_cancel, null)
            .setPositiveButton(R.string.profile_clear_confirm) { _, _ -> viewModel.clearMyData() }
            .show()
    }

    private companion object {
        const val COLUMNS = 3
        const val GLOW_STRENGTH = 0.55f
    }
}
