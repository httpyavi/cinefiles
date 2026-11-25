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
    fun bigPosterUrlUsesTheSwipeCardSize() {
        assertEquals("https://image.tmdb.org/t/p/w500/abc.jpg", ImageUrls.bigPoster("/abc.jpg"))
    }

    @Test
    fun backdropUrlUsesTheWideSize() {
        assertEquals("https://image.tmdb.org/t/p/w780/abc.jpg", ImageUrls.backdrop("/abc.jpg"))
    }

    @Test
    fun hdPosterUrlUsesTheSharpestSizeThatIsStillLight() {
        // w780, never "original": the full-screen poster must not cost several megabytes
        assertEquals("https://image.tmdb.org/t/p/w780/abc.jpg", ImageUrls.hdPoster("/abc.jpg"))
    }

    @Test
    fun profileUrlUsesTheSmallPhotoSize() {
        assertEquals("https://image.tmdb.org/t/p/w185/abc.jpg", ImageUrls.profile("/abc.jpg"))
    }

    @Test
    fun missingPathGivesNoUrl() {
        assertNull(ImageUrls.poster(null))
        assertNull(ImageUrls.bigPoster(null))
        assertNull(ImageUrls.hdPoster(null))
        assertNull(ImageUrls.profile(null))
        assertNull(ImageUrls.backdrop(null))
    }
}
