package com.mentalcoach.data.api

/** The coach. Real calls go to the Strata LLM; demo mode and tests use [FakeCoachApi]. */
interface CoachApi {
    suspend fun checkin(session: SessionPayload): CheckinResult

    suspend fun reflect(session: SessionPayload): ReflectResult

    suspend fun logbook(session: SessionPayload): LogbookResult
}

/** Any failure talking to the coach: network, HTTP status or unparseable reply. */
class CoachException(
    val kind: Kind,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    enum class Kind { NETWORK, HTTP, INVALID_REPLY, NOT_CONFIGURED }
}
