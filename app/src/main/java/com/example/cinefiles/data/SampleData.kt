package com.example.cinefiles.data

// TEMP: fake movies so the Home screen has something to show before real TMDB data exists.
// The images are real TMDB poster/backdrop files (public image addresses, no token needed).
// Removed in Batch 5, when Home loads real movies through MovieRepository (see TEMP.md).
object SampleData {

    val featured = Movie(
        id = 335984,
        title = "Blade Runner 2049",
        posterPath = "/gajva2L0rPYkEWjzgFlBXCAVBE5.jpg",
        backdropPath = "/ilRyazdMJwN05exqhwK4tMKBYZs.jpg"
    )

    val trending = listOf(
        Movie(872585, "Oppenheimer", "/8Gxv8gSFCU0XGDykEGv7zR1n2ua.jpg", null),
        Movie(438631, "Dune", "/d5NXSklXo0qyIYkgV94XAgMIckC.jpg", null),
        Movie(569094, "Spider-Man: Across the Spider-Verse", "/8Vt6mWEReuy4Of61Lnj5Xj704m8.jpg", null),
        Movie(545611, "Everything Everywhere All at Once", "/w3LxiVYdWWRvEVdn5RYq6jIqkb1.jpg", null),
        Movie(361743, "Top Gun: Maverick", "/62HCnUTziyWcpDaBO2i1DX17ljH.jpg", null),
        Movie(496243, "Parasite", "/7IiTTgloJzvGI1TAYymCfbfl3vT.jpg", null),
        Movie(475557, "Joker", "/udDclJoHjfjb8Ekgsd4FDteOkCU.jpg", null),
        Movie(313369, "La La Land", "/uDO8zWDhfWwoFdKS4fzkUJt0Rf0.jpg", null)
    )

    val popular = listOf(
        Movie(155, "The Dark Knight", "/qJ2tW6WMUDux911r6m7haRef0WH.jpg", null),
        Movie(27205, "Inception", "/ljsZTbVsrQSqZgWeep2B1QiDKuh.jpg", null),
        Movie(157336, "Interstellar", "/gEU2QniE6E77NI6lCU6MxlNBvIx.jpg", null),
        Movie(680, "Pulp Fiction", "/d5iIlFn5s0ImszYzBPb8JPIfbXD.jpg", null),
        Movie(550, "Fight Club", "/pB8BM7pdSp6B6Ih7QZ4DrQ3PmJK.jpg", null),
        Movie(76341, "Mad Max: Fury Road", "/8tZYtuWezp8JbcsvHYO0O46tFbo.jpg", null),
        Movie(603, "The Matrix", "/f89U3ADr1oiB1s9GkdPOEpXUk5H.jpg", null),
        Movie(98, "Gladiator", "/ty8TGRuvJLPUmAR1H1nRIsgwvim.jpg", null)
    )
}
