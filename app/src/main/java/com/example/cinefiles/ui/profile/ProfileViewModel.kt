package com.example.cinefiles.ui.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.cinefiles.CineFilesApp
import com.example.cinefiles.data.AchievementState
import com.example.cinefiles.logic.Achievement
import com.example.cinefiles.logic.AchievementId
import com.example.cinefiles.logic.Achievements
import com.example.cinefiles.logic.Taste
import com.example.cinefiles.logic.tasteOf
import com.example.cinefiles.util.Event
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** One medal in the grid. [celebrate] = it was earned since the Profile was last looked at: play the unlock animation. */
data class AchievementItem(
    val achievement: Achievement,
    val progress: Int,
    val unlocked: Boolean,
    val celebrate: Boolean
)

/** The panel above the grid: the badge you tapped, or else the one that is closest to being earned. */
data class FocusState(
    val item: AchievementItem?,   // null = every badge is earned and none is selected
    val selectedByUser: Boolean
)

/** Everything the Profile screen draws. */
data class ProfileState(
    val swiped: Int,
    val liked: Int,
    val passed: Int,
    val items: List<AchievementItem>,
    val unlockedCount: Int,
    val focus: FocusState
)

/**
 * Turns the achievement numbers (from the tracker) into what the Profile screen shows, and remembers which
 * badge the user tapped. The rules themselves are in logic/Achievements.kt (the badges) and logic/Profile.kt
 * (the name and "your taste"). It also renames the user and clears all their data.
 */
class ProfileViewModel(app: Application) : AndroidViewModel(app) {

    private val tracker = (app as CineFilesApp).achievements
    private val settings = (app as CineFilesApp).userSettings
    private val repository = (app as CineFilesApp).movieRepository
    private val selected = MutableLiveData<AchievementId?>(null)

    /** The user's display name ("Movie Lover" until they edit it). */
    val name: LiveData<String> = settings.name

    /** The genre you like most and how many movies you like, live from the database (so it works offline). */
    val taste: LiveData<Taste> = repository.observeLiked().map { tasteOf(it) }.asLiveData()

    private val _dataCleared = MutableLiveData<Event<Unit>>()

    /** Everything was cleared: the screen goes back to Home (once: see [Event]). */
    val dataCleared: LiveData<Event<Unit>> = _dataCleared

    val state: LiveData<ProfileState> = MediatorLiveData<ProfileState>().apply {
        addSource(tracker.state) { value = build(it, selected.value) }
        addSource(selected) { id -> tracker.state.value?.let { value = build(it, id) } }
    }

    /** A medal was tapped. Tapping the selected one again lets go of it (the panel goes back to "Next up"). */
    fun select(id: AchievementId) {
        selected.value = if (selected.value == id) null else id
    }

    /** Saves a new name. False (and nothing changes) when the text is blank. */
    fun rename(typed: String): Boolean = settings.rename(typed)

    /**
     * CLEAR MY DATA: deletes every saved movie and state (Room), the achievements and the name (both kept in
     * SharedPreferences). Afterwards the app is as it was just after installing.
     */
    fun clearMyData() {
        viewModelScope.launch {
            repository.clearAllData()
            tracker.reset()
            settings.clear()
            _dataCleared.value = Event(Unit)
        }
    }

    /** The unlock animation has been shown for these badges, so it is not played again. */
    fun markCelebrated(ids: Set<AchievementId>) = tracker.markCelebrated(ids)

    private fun build(data: AchievementState, selectedId: AchievementId?): ProfileState {
        val items = Achievements.all.map { achievement ->
            val unlocked = achievement.id in data.unlocked
            AchievementItem(
                achievement = achievement,
                progress = if (unlocked) achievement.goal else Achievements.progress(achievement, data.stats),
                unlocked = unlocked,
                celebrate = unlocked && achievement.id !in data.celebrated
            )
        }

        val chosen = items.firstOrNull { it.achievement.id == selectedId }
        val nextUp = items.filter { !it.unlocked }
            .maxByOrNull { it.progress.toFloat() / it.achievement.goal } // the one closest to its goal

        return ProfileState(
            swiped = data.stats.swiped,
            liked = data.stats.liked,
            passed = data.stats.passed,
            items = items,
            unlockedCount = items.count { it.unlocked },
            focus = FocusState(chosen ?: nextUp, selectedByUser = chosen != null)
        )
    }
}
