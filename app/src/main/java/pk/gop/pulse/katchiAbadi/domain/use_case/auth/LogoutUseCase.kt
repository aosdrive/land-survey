package pk.gop.pulse.katchiAbadi.domain.use_case.auth

import pk.gop.pulse.katchiAbadi.common.Resource
import pk.gop.pulse.katchiAbadi.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import pk.gop.pulse.katchiAbadi.data.remote.response.LogoutResponse
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

class LogoutUseCase @Inject constructor(
    private val repository: AuthRepository
) {
    operator fun invoke(userId: Long, mode: String = "Android"): Flow<Resource<LogoutResponse>> = flow {
        try {
            emit(Resource.Loading())
            val response = repository.logoutUser(userId, mode)
            emit(Resource.Success(response))
        } catch (e: HttpException) {
            emit(Resource.Error(e.localizedMessage ?: "An unexpected error occurred"))
        } catch (e: IOException) {
            emit(Resource.Error("Couldn't reach server. Check your internet connection."))
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "An unexpected error occurred"))
        }
    }
}