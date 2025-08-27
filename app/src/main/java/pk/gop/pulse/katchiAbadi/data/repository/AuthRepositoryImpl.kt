package pk.gop.pulse.katchiAbadi.data.repository

import android.content.SharedPreferences
import okhttp3.MultipartBody
import pk.gop.pulse.katchiAbadi.common.Constants
import pk.gop.pulse.katchiAbadi.common.Resource
import pk.gop.pulse.katchiAbadi.common.SimpleResource
import pk.gop.pulse.katchiAbadi.data.remote.ServerApi
import pk.gop.pulse.katchiAbadi.data.remote.request.ForgotPasswordRequest
import pk.gop.pulse.katchiAbadi.data.remote.request.LoginRequest
import pk.gop.pulse.katchiAbadi.data.remote.request.OtpVerificationRequest
import pk.gop.pulse.katchiAbadi.data.remote.request.UpdatePasswordRequest
import pk.gop.pulse.katchiAbadi.data.remote.response.ForgotPasswordDto
import pk.gop.pulse.katchiAbadi.data.remote.response.LoginDto
import pk.gop.pulse.katchiAbadi.data.remote.response.LoginSurveyorResponse
import pk.gop.pulse.katchiAbadi.data.remote.response.OtpVerificationDto
import pk.gop.pulse.katchiAbadi.data.remote.response.UpdatePasswordDto
import pk.gop.pulse.katchiAbadi.domain.repository.AuthRepository
import retrofit2.HttpException
import java.io.IOException
import java.util.concurrent.TimeoutException
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    private val api: ServerApi,
    private val sharedPreferences: SharedPreferences
) : AuthRepository {

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

    override suspend fun loginSurveyor(
        cnic: String,
        password: String
    ): Resource<LoginSurveyorResponse> {
        val body = LoginRequest(cnic, password)

        return try {
            val response = api.loginSurveyor(Constants.LOGIN_URL_SUR, body)
            Resource.Success(response)

        } catch (e: Exception) {
            when (e) {
                is IOException -> Resource.Error("Try again! Couldn't reach the server.\n${e.message}.")
                is TimeoutException -> Resource.Error("Request timed out. Please try again later.")
                is HttpException -> Resource.Error("An HTTP error occurred. Status code: ${e.code()}")
                else -> Resource.Error("An unexpected error occurred: ${e.message}")
            }
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
//        val body = ForgotPasswordRequest(cnic)

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


}