package com.example.cinefiles.ui.diary

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.cinefiles.CineFilesApp
import com.example.cinefiles.R
import com.example.cinefiles.data.DiaryEntry
import com.example.cinefiles.logic.DiaryCover
import com.example.cinefiles.logic.DiaryFont
import com.example.cinefiles.logic.DiaryText
import com.example.cinefiles.logic.DiaryTextSize
import com.example.cinefiles.logic.DiaryTheme
import com.example.cinefiles.logic.ImageSource
import com.example.cinefiles.logic.TextAlign
import com.example.cinefiles.util.ARG_DIARY_ENTRY_ID
import com.example.cinefiles.util.ARG_MOVIE_ID
import com.example.cinefiles.util.Event
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** What the Diary editor can be showing. */
sealed class EditorState {
    /** The page (or the movie it is about) is being read. */
    object Loading : EditorState()

    /** THE WATCH RULE: a new page is only for a WATCHED movie. (The Details screen already stops this; this is the safety net.) */
    object Blocked : EditorState()

    /** The movie of a new page is not known (not saved, not seen in a list, TMDB cannot be reached), or the page was deleted. */
    object Missing : EditorState()

    /** The page is ready. [headline] and [bodyHtml] are what the screen fills its two text fields with (once). */
    data class Ready(
        val movieId: Int,
        val movieTitle: String,
        val posterPath: String?,
        val ratingSnapshot: Float?,
        val date: Long,
        val headline: String,
        val bodyHtml: String
    ) : EditorState()
}

/** The movie stills the cover picker can offer. */
sealed class StillsState {
    object Loading : StillsState()
    object Error : StillsState()
    data class Ready(val paths: List<String>) : StillsState()
}

/**
 * Holds one diary page while it is edited (the navigation arguments: "entryId" of a saved page, or 0 and a "movieId" for a
 * new one). It survives a screen turn, so nothing typed is lost.
 *
 * The page is a headline, a body (HTML), a cover and a look (font, size, alignment, theme). The screen's two text fields are the
 * editor itself: they tell [onHeadlineChanged] and [onBodyChanged] what they hold, and the ViewModel never pushes the text back, so typing
 * never fights with the cursor. Saving is [saveIfNeeded]: the editor saves when you leave it, when the app goes to the background and when you
 * press Save. A NEW page with nothing on it is never saved, and an old page that was emptied is not overwritten (the screen asks whether to
 * delete it: [isEmptiedPage], [deletePage]). The cover files the page no longer uses are deleted after a save.
 */
class DiaryEditorViewModel(app: Application, handle: SavedStateHandle) : AndroidViewModel(app) {

    private val repository = (app as CineFilesApp).movieRepository
    private val diaryImages = (app as CineFilesApp).diaryImages

    private var entryId: Long = handle.get<Long>(ARG_DIARY_ENTRY_ID) ?: 0L
    private var movieId: Int = handle.get<Int>(ARG_MOVIE_ID) ?: 0
    private var movieTitle = ""
    private var posterPath: String? = null
    private var createdAt = 0L

    private var headline = ""
    private var bodyHtml = ""

    private val _state = MutableLiveData<EditorState>(EditorState.Loading)
    val state: LiveData<EditorState> = _state

    private val _font = MutableLiveData(DiaryFont.DEFAULT)
    val font: LiveData<DiaryFont> = _font

    private val _size = MutableLiveData(DiaryTextSize.DEFAULT)
    val size: LiveData<DiaryTextSize> = _size

    private val _align = MutableLiveData(TextAlign.DEFAULT)
    val align: LiveData<TextAlign> = _align

    private val _theme = MutableLiveData(DiaryTheme.DEFAULT)
    val theme: LiveData<DiaryTheme> = _theme

    private val _attachments = MutableLiveData<List<DiaryCover>>(emptyList())

    /** All picture attachments on this page. */
    val attachments: LiveData<List<DiaryCover>> = _attachments

    private val _message = MutableLiveData<Event<Int>>()

    /** A line to show once (a string resource). */
    val message: LiveData<Event<Int>> = _message

    private val _importing = MutableLiveData(false)

    /** True while a gallery picture is being copied into the app. */
    val importing: LiveData<Boolean> = _importing

    private val _stills = MutableLiveData<StillsState>()
    val stills: LiveData<StillsState> = _stills

