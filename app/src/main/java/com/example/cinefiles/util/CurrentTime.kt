package com.example.cinefiles.util

import java.util.Calendar

/*
 * The time, asked in one place. (minSdk is 24, so java.time is not available: Calendar it is.)
 */

/** The hour of the day on the phone's clock, 0 to 23. */
fun currentHour(): Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

/** The day of the year, 1 to 366. Home uses it to pick the "Featured today" movie. */
fun currentDayOfYear(): Int = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)

/** The year, for example 2026. */
fun currentYear(): Int = Calendar.getInstance().get(Calendar.YEAR)
