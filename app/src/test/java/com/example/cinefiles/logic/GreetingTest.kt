package com.example.cinefiles.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class GreetingTest {

    @Test
    fun morningRunsFromFiveToEleven() {
        assertEquals(DayPart.MORNING, dayPartForHour(5))
        assertEquals(DayPart.MORNING, dayPartForHour(8))
        assertEquals(DayPart.MORNING, dayPartForHour(11))
    }

    @Test
    fun afternoonRunsFromTwelveToSixteen() {
        assertEquals(DayPart.AFTERNOON, dayPartForHour(12))
        assertEquals(DayPart.AFTERNOON, dayPartForHour(14))
        assertEquals(DayPart.AFTERNOON, dayPartForHour(16))
    }

    @Test
    fun eveningRunsFromSeventeenUntilFour() {
        assertEquals(DayPart.EVENING, dayPartForHour(17))
        assertEquals(DayPart.EVENING, dayPartForHour(21))
        assertEquals(DayPart.EVENING, dayPartForHour(23))
        assertEquals(DayPart.EVENING, dayPartForHour(0))
        assertEquals(DayPart.EVENING, dayPartForHour(4))
    }

    @Test
    fun everyHourOfTheDayHasAGreeting() {
        // Mostly a guard against a gap being left in the ranges
        for (hour in 0..23) {
            dayPartForHour(hour)
        }
    }
}
