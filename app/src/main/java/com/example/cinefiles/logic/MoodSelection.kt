package com.example.cinefiles.logic

/** The most moods the user may pick at the same time. */
const val MAX_MOODS = 3

/** What happened when the user tapped a mood card. */
enum class ToggleResult {
    SELECTED,      // the mood is now selected
    DESELECTED,    // the mood was selected and is now off
    LIMIT_REACHED  // already three moods: nothing changed (the card shakes)
}

/** The new selection after a tap, together with what happened. */
data class ToggleOutcome(val selection: MoodSelection, val result: ToggleResult)

/**
 * Which moods are selected, in the order they were tapped.
 * It never changes: [toggle] returns a NEW selection. That makes the rules easy to test.
 *
 * The rules:
 *  1. At most three moods ([MAX_MOODS]). A fourth tap changes nothing.
 *  2. Tapping a selected mood turns it off.
 *  3. "Surprise Me" stands alone: selecting it clears every other mood,
 *     and selecting any other mood clears "Surprise Me".
 */
data class MoodSelection(val ids: List<String> = emptyList()) {

    val count: Int get() = ids.size

    /** The Continue button needs at least one mood. */
    val canContinue: Boolean get() = ids.isNotEmpty()

    fun isSelected(mood: Mood): Boolean = mood.id in ids

    fun toggle(mood: Mood): ToggleOutcome {
        // Rule 2: tapping a selected mood turns it off
        if (isSelected(mood)) {
            return ToggleOutcome(MoodSelection(ids - mood.id), ToggleResult.DESELECTED)
        }

        // Rule 3: Surprise Me replaces everything
        if (mood.isSurprise) {
            return ToggleOutcome(MoodSelection(listOf(mood.id)), ToggleResult.SELECTED)
        }

        // Rule 3: any other mood removes Surprise Me first
        val withoutSurprise = ids.filter { it != Moods.SURPRISE_ID }

        // Rule 1: no more than three
        if (withoutSurprise.size >= MAX_MOODS) {
            return ToggleOutcome(this, ToggleResult.LIMIT_REACHED)
        }

        return ToggleOutcome(MoodSelection(withoutSurprise + mood.id), ToggleResult.SELECTED)
    }
}
