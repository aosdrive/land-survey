package pk.gop.pulse.katchiAbadi.domain.repository

import pk.gop.pulse.katchiAbadi.common.Resource
import pk.gop.pulse.katchiAbadi.common.SimpleResource
import pk.gop.pulse.katchiAbadi.data.remote.response.ForgotPasswordDto
import pk.gop.pulse.katchiAbadi.data.remote.response.LoginDto
import pk.gop.pulse.katchiAbadi.data.remote.response.LoginSurveyorResponse
import pk.gop.pulse.katchiAbadi.data.remote.response.OtpVerificationDto
import pk.gop.pulse.katchiAbadi.data.remote.response.UpdatePasswordDto

interface AuthRepository {

    suspend fun login(
        cnic: String,
        password: String
    ): Resource<LoginDto>

    suspend fun loginSurveyor(
        cnic: String,
        password: String
    ): Resource<LoginSurveyorResponse>

    fun authenticate(): SimpleResource

    suspend fun otpVerification(
        cnic: String,
        otp: Int,
    ): SimpleResource

    suspend fun forgotPassword(
        cnic: String,
    ): SimpleResource

    suspend fun updatePassword(
        cnic: String,
        password: String
    ): SimpleResource


}