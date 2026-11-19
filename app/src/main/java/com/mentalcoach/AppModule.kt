package com.mentalcoach

import android.content.Context
import android.content.res.Configuration
import androidx.room.Room
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.mentalcoach.data.api.CoachApi
import com.mentalcoach.data.api.FakeCoachApi
import com.mentalcoach.data.api.RoutingCoachApi
import com.mentalcoach.data.api.StrataCoachApi
import com.mentalcoach.data.api.StrataService
import com.mentalcoach.data.db.VisitDatabase
import com.mentalcoach.data.repo.RoomVisitRepository
import com.mentalcoach.data.repo.VisitRepository
import com.mentalcoach.data.settings.DataStoreSettingsRepository
import com.mentalcoach.data.settings.Settings
import com.mentalcoach.data.settings.SettingsRepository
import com.mentalcoach.domain.ExerciseLibrary
import com.mentalcoach.domain.Lang
import com.mentalcoach.media.FilePhotoStore
import com.mentalcoach.media.PhotoStore
import com.mentalcoach.media.Speaker
import com.mentalcoach.media.SpeechOutput
import com.mentalcoach.media.locale
import com.mentalcoach.ui.Env
import com.mentalcoach.ui.VisitStrings
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun library(
        @ApplicationContext c: Context,
    ): ExerciseLibrary = ExerciseLibrary.parse(c.assets.open("exercises.json").bufferedReader().use { it.readText() })

    @Provides
    @Singleton
    fun settings(
        @ApplicationContext c: Context,
    ): SettingsRepository =
        DataStoreSettingsRepository(
            c,
            Settings(
                lang = if (Locale.getDefault().language == "de") Lang.DE else Lang.EN,
                // Without a key, start in demo mode so the flow still works.
                demoMode = BuildConfig.STRATA_API_KEY.isBlank(),
            ),
        )

    @Provides
    @Singleton
    fun strata(): StrataService {
        val client =
            OkHttpClient
                .Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .callTimeout(180, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    chain.proceed(
                        chain
                            .request()
                            .newBuilder()
                            .header("Authorization", "Bearer ${BuildConfig.STRATA_API_KEY}")
                            .build(),
                    )
                }.build()
        // No logging interceptor: visitor text must not end up in logs.
        return Retrofit
            .Builder()
            .baseUrl(BuildConfig.STRATA_BASE_URL)
            .client(client)
            .addConverterFactory(StrataCoachApi.json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(StrataService::class.java)
    }

    @Provides
    @Singleton
    fun coach(
        settings: SettingsRepository,
        service: StrataService,
        library: ExerciseLibrary,
    ): CoachApi =
        RoutingCoachApi(
            settings,
            real = StrataCoachApi(service, BuildConfig.STRATA_MODEL, library, configured = BuildConfig.STRATA_API_KEY.isNotBlank()),
            demo = FakeCoachApi(library, latencyMs = 900),
        )

    @Provides
    @Singleton
    fun photos(
        @ApplicationContext c: Context,
    ): PhotoStore = FilePhotoStore(c.filesDir, c.cacheDir)

    @Provides
    @Singleton
    fun db(
        @ApplicationContext c: Context,
    ): VisitDatabase = Room.databaseBuilder(c, VisitDatabase::class.java, "visits.db").build()

    @Provides
    @Singleton
    fun visits(
        db: VisitDatabase,
        photos: PhotoStore,
    ): VisitRepository = RoomVisitRepository(db.visits(), photos::deleteVisit)

    @Provides
    @Singleton
    fun speaker(
        @ApplicationContext c: Context,
    ): Speaker = Speaker(c)

    @Provides
    fun speech(speaker: Speaker): SpeechOutput = speaker

    @Provides
    fun env(): Env = object : Env {}

    @Provides
    @Singleton
    fun strings(
        @ApplicationContext c: Context,
    ): VisitStrings = ResourceStrings(c)
}

/** Reads string resources in the visit language, independent of the system locale. */
class ResourceStrings(
    private val context: Context,
) : VisitStrings {
    private val cache = mutableMapOf<Lang, Context>()

    private fun res(lang: Lang) =
        cache
            .getOrPut(lang) {
                context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(lang.locale()) })
            }.resources

    override fun coachOpener(
        lang: Lang,
        name: String,
    ) = res(lang).getString(R.string.coach_opener, name)

    override fun photoPrompt(
        lang: Lang,
        number: Int,
        prompt: String,
    ) = res(lang).getString(R.string.photo_prompt_spoken, number, prompt)

    override fun allPhotos(lang: Lang) = res(lang).getString(R.string.all_photos)

    override fun crisisDefault(lang: Lang) = res(lang).getString(R.string.crisis_default)

    override fun crisisStaff(lang: Lang) = res(lang).getString(R.string.crisis_staff)
}
