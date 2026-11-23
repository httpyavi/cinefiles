package com.example.cinefiles.data

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.cinefiles.logic.Achievement
import com.example.cinefiles.logic.AchievementId
import com.example.cinefiles.logic.AchievementStats
import com.example.cinefiles.logic.Achievements
import com.example.cinefiles.logic.SwipeDirection
import com.example.cinefiles.util.currentHour

/** What the Profile screen needs: the numbers, which badges are earned, and which have been "celebrated". */
data class AchievementState(
    val stats: AchievementStats = AchievementStats(),
    val unlocked: Set<AchievementId> = emptySet(),
    val celebrated: Set<AchievementId> = emptySet()
)

/**
 * Keeps the user's achievements: updates them after every swipe, remembers them between launches,
 * and says which badges were just earned.
 *
 * There is ONE of these for the whole app (CineFilesApp.achievements). The rules themselves are in
 * logic/Achievements.kt; this class only adds storing and the "what is new" bookkeeping.
 *
 * The numbers are stored in the phone's small key-value storage (SharedPreferences): they are a handful of
 * counters about the user, not movies, so they do not need the Room database.
 */
class AchievementTracker(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    private val _state = MutableLiveData(read())
    val state: LiveData<AchievementState> = _state

    /**
     * One more card was swiped. Returns the badges earned by this very swipe (usually none).
     * A badge that was earned once stays earned, even if Undo later takes the swipe back.
     */
    fun onSwipe(
        movie: Movie,
        direction: SwipeDirection,
        sessionSwipes: Int,
        emotionalSession: Boolean
    ): List<Achievement> {
        val current = _state.value ?: AchievementState()
        val stats = Achievements.afterSwipe(current.stats, movie, direction, currentHour(), sessionSwipes, emotionalSession)

        val earnedNow = Achievements.all.filter { it.id !in current.unlocked && Achievements.isUnlocked(it, stats) }
        publish(current.copy(stats = stats, unlocked = current.unlocked + earnedNow.map { it.id }))
        return earnedNow
    }

    /** The last swipe was taken back. */
    fun onUndo(movie: Movie, direction: SwipeDirection, emotionalSession: Boolean) {
        val current = _state.value ?: AchievementState()
        val stats = Achievements.afterUndo(current.stats, movie, direction, currentHour(), emotionalSession)
        publish(current.copy(stats = stats))
    }

    /** The Profile screen has played the "just earned" animation for these badges: do not play it again. */
    fun markCelebrated(ids: Set<AchievementId>) {
        val current = _state.value ?: return
        if (current.celebrated.containsAll(ids)) return
        publish(current.copy(celebrated = current.celebrated + ids))
    }

    /** Clear my data: every number and every badge is forgotten, as if the app was just installed. */
    fun reset() {
        prefs.edit().clear().apply()
        _state.value = AchievementState()
    }

    private fun publish(state: AchievementState) {
        _state.value = state
        write(state)
    }

    // ---------- Storing ----------

    private fun read(): AchievementState {
        val stats = AchievementStats(
            swiped = prefs.getInt("swiped", 0),
            liked = prefs.getInt("liked", 0),
            passed = prefs.getInt("passed", 0),
            likedGenres = parseCounts(prefs.getString("likedGenres", null)),
            seenGenres = parseList(prefs.getString("seenGenres", null)).toSet(),
            nightSwipes = prefs.getInt("nightSwipes", 0),
            bestSession = prefs.getInt("bestSession", 0),
            emotionalSwipes = prefs.getInt("emotionalSwipes", 0)
        )
        return AchievementState(
            stats = stats,
            unlocked = parseIds(prefs.getString("unlocked", null)),
            celebrated = parseIds(prefs.getString("celebrated", null))
        )
    }

    private fun write(state: AchievementState) {
        val stats = state.stats
        prefs.edit()
            .putInt("swiped", stats.swiped)
            .putInt("liked", stats.liked)
            .putInt("passed", stats.passed)
            .putString("likedGenres", stats.likedGenres.entries.joinToString(SEPARATOR) { "${it.key}$PAIR${it.value}" })
            .putString("seenGenres", stats.seenGenres.joinToString(SEPARATOR))
            .putInt("nightSwipes", stats.nightSwipes)
            .putInt("bestSession", stats.bestSession)
            .putInt("emotionalSwipes", stats.emotionalSwipes)
            .putString("unlocked", state.unlocked.joinToString(SEPARATOR) { it.name })
            .putString("celebrated", state.celebrated.joinToString(SEPARATOR) { it.name })
            .apply()
    }

    private fun parseList(text: String?): List<String> =
        text?.split(SEPARATOR)?.filter { it.isNotEmpty() } ?: emptyList()

    private fun parseCounts(text: String?): Map<String, Int> =
        parseList(text).mapNotNull { entry ->
            val parts = entry.split(PAIR)
            val count = parts.getOrNull(1)?.toIntOrNull()
            if (parts.size == 2 && count != null) parts[0] to count else null
        }.toMap()

    // A name that no longer exists (an old version) is simply ignored
    private fun parseIds(text: String?): Set<AchievementId> =
        parseList(text).mapNotNull { name -> AchievementId.values().firstOrNull { it.name == name } }.toSet()

    private companion object {
        const val FILE_NAME = "achievements"
        const val SEPARATOR = "|"
        const val PAIR = "="
    }
}
