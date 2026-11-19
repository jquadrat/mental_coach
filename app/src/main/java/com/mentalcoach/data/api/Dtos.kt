package com.mentalcoach.data.api

import com.mentalcoach.domain.ChatTurn
import com.mentalcoach.domain.CheckinReading
import com.mentalcoach.domain.Logbook
import com.mentalcoach.domain.Suggestion
import com.mentalcoach.domain.Visit
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// ---------- Session state sent to the coach (text only, never photos) ----------

@Serializable
data class ExerciseRecord(
    val id: String,
    val title: String,
    val startedAt: String? = null,
    val endedAt: String? = null,
    val chat: List<ChatTurn> = emptyList(),
    val moodNow: Int? = null,
)

@Serializable
data class SessionPayload(
    val name: String,
    val lang: String,
    val maxExercises: Int,
    val mood: String,
    val previousContext: String? = null,
    val checkin: CheckinReading? = null,
    val exercises: List<ExerciseRecord> = emptyList(),
) {
    companion object {
        fun from(v: Visit) =
            SessionPayload(
                name = v.name,
                lang = v.lang.code,
                maxExercises = v.maxExercises,
                mood = v.mood,
                previousContext = v.previousContext?.takeIf { it.isNotBlank() },
                checkin = v.checkin,
                exercises =
                    v.exercises.map {
                        ExerciseRecord(it.exerciseId, it.title, it.startedAt, it.endedAt, it.chat, it.moodNow)
                    },
            )
    }
}

// ---------- Coach results ----------

@Serializable
data class CheckinResult(
    val summary: String,
    val feelings: List<String> = emptyList(),
    val energy: Int = 5,
    val mood: Int = 5,
    val crisis: Boolean = false,
    val greeting: String = "",
    val exerciseId: String? = null,
    val exerciseIntro: String = "",
)

@Serializable
data class ReflectResult(
    val reply: String,
    val crisis: Boolean = false,
    val moodNow: Int? = null,
    val suggestion: Suggestion? = null,
)

typealias LogbookResult = Logbook

// ---------- OpenAI-compatible wire format ----------

@Serializable
data class ChatMessage(val role: String, val content: String)

@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.5,
    @SerialName("response_format") val responseFormat: JsonObject? = null,
)

@Serializable
data class ChatCompletionResponse(val choices: List<Choice> = emptyList()) {
    @Serializable
    data class Choice(val message: Message? = null, @SerialName("finish_reason") val finishReason: String? = null)

    @Serializable
    data class Message(val content: String? = null)
}
