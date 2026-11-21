package com.example.cinefiles.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImageUrlsTest {

    @Test
    fun posterUrlUsesTheMediumSize() {
        assertEquals("https://image.tmdb.org/t/p/w342/abc.jpg", ImageUrls.poster("/abc.jpg"))
    }

    @Test
    fun bigPosterUrlUsesTheLargeSize() {
        assertEquals("https://image.tmdb.org/t/p/w780/abc.jpg", ImageUrls.bigPoster("/abc.jpg"))
    }

    @Test
    fun backdropUrlUsesTheWideSize() {
        assertEquals("https://image.tmdb.org/t/p/w780/abc.jpg", ImageUrls.backdrop("/abc.jpg"))
    }

    @Test
    fun missingPathGivesNoUrl() {
        assertNull(ImageUrls.poster(null))
        assertNull(ImageUrls.bigPoster(null))
        assertNull(ImageUrls.backdrop(null))
    }
}
