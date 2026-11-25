package com.example.cinefiles.ui.diary

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.asLiveData
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import com.example.cinefiles.CineFilesApp
import com.example.cinefiles.data.DiaryEntry
import com.example.cinefiles.logic.DiaryCover
import com.example.cinefiles.logic.ImageSource
import com.example.cinefiles.util.ARG_MOVIE_ID
import com.example.cinefiles.util.Event
import kotlinx.coroutines.launch

/**
 * Holds the list of diary pages: every page ("My Diary"), or only the pages about one movie (the navigation argument
 * "movieId", 0 = all). The list comes live from the database, so a page that is saved or deleted shows up at once.
 */
class DiaryListViewModel(app: Application, handle: SavedStateHandle) : AndroidViewModel(app) {

    private val repository = (app as CineFilesApp).movieRepository
    private val diaryImages = (app as CineFilesApp).diaryImages

    /** The movie the list is limited to, or 0 for every page. */
    val movieId: Int = handle.get<Int>(ARG_MOVIE_ID) ?: 0

    /** The pages, newest first. */
    val entries: LiveData<List<DiaryEntry>> =
        (if (movieId == 0) repository.observeDiary() else repository.observeDiaryForMovie(movieId)).asLiveData()

    /** The title of the movie the list is limited to (null for "My Diary", or when it is not known). */
    val movieTitle: LiveData<String?> = liveData {
        emit(if (movieId == 0) null else (repository.savedMovie(movieId) ?: repository.recentlySeen(movieId))?.title)
    }

    private val _newPage = MutableLiveData<Event<Boolean>>()

    /** The answer to [requestNewPage] (once: see [Event]): true = the movie is watched, start the page; false = THE WATCH RULE says no. */
    val newPage: LiveData<Event<Boolean>> = _newPage

    /** "New page" for the movie of this list. Only a WATCHED movie can have a new page (logic/WatchRules.kt: canWriteDiary). */
    fun requestNewPage() {
        viewModelScope.launch { _newPage.value = Event(repository.canWriteDiary(movieId)) }
    }

    /** Deletes the page, and its cover picture when that is a copy of a gallery picture kept in the app. */
    fun delete(entry: DiaryEntry) {
        viewModelScope.launch {
            repository.deleteDiaryEntry(entry.id)
            DiaryCover.parse(entry.coverImageUri)?.takeIf { it.source == ImageSource.LOCAL }?.let { diaryImages.delete(listOf(it.ref)) }
        }
    }
}
