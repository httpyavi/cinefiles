package com.example.cinefiles.ui.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import com.example.cinefiles.CineFilesApp
import com.example.cinefiles.data.AchievementState
import com.example.cinefiles.logic.Achievement
import com.example.cinefiles.logic.AchievementId
import com.example.cinefiles.logic.Achievements

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
 * badge the user tapped. The rules themselves are in logic/Achievements.kt.
 */
class ProfileViewModel(app: Application) : AndroidViewModel(app) {

    private val tracker = (app as CineFilesApp).achievements
    private val selected = MutableLiveData<AchievementId?>(null)

    val state: LiveData<ProfileState> = MediatorLiveData<ProfileState>().apply {
        addSource(tracker.state) { value = build(it, selected.value) }
        addSource(selected) { id -> tracker.state.value?.let { value = build(it, id) } }
    }

    /** A medal was tapped. Tapping the selected one again lets go of it (the panel goes back to "Next up"). */
    fun select(id: AchievementId) {
        selected.value = if (selected.value == id) null else id
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
