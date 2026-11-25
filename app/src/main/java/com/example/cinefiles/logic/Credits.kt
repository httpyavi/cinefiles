package com.example.cinefiles.logic

import androidx.annotation.StringRes
import com.example.cinefiles.R
import com.example.cinefiles.data.CrewMember

/*
 * HOW THE CREW OF A MOVIE IS SHOWN ON THE DETAILS SCREEN.
 *
 * TMDB lists every crew member with a free-text job ("Director of Photography", "Original Music Composer"...), hundreds
 * of them. The Details screen only shows five kinds of people. This file says which jobs count as which kind, and
 * groups a crew list into those kinds: nothing else. It is plain Kotlin and knows nothing about TMDB or screens.
 */

/** The kinds of crew the Details screen shows. [label] is the plural heading of the group ("Writers"). */
enum class CrewRole(@StringRes val label: Int) {
    DIRECTOR(R.string.credits_directors),
    WRITER(R.string.credits_writers),
    PRODUCER(R.string.credits_producers),
    CINEMATOGRAPHY(R.string.credits_cinematography),
    COMPOSER(R.string.credits_composers)
}

object Credits {

    /** A group never shows more people than this (a big film has dozens of "Producer" credits). Directors are never cut. */
    const val MAX_PER_ROLE = 6

    // The jobs of each kind, the most important first: the order inside a group follows this order.
    private val JOBS: Map<CrewRole, List<String>> = mapOf(
        CrewRole.DIRECTOR to listOf("Director"),
        CrewRole.WRITER to listOf("Screenplay", "Writer", "Story", "Novel", "Author", "Original Story", "Teleplay"),
        CrewRole.PRODUCER to listOf("Producer", "Executive Producer"),
        CrewRole.CINEMATOGRAPHY to listOf("Director of Photography", "Cinematography"),
        CrewRole.COMPOSER to listOf("Original Music Composer", "Composer", "Music")
    ).mapValues { (_, jobs) -> jobs.map { it.lowercase() } }

    /** Which kind of crew a job belongs to, or null when the Details screen does not show that job. */
    fun roleOf(job: String?): CrewRole? {
        val wanted = job?.trim()?.lowercase() ?: return null
        return JOBS.entries.firstOrNull { wanted in it.value }?.key
    }

    /**
     * Groups [crew] by kind. Only kinds that have at least one person are in the answer, in the order of [CrewRole].
     * A person is listed once per group, even when they have two jobs in it (writer AND story), and the people of a
     * group follow the importance of their job, then TMDB's own order.
     */
    fun group(crew: List<CrewMember>): Map<CrewRole, List<CrewMember>> {
        val groups = LinkedHashMap<CrewRole, List<CrewMember>>()
        for (role in CrewRole.values()) {
            val jobs = JOBS.getValue(role)
            val people = crew
                .filter { roleOf(it.job) == role }
                .sortedBy { jobs.indexOf(it.job.trim().lowercase()) } // stable: equal jobs keep TMDB's order
                .distinctBy { it.id }
            if (people.isEmpty()) continue
            groups[role] = if (role == CrewRole.DIRECTOR) people else people.take(MAX_PER_ROLE)
        }
        return groups
    }
}
