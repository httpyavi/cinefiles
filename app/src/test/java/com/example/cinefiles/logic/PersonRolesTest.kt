package com.example.cinefiles.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Which role tab a crew job belongs to, and in which order the tabs of the Person screen come. */
class PersonRolesTest {

    @Test
    fun theJobsOfTheBriefLandOnTheirTabs() {
        assertEquals(PersonRole.DIRECTOR, PersonRoles.roleOfJob("Director"))
        for (job in listOf("Writer", "Screenplay", "Story")) assertEquals(PersonRole.WRITER, PersonRoles.roleOfJob(job))
        for (job in listOf("Producer", "Executive Producer")) assertEquals(PersonRole.PRODUCER, PersonRoles.roleOfJob(job))
        assertEquals(PersonRole.CINEMATOGRAPHER, PersonRoles.roleOfJob("Director of Photography"))
        assertEquals(PersonRole.COMPOSER, PersonRoles.roleOfJob("Original Music Composer"))
        assertEquals(PersonRole.EDITOR, PersonRoles.roleOfJob("Editor"))
    }

    @Test
    fun theCaseAndSpacesOfTheJobDoNotMatter() {
        assertEquals(PersonRole.CINEMATOGRAPHER, PersonRoles.roleOfJob("  director of photography "))
    }

    @Test
    fun everyOtherJobIsPlainCrew() {
        for (job in listOf("Gaffer", "Best Boy", "Assistant Director", "Music Supervisor", "", null)) {
            assertEquals(PersonRole.CREW, PersonRoles.roleOfJob(job))
        }
    }

    @Test
    fun theDepartmentTmdbKnowsThePersonForIsTheirPrimaryRole() {
        assertEquals(PersonRole.ACTOR, PersonRoles.primaryRoleOf("Acting"))
        assertEquals(PersonRole.DIRECTOR, PersonRoles.primaryRoleOf("Directing"))
        assertEquals(PersonRole.WRITER, PersonRoles.primaryRoleOf("Writing"))
        assertEquals(PersonRole.PRODUCER, PersonRoles.primaryRoleOf("Production"))
        assertEquals(PersonRole.CINEMATOGRAPHER, PersonRoles.primaryRoleOf("Camera"))
        assertEquals(PersonRole.COMPOSER, PersonRoles.primaryRoleOf("Sound"))
        assertEquals(PersonRole.EDITOR, PersonRoles.primaryRoleOf("Editing"))
        assertNull(PersonRoles.primaryRoleOf("Lighting"))
        assertNull(PersonRoles.primaryRoleOf(null))
    }

    @Test
    fun onlyRolesWithMoviesGetATab() {
        val tabs = PersonRoles.order(mapOf(PersonRole.ACTOR to 24, PersonRole.DIRECTOR to 0, PersonRole.WRITER to 3), "Acting")
        assertEquals(listOf(PersonRole.ACTOR, PersonRole.WRITER), tabs)
    }

    @Test
    fun thePrimaryRoleComesFirstEvenWhenAnotherRoleHasMoreMovies() {
        val counts = mapOf(PersonRole.DIRECTOR to 8, PersonRole.PRODUCER to 15, PersonRole.WRITER to 10)
        assertEquals(
            listOf(PersonRole.DIRECTOR, PersonRole.PRODUCER, PersonRole.WRITER),
            PersonRoles.order(counts, "Directing")
        )
    }

    @Test
    fun withoutAPrimaryRoleTheMostPopulatedComesFirst() {
        val counts = mapOf(PersonRole.ACTOR to 2, PersonRole.PRODUCER to 15, PersonRole.WRITER to 10)
        assertEquals(listOf(PersonRole.PRODUCER, PersonRole.WRITER, PersonRole.ACTOR), PersonRoles.order(counts, null))
        // A primary role the person has no movies in does not make a tab
        assertEquals(listOf(PersonRole.PRODUCER, PersonRole.WRITER, PersonRole.ACTOR), PersonRoles.order(counts, "Camera"))
    }

    @Test
    fun equalCountsFollowTheFixedOrderOfTheRoles() {
        val counts = mapOf(PersonRole.PRODUCER to 4, PersonRole.ACTOR to 4, PersonRole.WRITER to 4)
        assertEquals(listOf(PersonRole.ACTOR, PersonRole.WRITER, PersonRole.PRODUCER), PersonRoles.order(counts, null))
    }

    @Test
    fun theCrewTabIsAlwaysLast() {
        val counts = mapOf(PersonRole.CREW to 40, PersonRole.EDITOR to 1, PersonRole.ACTOR to 2)
        assertEquals(listOf(PersonRole.ACTOR, PersonRole.EDITOR, PersonRole.CREW), PersonRoles.order(counts, null))
    }
}
