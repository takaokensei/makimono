package zechs.drive.stream.di

import android.content.Context
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import zechs.drive.stream.utils.SessionManager
import zechs.drive.stream.utils.AppSettings
import javax.inject.Singleton


@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideGson(): Gson {
        return Gson()
    }

    @Singleton
    @Provides
    fun provideSessionDataStore(
        @ApplicationContext appContext: Context,
        gson: Gson,
        secretStore: zechs.drive.stream.utils.EncryptedSessionStore
    ): SessionManager = SessionManager(appContext, gson, secretStore)

    @Singleton
    @Provides
    fun provideThemeDataStore(
        @ApplicationContext appContext: Context,
        profileManager: zechs.drive.stream.utils.ProfileManager
    ): AppSettings = AppSettings(appContext, profileManager)

}