package pk.gop.pulse.katchiAbadi.di

import android.app.Application
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import android.util.Log
import androidx.room.Room
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.logging.HttpLoggingInterceptor
import pk.gop.pulse.katchiAbadi.BuildConfig
import pk.gop.pulse.katchiAbadi.common.Constants
import pk.gop.pulse.katchiAbadi.data.local.ActiveParcelDao
import pk.gop.pulse.katchiAbadi.data.local.AppDatabase
import pk.gop.pulse.katchiAbadi.data.remote.ServerApi
import pk.gop.pulse.katchiAbadi.data.remote.response.NewSurveyNewDao
import pk.gop.pulse.katchiAbadi.data.remote.response.SurveyImageDao
import pk.gop.pulse.katchiAbadi.data.remote.response.SurveyPersonDao
import pk.gop.pulse.katchiAbadi.data.repository.AuthRepositoryImpl
import pk.gop.pulse.katchiAbadi.data.repository.MenuRepositoryImpl
import pk.gop.pulse.katchiAbadi.data.repository.NAHRepositoryImpl
import pk.gop.pulse.katchiAbadi.data.repository.NewSurveyRepositoryImpl
import pk.gop.pulse.katchiAbadi.data.repository.SavedRepositoryImpl
import pk.gop.pulse.katchiAbadi.data.repository.SurveyFormRepositoryImpl
import pk.gop.pulse.katchiAbadi.data.repository.SurveyRepositoryImpl
import pk.gop.pulse.katchiAbadi.domain.repository.AuthRepository
import pk.gop.pulse.katchiAbadi.domain.repository.MenuRepository
import pk.gop.pulse.katchiAbadi.domain.repository.NAHRepository
import pk.gop.pulse.katchiAbadi.domain.repository.NewSurveyRepository
import pk.gop.pulse.katchiAbadi.domain.repository.SavedRepository
import pk.gop.pulse.katchiAbadi.domain.repository.SurveyFormRepository
import pk.gop.pulse.katchiAbadi.domain.repository.SurveyRepository
import pk.gop.pulse.katchiAbadi.domain.use_case.*
import pk.gop.pulse.katchiAbadi.domain.use_case.auth.AuthenticateUseCase
import pk.gop.pulse.katchiAbadi.domain.use_case.auth.ForgotPasswordUseCase
import pk.gop.pulse.katchiAbadi.domain.use_case.auth.OtpVerificationUseCase
import pk.gop.pulse.katchiAbadi.domain.use_case.auth.UpdatePasswordUseCase
import pk.gop.pulse.katchiAbadi.domain.use_case.auth.ValidateCredentials
import pk.gop.pulse.katchiAbadi.domain.use_case.auth.ValidateCredentialsSur
import pk.gop.pulse.katchiAbadi.domain.use_case.auth.ValidationUseCase
import pk.gop.pulse.katchiAbadi.domain.use_case.menu.FetchMauzaSyncUseCase
import pk.gop.pulse.katchiAbadi.domain.use_case.menu.GetMouzaDataUseCase
import pk.gop.pulse.katchiAbadi.domain.use_case.menu.GetSyncDataUseCase
import pk.gop.pulse.katchiAbadi.domain.use_case.menu.MenuDataUseCase
import pk.gop.pulse.katchiAbadi.domain.use_case.not_at_home.NAHSaveUseCase
import pk.gop.pulse.katchiAbadi.domain.use_case.not_at_home.NAHUseCase
import pk.gop.pulse.katchiAbadi.domain.use_case.saved.DeleteSavedRecordUseCase
import pk.gop.pulse.katchiAbadi.domain.use_case.saved.GetAllSavedFormsUseCase
import pk.gop.pulse.katchiAbadi.domain.use_case.saved.GetSavedRecordByStatusAndLimitUseCase
import pk.gop.pulse.katchiAbadi.domain.use_case.saved.PostSavedRecordUseCase
import pk.gop.pulse.katchiAbadi.domain.use_case.saved.SavedDataUseCase
import pk.gop.pulse.katchiAbadi.domain.use_case.saved.ViewSavedRecordUseCase
import pk.gop.pulse.katchiAbadi.domain.use_case.survey_form.temp.GetAllTempSurveyFormUseCase
import pk.gop.pulse.katchiAbadi.domain.use_case.survey_form.main.SaveAllSurveyFormUseCase
import pk.gop.pulse.katchiAbadi.domain.use_case.survey_form.main.SaveSurveyFormUseCase
import pk.gop.pulse.katchiAbadi.domain.use_case.survey_form.SurveyFormUseCase
import pk.gop.pulse.katchiAbadi.domain.use_case.survey_form.not_at_home.GetAllNotAtHomeUseCase
import pk.gop.pulse.katchiAbadi.domain.use_case.survey_form.not_at_home.NotAtHomeSaveAllUseCase
import pk.gop.pulse.katchiAbadi.domain.use_case.survey_form.not_at_home.NotAtHomeSaveUseCase
import pk.gop.pulse.katchiAbadi.domain.use_case.survey_form.temp.TempSaveSurveyFormUseCase
import pk.gop.pulse.katchiAbadi.domain.use_case.survey_list.GetAllFilteredSurveysUseCase
import pk.gop.pulse.katchiAbadi.domain.use_case.survey_list.GetAllSurveysUseCase
import pk.gop.pulse.katchiAbadi.domain.use_case.survey_list.SurveyListDataUseCase
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    fun provideContext(@ApplicationContext context: Context): Context {
        return context
    }

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getDatabase(context)
    }

    @Provides
    @Singleton
    fun provideSharedPref(app: Application): SharedPreferences {
        return app.getSharedPreferences(
            Constants.SHARED_PREF_NAME,
            MODE_PRIVATE
        )
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(sharedPreferences: SharedPreferences): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .protocols(listOf(Protocol.HTTP_1_1))
            .addInterceptor { chain ->
                val requestBuilder = chain.request().newBuilder()
                val original = chain.request()

                Log.d("API_REQUEST", "==========================================")
                Log.d("API_REQUEST", "Full URL: ${original.url}")
                Log.d("API_REQUEST", "Method: ${original.method}")
                Log.d("API_REQUEST", "Headers: ${original.headers}")

                val token = sharedPreferences.getString(Constants.SHARED_PREF_TOKEN, null)
                if (!token.isNullOrEmpty()) {
                    requestBuilder.addHeader("Authorization", "Bearer $token")
                }
                chain.proceed(requestBuilder.build())
            }
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
                    else HttpLoggingInterceptor.Level.NONE
                }
            )
            .readTimeout(60, TimeUnit.SECONDS)
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))

        // Allow all SSL certs in debug mode
        if (BuildConfig.DEBUG) {
            try {
                val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                    override fun checkClientTrusted(
                        chain: Array<X509Certificate>,
                        authType: String
                    ) {
                    }

                    override fun checkServerTrusted(
                        chain: Array<X509Certificate>,
                        authType: String
                    ) {
                    }

                    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                })

                val sslContext = SSLContext.getInstance("SSL").apply {
                    init(null, trustAllCerts, SecureRandom())
                }

                builder.sslSocketFactory(
                    sslContext.socketFactory,
                    trustAllCerts[0] as X509TrustManager
                )
                builder.hostnameVerifier { _, _ -> true }
            } catch (e: Exception) {
                throw RuntimeException(e)
            }
        }

        return builder.build()
    }

