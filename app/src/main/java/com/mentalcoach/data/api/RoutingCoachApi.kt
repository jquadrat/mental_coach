package com.mentalcoach.data.api

import com.mentalcoach.data.settings.SettingsRepository
import kotlinx.coroutines.flow.first

/** Sends each call to the fake coach in demo mode, otherwise to the Strata LLM. */
class RoutingCoachApi(
    private val settings: SettingsRepository,
    private val real: CoachApi,
    private val demo: CoachApi,
) : CoachApi {
    private suspend fun api() = if (settings.settings.first().demoMode) demo else real

    override suspend fun checkin(session: SessionPayload) = api().checkin(session)

    override suspend fun reflect(session: SessionPayload) = api().reflect(session)

    override suspend fun logbook(session: SessionPayload) = api().logbook(session)
}
