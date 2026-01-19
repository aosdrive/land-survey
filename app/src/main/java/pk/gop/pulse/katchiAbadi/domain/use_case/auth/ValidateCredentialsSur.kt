package pk.gop.pulse.katchiAbadi.domain.use_case.auth

import pk.gop.pulse.katchiAbadi.common.Constants
import pk.gop.pulse.katchiAbadi.common.Resource
import pk.gop.pulse.katchiAbadi.data.remote.response.LoginDto
import pk.gop.pulse.katchiAbadi.data.remote.response.LoginSurveyorResponse
import pk.gop.pulse.katchiAbadi.domain.repository.AuthRepository
import javax.inject.Inject

class ValidateCredentialsSur @Inject constructor(
    private val repository: AuthRepository
) {
    suspend operator fun invoke(cnic: String, password: String): Resource<LoginSurveyorResponse> {
        // Validate inputs before calling repository
        if (cnic.isBlank() || password.isBlank()) {
            return Resource.Error("CNIC and password cannot be empty")
        }

        if (cnic.length != 13) {
            return Resource.Error("CNIC must be 13 digits")
        }

        if (password.length < 8) {
            return Resource.Error("Password must be at least 8 characters")
        }

        // Include app version in the login request
        return repository.loginSurveyor(
            cnic = cnic,
            password = password,
            appVersion = Constants.VERSION_NAME
        )
    }
}
