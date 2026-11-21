package com.example.cinefiles.ui.components

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.content.withStyledAttributes
import com.example.cinefiles.R
import com.example.cinefiles.databinding.ViewStepIndicatorBinding

/**
 * "Step 1 of 3": three little bars (done ones light, the rest dark) and the words.
 * In a layout:  <StepIndicatorView app:currentStep="1" />   In code:  indicator.currentStep = 2
 */
class StepIndicatorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val binding = ViewStepIndicatorBinding.inflate(LayoutInflater.from(context), this)

    /** Which step the user is on: 1, 2 or 3. */
    var currentStep: Int = 1
        set(value) {
            field = value
            showStep()
        }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        context.withStyledAttributes(attrs, R.styleable.StepIndicatorView) {
            currentStep = getInt(R.styleable.StepIndicatorView_currentStep, 1)
        }
        showStep()
    }

    private fun showStep() {
        val bars = listOf(binding.segmentOne, binding.segmentTwo, binding.segmentThree)
        bars.forEachIndexed { index, bar ->
            val color = if (index < currentStep) R.color.text_primary else R.color.elevated
            bar.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, color))
        }
        binding.stepLabel.text = context.getString(R.string.flow_step_label, currentStep, TOTAL_STEPS)
    }

    private companion object {
        const val TOTAL_STEPS = 3
    }
}
