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
import com.example.cinefiles.logic.DiaryBlock
import com.example.cinefiles.logic.DiaryBlocks
import com.example.cinefiles.logic.DiaryFont
import com.example.cinefiles.logic.DiaryTheme
import com.example.cinefiles.logic.ImageSource
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

    /** The page is ready (a new page starts with one empty paragraph run). */
    data class Ready(
        val movieTitle: String,
        val ratingSnapshot: Float?,
        val date: Long
    ) : EditorState()
}

/** The movie stills the picture picker can offer. */
sealed class StillsState {
    object Loading : StillsState()
    object Error : StillsState()
    data class Ready(val paths: List<String>) : StillsState()
}

/**
 * Holds one diary page while it is edited (the navigation arguments: "entryId" of a saved page, or 0 and a "movieId" for a
 * new one). It survives a screen turn, so nothing typed is lost.
 *
 * The block list is edited by DiaryBlockAdapter, which tells [onBlocksChanged] after every change (a letter typed, a block
 * moved...). The ViewModel does not push the list back, so typing never fights with the cursor. Saving is [saveIfNeeded]:
 * the editor saves when you leave it, when the app goes to the background and when you press Save. A NEW page with nothing
 * on it is never saved. The gallery pictures that the page no longer uses are deleted after a save.
 */
class DiaryEditorViewModel(app: Application, handle: SavedStateHandle) : AndroidViewModel(app) {

    private val repository = (app as CineFilesApp).movieRepository
    private val diaryImages = (app as CineFilesApp).diaryImages

    private var entryId: Long = handle.get<Long>(ARG_DIARY_ENTRY_ID) ?: 0L
    private var movieId: Int = handle.get<Int>(ARG_MOVIE_ID) ?: 0
    private var movieTitle = ""
    private var posterPath: String? = null
    private var createdAt = 0L

    /** The blocks as the adapter last reported them. */
    private var blocks: List<DiaryBlock> = emptyList()

    private val _state = MutableLiveData<EditorState>(EditorState.Loading)
    val state: LiveData<EditorState> = _state

    private val _font = MutableLiveData(DiaryFont.SANS)
    val font: LiveData<DiaryFont> = _font

    private val _theme = MutableLiveData(DiaryTheme.MIDNIGHT)
    val theme: LiveData<DiaryTheme> = _theme

    private val _newBlock = MutableLiveData<Event<DiaryBlock>>()

    /** A block the user just made with a picture (from the phone or a movie still): the screen adds it to the page. */
    val newBlock: LiveData<Event<DiaryBlock>> = _newBlock

    private val _message = MutableLiveData<Event<Int>>()

    /** A line to show once (a string resource). */
    val message: LiveData<Event<Int>> = _message

    private val _importing = MutableLiveData(false)

    /** True while a gallery picture is being copied into the app. */
    val importing: LiveData<Boolean> = _importing

    private val _stills = MutableLiveData<StillsState>()
    val stills: LiveData<StillsState> = _stills

    private var savedSnapshot = ""
    private var loadedImages: Set<String> = emptySet()      // gallery pictures the saved page uses
    private val importedThisTime = HashSet<String>()         // gallery pictures copied while editing, not saved yet
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
                blocks = DiaryBlocks.fromJson(entry.blocksJson)
                _font.value = DiaryFont.of(entry.fontStyle)
                _theme.value = DiaryTheme.of(entry.backgroundTheme)
                savedSnapshot = snapshot()
                loadedImages = DiaryBlocks.localImages(blocks)
                _state.value = EditorState.Ready(movieTitle, entry.ratingSnapshot, entry.updatedAt)
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
                blocks = listOf(DiaryBlocks.newText(html = true)) // a fresh page starts with one empty paragraph run
                savedSnapshot = snapshot()
                _state.value = EditorState.Ready(movieTitle, repository.savedMovie(movieId)?.userRating, System.currentTimeMillis())
            }
        }
    }

    // ---------- Editing ----------

    /** The adapter reports the blocks after every change. */
    fun onBlocksChanged(list: List<DiaryBlock>) {
        blocks = list
    }

    /** The page's blocks as they are now (what a new screen after a screen turn starts from). */
    fun currentBlocks(): List<DiaryBlock> = blocks

    fun setFont(font: DiaryFont) {
        if (_font.value != font) _font.value = font
    }

    fun setTheme(theme: DiaryTheme) {
        if (_theme.value != theme) _theme.value = theme
    }

    /** Copies a picture from the phone's gallery into the app, then offers it to the page as a new block. */
    fun importImage(uri: Uri) {
        viewModelScope.launch {
            _importing.value = true
            val name = diaryImages.import(uri)
            _importing.value = false
            if (name == null) {
                _message.value = Event(R.string.diary_image_failed)
            } else {
                importedThisTime += name
                _newBlock.value = Event(DiaryBlocks.newImage(ImageSource.LOCAL, name))
            }
        }
    }

    /** Offers a still of the movie (a TMDB path) to the page as a new block. */
    fun addStill(path: String) {
        _newBlock.value = Event(DiaryBlocks.newImage(ImageSource.TMDB, path))
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

    private fun snapshot(): String =
        DiaryBlocks.toJson(DiaryBlocks.withoutBlank(blocks)) + "|" + (_font.value ?: DiaryFont.SANS).name + "|" + (_theme.value ?: DiaryTheme.MIDNIGHT).name

    /**
     * Saves the page when something changed ([onDone] is called afterwards, also when there was nothing to save).
     * Empty text cells are left out. A NEW page with nothing on it is not saved at all.
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
        val toSave = DiaryBlocks.withoutBlank(blocks)
        if (entryId == 0L && toSave.isEmpty()) return // a new page with nothing on it

        val entry = DiaryEntry(
            id = entryId,
            movieId = movieId,
            movieTitle = movieTitle,
            posterPath = posterPath,
            createdAt = createdAt,
            ratingSnapshot = repository.savedMovie(movieId)?.userRating, // the rating as it is when the page is written
            fontStyle = (_font.value ?: DiaryFont.SANS).name,
            backgroundTheme = (_theme.value ?: DiaryTheme.MIDNIGHT).name,
            blocksJson = DiaryBlocks.toJson(toSave)
        )
        entryId = repository.saveDiaryEntry(entry)
        savedSnapshot = current

        // The gallery pictures this page does not use any more (deleted blocks, or pictures added and removed again)
        val used = DiaryBlocks.localImages(toSave)
        diaryImages.delete((loadedImages + importedThisTime) - used)
        loadedImages = used
        importedThisTime.clear()
    }
}
