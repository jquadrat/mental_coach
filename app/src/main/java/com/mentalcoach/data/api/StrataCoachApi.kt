package com.mentalcoach.data.api

import com.mentalcoach.domain.CrisisDetector
import com.mentalcoach.domain.ExerciseLibrary
import com.mentalcoach.domain.Role
import com.mentalcoach.domain.Suggestion
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import retrofit2.HttpException
import retrofit2.http.Body
import retrofit2.http.POST
import java.io.IOException

interface StrataService {
    @POST("chat/completions")
    suspend fun complete(
        @Body request: ChatCompletionRequest,
    ): ChatCompletionResponse
}

/** Calls the Strata LLM (OpenAI-compatible) directly from the device. Text only. */
class StrataCoachApi(
    private val service: StrataService,
    private val model: String,
    private val library: ExerciseLibrary,
    private val configured: Boolean = true,
) : CoachApi {
    private val prompts = Prompts(library)

    override suspend fun checkin(session: SessionPayload): CheckinResult {
        val r = ask(CheckinResult.serializer(), prompts.checkin(session))
        return CoachRules.checkin(r, session, library)
    }

    override suspend fun reflect(session: SessionPayload): ReflectResult {
        val r = ask(ReflectResult.serializer(), prompts.reflect(session))
        return CoachRules.reflect(r, session, library)
    }

    override suspend fun logbook(session: SessionPayload): LogbookResult =
        CoachRules.logbook(ask(LogbookResult.serializer(), prompts.logbook(session)), session)

    private suspend fun <T> ask(
        serializer: KSerializer<T>,
        task: String,
    ): T {
        if (!configured) throw CoachException(CoachException.Kind.NOT_CONFIGURED, "STRATA_API_KEY is not set")
        var lastError: Throwable? = null
        // One automatic retry on an unparseable reply.
        repeat(2) {
            val content = call(task)
            try {
                return json.decodeFromString(serializer, extractJson(content))
            } catch (e: SerializationException) {
                lastError = e
            } catch (e: IllegalArgumentException) {
                lastError = e
            }
        }
        throw CoachException(CoachException.Kind.INVALID_REPLY, "The coach reply could not be read", lastError)
    }

    private suspend fun call(task: String): String {
        val request =
            ChatCompletionRequest(
                model = model,
                messages = listOf(ChatMessage("system", prompts.system), ChatMessage("user", task)),
                responseFormat = buildJsonObject { put("type", JsonPrimitive("json_object")) },
            )
        val response =
            try {
                service.complete(request)
            } catch (e: HttpException) {
                throw CoachException(CoachException.Kind.HTTP, "HTTP ${e.code()}", e)
            } catch (e: IOException) {
                throw CoachException(CoachException.Kind.NETWORK, "Network error", e)
            } catch (e: SerializationException) {
                throw CoachException(CoachException.Kind.INVALID_REPLY, "Malformed response", e)
            }
        return response.choices.firstOrNull()?.message?.content
            ?: throw CoachException(CoachException.Kind.INVALID_REPLY, "Empty response")
    }

    companion object {
        val json =
            Json {
                ignoreUnknownKeys = true
                isLenient = true
                coerceInputValues = true
                explicitNulls = false
            }

        /** Strips code fences or chatter around the first JSON object. */
        fun extractJson(content: String): String {
            val start = content.indexOf('{')
            val end = content.lastIndexOf('}')
            require(start >= 0 && end > start) { "No JSON object in reply" }
            return content.substring(start, end + 1)
        }
    }
}

/** Post-processing applied to every coach reply, real or fake. */
object CoachRules {
    /** Keeps only entries for exercises that actually happened, one per exercise. */
    fun logbook(
        r: LogbookResult,
        s: SessionPayload,
    ): LogbookResult = r.copy(entries = r.entries.filter { it.exerciseIndex in s.exercises.indices }.distinctBy { it.exerciseIndex })

    fun checkin(
        r: CheckinResult,
        s: SessionPayload,
        library: ExerciseLibrary,
    ): CheckinResult =
        r.copy(
            energy = r.energy.coerceIn(1, 10),
            mood = r.mood.coerceIn(1, 10),
            feelings = r.feelings.take(4),
            exerciseId = library.resolve(r.exerciseId).id,
        ).let {
            // Only the keyword backstop fired: the model's normal greeting must not be shown as the crisis message.
            if (!it.crisis && CrisisDetector.matches(s.mood)) it.copy(crisis = true, greeting = "") else it
        }

    fun reflect(
        r: ReflectResult,
        s: SessionPayload,
        library: ExerciseLibrary,
    ): ReflectResult {
        val last = s.exercises.lastOrNull()?.chat?.lastOrNull()
        val backstop = !r.crisis && last?.role == Role.USER && CrisisDetector.matches(last.text)
        if (backstop) return r.copy(crisis = true, reply = "", suggestion = null)
        val used = s.exercises.map { it.id }
        var sug = r.suggestion
        if (sug != null) {
            sug =
                when {
                    sug.isNext && s.exercises.size >= s.maxExercises -> sug.copy(action = Suggestion.ACTION_END, exerciseId = null)
                    // Never use an exercise outside the library: fall back to a valid unused one.
                    sug.isNext && library.find(sug.exerciseId) == null -> sug.copy(exerciseId = library.resolve(null, used).id)
                    sug.isNext -> sug
                    else -> sug.copy(action = Suggestion.ACTION_END, exerciseId = null)
                }
            if (sug.why.isBlank()) sug = sug.copy(why = r.reply)
        }
        return r.copy(moodNow = r.moodNow?.coerceIn(1, 10), suggestion = sug)
    }
}
