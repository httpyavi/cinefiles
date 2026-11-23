package com.example.cinefiles.util

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import com.example.cinefiles.data.ApiError
import com.example.cinefiles.databinding.ViewStateEmptyBinding
import com.example.cinefiles.databinding.ViewStateErrorBinding

/*
 * The two shared state layouts (view_state_empty and view_state_error) are filled in the same way on every screen,
 * so the filling is written once, here. Showing or hiding the layout itself stays with the screen.
 */

/** The error state says what went wrong: offline, TMDB key missing... (the wording is in data/ApiError.kt). */
fun ViewStateErrorBinding.show(error: ApiError) {
    errorTitle.setText(error.title)
    errorMessage.setText(error.message)
}

/** A button of the empty state: its text and what it does. */
class EmptyAction(@StringRes val label: Int, val onClick: () -> Unit)

/**
 * Fills the empty state: a round icon, a title, a message and up to two buttons. A button that is not given is hidden.
 * (A screen that shows the first button only sometimes can set `emptyAction.isVisible` afterwards.)
 */
fun ViewStateEmptyBinding.show(
    @DrawableRes icon: Int,
    @StringRes title: Int,
    @StringRes message: Int,
    action: EmptyAction? = null,
    secondaryAction: EmptyAction? = null
) {
    emptyIcon.setImageResource(icon)
    emptyTitle.setText(title)
    emptyMessage.setText(message)
    bindButton(emptyAction, action)
    bindButton(emptySecondaryAction, secondaryAction)
}

private fun bindButton(button: com.google.android.material.button.MaterialButton, action: EmptyAction?) {
    button.isVisible = action != null
    if (action != null) {
        button.setText(action.label)
        button.setOnClickListener { action.onClick() }
    }
}
