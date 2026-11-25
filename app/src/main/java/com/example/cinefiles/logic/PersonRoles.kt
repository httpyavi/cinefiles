package com.example.cinefiles.logic

import androidx.annotation.StringRes
import com.example.cinefiles.R

/*
 * THE ROLE TABS OF THE PERSON SCREEN (plain Kotlin, no screens: easy to test).
 *
 * TMDB lists a person's movies as "cast" (they acted) and "crew" (they worked behind the camera, each with a free-text
 * job such as "Director of Photography"). The Person screen shows one tab per kind of work the person really has:
 * Actor, Director, Writer, Producer, Cinematographer, Composer, Editor, and Crew for every other job.
 * This file says which job is which role (it reuses the job lists of logic/Credits.kt, so the Details screen and the
 * Person screen always agree) and in which order the tabs come.
 */
enum class PersonRole(@StringRes val label: Int) {
    ACTOR(R.string.person_role_actor),
    DIRECTOR(R.string.person_role_director),
    WRITER(R.string.person_role_writer),
    PRODUCER(R.string.person_role_producer),
    CINEMATOGRAPHER(R.string.person_role_cinematographer),
    COMPOSER(R.string.person_role_composer),
    EDITOR(R.string.person_role_editor),
    CREW(R.string.person_role_crew)
}

object PersonRoles {

    /** Which tab a crew job belongs to. A job we do not single out ("Gaffer", "Casting"...) is plain [PersonRole.CREW]. */
    fun roleOfJob(job: String?): PersonRole = when (Credits.roleOf(job)) {
        CrewRole.DIRECTOR -> PersonRole.DIRECTOR
        CrewRole.WRITER -> PersonRole.WRITER
        CrewRole.PRODUCER -> PersonRole.PRODUCER
        CrewRole.CINEMATOGRAPHY -> PersonRole.CINEMATOGRAPHER
        CrewRole.COMPOSER -> PersonRole.COMPOSER
        CrewRole.EDITOR -> PersonRole.EDITOR
        else -> PersonRole.CREW
    }

    /** The role of TMDB's "known for department" ("Acting", "Directing"...), or null when it is not one we have a tab for. */
    fun primaryRoleOf(knownFor: String?): PersonRole? = when (knownFor?.trim()?.lowercase()) {
        "acting" -> PersonRole.ACTOR
        "directing" -> PersonRole.DIRECTOR
        "writing" -> PersonRole.WRITER
        "production" -> PersonRole.PRODUCER
        "camera" -> PersonRole.CINEMATOGRAPHER
        "sound" -> PersonRole.COMPOSER // TMDB files composers under Sound
        "editing" -> PersonRole.EDITOR
        else -> null
    }

    /**
     * The tabs, in order. Only roles with at least one movie ([counts] > 0) get a tab. The person's PRIMARY role (what TMDB
     * says they are known for) comes first; the others follow with the most movies first, and between equal counts in
     * the order of [PersonRole] (Actor, Director, Writer...), so the order never changes by chance. "Crew" is always last.
     */
    fun order(counts: Map<PersonRole, Int>, knownFor: String?): List<PersonRole> {
        val present = counts.filterValues { it > 0 }.keys
        val primary = primaryRoleOf(knownFor)?.takeIf { it in present && it != PersonRole.CREW }
        val rest = present
            .filter { it != primary && it != PersonRole.CREW }
            .sortedWith(compareByDescending<PersonRole> { counts.getValue(it) }.thenBy { it.ordinal })
        return listOfNotNull(primary) + rest + listOfNotNull(PersonRole.CREW.takeIf { it in present })
    }
}
