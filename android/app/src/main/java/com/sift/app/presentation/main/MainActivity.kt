package com.sift.app.presentation.main

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.animation.doOnEnd
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.splashscreen.SplashScreenViewProvider
import com.sift.app.R
import com.sift.app.SiftApplication
import com.sift.app.databinding.ActivityMainBinding
import com.sift.app.presentation.common.Motion
import com.sift.app.presentation.home.HomeFragment
import com.sift.app.presentation.navigation.Navigator
import com.sift.app.presentation.navigation.NavigatorHost
import com.sift.app.presentation.navigation.Transition
import com.sift.app.presentation.onboarding.SplashFragment

/**
 * The single activity. Shows the system splash screen (which zooms and fades out),
 * draws edge to edge and hosts every screen as a Fragment managed by [Navigator].
 * First launch starts the onboarding flow; later launches go straight Home.
 */
class MainActivity : AppCompatActivity(), NavigatorHost {

    override lateinit var navigator: Navigator
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        if (Motion.enabled(this)) splash.setOnExitAnimationListener(::animateSplashExit)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        navigator = Navigator(this, supportFragmentManager, R.id.fragmentContainer)

        // After process death or a language change the FragmentManager restores the back stack itself.
        if (savedInstanceState == null) {
            val preferences = (application as SiftApplication).container.preferences
            val start = if (preferences.isOnboardingCompleted()) HomeFragment.newInstance() else SplashFragment.newInstance()
            navigator.setRoot(start, Transition.FADE_THROUGH)
        }
    }

    /** The splash icon grows slightly while the whole splash fades away. */
    private fun animateSplashExit(provider: SplashScreenViewProvider) {
        val duration = Motion.duration(this, R.integer.motion_long)
        val icon = provider.iconView
        val root = provider.view
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(icon, View.SCALE_X, 1f, SPLASH_EXIT_SCALE),
                ObjectAnimator.ofFloat(icon, View.SCALE_Y, 1f, SPLASH_EXIT_SCALE),
                ObjectAnimator.ofFloat(root, View.ALPHA, 1f, 0f)
            )
            this.duration = duration
            interpolator = Motion.standard
            doOnEnd { provider.remove() }
            start()
        }
    }

    private companion object {
        const val SPLASH_EXIT_SCALE = 1.25f
    }
}
