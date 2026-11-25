package com.example.cinefiles.ui.mood

import android.content.res.ColorStateList
import android.database.DataSetObserver
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.example.cinefiles.R
import com.example.cinefiles.databinding.FragmentPreferencesBinding
import com.example.cinefiles.logic.LanguageOption
import com.example.cinefiles.logic.Languages
import com.example.cinefiles.logic.Length
import com.example.cinefiles.logic.MinRating
import com.example.cinefiles.logic.ReleasePeriod
import com.example.cinefiles.util.applySystemBarsPadding
import com.example.cinefiles.util.openScreen
import com.google.android.material.button.MaterialButton

/**
 * Step 2 of 3: "Make it yours".
 * Redesigned as a cinematic personalization experience with customized visual treatments
 * for movie eras, ratings, runtimes, and languages.
 */
class PreferencesFragment : Fragment() {

    private var _binding: FragmentPreferencesBinding? = null
    private val binding get() = _binding!!

    private val watchFlow: WatchFlowViewModel by activityViewModels()

    private var eraColor = 0
    private var ratingColor = 0
    private var runtimeColor = 0
    private var glassStrokeColor = 0
    private var glassFillColor = 0
    private var textColorPrimary = 0
    private var textColorSecondary = 0

    private val periodButtons = HashMap<MaterialButton, ReleasePeriod>()
    private val ratingButtons = HashMap<MaterialButton, MinRating>()
    private val lengthButtons = HashMap<MaterialButton, Length>()

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

        eraColor = ContextCompat.getColor(requireContext(), R.color.pref_category_era)
        ratingColor = ContextCompat.getColor(requireContext(), R.color.pref_category_rating)
        runtimeColor = ContextCompat.getColor(requireContext(), R.color.pref_category_runtime)
        glassStrokeColor = ContextCompat.getColor(requireContext(), R.color.glass_stroke)
        glassFillColor = ContextCompat.getColor(requireContext(), R.color.glass_fill)
        textColorPrimary = ContextCompat.getColor(requireContext(), R.color.text_primary)
        textColorSecondary = ContextCompat.getColor(requireContext(), R.color.text_secondary)