    private var savedSnapshot = ""
    private var loadedLocalCovers: Set<String> = emptySet() // the gallery pictures the saved page uses
    private val importedThisTime = HashSet<String>()        // gallery pictures copied while editing, not saved yet
    private val saveLock = Mutex()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            if (entryId != 0L) {
                val entry = repository.diaryEntry(entryId)
                if (entry == null) {
                    _state.value = EditorState.Missing
                    return@launch
                }
                movieId = entry.movieId
                movieTitle = entry.movieTitle
                posterPath = entry.posterPath
                createdAt = entry.createdAt
                headline = entry.headline
                bodyHtml = entry.bodyHtml
                val parsed = DiaryCover.parseList(entry.coverImageUri)
                _attachments.value = parsed
                _font.value = DiaryFont.of(entry.fontStyle)
                _size.value = DiaryTextSize.of(entry.textSize)
                _align.value = TextAlign.of(entry.textAlignment)
                _theme.value = DiaryTheme.of(entry.canvasTheme)
                savedSnapshot = snapshot()
                loadedLocalCovers = localNames(parsed)
                _state.value = ready(entry.ratingSnapshot, entry.updatedAt)
            } else {
                if (!repository.canWriteDiary(movieId)) {
                    _state.value = EditorState.Blocked
                    return@launch
                }
                val movie = repository.savedMovie(movieId)
                    ?: repository.recentlySeen(movieId)
                    ?: repository.movieDetails(movieId).getOrNull()?.movie
                if (movie == null) {
                    _state.value = EditorState.Missing
                    return@launch
                }
                movieTitle = movie.title
                posterPath = movie.posterPath
                savedSnapshot = snapshot()
                _state.value = ready(repository.savedMovie(movieId)?.userRating, System.currentTimeMillis())
            }
        }
    }

    private fun ready(rating: Float?, date: Long) =
        EditorState.Ready(movieId, movieTitle, posterPath, rating, date, headline, bodyHtml)

    // ---------- Editing ----------

    /** The headline field says [text] now. */
    fun onHeadlineChanged(text: String) {
        headline = text
    }

    /** The body field says [html] now (see ui/diary/RichText.kt). */
    fun onBodyChanged(html: String) {
        bodyHtml = html
    }

    /** What the screen starts a new view from after a screen turn: the latest words. */
    fun currentHeadline(): String = headline

    fun currentBodyHtml(): String = bodyHtml

    fun setFont(font: DiaryFont) {
        if (_font.value != font) _font.value = font
    }

    fun setSize(size: DiaryTextSize) {
        if (_size.value != size) _size.value = size
    }

    fun setAlign(align: TextAlign) {
        if (_align.value != align) _align.value = align
    }

    fun setTheme(theme: DiaryTheme) {
        if (_theme.value != theme) _theme.value = theme
    }

    /** Copies pictures from the phone's gallery into the app, up to [DiaryText.MAX_PHOTOS] per entry. */
    fun importCovers(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val current = _attachments.value.orEmpty()
        val remaining = DiaryText.MAX_PHOTOS - current.size
        if (remaining <= 0) {
            _message.value = Event(R.string.diary_photos_limit_reached)
            return
        }
        val toImport = uris.take(remaining)
        val truncated = uris.size > remaining
        viewModelScope.launch {
            _importing.value = true
            val added = ArrayList<DiaryCover>()
            var anyFailed = false
            for (uri in toImport) {
                val name = diaryImages.import(uri)
                if (name == null) {
                    anyFailed = true
                } else {
                    importedThisTime += name
                    added += DiaryCover.local(name)
                }
            }
            _importing.value = false
            if (anyFailed) {
                _message.value = Event(R.string.diary_image_failed)
            } else if (truncated) {
                _message.value = Event(R.string.diary_photos_limit_reached)
            }
            if (added.isNotEmpty()) {
                _attachments.value = _attachments.value.orEmpty() + added
            }
        }
    }

    /** Appends a still of the movie (a TMDB path) to the page's attachments, up to [DiaryText.MAX_PHOTOS]. */
    fun setStillAsCover(path: String) {
        val current = _attachments.value.orEmpty()
        if (current.size >= DiaryText.MAX_PHOTOS) {
            _message.value = Event(R.string.diary_photos_limit_reached)
            return
        }
        val cover = DiaryCover.tmdb(path)
        if (cover !in current) {
            _attachments.value = current + cover
        }
    }

    /** Removes a single attachment at [index] without affecting the other attachments. */
    fun removeAttachment(index: Int) {
        val current = _attachments.value.orEmpty()
        if (index !in current.indices) return
        _attachments.value = current.toMutableList().apply { removeAt(index) }
    }

    /** Asks TMDB for the movie's official stills (once; the picker calls this when it opens). */
    fun loadStills(retry: Boolean = false) {
        if (!retry && _stills.value is StillsState.Ready) return
        _stills.value = StillsState.Loading
        viewModelScope.launch {
            repository.movieStills(movieId)
                .onSuccess { _stills.value = StillsState.Ready(it) }
                .onFailure { _stills.value = StillsState.Error }
        }
    }

    // ---------- Saving ----------

    private fun snapshot(): String = listOf(
        headline.trim(), bodyHtml, DiaryCover.encodeList(_attachments.value.orEmpty()).orEmpty(),
        (_font.value ?: DiaryFont.DEFAULT).name, (_size.value ?: DiaryTextSize.DEFAULT).name,
        (_align.value ?: TextAlign.DEFAULT).name, (_theme.value ?: DiaryTheme.DEFAULT).name
    ).joinToString("\u0000")

    private fun isBlank() = DiaryText.isBlank(headline, bodyHtml, DiaryCover.encodeList(_attachments.value.orEmpty()))

    /** The page has nothing on it (no headline, no words, no cover): there is nothing to save. */
    val isBlankPage: Boolean get() = _state.value is EditorState.Ready && isBlank()

    /** A page that was saved before and now has nothing on it: it is not saved empty (that would erase it); the screen offers to delete it. */
    val isEmptiedPage: Boolean get() = _state.value is EditorState.Ready && entryId != 0L && isBlank()

    /**
     * Saves the page when something changed ([onDone] is called afterwards, also when there was nothing to save).
     * A page with nothing on it is not saved (a NEW one is simply dropped).
     */
    fun saveIfNeeded(onDone: () -> Unit = {}) {
        if (_state.value !is EditorState.Ready) {
            onDone()
            return
        }
        viewModelScope.launch {
            saveLock.withLock { saveNow() }
            onDone()
        }
    }

    private suspend fun saveNow() {
        val current = snapshot()
        if (current == savedSnapshot) return
        if (isBlank()) {
            if (entryId == 0L) dropImportedPictures() // a new page with nothing on it
            return
        }

        val attachments = _attachments.value.orEmpty()
        val entry = DiaryEntry(
            id = entryId,
            movieId = movieId,
            movieTitle = movieTitle,
            posterPath = posterPath,
            createdAt = createdAt,
            ratingSnapshot = repository.savedMovie(movieId)?.userRating, // the rating as it is when the page is written
            headline = headline.trim(),
            bodyHtml = bodyHtml,
            coverImageUri = DiaryCover.encodeList(attachments),
            fontStyle = (_font.value ?: DiaryFont.DEFAULT).name,
            textAlignment = (_align.value ?: TextAlign.DEFAULT).name,
            textSize = (_size.value ?: DiaryTextSize.DEFAULT).name,
            canvasTheme = (_theme.value ?: DiaryTheme.DEFAULT).name
        )
        entryId = repository.saveDiaryEntry(entry)
        savedSnapshot = current

        // Any gallery pictures this page does not use any more are deleted
        val used = localNames(attachments)
        diaryImages.delete((loadedLocalCovers + importedThisTime) - used)
        loadedLocalCovers = used
        importedThisTime.clear()
    }

    /** Deletes this page (the screen asked first) and its attached local pictures. */
    fun deletePage(onDone: () -> Unit) {
        viewModelScope.launch {
            saveLock.withLock {
                if (entryId != 0L) repository.deleteDiaryEntry(entryId)
                diaryImages.delete(loadedLocalCovers + importedThisTime)
                loadedLocalCovers = emptySet()
                importedThisTime.clear()
                entryId = 0L
            }
            onDone()
        }
    }

    private suspend fun dropImportedPictures() {
        diaryImages.delete(importedThisTime)
        importedThisTime.clear()
    }

    private fun localNames(covers: List<DiaryCover>): Set<String> =
        covers.filter { it.source == ImageSource.LOCAL }.map { it.ref }.toSet()
}
