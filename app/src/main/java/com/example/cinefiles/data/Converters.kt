package com.example.cinefiles.data

import androidx.room.TypeConverter

/**
 * Room can only store simple values, so the two lists of a movie are stored as one line of text:
 * the genre ids [28, 12] become "28,12", and the genre names ["Action", "Adventure"] become "Action,Adventure".
 * (No genre name contains a comma, so a comma is a safe separator.)
 * Room calls these functions by itself when it writes or reads a movie.
 */
class Converters {

    @TypeConverter
    fun idsToText(ids: List<Int>): String = ids.joinToString(",")

    @TypeConverter
    fun textToIds(text: String): List<Int> = text.split(",").mapNotNull { it.trim().toIntOrNull() }

    @TypeConverter
    fun namesToText(names: List<String>): String = names.joinToString(",")

    @TypeConverter
    fun textToNames(text: String): List<String> = text.split(",").map { it.trim() }.filter { it.isNotEmpty() }
}
