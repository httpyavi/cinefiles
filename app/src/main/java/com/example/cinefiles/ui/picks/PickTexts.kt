package com.example.cinefiles.ui.picks

import android.content.Context
import com.example.cinefiles.R
import com.example.cinefiles.data.Movie
import com.example.cinefiles.logic.Moods
import com.example.cinefiles.logic.PickReason
import com.example.cinefiles.util.ratingText
import com.example.cinefiles.util.yearAndGenres

/** "Similar to La La Land" or "Matches your Happy mood". (The engine only says WHY; the words are made here.) */
fun Context.reasonText(reason: PickReason): String = when (reason) {
    is PickReason.SimilarTo -> getString(R.string.picks_reason_similar, reason.likedTitle)
    is PickReason.MoodMatch -> {
        val mood = Moods.all.firstOrNull { it.id == reason.moodId }
        if (mood != null) getString(R.string.picks_reason_mood, getString(mood.shortLabel)) else getString(R.string.picks_reason_generic)
    }
    PickReason.Generic -> getString(R.string.picks_reason_generic)
}

/** "7.8 · 2026 · Romance": the line under a pick's title (the rating, the year and up to two genres). */
fun pickMeta(movie: Movie): String =
    listOfNotNull(movie.ratingText(), movie.yearAndGenres().ifEmpty { null }).joinToString(" · ")
