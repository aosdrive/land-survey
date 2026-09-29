package pk.gop.pulse.katchiAbadi.common

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.appcompat.app.AlertDialog
import pk.gop.pulse.katchiAbadi.presentation.util.ZdAlert
import pk.gop.pulse.katchiAbadi.ui.fragments.auth.LoginFragment

object VersionUpdateHandler {

    fun handleVersionUpdateRequired(
        context: Context,
        sharedPreferences: SharedPreferences,
        message: String = "You are using an outdated version. Please contact admin to get the latest app."
    ) {
        // Clear user session
        sharedPreferences.edit().clear().apply()

        // Show dialog
        ZdAlert.error(
            context = context,
            title = "Update Required",
            message = "$message\n\nYou have been logged out for security reasons.",
            buttonText = "OK",
            onDismiss = {
                // Navigate to login screen
                val intent = Intent(context, LoginFragment::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                context.startActivity(intent)

                // Close current activity
                if (context is android.app.Activity) {
                    context.finishAffinity()
                }
            }
        )
    }

    fun isVersionUpdateError(errorMessage: String?): Boolean {
        if (errorMessage == null) return false

        return errorMessage.contains("outdated version", ignoreCase = true) ||
                errorMessage.contains("update required", ignoreCase = true) ||
                errorMessage.contains("isUpdateRequired", ignoreCase = true) ||
                errorMessage.contains("shouldLogout", ignoreCase = true) ||
                errorMessage.contains("App version header", ignoreCase = true) ||
                errorMessage.startsWith("APP_VERSION_OUTDATED:")
    }
}