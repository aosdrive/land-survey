package pk.gop.pulse.katchiAbadi.di

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.gson.Gson
import okhttp3.Interceptor
import okhttp3.Response
import pk.gop.pulse.katchiAbadi.BuildConfig
import pk.gop.pulse.katchiAbadi.data.remote.response.VersionCheckResponse

class VersionCheckInterceptor(
    private val context: Context,
    private val sharedPreferences: SharedPreferences
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // Add App-Version header to all requests
        val requestWithVersion = originalRequest.newBuilder()
            .addHeader("App-Version", BuildConfig.VERSION_NAME)
            .build()

        val response = chain.proceed(requestWithVersion)

        // Check if response is 401 Unauthorized
        if (response.code == 401) {
            try {
                val errorBody = response.peekBody(Long.MAX_VALUE).string()
                val errorResponse = Gson().fromJson(errorBody, VersionCheckResponse::class.java)

                // Check if this is a version-related logout
                if (errorResponse?.shouldLogout == true && errorResponse.isUpdateRequired == true) {
                    // Broadcast force logout event
                    val intent = Intent("ACTION_FORCE_LOGOUT")
                    intent.putExtra("message", errorResponse.message ?: "Update required")
                    intent.putExtra("currentVersion", errorResponse.currentVersion)
                    intent.putExtra("minRequiredVersion", errorResponse.minRequiredVersion)
                    LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
                }
            } catch (e: Exception) {
                // If parsing fails, continue with normal response
                e.printStackTrace()
            }
        }

        return response
    }
}