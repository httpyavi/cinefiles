package com.example.cinefiles.logic

import androidx.annotation.StringRes
import com.example.cinefiles.R
import com.example.cinefiles.data.CrewMember

/*
 * HOW THE CREW OF A MOVIE IS SHOWN ON THE DETAILS SCREEN.
 *
 * TMDB lists every crew member with a free-text job ("Director of Photography", "Original Music Composer"...), hundreds
 * of them. The Details screen only shows a few kinds of people: five KEY kinds in its Crew section (director, writers,
 * producers, cinematography, music) and a few more in the "See all crew" sheet. This file says which jobs count as which
 * kind, groups a crew list into those kinds and picks the short list the Crew section starts with.
 * It is plain Kotlin and knows nothing about TMDB or screens.
 */

/** The kinds of crew the Details screen shows. [label] is the heading of the group ("Writers"). */
enum class CrewRole(@StringRes val label: Int) {
    DIRECTOR(R.string.credits_directors),
    WRITER(R.string.credits_writers),
    PRODUCER(R.string.credits_producers),
    CINEMATOGRAPHY(R.string.credits_cinematography),
    COMPOSER(R.string.credits_composers),
    // Only in the "See all crew" sheet, never in the short Crew section
    EDITOR(R.string.credits_editors),
    PRODUCTION_DESIGN(R.string.credits_production_design),
    COSTUME_DESIGN(R.string.credits_costume_design)
}

/** One line of the short Crew section: a role, the people shown on it, and how many more the sheet has. */
data class CrewPreviewRow(val role: CrewRole, val people: List<CrewMember>, val hiddenCount: Int)

/** The short Crew section: [rows], and whether the "See all crew" sheet has more to show than these rows. */
data class CrewPreview(val rows: List<CrewPreviewRow>, val hasMore: Boolean)

object Credits {

    /** A group never shows more people than this (a big film has dozens of "Producer" credits). Directors are never cut. */
    const val MAX_PER_ROLE = 6

    /** The "See all crew" sheet lists up to this many people per role (still not the whole end credits). */
    const val MAX_FULL_PER_ROLE = 20

    /** The short Crew section: at most this many rows... */
    const val PREVIEW_ROWS = 4

    /** ...with at most this many names on each. */
    const val PREVIEW_PEOPLE = 2

    /** The roles the short Crew section is made of, in the order of its rows. */
    val KEY_ROLES = listOf(CrewRole.DIRECTOR, CrewRole.WRITER, CrewRole.PRODUCER, CrewRole.CINEMATOGRAPHY, CrewRole.COMPOSER)

    // The jobs of each kind, the most important first: the order inside a group follows this order.
    private val JOBS: Map<CrewRole, List<String>> = mapOf(
        CrewRole.DIRECTOR to listOf("Director"),
        CrewRole.WRITER to listOf("Screenplay", "Writer", "Story", "Novel", "Author", "Original Story", "Teleplay"),
        CrewRole.PRODUCER to listOf("Producer", "Executive Producer"),
        CrewRole.CINEMATOGRAPHY to listOf("Director of Photography", "Cinematography"),
        CrewRole.COMPOSER to listOf("Original Music Composer", "Composer", "Music"),
        CrewRole.EDITOR to listOf("Editor", "Film Editor"),
        CrewRole.PRODUCTION_DESIGN to listOf("Production Design"),
        CrewRole.COSTUME_DESIGN to listOf("Costume Design")
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
    fun group(crew: List<CrewMember>, perRole: Int = MAX_PER_ROLE): Map<CrewRole, List<CrewMember>> {
        val groups = LinkedHashMap<CrewRole, List<CrewMember>>()
        for (role in CrewRole.values()) {
            val jobs = JOBS.getValue(role)
            val people = crew
                .filter { roleOf(it.job) == role }
                .sortedBy { jobs.indexOf(it.job.trim().lowercase()) } // stable: equal jobs keep TMDB's order
                .distinctBy { it.id }
            if (people.isEmpty()) continue
            groups[role] = if (role == CrewRole.DIRECTOR) people else people.take(perRole)
        }
        return groups
    }

    /**
     * The short Crew section of the Details screen, made from ALL the crew groups ([full], with the director in it):
     * the first [PREVIEW_ROWS] of the key roles that have people, each with at most [PREVIEW_PEOPLE] names. [CrewPreview.hasMore]
     * says the "See all crew" button is worth showing: some names, some role or some other kind of crew did not fit.
     */
    fun preview(full: Map<CrewRole, List<CrewMember>>): CrewPreview {
        val rows = KEY_ROLES
            .filter { full[it].orEmpty().isNotEmpty() }
            .take(PREVIEW_ROWS)
            .map { role ->
                val people = full.getValue(role)
                CrewPreviewRow(role, people.take(PREVIEW_PEOPLE), hiddenCount = (people.size - PREVIEW_PEOPLE).coerceAtLeast(0))
            }
        val shownRoles = rows.map { it.role }.toSet()
        val hasMore = rows.any { it.hiddenCount > 0 } || full.any { (role, people) -> people.isNotEmpty() && role !in shownRoles }
        return CrewPreview(rows, hasMore)
    }
}
