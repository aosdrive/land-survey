package pk.gop.pulse.katchiAbadi.data.repository

import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import okhttp3.MultipartBody
import pk.gop.pulse.katchiAbadi.common.Constants
import pk.gop.pulse.katchiAbadi.common.Resource
import pk.gop.pulse.katchiAbadi.common.SimpleResource
import pk.gop.pulse.katchiAbadi.data.remote.ServerApi
import pk.gop.pulse.katchiAbadi.data.remote.request.ForgotPasswordRequest
import pk.gop.pulse.katchiAbadi.data.remote.request.LoginRequest
import pk.gop.pulse.katchiAbadi.data.remote.request.OtpVerificationRequest
import pk.gop.pulse.katchiAbadi.data.remote.request.UpdatePasswordRequest
import pk.gop.pulse.katchiAbadi.data.remote.response.ErrorResponse
import pk.gop.pulse.katchiAbadi.data.remote.response.ForgotPasswordDto
import pk.gop.pulse.katchiAbadi.data.remote.response.LoginDto
import pk.gop.pulse.katchiAbadi.data.remote.response.LoginSurveyorResponse
import pk.gop.pulse.katchiAbadi.data.remote.response.LogoutResponse
import pk.gop.pulse.katchiAbadi.data.remote.response.OtpVerificationDto
import pk.gop.pulse.katchiAbadi.data.remote.response.UpdatePasswordDto
import pk.gop.pulse.katchiAbadi.data.remote.response.VersionCheckResponse
import pk.gop.pulse.katchiAbadi.domain.repository.AuthRepository
import retrofit2.HttpException
import java.io.IOException
import java.util.concurrent.TimeoutException
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    private val api: ServerApi,
    private val sharedPreferences: SharedPreferences
) : AuthRepository {

    /**
     * Check app version against backend requirements
     */
    override suspend fun checkAppVersion(appVersion: String): Resource<VersionCheckResponse> {
        return try {
            val url = "${Constants.BASE_URL}${Constants.CHECK_VERSION_URL}"

            Log.d("AuthRepository", "Checking version: $appVersion at $url")

            val response = api.checkAppVersion(
                url = url,
                appVersion = appVersion
            )

            if (response.isSuccessful) {
                response.body()?.let { versionData ->
                    Log.d("AuthRepository", "Version check response: success=${versionData.success}, isUpdateRequired=${versionData.isUpdateRequired}")

                    if (versionData.isUpdateRequired) {
                        // Return error with data so we can access version info
                        Resource.Error(
                            message = versionData.message,
//                            data = versionData
                        )
                    } else {
                        Resource.Success(versionData)
                    }
                } ?: run {
                    Log.e("AuthRepository", "Version check body is null")
                    Resource.Error("Version check response is empty")
                }
            } else {
                val errorMessage = when (response.code()) {
                    400 -> "Invalid app version format"
                    401 -> "Unauthorized version check"
                    404 -> "Version check endpoint not found"
                    500 -> "Server error during version check"
                    else -> "Version check failed: ${response.message()}"
                }
                Log.e("AuthRepository", "Version check failed: $errorMessage (code: ${response.code()})")
                Resource.Error(errorMessage)
            }
        } catch (e: Exception) {
            Log.e("AuthRepository", "Version check exception: ${e.message}", e)
            when (e) {
                is HttpException -> {
                    val errorMessage = parseHttpError(e)
                    Resource.Error("Version check failed: $errorMessage")
                }
                is IOException -> {
                    Resource.Error("Connection error: Please check your internet connection")
                }
                is TimeoutException -> {
                    Resource.Error("Request timed out. Please try again.")
                }
                else -> {
                    Resource.Error(e.message ?: "Version check failed")
                }
            }
        }
    }

    override suspend fun login(cnic: String, password: String): Resource<LoginDto> {
        val body = LoginRequest(cnic, password)

        return try {
            val response = api.login(Constants.LOGIN_URL, json = body)
            if (response.code == 200) {
                response.data?.let {
                    if (it.mauzaId > 0) {
                        Resource.Success(it)
                    } else {
                        Resource.Error(message = "Territory is not assigned, contact administration")
                    }
                } ?: Resource.Error(message = "Response data is null")
            } else {
                response.message.let { msg ->
                    Resource.Error(msg)
                }
            }
        } catch (e: Exception) {
            Log.e("AuthRepository", "Login error: ${e.message}", e)
            return when (e) {
                is HttpException -> {
                    val errorMessage = parseHttpError(e)
                    Resource.Error(message = errorMessage)
                }

                is IOException -> {
                    Resource.Error(
                        message = "Try again! Couldn't reach the server.\n${e.message}."
                    )
                }

                is TimeoutException -> {
                    Resource.Error(
                        message = "Request timed out. Please try again later."
                    )
                }

                else -> {
                    Resource.Error(
                        message = "An unexpected error occurred: ${e.message}"
                    )
                }
            }
        }
    }

    override suspend fun loginSurveyor(
        cnic: String,
        password: String,
        appVersion: String?
    ): Resource<LoginSurveyorResponse> {
        val body = LoginRequest(cnic, password, appVersion = appVersion)

        return try {
            Log.d("AuthRepository", "Login attempt for CNIC: $cnic with version: $appVersion")

            val response = api.loginSurveyor(Constants.LOGIN_URL_SUR, body)

            Log.d("AuthRepository", "Login response received: $response")

            if (response.userId > 0) {
                Resource.Success(response)
            } else {
                val errorMessage = response.message ?: "Login failed"

                // Check if error is version-related
                if (errorMessage.contains("outdated version", ignoreCase = true) ||
                    errorMessage.contains("update required", ignoreCase = true) ||
                    errorMessage.contains("old version", ignoreCase = true) ||
                    errorMessage.contains("old mobile app", ignoreCase = true) ||
                    errorMessage.contains("contact admin", ignoreCase = true)) {
                    Resource.Error("APP_VERSION_OUTDATED: $errorMessage")
                } else {
                    Resource.Error(errorMessage)
                }
            }

        } catch (e: Exception) {
            Log.e("AuthRepository", "Surveyor login error: ${e.message}", e)
            when (e) {
                is HttpException -> {
                    val errorMessage = parseHttpError(e)

                    // Check if the HTTP error is version-related
                    if (errorMessage.contains("outdated version", ignoreCase = true) ||
                        errorMessage.contains("update required", ignoreCase = true) ||
                        errorMessage.contains("old version", ignoreCase = true)) {
                        Resource.Error("APP_VERSION_OUTDATED: $errorMessage")
                    } else {
                        Resource.Error(errorMessage)
                    }
                }

                is IOException -> Resource.Error("Try again! Couldn't reach the server.\n${e.message}.")
                is TimeoutException -> Resource.Error("Request timed out. Please try again later.")
                else -> Resource.Error("An unexpected error occurred: ${e.message}")
            }
        }
    }

    override suspend fun logoutUser(userId: Long, mode: String): LogoutResponse {
        val response = api.logoutUser(userId, mode)
        if (response.isSuccessful && response.body() != null) {
            return response.body()!!
        } else {
            throw HttpException(response)
        }
    }

    override fun authenticate(): SimpleResource {
        return try {
            if (getLoginStatus(sharedPreferences)) {
                Resource.Success(Unit)
            } else {
                Resource.Error(
                    message = "User not signed in"
                )
            }
        } catch (e: Exception) {
            Resource.Error(
                message = "Unknown error"
            )
        }
    }

    private fun getLoginStatus(sharedPreferences: SharedPreferences): Boolean {
        val sharedIdValue = sharedPreferences.getInt(
            Constants.SHARED_PREF_LOGIN_STATUS,
            Constants.LOGIN_STATUS_INACTIVE
        )
        return sharedIdValue == Constants.LOGIN_STATUS_ACTIVE
    }

    override suspend fun otpVerification(cnic: String, otp: Int): SimpleResource {
        val body = OtpVerificationRequest(cnic, otp)

        return try {
            val response = api.otpVerification(Constants.OTP_VERIFICATION_URL, cnic, otp.toString())
            if (response.code == 200) {
                Resource.Success(Unit)
            } else {
                response.message.let { msg ->
                    Resource.Error(msg)
                }
            }
        } catch (e: Exception) {
            return when (e) {
                is IOException -> {
                    Resource.Error(
                        message = "Try again! Couldn't reach the server.\n${e.message}."
                    )
                }

                is TimeoutException -> {
                    Resource.Error(
                        message = "Request timed out. Please try again later."
                    )
                }

                is HttpException -> {
                    Resource.Error(
                        message = "An HTTP error occurred. Status code: ${e.code()}"
                    )
                }

                else -> {
                    Resource.Error(
                        message = "An unexpected error occurred: ${e.message}"
                    )
                }
            }
        }
    }

    override suspend fun forgotPassword(cnic: String): SimpleResource {
        return try {
            val response = api.forgotPassword(Constants.FORGOT_PASSWORD_URL, cnic)
            if (response.code == 200) {
                Resource.Success(Unit)
            } else {
                response.message.let { msg ->
                    Resource.Error(msg)
                }
            }
        } catch (e: Exception) {
            return when (e) {
                is IOException -> {
                    Resource.Error(
                        message = "Try again! Couldn't reach the server.\n${e.message}."
                    )
                }

                is TimeoutException -> {
                    Resource.Error(
                        message = "Request timed out. Please try again later."
                    )
                }

                is HttpException -> {
                    Resource.Error(
                        message = "An HTTP error occurred. Status code: ${e.code()}"
                    )
                }

                else -> {
                    Resource.Error(
                        message = "An unexpected error occurred: ${e.message}"
                    )
                }
            }
        }
    }

    override suspend fun updatePassword(cnic: String, password: String): SimpleResource {
        val body = UpdatePasswordRequest(cnic, password)

        return try {
            val response = api.updatePassword(Constants.UPDATE_PASSWORD_URL, cnic, password)
            if (response.code == 200) {
                Resource.Success(Unit)
            } else {
                response.message.let { msg ->
                    Resource.Error(msg)
                }
            }
        } catch (e: Exception) {
            return when (e) {
                is IOException -> {
                    Resource.Error(
                        message = "Try again! Couldn't reach the server.\n${e.message}."
                    )
                }

                is TimeoutException -> {
                    Resource.Error(
                        message = "Request timed out. Please try again later."
                    )
                }

                is HttpException -> {
                    Resource.Error(
                        message = "An HTTP error occurred. Status code: ${e.code()}"
                    )
                }

                else -> {
                    Resource.Error(
                        message = "An unexpected error occurred: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Parse HTTP error and return user-friendly message
     */
    private fun parseHttpError(e: HttpException): String {
        val statusCode = e.code()
        val errorBody = e.response()?.errorBody()?.string()
        val requestUrl = e.response()?.raw()?.request?.url?.toString() ?: ""

        Log.d("AuthRepository", "HTTP Error - Status: $statusCode, URL: $requestUrl, Body: $errorBody")

        return try {
            if (errorBody.isNullOrBlank()) {
                return getDefaultErrorMessage(statusCode, requestUrl)
            }

            val gson = Gson()
            val errorResponse = gson.fromJson(errorBody, ErrorResponse::class.java)

            Log.d("AuthRepository", "Parsed error response: $errorResponse")

            val errorMsg = errorResponse.message ?: errorResponse.error ?: ""
            val title = errorResponse.title ?: ""

            when {
                // Check if version related
                errorMsg.contains("outdated version", ignoreCase = true) ||
                        errorMsg.contains("update required", ignoreCase = true) ||
                        errorMsg.contains("old version", ignoreCase = true) ||
                        errorMsg.contains("old mobile app", ignoreCase = true) ||
                        title.contains("update", ignoreCase = true) ||
                        errorResponse.errorCode == "VERSION_OUTDATED" -> {
                    errorMsg // Return the actual version error message
                }

                // Check if already logged in
                errorMsg.contains("already logged in", ignoreCase = true) ||
                        errorMsg.contains("already loggedin", ignoreCase = true) ||
                        errorMsg.contains("another device", ignoreCase = true) ||
                        errorMsg.contains("another Android", ignoreCase = true) ||
                        errorMsg.contains("other Machine", ignoreCase = true) ||
                        errorMsg.contains("logged in elsewhere", ignoreCase = true) ||
                        errorMsg.contains("other machine", ignoreCase = true) ||
                        title.contains("already logged", ignoreCase = true) ||
                        errorResponse.errorCode == "ALREADY_LOGGED_IN" -> {
                    errorMsg // Return the actual backend message
                }

                // 401 Unauthorized on LOGIN endpoint only
                statusCode == 401 && requestUrl.contains("login", ignoreCase = true) -> {
                    // If it's a login endpoint, check the message
                    if (errorMsg.isNotBlank()) {
                        errorMsg
                    } else {
                        "Invalid username or password"
                    }
                }

                // Invalid credentials
                statusCode == 403 ||
                        errorMsg.contains("invalid", ignoreCase = true) ||
                        errorMsg.contains("incorrect", ignoreCase = true) -> {
                    errorMsg.ifBlank { "Invalid username or password. Please try again." }
                }

                // Return the actual error message if available
                errorMsg.isNotBlank() -> errorMsg

                // Fallback to default
                else -> getDefaultErrorMessage(statusCode, requestUrl)
            }
        } catch (parseException: Exception) {
            Log.e("AuthRepository", "Error parsing error message: ${parseException.message}")
            getDefaultErrorMessage(statusCode, requestUrl)
        }
    }

    /**
     * Get default error message based on HTTP status code
     */
    private fun getDefaultErrorMessage(statusCode: Int, requestUrl: String = ""): String {
        return when (statusCode) {
            401 -> {
                // Only show "already logged in" if it's a login URL
                if (requestUrl.contains("login", ignoreCase = true)) {
                    "Invalid username or password"
                } else {
                    "Unauthorized request"
                }
            }
            403 -> "Invalid username or password. Please check your credentials."
            404 -> "Service not found. Please contact support."
            500, 502, 503 -> "Server error. Please try again later."
            else -> "An HTTP error occurred. Status code: $statusCode"
        }
    }
}