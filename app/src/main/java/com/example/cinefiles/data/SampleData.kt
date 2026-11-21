package com.example.cinefiles.data

import com.example.cinefiles.logic.DiscoveryRequest
import com.example.cinefiles.logic.DiscoverySource

// TEMP: fake movies so the Home screen and the swipe deck have something to show before real TMDB data
// exists. The images are real TMDB poster/backdrop files (public image addresses, no token needed);
// the ratings, years, genres and runtimes are only roughly right.
//
// This is also WHY the swipe deck feels repetitive today: there are only these 20 movies, and Home shows
// 17 of them. The deck chooses among them with the real logic (logic/Discovery.kt: mood, preferences,
// a mix of kinds, no repeats), but it cannot invent variety that is not in the list.
// Removed in Batch 5, when Home and the swipe deck load real movies through MovieRepository (see TEMP.md).
object SampleData {

    // TMDB's genre numbers (the names are what TMDB calls them)
    private val genreIds = mapOf(
        "Action" to 28, "Adventure" to 12, "Animation" to 16, "Comedy" to 35, "Crime" to 80,
        "Drama" to 18, "History" to 36, "Romance" to 10749, "Science Fiction" to 878, "Thriller" to 53
    )

    private fun sample(
        id: Int, title: String, poster: String, backdrop: String?, rating: Double, year: Int,
        genres: List<String>, minutes: Int, language: String = "en"
    ) = Movie(
        id = id, title = title, posterPath = poster, backdropPath = backdrop,
        voteAverage = rating, releaseYear = year, genreNames = genres,
        genreIds = genres.map { genreIds.getValue(it) },
        // voteCount is left at 0 (= unknown): we do not know how many people rated these, so
        // none of them can honestly be called a "hidden gem". Real data has the numbers.
        originalLanguage = language, runtimeMinutes = minutes
    )

    // ---- The 20 sample movies. Each one is written down exactly once. ----

