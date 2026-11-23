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
