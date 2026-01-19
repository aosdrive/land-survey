package pk.gop.pulse.katchiAbadi

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import com.jakewharton.threetenabp.AndroidThreeTen
import pk.gop.pulse.katchiAbadi.common.Constants
import dagger.hilt.android.HiltAndroidApp
import pk.gop.pulse.katchiAbadi.common.Utility

@HiltAndroidApp
class MyApplication : Application() {
    private external fun getApiBaseUrlPublic(): String
    private external fun getApiLogin(): String
    private external fun getApiLoginSur(): String
    private external fun getApiForgotPassword(): String
    private external fun getApiOtpVerification(): String
    private external fun getApiUpdatePassword(): String
    private external fun getApiMauzaAssigned(): String
    private external fun getApiSyncMauzaInfo(): String
    private external fun getApiSyncData(): String
    private external fun getApiPostSurveyData(): String
    private external fun getApiPostSurveyDataRevisit(): String
    private external fun getApiPostSurveyDataRetakePictures(): String
    private external fun getApiCheckVersion(): String // Add this




    companion object {
        init {
            System.loadLibrary("native-lib")
        }

        // ADD THIS HELPER METHOD
        fun updateTheme(context: Context, themeMode: Int) {
            try {
                val sharedPreferences = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
                sharedPreferences.edit()
                    .putInt("theme_mode", themeMode)
                    .apply()

                AppCompatDelegate.setDefaultNightMode(themeMode)
                Log.d("MyApplication", "Theme updated to: $themeMode")
            } catch (e: Exception) {
                Log.e("MyApplication", "Error updating theme: ${e.message}")
            }
        }
    }



    override fun onCreate() {
        super.onCreate()

        // Apply saved theme on app startup
        applySavedTheme()

        Constants.VERSION_NAME = BuildConfig.VERSION_NAME

        Constants.BASE_URL = getApiBaseUrlPublic()

        Constants.LOGIN_URL = getApiLogin()
        Constants.LOGIN_URL_SUR = getApiLoginSur()
        Constants.FORGOT_PASSWORD_URL = getApiForgotPassword()
        Constants.OTP_VERIFICATION_URL = getApiOtpVerification()
        Constants.UPDATE_PASSWORD_URL = getApiUpdatePassword()
        Constants.Mauza_Assigned_URL = getApiMauzaAssigned()
        Constants.Sync_Mauza_Info_URL = getApiSyncMauzaInfo()
        Constants.SYNC_DATA_URL = getApiSyncData()
        Constants.POST_SURVEY_DATA_URL = getApiPostSurveyData()
        Constants.POST_SURVEY_DATA_REVISIT_URL = getApiPostSurveyDataRevisit()
        Constants.POST_SURVEY_DATA_RETAKE_PICTURE_URL = getApiPostSurveyDataRetakePictures()
        Constants.CHECK_VERSION_URL = getApiCheckVersion() // Add this


        AndroidThreeTen.init(this)

    }


    private fun applySavedTheme() {
        try {
            val sharedPreferences = getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
            val savedTheme = sharedPreferences.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            AppCompatDelegate.setDefaultNightMode(savedTheme)
            Log.d("MyApplication", "Applied theme mode: $savedTheme")
        } catch (e: Exception) {
            Log.e("MyApplication", "Error applying theme: ${e.message}")
            // Fallback to system default if there's an error
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }
}