    private val bladeRunner = sample(
        335984, "Blade Runner 2049", "/gajva2L0rPYkEWjzgFlBXCAVBE5.jpg", "/ilRyazdMJwN05exqhwK4tMKBYZs.jpg",
        7.6, 2017, listOf("Science Fiction", "Drama"), 164
    )
    private val oppenheimer = sample(
        872585, "Oppenheimer", "/8Gxv8gSFCU0XGDykEGv7zR1n2ua.jpg", null,
        8.1, 2023, listOf("Drama", "History"), 180
    )
    private val dune = sample(
        438631, "Dune", "/d5NXSklXo0qyIYkgV94XAgMIckC.jpg", null,
        7.8, 2021, listOf("Science Fiction", "Adventure"), 155
    )
    private val spiderVerse = sample(
        569094, "Spider-Man: Across the Spider-Verse", "/8Vt6mWEReuy4Of61Lnj5Xj704m8.jpg", null,
        8.4, 2023, listOf("Animation", "Action"), 140
    )
    private val everything = sample(
        545611, "Everything Everywhere All at Once", "/w3LxiVYdWWRvEVdn5RYq6jIqkb1.jpg", null,
        7.8, 2022, listOf("Action", "Adventure", "Science Fiction"), 139
    )
    private val topGun = sample(
        361743, "Top Gun: Maverick", "/62HCnUTziyWcpDaBO2i1DX17ljH.jpg", null,
        8.2, 2022, listOf("Action", "Drama"), 131
    )
    private val parasite = sample(
        496243, "Parasite", "/7IiTTgloJzvGI1TAYymCfbfl3vT.jpg", null,
        8.5, 2019, listOf("Comedy", "Thriller", "Drama"), 132, language = "ko"
    )
    private val joker = sample(
        475557, "Joker", "/udDclJoHjfjb8Ekgsd4FDteOkCU.jpg", null,
        8.1, 2019, listOf("Crime", "Thriller", "Drama"), 122
    )
    private val laLaLand = sample(
        313369, "La La Land", "/uDO8zWDhfWwoFdKS4fzkUJt0Rf0.jpg", null,
        7.9, 2016, listOf("Drama", "Romance", "Comedy"), 128
    )
    private val darkKnight = sample(
        155, "The Dark Knight", "/qJ2tW6WMUDux911r6m7haRef0WH.jpg", null,
        8.5, 2008, listOf("Drama", "Action", "Crime"), 152
    )
    private val inception = sample(
        27205, "Inception", "/ljsZTbVsrQSqZgWeep2B1QiDKuh.jpg", null,
        8.4, 2010, listOf("Action", "Science Fiction", "Adventure"), 148
    )
    private val interstellar = sample(
        157336, "Interstellar", "/gEU2QniE6E77NI6lCU6MxlNBvIx.jpg", null,
        8.5, 2014, listOf("Adventure", "Drama", "Science Fiction"), 169
    )
    private val pulpFiction = sample(
        680, "Pulp Fiction", "/d5iIlFn5s0ImszYzBPb8JPIfbXD.jpg", null,
        8.5, 1994, listOf("Thriller", "Crime"), 154
    )
    private val fightClub = sample(
        550, "Fight Club", "/pB8BM7pdSp6B6Ih7QZ4DrQ3PmJK.jpg", null,
        8.4, 1999, listOf("Drama"), 139
    )
    private val madMax = sample(
        76341, "Mad Max: Fury Road", "/8tZYtuWezp8JbcsvHYO0O46tFbo.jpg", null,
        7.6, 2015, listOf("Action", "Adventure", "Science Fiction"), 120
    )
    private val matrix = sample(
        603, "The Matrix", "/f89U3ADr1oiB1s9GkdPOEpXUk5H.jpg", null,
        8.2, 1999, listOf("Action", "Science Fiction"), 136
    )
    private val gladiator = sample(
        98, "Gladiator", "/ty8TGRuvJLPUmAR1H1nRIsgwvim.jpg", null,
        8.2, 2000, listOf("Action", "Drama", "Adventure"), 155
    )
    private val godfather = sample(
        238, "The Godfather", "/3bhkrj58Vtu7enYsRolD1fZdja1.jpg", null,
        8.7, 1972, listOf("Drama", "Crime"), 175
    )
    private val shawshank = sample(
        278, "The Shawshank Redemption", "/9cqNxx0GxF0bflZmeSMuL5tnGzr.jpg", null,
        8.7, 1994, listOf("Drama", "Crime"), 142
    )
    private val forrestGump = sample(
        13, "Forrest Gump", "/arw2vcBveWOVZr6pxd9XTd1TdQa.jpg", null,
        8.5, 1994, listOf("Comedy", "Drama", "Romance"), 142
    )

    // ---- Home ----

    val featured = bladeRunner

    val trending = listOf(oppenheimer, dune, spiderVerse, everything, topGun, parasite, joker, laLaLand)

    val popular = listOf(darkKnight, inception, interstellar, pulpFiction, fightClub, madMax, matrix, gladiator)

    // ---- The swipe deck ----

    // All 20 movies in a mixed-up order (different eras and moods next to each other).
    private val all = listOf(
        interstellar, laLaLand, parasite, spiderVerse, godfather,
        madMax, forrestGump, bladeRunner, topGun, fightClub,
        dune, joker, shawshank, everything, gladiator,
        oppenheimer, inception, pulpFiction, matrix, darkKnight
    )

    /**
     * Stands in for TMDB: the sample movies that fit one question of the swipe deck (see logic/Discovery.kt).
     * Each kind of question starts from the list TMDB would answer it from:
     * trending from the trending list, popular from the popular list, top rated best-first, the rest
     * from everything. [DiscoveryRequest.matches] then keeps the movies that fit the mood and preferences.
     * Batch 5 replaces this with a MovieRepository call.
     */
    fun discover(request: DiscoveryRequest): List<Movie> {
        val pool = when (request.source) {
            DiscoverySource.TRENDING -> trending
            DiscoverySource.POPULAR -> popular
            DiscoverySource.TOP_RATED -> all.sortedByDescending { it.voteAverage }
            DiscoverySource.MOOD_MATCH, DiscoverySource.HIDDEN_GEM, DiscoverySource.EXPLORE -> all
        }
        return pool.filter { request.matches(it) }
    }
}
