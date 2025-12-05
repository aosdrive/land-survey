package pk.gop.pulse.katchiAbadi.ui.activities

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import dagger.hilt.android.AndroidEntryPoint
import pk.gop.pulse.katchiAbadi.common.Utility
import pk.gop.pulse.katchiAbadi.databinding.ActivitySurveyFormBinding
import pk.gop.pulse.katchiAbadi.presentation.form.SharedFormViewModel
import pk.gop.pulse.katchiAbadi.presentation.util.ToastUtil

@AndroidEntryPoint
class SurveyFormActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySurveyFormBinding
    private val viewModel: SharedFormViewModel by viewModels()

    private var lm: LocationManager? = null
    private var ls: LocationListener? = null
    private lateinit var context: Context

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySurveyFormBinding.inflate(layoutInflater)
//        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        setContentView(binding.root)
//        supportActionBar?.title = supportActionBar?.title?.toString()?.uppercase()
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setHomeButtonEnabled(true) // This line ensures the home button responds
            title = "MAP" // Or whatever custom title you want
        }


        context = this@SurveyFormActivity
    }

    override fun onPause() {
        super.onPause()
        ls?.let { lm?.removeUpdates(it) }
    }

    override fun onResume() {
        super.onResume()
        getCurrentLocation()
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                // Navigate back or finish the activity
//                onBackPressedDispatcher.onBackPressed()
                startActivity(Intent(this, MenuActivity::class.java))
                finish()

                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }


    private fun getCurrentLocation() {
        try {
            lm = context.getSystemService(LOCATION_SERVICE) as LocationManager
            ls = object : LocationListener {
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

                override fun onLocationChanged(location: Location) {
                    if (Build.VERSION.SDK_INT < 31) {
                        if (!location.isFromMockProvider) {
                            ls?.let { lm?.removeUpdates(it) }
                            viewModel.currentLocation = Utility.convertGpsTimeToString(location.time)
                        } else {
                            ls?.let { lm?.removeUpdates(it) }
                            Utility.exitApplication(
                                "Warning!",
                                "Please disable mock/fake location. The application will exit now.",
                                this@SurveyFormActivity
                            )
                        }
                    } else {
                        if (!location.isMock) {
                            ls?.let { lm?.removeUpdates(it) }
                            viewModel.currentLocation = Utility.convertGpsTimeToString(location.time)
                        } else {
                            ls?.let { lm?.removeUpdates(it) }
                            Utility.exitApplication(
                                "Warning!",
                                "Please disable mock/fake location. The application will exit now.",
                                this@SurveyFormActivity
                            )
                        }
                    }
                }
            }

            if (ActivityCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }

            ls?.let {
                lm?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1, 0f, it)
//                lm?.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 10000, 1f, it)
            }
        } catch (e: Exception) {
            ToastUtil.showShort(this, "Current Location Exception :${e.message}")
        }
    }
}
