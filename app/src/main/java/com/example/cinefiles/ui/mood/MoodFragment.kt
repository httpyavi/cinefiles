package com.example.cinefiles.ui.mood

import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
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
 * The slider moves through the nine moods (in the order of Moods.spectrum), the face follows it, and the
 * light behind them takes the mood's color. Tapping the face, or the pill under the name, adds that mood
 * to the mix. The rules (max three, Surprise Me alone) are in logic/MoodSelection.kt, and the choice is
 * kept in the shared WatchFlowViewModel, exactly as before: only the way of choosing is new.
 */
class MoodFragment : Fragment(), MoodSliderView.Listener {

    // ViewBinding: only valid between onCreateView and onDestroyView
    private var _binding: FragmentMoodBinding? = null
    private val binding get() = _binding!!

    // The SAME instance is shared by Mood, Preferences, Swipe and Picks
    private val watchFlow: WatchFlowViewModel by activityViewModels()

    private val moods = Moods.spectrum
    private lateinit var colors: IntArray

    private var shownIndex = -1       // the mood whose name and sentence are on screen
    private var lastValue = 0f        // to see which way, and how fast, the slider is moving
    private var shownSelection = MoodSelection()

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

        colors = MoodSpectrum.colors(requireContext())
        binding.moodGlow.focusY = GLOW_FOCUS_Y

        setUpButtons()
        setUpSlider()
        observeViewModel()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ---------- Setup ----------

    private fun setUpButtons() {
        // X: leave the flow and go back to Home
        binding.moodClose.setOnClickListener {
            findNavController().popBackStack(R.id.homeFragment, false)
        }
        // Continue: on to the Preferences screen
        binding.continueButton.setOnClickListener {
            findNavController().openScreen(R.id.preferencesFragment, slide = true)
        }
        // The face and the pill do the same thing: add (or take out) the mood that is showing
        binding.moodFace.setOnClickListener { toggleCurrentMood() }
        binding.moodToggle.setOnClickListener { toggleCurrentMood() }
    }

    private fun setUpSlider() {
        // Start where the user left off (or on the first chosen mood, if they come back here)
        val firstChosen = watchFlow.selection.value?.ids?.firstOrNull()
            ?.let { id -> moods.indexOfFirst { it.id == id } }?.takeIf { it >= 0 }
        val startIndex = firstChosen ?: watchFlow.moodSliderIndex

        binding.moodSlider.jumpTo(startIndex.toFloat())
        lastValue = startIndex.toFloat()
        binding.moodSlider.listener = this

        showPosition(startIndex.toFloat())
        showMoodTexts(startIndex, animate = false, movingForward = true)
    }

    // ---------- The slider tells us ----------

    override fun onValueChanged(value: Float) {
        showPosition(value)

        // The face leans the way the handle is dragged: a small, smoothed tilt
        val lean = ((value - lastValue) * TILT_PER_STEP).coerceIn(-MAX_TILT, MAX_TILT)
        binding.moodFace.rotation = binding.moodFace.rotation * 0.8f + lean * 0.2f
        lastValue = value
    }

    override fun onIndexChanged(index: Int) {
        watchFlow.moodSliderIndex = index
        showMoodTexts(index, animate = true, movingForward = index > shownIndex)
        showToggle()
    }

    override fun onSettled(index: Int) {
        binding.moodFace.animate().rotation(0f).setDuration(TILT_RESET_MS).start()
        // Say the new mood once the handle has come to rest (not on every tick while dragging)
        ViewCompat.setStateDescription(
            binding.moodSlider,
            getString(R.string.mood_slider_state, getString(moods[index].shortLabel), index + 1, moods.size)
        )
    }

    /** The face, the light and the slider all show the same place on the slider. */
    private fun showPosition(position: Float) {
        binding.moodFace.setPosition(position)
        binding.moodGlow.setTint(MoodSpectrum.colorAt(colors, position))
    }

    // ---------- Showing a mood ----------

