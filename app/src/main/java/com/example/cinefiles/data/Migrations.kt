package com.example.cinefiles.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.cinefiles.logic.DiaryBlocks

/**
 * Database version 2 -> 3. Nothing the user saved is lost:
 *
 *  - movies: the new column `favoriteOrder` (the place among the 4 favorites). The favorites keep the order the Profile
 *    showed before (newest first).
 *  - THE WATCH RULE (logic/WatchRules.kt) says a like, a rating or a favorite only belongs on a WATCHED movie. A movie that
 *    already had one of them but was not marked watched is marked watched now (and leaves the watchlist): you cannot
 *    like, rate or favorite what you have not seen, so the like / rating / favorite is the proof. Nothing is deleted.
 *  - diary_entries: the old simple table (one text note, a date, a rating) is replaced by the new page table (a list of
 *    blocks, a font and a theme). Every old note becomes one text block of a page, with its rating as the snapshot.
 */
val MIGRATION_2_3: Migration = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        migrateMovies(db)
        migrateDiary(db)
    }

    private fun migrateMovies(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE movies ADD COLUMN favoriteOrder INTEGER NOT NULL DEFAULT 0")

        // The favorites keep their order: the newest was first, so it gets 0, the next 1, ...
        db.execSQL(
            "UPDATE movies SET favoriteOrder = (SELECT COUNT(*) FROM movies AS other WHERE other.isFavorite = 1 AND " +
                "(other.favoritedAt > movies.favoritedAt OR (other.favoritedAt = movies.favoritedAt AND other.id > movies.id))) " +
                "WHERE isFavorite = 1"
        )

        // The watch rule: liked, rated or favorited means watched
        db.execSQL(
            "UPDATE movies SET isWatched = 1, " +
                "watchedAt = MAX(CASE WHEN reaction = 'LIKED' THEN reactedAt ELSE 0 END, ratedAt, favoritedAt, 1), " +
                "inWatchlist = 0, watchlistAddedAt = 0 " +
                "WHERE isWatched = 0 AND (reaction = 'LIKED' OR personalRating IS NOT NULL OR isFavorite = 1)"
        )
    }

    private fun migrateDiary(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS diary_entries_v2")
        val oldTableExists = db.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'diary_entries'")
            .use { it.moveToFirst() }
        if (oldTableExists) db.execSQL("ALTER TABLE diary_entries RENAME TO diary_entries_v2")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `diary_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`movieId` INTEGER NOT NULL, `movieTitle` TEXT NOT NULL, `posterPath` TEXT, `createdAt` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, `ratingSnapshot` REAL, `fontStyle` TEXT NOT NULL, " +
                "`backgroundTheme` TEXT NOT NULL, `blocksJson` TEXT NOT NULL)"
        )

        if (oldTableExists) {
            db.query("SELECT movieId, movieTitle, posterPath, rating, notes, dateWatched, createdAt FROM diary_entries_v2 ORDER BY id")
                .use { cursor ->
                    while (cursor.moveToNext()) {
                        val notes = cursor.getString(4).orEmpty()
                        val blocks = if (notes.isBlank()) emptyList() else listOf(DiaryBlocks.newText(notes.trim()))
                        val created = cursor.getLong(6).takeIf { it > 0 } ?: cursor.getLong(5)
                        db.execSQL(
                            "INSERT INTO diary_entries (movieId, movieTitle, posterPath, createdAt, updatedAt, ratingSnapshot, " +
                                "fontStyle, backgroundTheme, blocksJson) VALUES (?, ?, ?, ?, ?, ?, 'SANS', 'MIDNIGHT', ?)",
                            arrayOf<Any?>(
                                cursor.getInt(0), cursor.getString(1).orEmpty(), cursor.getString(2),
                                created, created,
                                if (cursor.isNull(3)) null else cursor.getFloat(3),
                                DiaryBlocks.toJson(blocks)
                            )
                        )
                    }
                }
            db.execSQL("DROP TABLE diary_entries_v2")
        }
    }
}
