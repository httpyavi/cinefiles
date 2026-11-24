package com.example.cinefiles.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoodSelectionTest {

    // Short names for the moods used in the tests
    private val happy = Moods.all.first { it.id == "happy" }
    private val excited = Moods.all.first { it.id == "excited" }
    private val romantic = Moods.all.first { it.id == "romantic" }
    private val angry = Moods.all.first { it.id == "angry" }
    private val surprise = Moods.all.first { it.isSurprise }

    /** Taps the moods one after another, like a user would. */
    private fun tapAll(vararg moods: Mood): MoodSelection {
        var selection = MoodSelection()
        for (mood in moods) selection = selection.toggle(mood).selection
        return selection
    }

    @Test
    fun startsEmptyAndCannotContinue() {
        val selection = MoodSelection()
        assertEquals(0, selection.count)
        assertFalse(selection.canContinue)
    }

    @Test
    fun oneMoodIsEnoughToContinue() {
        val selection = tapAll(happy)
        assertEquals(1, selection.count)
        assertTrue(selection.canContinue)
    }

    @Test
    fun tappingAMoodSelectsIt() {
        val outcome = MoodSelection().toggle(happy)
        assertEquals(ToggleResult.SELECTED, outcome.result)
        assertTrue(outcome.selection.isSelected(happy))
    }

    @Test
    fun tappingASelectedMoodTurnsItOff() {
        val outcome = tapAll(happy, excited).toggle(happy)
        assertEquals(ToggleResult.DESELECTED, outcome.result)
        assertFalse(outcome.selection.isSelected(happy))
        assertTrue(outcome.selection.isSelected(excited))
    }

    @Test
    fun threeMoodsAreAllowed() {
        val selection = tapAll(happy, excited, romantic)
        assertEquals(3, selection.count)
    }

    @Test
    fun aFourthMoodIsRefusedAndNothingChanges() {
        val three = tapAll(happy, excited, romantic)
        val outcome = three.toggle(angry)
        assertEquals(ToggleResult.LIMIT_REACHED, outcome.result)
        assertEquals(three, outcome.selection)
        assertFalse(outcome.selection.isSelected(angry))
    }

    @Test
    fun afterThreeYouCanStillTurnOneOffAndPickAnother() {
        val three = tapAll(happy, excited, romantic)
        val afterOff = three.toggle(happy).selection
        val afterNew = afterOff.toggle(angry)
        assertEquals(ToggleResult.SELECTED, afterNew.result)
        assertEquals(3, afterNew.selection.count)
        assertFalse(afterNew.selection.isSelected(happy))
    }

    @Test
    fun surpriseClearsTheOtherMoods() {
        val outcome = tapAll(happy, excited).toggle(surprise)
        assertEquals(ToggleResult.SELECTED, outcome.result)
        assertEquals(listOf(surprise.id), outcome.selection.ids)
    }

    @Test
    fun surpriseWorksEvenWhenThreeMoodsAreSelected() {
        val outcome = tapAll(happy, excited, romantic).toggle(surprise)
        assertEquals(ToggleResult.SELECTED, outcome.result)
        assertEquals(listOf(surprise.id), outcome.selection.ids)
    }

    @Test
    fun pickingAnotherMoodClearsSurprise() {
        val outcome = tapAll(surprise).toggle(happy)
        assertEquals(ToggleResult.SELECTED, outcome.result)
        assertEquals(listOf(happy.id), outcome.selection.ids)
    }

    @Test
    fun tappingSurpriseAgainTurnsItOff() {
        val outcome = tapAll(surprise).toggle(surprise)
        assertEquals(ToggleResult.DESELECTED, outcome.result)
        assertFalse(outcome.selection.canContinue)
    }

    @Test
    fun toggleNeverChangesTheOldSelection() {
        val before = tapAll(happy)
        before.toggle(excited)
        assertEquals(listOf(happy.id), before.ids)
    }
}
