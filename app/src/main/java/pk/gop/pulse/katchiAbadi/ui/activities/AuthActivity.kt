package pk.gop.pulse.katchiAbadi.ui.activities

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import pk.gop.pulse.katchiAbadi.common.BaseClass
import pk.gop.pulse.katchiAbadi.databinding.ActivityAuthBinding
import pk.gop.pulse.katchiAbadi.presentation.splash.SplashViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import pk.gop.pulse.katchiAbadi.R

@AndroidEntryPoint
class AuthActivity : BaseClass() {

    private lateinit var binding: ActivityAuthBinding
    private val viewModel: SplashViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {

        installSplashScreen().apply {
            setKeepOnScreenCondition {
                viewModel.isLoading.value
            }
        }
        setTheme(R.style.Theme_Survey_NoActionBar)
        super.onCreate(savedInstanceState)

        binding = ActivityAuthBinding.inflate(layoutInflater)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        setContentView(binding.root)
        supportActionBar?.hide()
        hideStatusBar()
        // Set ActionBar title to uppercase
//        supportActionBar?.title = supportActionBar?.title?.toString()?.uppercase()

        lifecycleScope.launch {
            viewModel.navigate.collect { value ->
                when (value) {
                    true -> {
                        Intent(this@AuthActivity, MenuActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            startActivity(this)
                            finish()
                        }
                    }

                    else -> Unit
                }
            }
        }
    }

    private fun hideStatusBar() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.statusBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}