        setUpButtons()
        setUpOptionGroups()
        renderSelections()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        periodButtons.clear()
        ratingButtons.clear()
        lengthButtons.clear()
        _binding = null
    }

    private fun setUpButtons() {
        binding.prefsBack.setOnClickListener { findNavController().navigateUp() }

        binding.prefsSkip.setOnClickListener {
            watchFlow.resetPreferences()
            startSwiping()
        }

        binding.startSwipingButton.setOnClickListener { startSwiping() }
    }

    private fun startSwiping() {
        findNavController().openScreen(R.id.swipeFragment, slide = true)
    }

    private fun setUpOptionGroups() {
        // A. Release Period
        periodButtons[binding.periodAny] = ReleasePeriod.ANY
        periodButtons[binding.periodBefore2000] = ReleasePeriod.BEFORE_2000
        periodButtons[binding.period2000To2014] = ReleasePeriod.FROM_2000_TO_2014
        periodButtons[binding.periodFrom2015] = ReleasePeriod.FROM_2015

        for ((button, period) in periodButtons) {
            button.setOnClickListener {
                watchFlow.setReleasePeriod(period)
                renderPeriodGroup()
                vibrateTick(button)
            }
        }

        // B. Minimum Rating
        ratingButtons[binding.ratingAny] = MinRating.ANY
        ratingButtons[binding.rating6] = MinRating.SIX_PLUS
        ratingButtons[binding.rating7] = MinRating.SEVEN_PLUS
        ratingButtons[binding.rating8] = MinRating.EIGHT_PLUS

        for ((button, rating) in ratingButtons) {
            button.setOnClickListener {
                watchFlow.setMinRating(rating)
                renderRatingGroup()
                vibrateTick(button)
            }
        }

        // C. Length
        lengthButtons[binding.lengthAny] = Length.ANY
        lengthButtons[binding.lengthUnder90] = Length.UNDER_90_MIN
        lengthButtons[binding.lengthUnder2h] = Length.UNDER_2_HOURS
        lengthButtons[binding.lengthOver2h] = Length.OVER_2_HOURS

        for ((button, length) in lengthButtons) {
            button.setOnClickListener {
                watchFlow.setLength(length)
                renderLengthGroup()
                vibrateTick(button)
            }
        }

        // D. Language: a search field with live suggestions (any language, not four fixed ones)
        setUpLanguageSearch()
    }

    private fun setUpLanguageSearch() {
        val input = binding.languageInput
        val adapter = LanguageAdapter(requireContext())
        input.setAdapter(adapter)

        // The list is as tall as its suggestions (one match is one row), and scrolls from about five rows on
        adapter.registerDataSetObserver(object : DataSetObserver() {
            override fun onChanged() {
                input.dropDownHeight = if (adapter.count > DROPDOWN_ROWS) {
                    resources.getDimensionPixelSize(R.dimen.pref_dropdown_height)
                } else {
                    ViewGroup.LayoutParams.WRAP_CONTENT
                }
            }
        })

        // Tapping a suggestion picks it
        input.setOnItemClickListener { _, _, position, _ -> adapter.getItem(position)?.let { chooseLanguage(it.code) } }

        // Tapping the field (or arriving in it) shows the suggestions at once; leaving it with half-typed words puts the chosen language back
        input.setOnClickListener { showAllLanguages(adapter) }
        input.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) showAllLanguages(adapter) else renderLanguageGroup()
        }

        // The keyboard's Done: take what was typed if it is exactly a language ("korean", "Tagalog"), then close the keyboard
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                Languages.exact(input.text.toString())?.let { chooseLanguage(it.code) } ?: renderLanguageGroup()
                closeKeyboard()
                true
            } else {
                false
            }
        }

        binding.languageClear.setOnClickListener { chooseLanguage(null) }
    }

    private fun showAllLanguages(adapter: LanguageAdapter) {
        if (binding.languageInput.text.isNullOrBlank() || Languages.exact(binding.languageInput.text.toString()) != null) adapter.showAll()
        binding.languageInput.showDropDown()
    }

    /** Saves the choice (null = any language) and closes the suggestions and the keyboard. */
    private fun chooseLanguage(code: String?) {
        watchFlow.setLanguage(code)
        renderLanguageGroup()
        binding.languageInput.dismissDropDown()
        closeKeyboard()
        vibrateTick(binding.languageInput)
    }

    private fun closeKeyboard() {
        binding.languageInput.clearFocus()
        requireContext().getSystemService<InputMethodManager>()?.hideSoftInputFromWindow(binding.languageInput.windowToken, 0)
    }

    private fun renderSelections() {
        renderPeriodGroup()
        renderRatingGroup()
        renderLengthGroup()
        renderLanguageGroup()
    }

    private fun renderPeriodGroup() {
        val selected = watchFlow.preferences.releasePeriod
        for ((button, period) in periodButtons) {
            applyCardState(button, period == selected, eraColor)
        }
    }

    private fun renderRatingGroup() {
        val selected = watchFlow.preferences.minRating
        for ((button, rating) in ratingButtons) {
            applyCardState(button, rating == selected, ratingColor)
        }
    }

    private fun renderLengthGroup() {
        val selected = watchFlow.preferences.length
        for ((button, length) in lengthButtons) {
            applyCardState(button, length == selected, runtimeColor)
        }
    }

    /** The field shows the chosen language (empty = any), with a red edge and a clear button when there is one, and a line that says it. */
    private fun renderLanguageGroup() {
        val chosen: LanguageOption? = Languages.byCode(watchFlow.preferences.language)
        binding.languageInput.setText(chosen?.name.orEmpty(), false) // false: do not start the suggestions
        binding.languageInput.isSelected = chosen != null
        binding.languageClear.isVisible = chosen != null
        binding.languageStatus.text = chosen?.let { getString(R.string.pref_language_status_picked, it.name) }
            ?: getString(R.string.pref_language_status_any)
    }

    private fun applyCardState(button: MaterialButton, isSelected: Boolean, activeColor: Int) {
        val strokeWidth = if (isSelected) dpToPx(2f) else dpToPx(1f)

        button.strokeWidth = strokeWidth
        if (isSelected) {
            button.strokeColor = ColorStateList.valueOf(activeColor)
            button.backgroundTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(activeColor, ACTIVE_FILL_ALPHA))
            button.setTextColor(textColorPrimary)
        } else {
            button.strokeColor = ColorStateList.valueOf(glassStrokeColor)
            button.backgroundTintList = ColorStateList.valueOf(glassFillColor)
            button.setTextColor(textColorSecondary)
        }
    }

    private fun dpToPx(dp: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics).toInt()

    private fun vibrateTick(view: View) {
        val feedback = if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CLOCK_TICK else HapticFeedbackConstants.CONTEXT_CLICK
        view.performHapticFeedback(feedback)
    }

    private companion object {
        const val GLOW_STRENGTH = 0.45f
        const val ACTIVE_FILL_ALPHA = 52 // ~20% red wash for active option
        const val DROPDOWN_ROWS = 5       // the language suggestions scroll when there are more rows than this
    }
}
