package com.example.cinefiles.ui.mood

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
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
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Step 1 of 3: "What are you feeling today?"
 *
 * Users explore moods by directly swiping left or right on the character, or tapping the directional cues.
 * Features an ambient background glow matching the active mood, expressive Poppins typography,
 * and playful, responsive transitions.
 */
class MoodFragment : Fragment() {

    private var _binding: FragmentMoodBinding? = null
    private val binding get() = _binding!!

    private val watchFlow: WatchFlowViewModel by activityViewModels()

    private val moods = Moods.spectrum
    private lateinit var colors: IntArray

    private var currentIndex = 0
    private var currentPosition = 0f
    private var shownIndex = -1
    private var shownSelection = MoodSelection()

    private var positionAnimator: ValueAnimator? = null
    private var velocityTracker: VelocityTracker? = null

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

        colors = MoodSpectrum.colors(requireContext())
        binding.moodGlow.strength = GLOW_STRENGTH
        binding.moodGlow.focusY = GLOW_FOCUS_Y

        setUpInitialIndex()
        setUpButtons()
        setUpSwiping()
        observeViewModel()
    }

    override fun onDestroyView() {
        positionAnimator?.cancel()
        velocityTracker?.recycle()
        velocityTracker = null
        super.onDestroyView()
        _binding = null
    }

    // ---------- Setup ----------

    private fun setUpInitialIndex() {
        val firstChosen = watchFlow.selection.value?.ids?.firstOrNull()
            ?.let { id -> moods.indexOfFirst { it.id == id } }?.takeIf { it >= 0 }
        currentIndex = (firstChosen ?: watchFlow.moodSliderIndex).coerceIn(0, moods.size - 1)
        currentPosition = currentIndex.toFloat()

        binding.moodFace.setPosition(currentPosition)
        binding.moodGlow.setTint(MoodSpectrum.colorAt(colors, currentPosition))
        showMoodTexts(currentIndex, animate = false, movingForward = true)
        updateArrowStates()
    }

    private fun setUpButtons() {
        binding.moodClose.setOnClickListener {
            findNavController().popBackStack(R.id.homeFragment, false)
        }

        binding.continueButton.setOnClickListener {
            findNavController().openScreen(R.id.preferencesFragment, slide = true)
        }

        binding.moodToggle.setOnClickListener { toggleCurrentMood() }

        binding.moodPrev.setOnClickListener {
            if (currentIndex > 0) {
                navigateToMood(currentIndex - 1)
            }
        }

        binding.moodNext.setOnClickListener {
            if (currentIndex < moods.size - 1) {
                navigateToMood(currentIndex + 1)
            }
        }
    }

    /**
     * Implements direct horizontal swiping on the character area.
     * Horizontal dragging disallows parent vertical scroll interception.
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun setUpSwiping() {
        val viewConfig = ViewConfiguration.get(requireContext())
        val touchSlop = viewConfig.scaledTouchSlop
        val minFlingVelocity = viewConfig.scaledMinimumFlingVelocity * 1.5f

        var startX = 0f
        var startY = 0f
        var startTime = 0L
        var isDraggingHorizontally = false
        var lastTickedStep = currentIndex

        binding.moodFace.setOnTouchListener { v, event ->
            if (velocityTracker == null) {
                velocityTracker = VelocityTracker.obtain()
            }
            velocityTracker?.addMovement(event)

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    positionAnimator?.cancel()
                    startX = event.x
                    startY = event.y
                    startTime = System.currentTimeMillis()
                    isDraggingHorizontally = false
                    lastTickedStep = currentIndex
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - startX
                    val dy = event.y - startY

                    if (!isDraggingHorizontally) {
                        if (abs(dx) > touchSlop && abs(dx) > abs(dy)) {
                            isDraggingHorizontally = true
                            v.parent?.requestDisallowInterceptTouchEvent(true)
                        }
                    }

                    if (isDraggingHorizontally) {
                        val swipeRange = (v.width * 0.7f).coerceAtLeast(100f)
                        val posDelta = -dx / swipeRange
                        val newPos = (currentIndex + posDelta).coerceIn(0f, (moods.size - 1).toFloat())
                        currentPosition = newPos

                        binding.moodFace.setPosition(newPos)
                        binding.moodGlow.setTint(MoodSpectrum.colorAt(colors, newPos))

                        // Character lean tilt
                        val lean = (-posDelta * TILT_PER_STEP).coerceIn(-MAX_TILT, MAX_TILT)
                        binding.moodFace.rotation = lean

                        // Subtle haptic tick when crossing mood thresholds
                        val currentStep = newPos.roundToInt()
                        if (currentStep != lastTickedStep && currentStep in moods.indices) {
                            lastTickedStep = currentStep
                            tickHaptic()
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val duration = System.currentTimeMillis() - startTime
                    val totalDx = event.x - startX
                    val totalDy = event.y - startY

                    if (!isDraggingHorizontally && abs(totalDx) < touchSlop && abs(totalDy) < touchSlop && duration < 300) {
                        // Quick tap toggles current mood
                        toggleCurrentMood()
                    } else if (isDraggingHorizontally) {
                        velocityTracker?.computeCurrentVelocity(1000)
                        val xVelocity = velocityTracker?.xVelocity ?: 0f

                        val targetIndex = when {
                            abs(xVelocity) > minFlingVelocity -> {
                                if (xVelocity < 0) {
                                    (currentIndex + 1).coerceAtMost(moods.size - 1)
                                } else {
                                    (currentIndex - 1).coerceAtLeast(0)
                                }
                            }
                            else -> currentPosition.roundToInt().coerceIn(0, moods.size - 1)
                        }

                        animateToPosition(targetIndex)
                    }

                    velocityTracker?.recycle()
                    velocityTracker = null
                    isDraggingHorizontally = false
                    true
                }
                else -> false
            }
        }
    }

    private fun navigateToMood(targetIndex: Int) {
        if (targetIndex !in moods.indices) return
        animateToPosition(targetIndex)
    }

    private fun animateToPosition(targetIndex: Int) {
        positionAnimator?.cancel()
        val startPos = currentPosition
        val endPos = targetIndex.toFloat()

        positionAnimator = ValueAnimator.ofFloat(startPos, endPos).apply {
            duration = POSITION_ANIM_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                val pos = anim.animatedValue as Float
                currentPosition = pos
                binding.moodFace.setPosition(pos)
                binding.moodGlow.setTint(MoodSpectrum.colorAt(colors, pos))
            }
            start()
        }

        binding.moodFace.animate().rotation(0f).setDuration(TILT_RESET_MS).start()

        val movingForward = targetIndex > currentIndex
        currentIndex = targetIndex
        watchFlow.moodSliderIndex = targetIndex

        showMoodTexts(targetIndex, animate = true, movingForward = movingForward)
        showToggle()
        updateArrowStates()
        confirmHaptic()
    }

    private fun updateArrowStates() {
        binding.moodPrev.isEnabled = currentIndex > 0
        binding.moodPrev.alpha = if (currentIndex > 0) 0.85f else 0.25f

        binding.moodNext.isEnabled = currentIndex < moods.size - 1
        binding.moodNext.alpha = if (currentIndex < moods.size - 1) 0.85f else 0.25f
    }

    // ---------- Showing Mood Details ----------

    private fun showMoodTexts(index: Int, animate: Boolean, movingForward: Boolean) {
        if (index == shownIndex && animate) return
        shownIndex = index
        val mood = moods[index]
        swapText(binding.moodName, getString(mood.shortLabel), animate, movingForward)
        swapText(binding.moodLine, getString(mood.line), animate, movingForward)
        showToggle()
    }

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

    // ---------- Choosing & Mix ----------

    private fun toggleCurrentMood() {
        val mood = moods[currentIndex]
        val result = watchFlow.onMoodTapped(mood)
        when (result) {
            ToggleResult.SELECTED -> {
                binding.moodFace.pop()
                confirmHaptic()
            }
            ToggleResult.DESELECTED -> binding.moodFace.pop()
            ToggleResult.LIMIT_REACHED -> {
                binding.moodFace.shake()
                binding.moodFace.announceForAccessibility(getString(R.string.mood_limit_reached))
            }
        }
    }

    private fun tickHaptic() {
        val feedback = if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CLOCK_TICK else HapticFeedbackConstants.CONTEXT_CLICK
        binding.moodFace.performHapticFeedback(feedback)
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

    private fun showToggle() {
        val mood = moods[currentIndex]
        val chosen = shownSelection.isSelected(mood)
        val color = MoodSpectrum.colorAt(colors, currentIndex.toFloat())
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
        const val POSITION_ANIM_MS = 240L
        const val TILT_PER_STEP = 18f
        const val MAX_TILT = 8f
        const val TILT_RESET_MS = 250L
        const val GLOW_STRENGTH = 0.45f
        const val GLOW_FOCUS_Y = 0.38f
        const val CHOSEN_FILL_ALPHA = 64
    }
}
