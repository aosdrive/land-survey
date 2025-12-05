package pk.gop.pulse.katchiAbadi.ui.activities

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import pk.gop.pulse.katchiAbadi.databinding.ActivityPrivacyPolicyBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class PrivacyPolicyActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPrivacyPolicyBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityPrivacyPolicyBinding.inflate(layoutInflater)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        setContentView(binding.root)
        // Set ActionBar title to uppercase
        supportActionBar?.title = supportActionBar?.title?.toString()?.uppercase()

        binding.apply {
            val webSettings = webView.settings
            webSettings.javaScriptEnabled = true

            webView.loadUrl("file:///android_asset/privacy_policy.html")
        }
    }
}