package com.example.cinefiles.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** A fake of the diary table for the tests that run on the computer: pages in memory, ids handed out one after another. */
class FakeDiaryDao : DiaryDao {

    private val rows = MutableStateFlow<List<DiaryEntry>>(emptyList())
    private var lastId = 0L

    private fun newestFirst(list: List<DiaryEntry>) = list.sortedWith(compareByDescending<DiaryEntry> { it.updatedAt }.thenByDescending { it.id })

    override suspend fun insert(entry: DiaryEntry): Long {
        val saved = entry.copy(id = ++lastId)
        rows.value = rows.value + saved
        return saved.id
    }

    override suspend fun update(entry: DiaryEntry) {
        rows.value = rows.value.map { if (it.id == entry.id) entry else it }
    }

    override suspend fun delete(id: Long) {
        rows.value = rows.value.filterNot { it.id == id }
    }

    override suspend fun get(id: Long): DiaryEntry? = rows.value.firstOrNull { it.id == id }

    override fun observeAll(): Flow<List<DiaryEntry>> = rows.map(::newestFirst)

    override fun observeForMovie(movieId: Int): Flow<List<DiaryEntry>> = rows.map { newestFirst(it.filter { e -> e.movieId == movieId }) }

    override suspend fun getAll(): List<DiaryEntry> = newestFirst(rows.value)

    override fun observeCount(): Flow<Int> = rows.map { it.size }

    override suspend fun count(): Int = rows.value.size

    override suspend fun deleteAll() {
        rows.value = emptyList()
    }
}
