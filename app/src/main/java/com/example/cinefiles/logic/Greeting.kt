package com.example.cinefiles.logic

/** The three parts of the day the Home greeting knows about. */
enum class DayPart { MORNING, AFTERNOON, EVENING }

/**
 * Which part of the day is it? [hour] is the hour of the day from 0 to 23.
 *
 *  - 05:00 to 11:59 -> morning
 *  - 12:00 to 16:59 -> afternoon
 *  - everything else (17:00 to 04:59) -> evening
 */
fun dayPartForHour(hour: Int): DayPart = when (hour) {
    in 5..11 -> DayPart.MORNING
    in 12..16 -> DayPart.AFTERNOON
    else -> DayPart.EVENING
}