    private fun showMoodTexts(index: Int, animate: Boolean, movingForward: Boolean) {
        if (index == shownIndex) return
        shownIndex = index
        val mood = moods[index]
        swapText(binding.moodName, getString(mood.shortLabel), animate, movingForward)
        swapText(binding.moodLine, getString(mood.line), animate, movingForward)
        showToggle()
    }

    /** The name and the sentence slide out and the new ones slide in, in the direction you are moving. */
    private fun swapText(view: TextView, text: String, animate: Boolean, movingForward: Boolean) {
        view.animate().cancel()
        if (!animate) {
            view.text = text
            view.alpha = 1f
            view.translationY = 0f
            return
        }
        val shift = resources.getDimension(R.dimen.mood_text_shift) * (if (movingForward) 1f else -1f)
        view.animate().alpha(0f).translationY(-shift).setDuration(TEXT_OUT_MS).withEndAction {
            view.text = text
            view.translationY = shift
            view.animate().alpha(1f).translationY(0f).setDuration(TEXT_IN_MS)
                .setInterpolator(DecelerateInterpolator()).start()
        }.start()
    }

    // ---------- Choosing ----------

    private fun toggleCurrentMood() {
        val mood = moods[binding.moodSlider.index]
        val result = watchFlow.onMoodTapped(mood)
        when (result) {
            ToggleResult.SELECTED -> {
                binding.moodFace.pop()
                confirmHaptic()
            }
            ToggleResult.DESELECTED -> binding.moodFace.pop()
            ToggleResult.LIMIT_REACHED -> {
                binding.moodFace.shake() // "no": already three moods
                // People who cannot see the shake hear the reason instead
                binding.moodFace.announceForAccessibility(getString(R.string.mood_limit_reached))
            }
        }
    }

    private fun confirmHaptic() {
        val feedback = if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CONTEXT_CLICK
        binding.moodFace.performHapticFeedback(feedback)
    }

    // ---------- Drawing what the ViewModel says ----------

    private fun observeViewModel() {
        watchFlow.selection.observe(viewLifecycleOwner) { showSelection(it) }
    }

    private fun showSelection(selection: MoodSelection) {
        shownSelection = selection

        binding.moodCounter.text = if (selection.count == 0) {
            getString(R.string.mood_hint)
        } else {
            getString(R.string.mood_selected_count, selection.count, MAX_MOODS)
        }
        binding.continueButton.isEnabled = selection.canContinue

        // One removable chip for each chosen mood, in the order they were chosen
        binding.moodChips.removeAllViews()
        for (id in selection.ids) {
            val mood = Moods.all.first { it.id == id }
            binding.moodChips.addView(makeChip(mood))
        }
        showToggle()
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

    /** The pill under the name says "Add to my mix" or "In my mix", in the mood's own color. */
    private fun showToggle() {
        val mood = moods[binding.moodSlider.index]
        val chosen = shownSelection.isSelected(mood)
        val color = MoodSpectrum.colorAt(colors, binding.moodSlider.index.toFloat())
        val toggle = binding.moodToggle

        toggle.setText(if (chosen) R.string.mood_added else R.string.mood_add)
        toggle.setIconResource(if (chosen) R.drawable.ic_check else R.drawable.ic_add)
        if (chosen) {
            toggle.backgroundTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(color, CHOSEN_FILL_ALPHA))
            toggle.strokeColor = ColorStateList.valueOf(color)
        } else {
            toggle.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.glass_fill_strong)
            toggle.strokeColor = ContextCompat.getColorStateList(requireContext(), R.color.glass_stroke)
        }
        toggle.contentDescription = getString(
            if (chosen) R.string.mood_remove_description else R.string.mood_add_description,
            getString(mood.shortLabel)
        )
    }

    private companion object {
        const val TEXT_OUT_MS = 90L
        const val TEXT_IN_MS = 170L
        const val TILT_PER_STEP = 28f     // degrees of lean for each whole mood the handle travels
        const val MAX_TILT = 9f
        const val TILT_RESET_MS = 320L
        const val GLOW_FOCUS_Y = 0.36f    // the light sits right behind the face
        const val CHOSEN_FILL_ALPHA = 64  // the pill's fill when the mood is chosen (out of 255)
    }
}
