package com.example.cinefiles.data

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Database migration 3 -> 4 on a real SQLite: a diary page of the first version (a list of blocks) becomes one headline-less body with
 * a cover, keeping its id, movie, dates, rating, font and theme. The old table is built here by hand, with the version 3 columns.
 */
@RunWith(AndroidJUnit4::class)
class DiaryMigrationTest {

    private lateinit var helper: SupportSQLiteOpenHelper
    private lateinit var db: SupportSQLiteDatabase

    @Before
    fun openVersion3() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null) // in memory
                .callback(object : SupportSQLiteOpenHelper.Callback(3) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE `diary_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `movieId` INTEGER NOT NULL, " +
                                "`movieTitle` TEXT NOT NULL, `posterPath` TEXT, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                                "`ratingSnapshot` REAL, `fontStyle` TEXT NOT NULL, `backgroundTheme` TEXT NOT NULL, `blocksJson` TEXT NOT NULL)"
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        db = helper.writableDatabase
    }

    @After
    fun close() {
        helper.close()
    }

    private fun insert(id: Long, rating: Float?, font: String, theme: String, blocks: String) {
        db.execSQL(
            "INSERT INTO diary_entries (id, movieId, movieTitle, posterPath, createdAt, updatedAt, ratingSnapshot, fontStyle, backgroundTheme, blocksJson) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf<Any?>(id, 500 + id, "Movie $id", "/poster$id.jpg", 1000L + id, 2000L + id, rating, font, theme, blocks)
        )
    }

    private data class Row(
        val id: Long, val movieId: Int, val title: String, val created: Long, val updated: Long, val rating: Float?,
        val headline: String, val body: String, val cover: String?, val font: String, val align: String, val size: String, val theme: String
    )

    private fun rows(): List<Row> =
        db.query(
            "SELECT id, movieId, movieTitle, createdAt, updatedAt, ratingSnapshot, headline, bodyHtml, coverImageUri, fontStyle, " +
                "textAlignment, textSize, canvasTheme FROM diary_entries ORDER BY id"
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        Row(
                            c.getLong(0), c.getInt(1), c.getString(2), c.getLong(3), c.getLong(4),
                            if (c.isNull(5)) null else c.getFloat(5), c.getString(6), c.getString(7),
                            if (c.isNull(8)) null else c.getString(8), c.getString(9), c.getString(10), c.getString(11), c.getString(12)
                        )
                    )
                }
            }
        }

    @Test
    fun everyPageKeepsItsIdMovieDatesAndRating() {
        insert(7, 3.5f, "SERIF", "CRIMSON", "[{\"id\":\"a\",\"text\":\"Hello\",\"type\":\"TEXT\"}]")
        insert(9, null, "SANS", "MIDNIGHT", "[]")

        MIGRATION_3_4.migrate(db)

        val rows = rows()
        assertEquals(listOf(7L, 9L), rows.map { it.id })
        assertEquals(507, rows[0].movieId)
        assertEquals("Movie 7", rows[0].title)
        assertEquals(1007L, rows[0].created)
        assertEquals(2007L, rows[0].updated)
        assertEquals(3.5f, rows[0].rating)
        assertNull(rows[1].rating)
    }

    @Test
    fun blocksBecomeABodyAndTheFirstPictureBecomesTheCover() {
        insert(
            1, 4.0f, "MONO", "SEPIA",
            "[{\"id\":\"a\",\"html\":true,\"text\":\"<p dir=\\\"ltr\\\">First <b>bold</b></p>\",\"align\":\"CENTER\",\"type\":\"TEXT\"}," +
                "{\"id\":\"b\",\"ref\":\"/still.jpg\",\"source\":\"TMDB\",\"type\":\"IMAGE\"}," +
                "{\"id\":\"c\",\"text\":\"Second\",\"type\":\"TEXT\"}," +
                "{\"id\":\"d\",\"ref\":\"mine.jpg\",\"source\":\"LOCAL\",\"type\":\"IMAGE\"}]"
        )

        MIGRATION_3_4.migrate(db)

        val row = rows().single()
        assertEquals("<p dir=\"ltr\">First <b>bold</b></p><p dir=\"ltr\">Second</p>", row.body)
        assertEquals("tmdb:/still.jpg", row.cover)
        assertEquals("CENTER", row.align)
        assertEquals("", row.headline)
        assertEquals("STANDARD", row.size)
        assertEquals("MONO", row.font)
        assertEquals("SEPIA", row.theme)
    }

    @Test
    fun theTwoRenamedThemesKeepTheirLook() {
        insert(1, null, "SANS", "PARCHMENT", "[]")
        insert(2, null, "SANS", "SLATE", "[]")

        MIGRATION_3_4.migrate(db)

        assertEquals(listOf("CHARCOAL", "NAVY"), rows().map { it.theme })
    }

    @Test
    fun brokenJsonGivesAnEmptyPageAndDoesNotStopTheMigration() {
        insert(1, null, "SANS", "MIDNIGHT", "this is not json")
        insert(2, 2.0f, "SANS", "MIDNIGHT", "[{\"type\":\"TEXT\",\"text\":\"fine\"}]")

        MIGRATION_3_4.migrate(db)

        val rows = rows()
        assertEquals("", rows[0].body)
        assertNull(rows[0].cover)
        assertEquals("<p dir=\"ltr\">fine</p>", rows[1].body)
    }

    @Test
    fun theNewTableHasAnAutoIncrementThatContinuesAfterTheOldIds() {
        insert(41, null, "SANS", "MIDNIGHT", "[]")
        MIGRATION_3_4.migrate(db)

        db.execSQL(
            "INSERT INTO diary_entries (movieId, movieTitle, createdAt, updatedAt, headline, bodyHtml, fontStyle, textAlignment, textSize, canvasTheme) " +
                "VALUES (1, 'New', 1, 1, '', '', 'SANS', 'LEFT', 'STANDARD', 'MIDNIGHT')"
        )
        assertEquals(listOf(41L, 42L), rows().map { it.id })
    }
}
