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
import com.example.cinefiles.logic.Moods
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
 */
class WatchFlowViewModel(app: Application) : AndroidViewModel(app) {

    private var currentSelection = MoodSelection()
    private val _selection = MutableLiveData(currentSelection)

    /** The moods picked so far. */
    val selection: LiveData<MoodSelection> = _selection

    // TEMP: no mood photos yet (no TMDB data), so every card shows its tinted fallback.
    // Batch 5 fills this in: mood id -> photo web address. Replaced in Batch 5 (see TEMP.md).
    private val moodImageUrls: Map<String, String> = emptyMap()

    private val _moodCards = MutableLiveData(buildCards())

    /** The nine cards, ready to draw (mood + photo + selected or not). */
    val moodCards: LiveData<List<MoodCard>> = _moodCards

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
        _moodCards.value = buildCards()
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
        _moodCards.value = buildCards()
        preferences = Preferences()
    }

    private fun buildCards(): List<MoodCard> = Moods.all.map { mood ->
        MoodCard(mood, moodImageUrls[mood.id], currentSelection.isSelected(mood))
    }
}
