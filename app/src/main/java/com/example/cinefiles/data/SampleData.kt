package com.example.cinefiles.data

// TEMP: fake movies so the Home screen and the swipe deck have something to show before real TMDB data
// exists. The images are real TMDB poster/backdrop files (public image addresses, no token needed);
// the ratings, years and genres are only roughly right. Removed in Batch 5, when Home and the swipe
// deck load real movies through MovieRepository (see TEMP.md).
object SampleData {

    // ---- The 20 sample movies. Each one is written down exactly once. ----

    private val bladeRunner = Movie(
        335984, "Blade Runner 2049", "/gajva2L0rPYkEWjzgFlBXCAVBE5.jpg", "/ilRyazdMJwN05exqhwK4tMKBYZs.jpg",
        7.6, 2017, listOf("Science Fiction", "Drama")
    )
    private val oppenheimer = Movie(
        872585, "Oppenheimer", "/8Gxv8gSFCU0XGDykEGv7zR1n2ua.jpg", null,
        8.1, 2023, listOf("Drama", "History")
    )
    private val dune = Movie(
        438631, "Dune", "/d5NXSklXo0qyIYkgV94XAgMIckC.jpg", null,
        7.8, 2021, listOf("Science Fiction", "Adventure")
    )
    private val spiderVerse = Movie(
        569094, "Spider-Man: Across the Spider-Verse", "/8Vt6mWEReuy4Of61Lnj5Xj704m8.jpg", null,
        8.4, 2023, listOf("Animation", "Action")
    )
    private val everything = Movie(
        545611, "Everything Everywhere All at Once", "/w3LxiVYdWWRvEVdn5RYq6jIqkb1.jpg", null,
        7.8, 2022, listOf("Action", "Adventure", "Science Fiction")
    )
    private val topGun = Movie(
        361743, "Top Gun: Maverick", "/62HCnUTziyWcpDaBO2i1DX17ljH.jpg", null,
        8.2, 2022, listOf("Action", "Drama")
    )
    private val parasite = Movie(
        496243, "Parasite", "/7IiTTgloJzvGI1TAYymCfbfl3vT.jpg", null,
        8.5, 2019, listOf("Comedy", "Thriller", "Drama")
    )
    private val joker = Movie(
        475557, "Joker", "/udDclJoHjfjb8Ekgsd4FDteOkCU.jpg", null,
        8.1, 2019, listOf("Crime", "Thriller", "Drama")
    )
    private val laLaLand = Movie(
        313369, "La La Land", "/uDO8zWDhfWwoFdKS4fzkUJt0Rf0.jpg", null,
        7.9, 2016, listOf("Drama", "Romance", "Comedy")
    )
    private val darkKnight = Movie(
        155, "The Dark Knight", "/qJ2tW6WMUDux911r6m7haRef0WH.jpg", null,
        8.5, 2008, listOf("Drama", "Action", "Crime")
    )
    private val inception = Movie(
        27205, "Inception", "/ljsZTbVsrQSqZgWeep2B1QiDKuh.jpg", null,
        8.4, 2010, listOf("Action", "Science Fiction", "Adventure")
    )
    private val interstellar = Movie(
        157336, "Interstellar", "/gEU2QniE6E77NI6lCU6MxlNBvIx.jpg", null,
        8.5, 2014, listOf("Adventure", "Drama", "Science Fiction")
    )
    private val pulpFiction = Movie(
        680, "Pulp Fiction", "/d5iIlFn5s0ImszYzBPb8JPIfbXD.jpg", null,
        8.5, 1994, listOf("Thriller", "Crime")
    )
    private val fightClub = Movie(
        550, "Fight Club", "/pB8BM7pdSp6B6Ih7QZ4DrQ3PmJK.jpg", null,
        8.4, 1999, listOf("Drama")
    )
    private val madMax = Movie(
        76341, "Mad Max: Fury Road", "/8tZYtuWezp8JbcsvHYO0O46tFbo.jpg", null,
        7.6, 2015, listOf("Action", "Adventure", "Science Fiction")
    )
    private val matrix = Movie(
        603, "The Matrix", "/f89U3ADr1oiB1s9GkdPOEpXUk5H.jpg", null,
        8.2, 1999, listOf("Action", "Science Fiction")
    )
    private val gladiator = Movie(
        98, "Gladiator", "/ty8TGRuvJLPUmAR1H1nRIsgwvim.jpg", null,
        8.2, 2000, listOf("Action", "Drama", "Adventure")
    )
    private val godfather = Movie(
        238, "The Godfather", "/3bhkrj58Vtu7enYsRolD1fZdja1.jpg", null,
        8.7, 1972, listOf("Drama", "Crime")
    )
    private val shawshank = Movie(
        278, "The Shawshank Redemption", "/9cqNxx0GxF0bflZmeSMuL5tnGzr.jpg", null,
        8.7, 1994, listOf("Drama", "Crime")
    )
    private val forrestGump = Movie(
        13, "Forrest Gump", "/arw2vcBveWOVZr6pxd9XTd1TdQa.jpg", null,
        8.5, 1994, listOf("Comedy", "Drama", "Romance")
    )

    // ---- Home ----

    val featured = bladeRunner

    val trending = listOf(oppenheimer, dune, spiderVerse, everything, topGun, parasite, joker, laLaLand)

    val popular = listOf(darkKnight, inception, interstellar, pulpFiction, fightClub, madMax, matrix, gladiator)

    // ---- The swipe deck ----

    // All 20 movies in a mixed-up order (different eras and moods next to each other).
    private val swipeOrder = listOf(
        interstellar, laLaLand, parasite, spiderVerse, godfather,
        madMax, forrestGump, bladeRunner, topGun, fightClub,
        dune, joker, shawshank, everything, gladiator,
        oppenheimer, inception, pulpFiction, matrix, darkKnight
    )

    /**
     * One "page" of 20 swipe cards. Page 0 is the order above. Every later page is the same 20 movies
     * starting somewhere else, so "Keep swiping" gives cards in a fresh order. (Real TMDB pages in Batch 5
     * will give new movies.)
     */
    fun swipePage(page: Int): List<Movie> {
        val shift = (page * SWIPE_PAGE_SHIFT) % swipeOrder.size
        return swipeOrder.drop(shift) + swipeOrder.take(shift)
    }

    private const val SWIPE_PAGE_SHIFT = 7
}
