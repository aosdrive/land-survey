package pk.gop.pulse.katchiAbadi.presentation.base

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import pk.gop.pulse.katchiAbadi.common.Constants
import pk.gop.pulse.katchiAbadi.ui.activities.AuthActivity
import pk.gop.pulse.katchiAbadi.ui.fragments.auth.LoginFragment

open class BaseActivity : AppCompatActivity() {

    private var logoutDialog: AlertDialog? = null

    private val forceLogoutReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "ACTION_FORCE_LOGOUT") {
                val message = intent.getStringExtra("message") ?: "Update required"
                val currentVersion = intent.getStringExtra("currentVersion")
                val minVersion = intent.getStringExtra("minRequiredVersion")

                showForceLogoutDialog(message, currentVersion, minVersion)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Register broadcast receiver
        LocalBroadcastManager.getInstance(this).registerReceiver(
            forceLogoutReceiver,
            IntentFilter("ACTION_FORCE_LOGOUT")
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        logoutDialog?.dismiss()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(forceLogoutReceiver)
    }

    private fun showForceLogoutDialog(message: String, currentVersion: String?, minVersion: String?) {
        // Don't show multiple dialogs
        if (logoutDialog?.isShowing == true) return

        val dialogMessage = buildString {
            append(message)
            if (currentVersion != null && minVersion != null) {
                append("\n\n")
                append("Your version: $currentVersion")
                append("\n")
                append("Required version: $minVersion")
            }
        }

        logoutDialog = AlertDialog.Builder(this)
            .setTitle("Update Required")
            .setMessage(dialogMessage)
            .setCancelable(false)
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
                clearSessionAndNavigateToLogin()
            }
            .create()

        logoutDialog?.show()
    }

    private fun clearSessionAndNavigateToLogin() {
        // Clear SharedPreferences
        getSharedPreferences(Constants.SHARED_PREF_NAME, MODE_PRIVATE)
            .edit()
            .clear()
            .apply()

        // Navigate to Login
        val intent = Intent(this, AuthActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}