package com.example.cinefiles.util

import android.content.Context
import android.provider.Settings

/**
 * Are the phone's animations on? A person who turned them off ("Remove animations", or "Animator duration scale: off"
 * in the developer options) should not be made to wait for a decorative moment, so screens skip theirs.
 */
fun Context.animationsEnabled(): Boolean =
    Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
