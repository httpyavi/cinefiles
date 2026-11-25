package com.example.cinefiles.ui.person

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.cinefiles.CineFilesApp
import com.example.cinefiles.data.ApiError
import com.example.cinefiles.data.friendlyError
import com.example.cinefiles.util.ARG_PERSON_ID
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Holds what the Person screen shows for ONE person (its id arrives as the navigation argument "personId").
 * TMDB's answer and the tab the user chose are put together by buildPersonState (ui/person/PersonState.kt).
 * Nothing here is saved: the screen asks TMDB each time it opens.
 */
class PersonViewModel(app: Application, handle: SavedStateHandle) : AndroidViewModel(app) {

    private val repository = (app as CineFilesApp).movieRepository
    private val personId: Int = handle.get<Int>(ARG_PERSON_ID) ?: 0

    private val remote = MutableStateFlow(PersonRemote())
    private val chosenTab = MutableStateFlow<PersonTab?>(null)
    private var loadJob: Job? = null

    /** The state to draw. It changes when TMDB answers or the user taps a tab. */
    val state: LiveData<PersonState> = combine(remote, chosenTab) { remote, tab ->
        buildPersonState(remote, tab)
    }.asLiveData()

    init {
        load()
    }

    /** Asks TMDB for the person and their movies. The Retry button calls this too. */
    fun load() {
        if (personId == 0) {
            remote.value = PersonRemote(error = ApiError.UNKNOWN, isLoading = false) // no person was given
            return
        }

        loadJob?.cancel()
        val before = remote.value
        remote.value = before.copy(isLoading = true, error = null)

        loadJob = viewModelScope.launch {
            val result = repository.person(personId)
            remote.value = PersonRemote(
                details = result.getOrNull() ?: before.details,
                error = result.exceptionOrNull()?.let { friendlyError(it) },
                isLoading = false
            )
        }
    }

    fun selectTab(tab: PersonTab) {
        chosenTab.value = tab
    }
}
