package com.example.cinefiles.data

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import com.example.cinefiles.R
import com.example.cinefiles.logic.cleanDisplayName
import com.example.cinefiles.logic.initialOf

/** The user's profile photo: where the file is, and [version], which changes every time a new photo is set (so a picture is never shown stale). */
data class Avatar(val path: String, val version: Long)

/** What an avatar shows: the user's [photo], or the first [letter] of their name when there is none. */
data class AvatarLook(val letter: String, val photo: Avatar?)

/**
 * The user's own settings: the display name ("Movie Lover" until the user edits it) and the profile photo (none until the
 * user picks one; the picture itself is the file data/AvatarStore.kt keeps). They are kept in the phone's small key-value storage
 * (SharedPreferences), because they are a short text and a file path and have nothing to do with movies.
 *
 * There is ONE of these for the whole app (CineFilesApp.userSettings). Home shows the name and the photo and the Profile screen edits
 * them; both watch [name] and [avatar], so a change shows everywhere at once.
 */
class UserSettings(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    private val defaultName = appContext.getString(R.string.default_user_name)

    private val _name = MutableLiveData(prefs.getString(KEY_NAME, null) ?: defaultName)

    /** The name to show. Never empty. */
    val name: LiveData<String> = _name

    private val _avatar = MutableLiveData(readAvatar())

    /** The profile photo, or null when the user has not picked one (the screens then show the first letter of the name). */
    val avatar: LiveData<Avatar?> = _avatar

    /** The name's first letter and the photo together, for every place that draws the avatar (Home and Profile). */
    val avatarLook: LiveData<AvatarLook> = MediatorLiveData<AvatarLook>().apply {
        fun update() {
            value = AvatarLook(initialOf(_name.value.orEmpty()), _avatar.value)
        }
        addSource(_name) { update() }
        addSource(_avatar) { update() }
    }

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

    /** Remembers that the profile photo is the file at [path] (just written). Call it from the main thread. */
    fun setAvatar(path: String) {
        val avatar = Avatar(path, System.currentTimeMillis())
        prefs.edit().putString(KEY_AVATAR_PATH, avatar.path).putLong(KEY_AVATAR_VERSION, avatar.version).apply()
        _avatar.value = avatar
    }

    /** Forgets the profile photo (the file is deleted by AvatarStore). Call it from the main thread. */
    fun removeAvatar() {
        prefs.edit().remove(KEY_AVATAR_PATH).remove(KEY_AVATAR_VERSION).apply()
        _avatar.value = null
    }

    /** Forgets everything: the name goes back to the default and the photo is gone. (Clear my data uses this.) Call it from the main thread. */
    fun clear() {
        prefs.edit().clear().apply()
        _name.value = defaultName
        _avatar.value = null
    }

    private fun readAvatar(): Avatar? {
        val path = prefs.getString(KEY_AVATAR_PATH, null) ?: return null
        return Avatar(path, prefs.getLong(KEY_AVATAR_VERSION, 0L))
    }

    private companion object {
        const val FILE_NAME = "user_settings"
        const val KEY_NAME = "name"
        const val KEY_AVATAR_PATH = "avatar_path"
        const val KEY_AVATAR_VERSION = "avatar_version"
    }
}
