package com.example.cinefiles.ui.mood

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.cinefiles.logic.Language
import com.example.cinefiles.logic.Length
import com.example.cinefiles.logic.MinRating
import com.example.cinefiles.logic.Mood
import com.example.cinefiles.logic.MoodSelection
import com.example.cinefiles.logic.Preferences
import com.example.cinefiles.logic.ReleasePeriod
import com.example.cinefiles.logic.ToggleResult

/**
 * Remembers what the user chose while they walk through "Watch something":
 * Mood -> Preferences -> Swipe -> Picks.
 *
 * All of those screens get THE SAME instance with `by activityViewModels()`, so a choice made on one
 * screen is still there on the next, and when you go back. It also survives screen rotation.
 * Home calls [reset] when a new flow starts.
 *
 * The rules for picking moods live in logic/MoodSelection.kt; this class only holds the result.
 * (The old grid of mood cards and its TEMP photo map are gone: the Mood screen is a slider now.)
 */
class WatchFlowViewModel(app: Application) : AndroidViewModel(app) {

    private var currentSelection = MoodSelection()
    private val _selection = MutableLiveData(currentSelection)

    /** The moods picked so far. */
    val selection: LiveData<MoodSelection> = _selection

    /**
     * Which place on the Mood screen's slider is showing (an index into Moods.spectrum). Kept here so that
     * going on to Preferences and coming back, or turning the phone, leaves the slider where it was.
     */
    var moodSliderIndex = DEFAULT_MOOD_INDEX

    /** The choices of the Preferences screen. Nothing needs to watch them yet, so this is a plain value. */
    var preferences = Preferences()
        private set

    /**
     * The user tapped a mood card. Applies the selection rules and says what happened,
     * so the screen can shake the card when the limit of three was reached.
     */
    fun onMoodTapped(mood: Mood): ToggleResult {
        val outcome = currentSelection.toggle(mood)
        currentSelection = outcome.selection
        _selection.value = currentSelection
        return outcome.result
    }

    fun setReleasePeriod(value: ReleasePeriod) {
        preferences = preferences.copy(releasePeriod = value)
    }

    fun setMinRating(value: MinRating) {
        preferences = preferences.copy(minRating = value)
    }

    fun setLength(value: Length) {
        preferences = preferences.copy(length = value)
    }

    fun setLanguage(value: Language) {
        preferences = preferences.copy(language = value)
    }

    /** Back to "Any" everywhere. Used by Skip. */
    fun resetPreferences() {
        preferences = Preferences()
    }

    /** Forget everything. Home calls this when a new flow starts. */
    fun reset() {
        currentSelection = MoodSelection()
        _selection.value = currentSelection
        preferences = Preferences()
        moodSliderIndex = DEFAULT_MOOD_INDEX
    }

    private companion object {
        const val DEFAULT_MOOD_INDEX = 1 // the slider starts on the second mood, Happy
    }
}
