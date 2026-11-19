package com.mentalcoach

import com.mentalcoach.data.api.ExerciseRecord
import com.mentalcoach.data.api.SessionPayload
import com.mentalcoach.data.api.StrataCoachApi
import com.mentalcoach.data.api.StrataService
import com.mentalcoach.domain.ChatTurn
import com.mentalcoach.domain.CheckinReading
import com.mentalcoach.domain.Role
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/** Calls the real Strata LLM. Opt-in: run with STRATA_LIVE=1 and STRATA_API_KEY set. Uses invented text only. */
class StrataLiveTest {
    @Test
    fun fullConversationAgainstStrata() =
        runBlocking {
            assumeTrue(System.getenv("STRATA_LIVE") == "1" && !System.getenv("STRATA_API_KEY").isNullOrBlank())
            val client =
                OkHttpClient
                    .Builder()
                    .readTimeout(120, TimeUnit.SECONDS)
                    .addInterceptor { it.proceed(it.request().newBuilder().header("Authorization", "Bearer ${System.getenv("STRATA_API_KEY")}").build()) }
                    .build()
            val service =
                Retrofit
                    .Builder()
                    .baseUrl(BuildConfig.STRATA_BASE_URL)
                    .client(client)
                    .addConverterFactory(StrataCoachApi.json.asConverterFactory("application/json".toMediaType()))
                    .build()
                    .create(StrataService::class.java)
            val lib = TestLibrary.library
            val api = StrataCoachApi(service, BuildConfig.STRATA_MODEL, lib)
            var s = SessionPayload("Test", "de", 2, "Ich bin gestresst und verspannt nach einer langen Woche.")
            val c = api.checkin(s)
            println("CHECKIN $c")
            assertTrue(lib.find(c.exerciseId) != null)
            s = s.copy(checkin = CheckinReading(c.summary, c.feelings, c.energy, c.mood))
            val chat =
                listOf(
                    ChatTurn(Role.COACH, "Wie fühlst du dich jetzt?"),
                    ChatTurn(Role.USER, "Ruhiger, die Schultern sind lockerer."),
                    ChatTurn(Role.COACH, "Schön. Was hast du bemerkt?"),
                    ChatTurn(Role.USER, "Dass ich langsamer atme. Gerne noch eine Übung."),
                )
            s = s.copy(exercises = listOf(ExerciseRecord(c.exerciseId!!, "Übung", chat = chat)))
            val r = api.reflect(s)
            println("REFLECT $r")
            assertTrue(!r.crisis)
            val lb = api.logbook(s)
            println("LOGBOOK $lb")
            assertTrue(lb.title.isNotBlank())
        }
}
