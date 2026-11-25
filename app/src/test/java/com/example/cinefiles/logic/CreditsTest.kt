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
}
