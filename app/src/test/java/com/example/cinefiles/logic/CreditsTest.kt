package com.example.cinefiles.logic

import com.example.cinefiles.data.CrewMember
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which crew jobs the Details screen shows, and how it groups them. */
class CreditsTest {

    private fun crew(id: Int, job: String, name: String = "Person $id") = CrewMember(id, name, job, null)

    @Test
    fun theFiveKindsOfCrewAreRecognisedWhateverTheCase() {
        assertEquals(CrewRole.DIRECTOR, Credits.roleOf("Director"))
        assertEquals(CrewRole.WRITER, Credits.roleOf("Screenplay"))
        assertEquals(CrewRole.WRITER, Credits.roleOf(" writer "))
        assertEquals(CrewRole.PRODUCER, Credits.roleOf("Executive Producer"))
        assertEquals(CrewRole.CINEMATOGRAPHY, Credits.roleOf("Director of Photography"))
        assertEquals(CrewRole.COMPOSER, Credits.roleOf("Original Music Composer"))
    }

    @Test
    fun otherJobsAreNotShown() {
        assertNull(Credits.roleOf("Best Boy"))
        assertNull(Credits.roleOf("Assistant Director")) // not "Director"
        assertNull(Credits.roleOf("Music Supervisor"))
        assertNull(Credits.roleOf(""))
        assertNull(Credits.roleOf(null))
    }

    @Test
    fun onlyKindsWithPeopleAreInTheAnswerInTheOrderOfCrewRole() {
        val groups = Credits.group(listOf(crew(1, "Original Music Composer"), crew(2, "Screenplay"), crew(3, "Gaffer")))
        assertEquals(listOf(CrewRole.WRITER, CrewRole.COMPOSER), groups.keys.toList())
    }

    @Test
    fun aPersonIsListedOnceInAGroupEvenWithTwoJobsInIt() {
        val groups = Credits.group(listOf(crew(1, "Screenplay"), crew(1, "Story"), crew(2, "Writer")))
        assertEquals(listOf(1, 2), groups.getValue(CrewRole.WRITER).map { it.id })
    }

    @Test
    fun aPersonCanBeInTwoGroups() {
        val groups = Credits.group(listOf(crew(1, "Director"), crew(1, "Producer")))
        assertEquals(listOf(1), groups.getValue(CrewRole.DIRECTOR).map { it.id })
        assertEquals(listOf(1), groups.getValue(CrewRole.PRODUCER).map { it.id })
    }

    @Test
    fun theMoreImportantJobComesFirstThenTmdbsOrder() {
        val groups = Credits.group(listOf(crew(1, "Executive Producer"), crew(2, "Producer"), crew(3, "Executive Producer"), crew(4, "Producer")))
        assertEquals(listOf(2, 4, 1, 3), groups.getValue(CrewRole.PRODUCER).map { it.id })
    }

    @Test
    fun aGroupIsCutAfterSixPeopleButDirectorsNeverAre() {
        val producers = (1..10).map { crew(it, "Producer") }
        val directors = (11..18).map { crew(it, "Director") }
        val groups = Credits.group(producers + directors)

        assertEquals(Credits.MAX_PER_ROLE, groups.getValue(CrewRole.PRODUCER).size)
        assertEquals(8, groups.getValue(CrewRole.DIRECTOR).size)
    }

    @Test
    fun noCrewGivesNoGroups() {
        assertTrue(Credits.group(emptyList()).isEmpty())
    }

    // ---------- the short Crew section and the full crew ----------

    @Test
    fun theFullCrewHasMoreNamesPerRoleThanTheShortGroups() {
        val producers = (1..30).map { crew(it, "Producer") }
        assertEquals(Credits.MAX_PER_ROLE, Credits.group(producers).getValue(CrewRole.PRODUCER).size)
        assertEquals(Credits.MAX_FULL_PER_ROLE, Credits.group(producers, Credits.MAX_FULL_PER_ROLE).getValue(CrewRole.PRODUCER).size)
    }

    @Test
    fun editorsAndDesignersAreRecognisedForTheFullCrew() {
        assertEquals(CrewRole.EDITOR, Credits.roleOf("Editor"))
        assertEquals(CrewRole.PRODUCTION_DESIGN, Credits.roleOf("Production Design"))
        assertEquals(CrewRole.COSTUME_DESIGN, Credits.roleOf("Costume Design"))
    }

    @Test
    fun theShortSectionShowsAtMostFourRowsWithTwoNamesEach() {
        val full = Credits.group(
            listOf(crew(1, "Director"), crew(2, "Writer"), crew(3, "Writer"), crew(4, "Writer"), crew(5, "Producer"), crew(6, "Director of Photography"), crew(7, "Original Music Composer")),
            Credits.MAX_FULL_PER_ROLE
        )

        val preview = Credits.preview(full)

        assertEquals(listOf(CrewRole.DIRECTOR, CrewRole.WRITER, CrewRole.PRODUCER, CrewRole.CINEMATOGRAPHY), preview.rows.map { it.role })
        val writers = preview.rows.first { it.role == CrewRole.WRITER }
        assertEquals(listOf(2, 3), writers.people.map { it.id })
        assertEquals(1, writers.hiddenCount) // "+1 more"
        assertTrue(preview.hasMore)             // the fifth role (music) and a hidden writer wait in "See all"
    }

    @Test
    fun aSmallCrewFitsAndNeedsNoSeeAllButton() {
        val preview = Credits.preview(Credits.group(listOf(crew(1, "Director"), crew(2, "Writer"), crew(3, "Original Music Composer"))))
        assertEquals(3, preview.rows.size)
        assertEquals(false, preview.hasMore)
    }

    @Test
    fun extraKindsOfCrewMakeTheSeeAllButtonUseful() {
        val preview = Credits.preview(Credits.group(listOf(crew(1, "Director"), crew(2, "Editor"))))
        assertEquals(listOf(CrewRole.DIRECTOR), preview.rows.map { it.role }) // editing is not a key role
        assertTrue(preview.hasMore)
    }

    @Test
    fun noCrewGivesAnEmptyPreview() {
        val preview = Credits.preview(emptyMap())
        assertTrue(preview.rows.isEmpty())
        assertEquals(false, preview.hasMore)
    }
}
