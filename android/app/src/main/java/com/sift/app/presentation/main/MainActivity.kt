package com.sift.app.presentation.main

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.sift.app.R
import com.sift.app.SiftApplication
import com.sift.app.databinding.ActivityMainBinding
import com.sift.app.presentation.home.HomeFragment
import com.sift.app.presentation.intro.IntroFragment
import com.sift.app.presentation.navigation.Navigator
import com.sift.app.presentation.navigation.NavigatorHost
import com.sift.app.presentation.navigation.Transition

/**
 * The single activity. Shows the system splash screen, draws edge to edge and
 * hosts every screen as a Fragment managed by [Navigator].
 */
class MainActivity : AppCompatActivity(), NavigatorHost {

    override lateinit var navigator: Navigator
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        navigator = Navigator(this, supportFragmentManager, R.id.fragmentContainer)

        // After process death the FragmentManager restores the back stack itself.
        if (savedInstanceState == null) {
            val preferences = (application as SiftApplication).container.preferences
            val start = if (preferences.isIntroCompleted()) HomeFragment.newInstance() else IntroFragment.newInstance()
            navigator.setRoot(start, Transition.FADE_THROUGH)
        }
    }
}
