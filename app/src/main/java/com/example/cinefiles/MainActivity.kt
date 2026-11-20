package com.example.cinefiles

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.cinefiles.databinding.ActivityMainBinding

/**
 * The only Activity in the app. It just hosts the Navigation Component, which shows one
 * Fragment (screen) at a time. See res/navigation/nav_graph.xml.
 */
class MainActivity : AppCompatActivity() {

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

        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }
}
