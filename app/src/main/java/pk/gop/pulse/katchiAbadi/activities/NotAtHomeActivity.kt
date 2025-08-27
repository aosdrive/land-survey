package pk.gop.pulse.katchiAbadi.activities

import android.Manifest
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import pk.gop.pulse.katchiAbadi.databinding.ActivitySurveyFormBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import pk.gop.pulse.katchiAbadi.R
import pk.gop.pulse.katchiAbadi.common.Utility
import pk.gop.pulse.katchiAbadi.data.local.AppDatabase
import pk.gop.pulse.katchiAbadi.databinding.ActivityNotAtHomeBinding
import pk.gop.pulse.katchiAbadi.presentation.form.SharedFormViewModel
import pk.gop.pulse.katchiAbadi.presentation.not_at_home.SharedNAHViewModel
import javax.inject.Inject

@AndroidEntryPoint
class NotAtHomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNotAtHomeBinding

    private val viewModel: SharedNAHViewModel by viewModels()

    private var lm: LocationManager? = null
    private var ls: LocationListener? = null

    private lateinit var context: Context


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNotAtHomeBinding.inflate(layoutInflater)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        setContentView(binding.root)
        // Set ActionBar title to uppercase
        supportActionBar?.title = supportActionBar?.title?.toString()?.uppercase()
        context = this@NotAtHomeActivity
    }

    override fun onPause() {
        super.onPause()
        ls?.let { lm?.removeUpdates(it) }
    }

    override fun onResume() {
        super.onResume()
        getCurrentLocation()
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
                                this@NotAtHomeActivity
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
                                this@NotAtHomeActivity
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
            Toast.makeText(context, "Current Location Exception :${e.message}", Toast.LENGTH_SHORT)
                .show()
        }
    }
}