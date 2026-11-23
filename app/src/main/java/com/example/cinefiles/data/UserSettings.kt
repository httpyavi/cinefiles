package com.example.cinefiles.data

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.cinefiles.R
import com.example.cinefiles.logic.cleanDisplayName

/**
 * The user's own settings. For now that is one thing: the display name ("Movie Lover" until the user edits it).
 * It is kept in the phone's small key-value storage (SharedPreferences), because it is one short text and has
 * nothing to do with movies.
 *
 * There is ONE of these for the whole app (CineFilesApp.userSettings). Home shows the name in its greeting and the
 * Profile screen edits it; both watch [name], so a change shows everywhere at once.
 */
class UserSettings(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    private val defaultName = appContext.getString(R.string.default_user_name)

    private val _name = MutableLiveData(prefs.getString(KEY_NAME, null) ?: defaultName)

    /** The name to show. Never empty. */
    val name: LiveData<String> = _name

    /**
     * Saves a new name (see [cleanDisplayName] for what is accepted). Returns false, and changes nothing, when the
     * text is blank. Call it from the main thread.
     */
    fun rename(typed: String): Boolean {
        val clean = cleanDisplayName(typed, appContext.resources.getInteger(R.integer.name_max_length)) ?: return false
        prefs.edit().putString(KEY_NAME, clean).apply()
        _name.value = clean
        return true
    }

    /** Forgets everything: the name goes back to the default. (Clear my data uses this.) Call it from the main thread. */
    fun clear() {
        prefs.edit().clear().apply()
        _name.value = defaultName
    }

    private companion object {
        const val FILE_NAME = "user_settings"
        const val KEY_NAME = "name"
    }
}
