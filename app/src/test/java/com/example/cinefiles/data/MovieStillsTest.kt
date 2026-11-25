package com.example.cinefiles.data

import com.example.cinefiles.data.dto.ImageFileDto
import com.example.cinefiles.data.dto.MovieImagesDto
import com.example.cinefiles.data.dto.toStillPaths
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The movie stills the Diary offers (TMDB: GET /movie/{id}/images): which paths are kept and in which order. */
class MovieStillsTest {

    private fun image(path: String?, language: String? = null) = ImageFileDto(filePath = path, language = language)

    @Test
    fun picturesWithoutTextComeBeforePicturesWithTextAndTmdbsOrderIsKeptInsideEachGroup() {
        val dto = MovieImagesDto(backdrops = listOf(image("/a.jpg", "en"), image("/b.jpg"), image("/c.jpg", "en"), image("/d.jpg")))
        assertEquals(listOf("/b.jpg", "/d.jpg", "/a.jpg", "/c.jpg"), dto.toStillPaths())
    }

    @Test
    fun blankPathsAndRepeatsAreDropped() {
        val dto = MovieImagesDto(backdrops = listOf(image(null), image(""), image("/a.jpg"), image("/a.jpg")))
        assertEquals(listOf("/a.jpg"), dto.toStillPaths())
    }

    @Test
    fun theListIsCut() {
        val dto = MovieImagesDto(backdrops = (1..50).map { image("/$it.jpg") })
        assertEquals(30, dto.toStillPaths().size)
        assertEquals(5, dto.toStillPaths(limit = 5).size)
    }

    @Test
    fun noPicturesAtAllIsAnEmptyList() {
        assertTrue(MovieImagesDto().toStillPaths().isEmpty())
        assertTrue((null as MovieImagesDto?).toStillPaths().isEmpty())
    }

    @Test
    fun tmdbsAnswerIsReadFromItsJsonNames() {
        val json = """{ "backdrops": [ { "file_path": "/x.jpg", "iso_639_1": null, "vote_average": 5.5, "width": 1920, "height": 1080 } ], "posters": [] }"""
        val dto = Gson().fromJson(json, MovieImagesDto::class.java)
        assertEquals(listOf("/x.jpg"), dto.toStillPaths())
    }
}
