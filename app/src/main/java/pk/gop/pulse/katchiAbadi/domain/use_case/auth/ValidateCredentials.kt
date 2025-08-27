package pk.gop.pulse.katchiAbadi.domain.use_case.auth

import pk.gop.pulse.katchiAbadi.common.Resource
import pk.gop.pulse.katchiAbadi.data.remote.response.LoginDto
import pk.gop.pulse.katchiAbadi.domain.repository.AuthRepository
import javax.inject.Inject

class ValidateCredentials @Inject constructor(
    private val repository: AuthRepository
) {
    suspend operator fun invoke(cnic: String, password: String): Resource<LoginDto> {
        return repository.login(cnic, password)
    }
}