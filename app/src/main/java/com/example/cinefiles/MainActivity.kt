package com.example.cinefiles

import android.graphics.Color
import android.os.Bundle
import android.view.animation.DecelerateInterpolator
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentContainerView
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.cinefiles.databinding.ActivityMainBinding
import com.example.cinefiles.util.openTab

/**
 * The only Activity in the app. It hosts the Navigation Component (one Fragment = one screen)
 * and owns the bottom tab bar.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    // Is the tab bar supposed to be on screen right now?
    private var isBarShown = true

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must come first: it swaps the splash theme for the normal theme.
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // Draw behind the status and navigation bars. The app is dark only,
        // so the bars always get light icons.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHost = supportFragmentManager.findFragmentById(R.id.navHost) as NavHostFragment
        navController = navHost.navController

        setUpTabBar()
    }

    private fun setUpTabBar() {
        // This keeps the highlighted tab in sync with the screen that is showing
        // (also when a screen switches tabs by itself, like the search icon on Home).
        binding.bottomNav.setupWithNavController(navController)

        // ...but WE decide how a tap on a tab navigates (see openTab): fade-through animation,
        // and every tab keeps its own state.
        binding.bottomNav.setOnItemSelectedListener { item ->
            navController.openTab(item.itemId)
            true
        }

        // Show the bar on the four tab screens, slide it away on every other screen.
        var isFirstScreen = true
        navController.addOnDestinationChangedListener { _, destination, _ ->
            showBar(destination.id in TAB_SCREEN_IDS, animate = !isFirstScreen)
            isFirstScreen = false
        }
    }

    /** Slides the tab bar up (show) or down (hide). With animate = false it jumps straight there. */
    private fun showBar(show: Boolean, animate: Boolean) {
        val bar = binding.bottomNav
        isBarShown = show
        bar.animate().cancel()

        if (!animate) {
            bar.translationY = 0f
            bar.isVisible = show
            return
        }

        if (show) {
            if (bar.isGone) {
                bar.translationY = bar.height.toFloat() // start below the screen edge
                bar.isVisible = true
            }
            bar.animate()
                .translationY(0f)
                .setDuration(BAR_SLIDE_MS)
                .setInterpolator(DecelerateInterpolator())
                .start()
        } else {
            bar.animate()
                .translationY(bar.height.toFloat())
                .setDuration(BAR_SLIDE_MS)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction {
                    // Only really remove it if nobody asked to show it again in the meantime
                    if (!isBarShown) bar.isGone = true
                }
                .start()
        }
    }

    private companion object {
        const val BAR_SLIDE_MS = 220L

        // The screens that are tabs. Every other screen hides the bar.
        val TAB_SCREEN_IDS = setOf(
            R.id.homeFragment,
            R.id.discoverFragment,
            R.id.collectionFragment,
            R.id.profileFragment
        )
    }
}
