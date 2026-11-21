package com.example.cinefiles.util

/**
 * Wraps something that should be handled ONCE, for example "show the achievement banner".
 * LiveData hands its last value to a screen again after a rotation; wrapping the value in an Event
 * and asking [getIfNotHandled] makes sure the banner is not shown a second time.
 */
class Event<out T>(private val content: T) {
    private var handled = false

    /** The content the first time you ask, and null every time after. */
    fun getIfNotHandled(): T? {
        if (handled) return null
        handled = true
        return content
    }
}
