package zechs.drive.stream.di

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import zechs.drive.stream.BuildConfig
import zechs.drive.stream.data.model.StarredAdapter
import zechs.drive.stream.data.remote.AniSkipApi
import zechs.drive.stream.data.remote.DriveApi
import zechs.drive.stream.data.remote.GithubApi
import zechs.drive.stream.data.remote.MalApi
import zechs.drive.stream.data.remote.AnimePosterResolver
import zechs.drive.stream.data.remote.TokenApi
import zechs.drive.stream.data.repository.AniSkipRepository
import zechs.drive.stream.data.repository.DriveRepository
import zechs.drive.stream.data.repository.GithubRepository
import zechs.drive.stream.data.repository.MalRepository
import zechs.drive.stream.data.repository.TokenAuthenticator
import zechs.drive.stream.utils.MalSessionManager
import zechs.drive.stream.utils.SessionManager
import zechs.drive.stream.utils.util.Constants.Companion.ANISKIP_API_BASE_URL
import zechs.drive.stream.utils.util.Constants.Companion.GITHUB_API
import zechs.drive.stream.utils.util.Constants.Companion.GOOGLE_ACCOUNTS_URL
import zechs.drive.stream.utils.util.Constants.Companion.GOOGLE_API
import zechs.drive.stream.utils.util.Constants.Companion.MAL_API_BASE_URL
import javax.inject.Named
import javax.inject.Singleton


@Module
@InstallIn(SingletonComponent::class)
object ApiModule {

    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor {
        return zechs.drive.stream.utils.RedactingLoggingInterceptor.create(
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.HEADERS else HttpLoggingInterceptor.Level.NONE
        )
    }

    @Provides
    @Singleton
    fun provideTokenProvider(
        sessionManager: SessionManager,
        driveRepository: Lazy<DriveRepository>
    ): zechs.drive.stream.data.repository.TokenProvider {
        return zechs.drive.stream.data.repository.DefaultTokenProvider(sessionManager, driveRepository)
    }

    @Provides
    @Singleton
    fun provideTokenAuthenticator(
        tokenProvider: zechs.drive.stream.data.repository.TokenProvider
    ): TokenAuthenticator {
        return TokenAuthenticator(tokenProvider)
    }

    @Provides
    @Singleton
    @Named("OkHttpClientWithAuthenticator")
    fun provideOkHttpClientWithAuthenticator(
        logging: Lazy<HttpLoggingInterceptor>,
        tokenAuthenticator: TokenAuthenticator
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .also {
                if (BuildConfig.DEBUG) {
                    // Logging only in debug builds
                    it.addInterceptor(logging.get())
                }
                it.authenticator(tokenAuthenticator)
            }.build()
    }

    @Provides
    @Singleton
    @Named("OkHttpClient")
    fun provideOkHttpClient(
        logging: Lazy<HttpLoggingInterceptor>,
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .also {
                if (BuildConfig.DEBUG) {
                    // Logging only in debug builds
                    it.addInterceptor(logging.get())
                }
            }.build()
    }

    @Provides
    @Singleton
    fun provideMoshi(): Moshi {
        return Moshi.Builder()
            .add(StarredAdapter())
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    @Provides
    @Singleton
    fun provideDriveApi(
        @Named("OkHttpClientWithAuthenticator")
        client: OkHttpClient,
        moshi: Moshi
    ): DriveApi {
        return Retrofit.Builder()
            .baseUrl(GOOGLE_API)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(DriveApi::class.java)
    }

    @Provides
    @Singleton
    fun provideTokenApi(
        @Named("OkHttpClient")
        client: OkHttpClient,
        moshi: Moshi
    ): TokenApi {
        return Retrofit.Builder()
            .baseUrl(GOOGLE_ACCOUNTS_URL)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(TokenApi::class.java)
    }

    @Provides
    @Singleton
    fun provideGithubApi(
        @Named("OkHttpClient")
        client: OkHttpClient,
        moshi: Moshi
    ): GithubApi {
        return Retrofit.Builder()
            .baseUrl(GITHUB_API)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GithubApi::class.java)
    }

    @Provides
    @Singleton
    fun provideDriveRepository(
        driveApi: DriveApi,
        tokenApi: Lazy<TokenApi>,
        sessionManager: SessionManager
    ): DriveRepository {
        return DriveRepository(driveApi, tokenApi, sessionManager)
    }

    @Provides
    @Singleton
    fun provideGithubRepository(
        githubApi: Lazy<GithubApi>
    ): GithubRepository {
        return GithubRepository(githubApi)
    }

    @Provides
    @Singleton
    fun provideMalApi(
        @Named("OkHttpClient")
        client: OkHttpClient,
        moshi: Moshi
    ): MalApi {
        return Retrofit.Builder()
            .baseUrl(MAL_API_BASE_URL)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(MalApi::class.java)
    }

    @Provides
    @Singleton
    fun provideMalRepository(
        malApi: Lazy<MalApi>,
        sessionManager: MalSessionManager,
        posterResolver: AnimePosterResolver
    ): MalRepository {
        return MalRepository(malApi, sessionManager, posterResolver)
    }

    @Provides
    @Singleton
    fun provideAniSkipApi(
        @Named("OkHttpClient")
        client: OkHttpClient,
        moshi: Moshi
    ): AniSkipApi {
        return Retrofit.Builder()
            .baseUrl(ANISKIP_API_BASE_URL)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(AniSkipApi::class.java)
    }

    @Provides
    @Singleton
    fun provideAniSkipRepository(
        aniSkipApi: Lazy<AniSkipApi>
    ): AniSkipRepository {
        return AniSkipRepository(aniSkipApi)
    }

}