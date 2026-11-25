package com.example.cinefiles.util

import android.view.LayoutInflater
import android.view.View
import com.example.cinefiles.R
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

/**
 * Puts one more selectable chip (glass, with a red edge when selected) into this group and returns it.
 * The chip gets its own view id, so the group can tell which one is checked. The Preferences screen and the Discover
 * tab build all their chips this way.
 */
fun ChipGroup.addChoiceChip(label: CharSequence): Chip {
    val chip = LayoutInflater.from(context).inflate(R.layout.item_choice_chip, this, false) as Chip
    chip.id = View.generateViewId()
    chip.text = label
    addView(chip)
    return chip
}

/**
 * Puts one more tappable name into this group (a writer, a producer...): the same glass pill, but it is a button, not a
 * choice. Tapping it calls [onClick], which opens the Person screen. The Crew section of Movie Details builds its chips this way.
 */
fun ChipGroup.addPersonChip(label: CharSequence, onClick: () -> Unit): Chip {
    val chip = LayoutInflater.from(context).inflate(R.layout.item_person_chip, this, false) as Chip
    chip.text = label
    chip.contentDescription = context.getString(R.string.detail_open_person, label)
    chip.setOnClickListener { onClick() }
    addView(chip)
    return chip
}