//    fun provideOkHttpClient(sharedPreferences: SharedPreferences): OkHttpClient {
//        val builder = OkHttpClient.Builder()
//            .protocols(listOf(Protocol.HTTP_1_1))
//            .addInterceptor {
//                val modifiedRequest = it.request().newBuilder()
//                    .addHeader(
//                        "Authorization",
//                        "Bearer ${
//                            sharedPreferences.getString(Constants.SHARED_PREF_TOKEN, "").toString()
//                        }"
//                    )
//                    .build()
//                it.proceed(modifiedRequest)
//            }
//            .addInterceptor(
//                HttpLoggingInterceptor().apply {
//                    level = HttpLoggingInterceptor.Level.HEADERS
//                }
//            )
//            .addInterceptor(
//                HttpLoggingInterceptor().apply {
//                    level = HttpLoggingInterceptor.Level.BODY
//                }
//            )
//            .readTimeout(20, TimeUnit.MINUTES)
//            .connectTimeout(2, TimeUnit.MINUTES)
//            .writeTimeout(2, TimeUnit.MINUTES)
//            .retryOnConnectionFailure(true)
//            .followRedirects(true)
//            .followSslRedirects(true)
//            .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
//            .cache(cache = null)
//
//        // SSL Configuration for Development
//        if (BuildConfig.DEBUG) {
//            try {
//                // Create a trust manager that does not validate certificate chains
//                val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
//                    override fun checkClientTrusted(
//                        chain: Array<X509Certificate>,
//                        authType: String
//                    ) {
//                    }
//
//                    override fun checkServerTrusted(
//                        chain: Array<X509Certificate>,
//                        authType: String
//                    ) {
//                    }
//
//                    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
//                })
//
//                // Install the all-trusting trust manager
//                val sslContext = SSLContext.getInstance("SSL")
//                sslContext.init(null, trustAllCerts, SecureRandom())
//
//                // Create an ssl socket factory with our all-trusting manager
//                val sslSocketFactory = sslContext.socketFactory
//
//                builder.sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
//                builder.hostnameVerifier { _, _ -> true }
//            } catch (e: Exception) {
//                throw RuntimeException(e)
//            }
//        }
//
//        return builder.build()
//    }

    @Provides
    @Singleton
    fun provideGson(): Gson {
        return Gson()
    }

    @Provides
    @Singleton
    fun provideAuthApi(client: OkHttpClient): ServerApi {
        return Retrofit.Builder()
            .baseUrl(Constants.BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ServerApi::class.java)
    }

    @Provides
    @Singleton
    fun provideAuthRepository(
        api: ServerApi,
        sharedPreferences: SharedPreferences
    ): AuthRepository {
        return AuthRepositoryImpl(api, sharedPreferences)
    }

    @Provides
    @Singleton
    fun provideSyncRepository(
        context: Context,
        api: ServerApi,
        db: AppDatabase,
        sharedPreferences: SharedPreferences
    ): MenuRepository {
        return MenuRepositoryImpl(context, api, db, sharedPreferences)
    }

    @Provides
    @Singleton
    fun provideSurveyRepository(
        db: AppDatabase,
        sharedPreferences: SharedPreferences
    ): SurveyRepository {
        return SurveyRepositoryImpl(db, sharedPreferences)
    }

    @Provides
    @Singleton
    fun provideSurveyFormRepository(
        context: Context,
        api: ServerApi,
        db: AppDatabase,
        sharedPreferences: SharedPreferences
    ): SurveyFormRepository {
        return SurveyFormRepositoryImpl(context, api, db, sharedPreferences)
    }

    @Provides
    @Singleton
    fun provideNAHRepositoryRepository(
        context: Context,
        api: ServerApi,
        db: AppDatabase,
        sharedPreferences: SharedPreferences
    ): NAHRepository {
        return NAHRepositoryImpl(context, api, db, sharedPreferences)
    }

    @Provides
    @Singleton
    fun provideSavedRepository(
        context: Context,
        api: ServerApi,
        db: AppDatabase,
        sharedPreferences: SharedPreferences
    ): SavedRepository {
        return SavedRepositoryImpl(context, api, db, sharedPreferences)
    }

    @Provides
    @Singleton
    fun provideAuthenticationUseCase(repository: AuthRepository): AuthenticateUseCase {
        return AuthenticateUseCase(repository)
    }

    @Provides
    @Singleton
    fun provideValidateCredentials(repository: AuthRepository): ValidateCredentials {
        return ValidateCredentials(repository)
    }

    @Provides
    @Singleton
    fun provideAllSurveysUseCase(repository: SurveyRepository): SurveyListDataUseCase {
        return SurveyListDataUseCase(
            getAllSurveysUseCase = GetAllSurveysUseCase(repository),
            getAllFilteredSurveysUseCase = GetAllFilteredSurveysUseCase(repository),
        )
    }

    @Provides
    @Singleton
    fun provideSchoolListUseCase(repository: MenuRepository): MenuDataUseCase {
        return MenuDataUseCase(
            getSyncDataUseCase = GetSyncDataUseCase(repository),
            getMouzaDataUseCase = GetMouzaDataUseCase(repository),
            fetchMauzaSyncUseCase = FetchMauzaSyncUseCase(repository),
        )
    }

    @Provides
    @Singleton
    fun provideSurveyFormUseCase(repository: SurveyFormRepository): SurveyFormUseCase {
        return SurveyFormUseCase(
            saveSurveyFormUseCase = SaveSurveyFormUseCase(repository),
            saveTempSurveyFormUseCase = TempSaveSurveyFormUseCase(repository),
            saveAllSurveyFormUseCase = SaveAllSurveyFormUseCase(repository),
            getAllTempSurveyFormUseCase = GetAllTempSurveyFormUseCase(repository),

            notAtHomeSaveAllUseCase = NotAtHomeSaveAllUseCase(repository),
            notAtHomeSaveUseCase = NotAtHomeSaveUseCase(repository),
            getAllNotAtHomeUseCase = GetAllNotAtHomeUseCase(repository)
        )
    }

    @Provides
    @Singleton
    fun provideNAHUseCase(repository: NAHRepository): NAHUseCase {
        return NAHUseCase(
            nAHSaveUseCase = NAHSaveUseCase(repository),
        )
    }

    @Provides
    @Singleton
    fun provideSavedUseCase(repository: SavedRepository): SavedDataUseCase {
        return SavedDataUseCase(
            getAllSavedFormsUseCase = GetAllSavedFormsUseCase(repository),
            deleteSavedRecordUseCase = DeleteSavedRecordUseCase(repository),
            getSavedRecordByStatusAndLimitUseCase = GetSavedRecordByStatusAndLimitUseCase(repository),
            postSavedRecordUseCase = PostSavedRecordUseCase(repository),
//            postAllSavedRecordUseCase = PostAllSavedRecordUseCase(repository),
            viewSavedRecordUseCase = ViewSavedRecordUseCase(repository),
        )
    }

    @Provides
    @Singleton
    fun provideValidationUseCase(repository: AuthRepository): ValidationUseCase {
        return ValidationUseCase(
            validateCredentials = ValidateCredentials(repository),
            validateCredentialsSur = ValidateCredentialsSur(repository),
            forgotPasswordUseCase = ForgotPasswordUseCase(repository),
            otpVerificationUseCase = OtpVerificationUseCase(repository),
            updatePasswordUseCase = UpdatePasswordUseCase(repository),
        )
    }

    @Provides
    @Singleton
    fun provideNewSurveyNewDao(db: AppDatabase): NewSurveyNewDao {
        return db.newSurveyNewDao()
    }

    // Provide other DAOs similarly:
    @Provides
    @Singleton
    fun provideSurveyImageDao(db: AppDatabase): SurveyImageDao {
        return db.imageDao()
    }

    @Provides
    @Singleton
    fun providePersonDao(db: AppDatabase): SurveyPersonDao {
        return db.personDao()
    }

    @Provides
    @Singleton
    fun provideActiveParcelDao(db: AppDatabase): ActiveParcelDao {
        return db.activeParcelDao()
    }


    // Provide your repository with all dependencies injected
    @Provides
    @Singleton
    fun provideNewSurveyRepository(
        dao: NewSurveyNewDao,
        imageDao: SurveyImageDao,
        personDao: SurveyPersonDao,
        api: ServerApi,
        sharedPreferences: SharedPreferences,
        activeParcelDao: ActiveParcelDao   // <-- Add this
    ): NewSurveyRepository {
        return NewSurveyRepositoryImpl(
            dao,
            imageDao,
            personDao,
            api,
            sharedPreferences,
            activeParcelDao
        )
    }

}