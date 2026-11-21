package com.example.cinefiles.ui.mood

import com.example.cinefiles.logic.Mood

/**
 * One mood card exactly as the Mood screen draws it.
 *
 * @param imageUrl  the photo's web address, or null when there is no photo (the card then
 *                  shows its tinted fallback). Batch 5 supplies real TMDB photos here.
 */
data class MoodCard(
    val mood: Mood,
    val imageUrl: String?,
    val isSelected: Boolean
)
