package com.mentalcoach

import com.mentalcoach.data.api.CoachException
import com.mentalcoach.data.api.FakeCoachApi
import com.mentalcoach.data.repo.InMemoryVisitRepository
import com.mentalcoach.data.settings.InMemorySettingsRepository
import com.mentalcoach.data.settings.Settings
import com.mentalcoach.domain.Role
import com.mentalcoach.domain.Screen
import com.mentalcoach.ui.ErrorKind
import com.mentalcoach.ui.RetryAction
import com.mentalcoach.ui.VisitViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class VisitViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val library = TestLibrary.library
    private lateinit var coach: FakeCoachApi
    private lateinit var repo: InMemoryVisitRepository
    private lateinit var speech: RecordingSpeech
    private lateinit var photos: FakePhotoStore
    private lateinit var settings: InMemorySettingsRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        coach = FakeCoachApi(library)
        repo = InMemoryVisitRepository()
        speech = RecordingSpeech()
        photos = FakePhotoStore()
        settings = InMemorySettingsRepository(Settings(maxExercises = 2))
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.vm(): VisitViewModel =
        VisitViewModel(coach, repo, settings, library, photos, speech, TestStrings, FixedEnv()).also { advanceUntilIdle() }

    private fun TestScope.checkIn(
        vm: VisitViewModel,
        mood: String = "I feel stressed and tense after work",
    ) {
        vm.updateName("Ana")
        vm.updateMood(mood)
        vm.setConsent(true)
        vm.submitCheckin()
        advanceUntilIdle()
    }

    private fun TestScope.doExercise(
        vm: VisitViewModel,
        photoCount: Int,
    ) {
        vm.startExercise()
        advanceUntilIdle()
        repeat(photoCount) {
            vm.onExercisePhoto(File("x"), mirror = false)
            advanceUntilIdle()
        }
        vm.endExercise()
        advanceUntilIdle()
    }

    private fun TestScope.say(
        vm: VisitViewModel,
        text: String,
    ) {
        vm.updateChatDraft(text)
        vm.sendChat()
        advanceUntilIdle()
    }

    @Test
    fun checkinValidationKeepsTheVisitorOnCheckin() =
        runTest(dispatcher) {
            val vm = vm()
            vm.submitCheckin()
            assertEquals(ErrorKind.NEED_NAME, vm.state.value.error?.kind)
            vm.updateName("Ana")
            vm.updateMood("ok")
            vm.submitCheckin()
            assertEquals(ErrorKind.NEED_CONSENT, vm.state.value.error?.kind)
            assertEquals(Screen.CHECKIN, vm.state.value.screen)
            assertTrue(coach.calls.isEmpty())
        }

    @Test
    fun fullFlowCheckinToLogbook() =
        runTest(dispatcher) {
            val vm = vm()
            checkIn(vm)
            var s = vm.state.value
            assertEquals(Screen.INTRO, s.screen)
            assertEquals("box-breathing", s.visit!!.current!!.exerciseId)
            assertNotNull(s.visit!!.checkin)
            assertTrue(speech.spoken.last().startsWith("Welcome, Ana."))

            doExercise(vm, photoCount = 3)
            s = vm.state.value
            assertEquals(Screen.CHAT, s.screen)
            assertEquals(listOf(0, 2), s.visit!!.current!!.selected)
            assertEquals(Role.COACH, s.visit!!.current!!.chat.single().role)

            say(vm, "Calmer now")
            assertNull(vm.state.value.visit!!.suggestion)
            say(vm, "My shoulders dropped")
            s = vm.state.value
            assertTrue(s.visit!!.suggestion!!.isNext)
            val nextId = s.visit!!.suggestion!!.exerciseId
            assertNotNull(library.find(nextId))

            vm.startNextExercise()
            advanceUntilIdle()
            s = vm.state.value
            assertEquals(Screen.INTRO, s.screen)
            assertEquals(2, s.visit!!.exercises.size)
            assertEquals(nextId, s.visit!!.current!!.exerciseId)

            doExercise(vm, photoCount = 1)
            assertEquals(listOf(0), vm.state.value.visit!!.current!!.selected)
            say(vm, "Good")
            say(vm, "Really good")
            // N = 2 exercises done: the coach proposes the end.
            assertFalse(vm.state.value.visit!!.suggestion!!.isNext)

            vm.endVisit()
            advanceUntilIdle()
            s = vm.state.value
            assertEquals(Screen.LOGBOOK, s.screen)
            assertEquals(2, s.visit!!.logbook!!.entries.size)
            assertNotNull(s.visit!!.endedAt)
            assertEquals(s.visit, repo.stored)

            vm.finishVisit()
            advanceUntilIdle()
            assertNull(vm.state.value.visit)
            assertNull(repo.stored)
            assertEquals(Screen.CHECKIN, vm.state.value.screen)
        }

    @Test
    fun endingEarlyDropsAnUnstartedExercise() =
        runTest(dispatcher) {
            val vm = vm()
            checkIn(vm)
            doExercise(vm, 2)
            say(vm, "fine")
            say(vm, "fine again")
            vm.startNextExercise()
            advanceUntilIdle()
            vm.endVisit()
            advanceUntilIdle()
            val v = vm.state.value.visit!!
            assertEquals(Screen.LOGBOOK, v.screen)
            assertEquals(1, v.exercises.size)
        }

    @Test
    fun crisisFromCheckinStopsTheFlow() =
        runTest(dispatcher) {
            val vm = vm()
            checkIn(vm, mood = "I want to die")
            val s = vm.state.value
            assertEquals(Screen.CRISIS, s.screen)
            assertTrue(s.visit!!.exercises.isEmpty())
            assertEquals(Screen.CRISIS, repo.stored!!.screen)
            assertEquals("Default crisis text.", s.visit!!.crisisText)
            assertTrue(speech.spoken.last().contains("Talk to the lab team."))
            // Nothing continues from the crisis screen.
            vm.startExercise()
            vm.endVisit()
            advanceUntilIdle()
            assertEquals(Screen.CRISIS, vm.state.value.screen)
            assertEquals(listOf("checkin"), coach.calls)
        }

    @Test
    fun crisisFromChatStopsTheFlow() =
        runTest(dispatcher) {
            val vm = vm()
            checkIn(vm)
            doExercise(vm, 1)
            say(vm, "honestly I think about suicide")
            val s = vm.state.value
            assertEquals(Screen.CRISIS, s.screen)
            assertNull(s.visit!!.suggestion)
            vm.startNextExercise()
            advanceUntilIdle()
            assertEquals(Screen.CRISIS, vm.state.value.screen)
        }

    @Test
    fun reflectErrorKeepsTheInputAndRetries() =
        runTest(dispatcher) {
            val vm = vm()
            checkIn(vm)
            doExercise(vm, 1)
            coach.failNext = CoachException(CoachException.Kind.NETWORK, "down")
            say(vm, "Calmer now")
            var s = vm.state.value
            assertEquals(ErrorKind.NETWORK, s.error?.kind)
            assertEquals(RetryAction.REFLECT, s.error?.retry)
            assertEquals("Calmer now", s.chatDraft)
            assertEquals(1, s.visit!!.current!!.chat.size)

            vm.retry()
            advanceUntilIdle()
            s = vm.state.value
            assertNull(s.error)
            assertEquals(3, s.visit!!.current!!.chat.size)
        }

    @Test
    fun checkinErrorKeepsTheDraft() =
        runTest(dispatcher) {
            val vm = vm()
            coach.failNext = CoachException(CoachException.Kind.INVALID_REPLY, "bad")
            checkIn(vm)
            val s = vm.state.value
            assertEquals(Screen.CHECKIN, s.screen)
            assertEquals(ErrorKind.INVALID_REPLY, s.error?.kind)
            assertEquals("Ana", s.draft.name)
            assertNull(repo.stored)
        }

    @Test
    fun photosAreCappedAtFiveAndSelectionFollowsDeletes() =
        runTest(dispatcher) {
            val vm = vm()
            checkIn(vm)
            vm.startExercise()
            advanceUntilIdle()
            repeat(7) {
                vm.onExercisePhoto(File("x"), false)
                advanceUntilIdle()
            }
            assertEquals(5, vm.state.value.visit!!.current!!.photos.size)
            vm.deletePhoto(0)
            advanceUntilIdle()
            assertEquals(4, vm.state.value.visit!!.current!!.photos.size)
            vm.endExercise()
            advanceUntilIdle()
            assertEquals(listOf(0, 3), vm.state.value.visit!!.current!!.selected)

            vm.endVisit()
            advanceUntilIdle()
            vm.togglePhoto(0, 1)
            advanceUntilIdle()
            assertEquals(listOf(1, 3), vm.state.value.visit!!.current!!.selected)
            vm.dontCare(0)
            advanceUntilIdle()
            assertEquals(listOf(0, 3), vm.state.value.visit!!.current!!.selected)
        }

    @Test
    fun resumesAVisitAfterProcessDeath() =
        runTest(dispatcher) {
            val first = vm()
            checkIn(first)
            doExercise(first, 2)
            val restored = vm()
            assertEquals(Screen.CHAT, restored.state.value.screen)
            assertEquals(2, restored.state.value.visit!!.current!!.photos.size)
        }
}
