package com.mentalcoach

import com.mentalcoach.domain.ExerciseLibrary
import com.mentalcoach.domain.Lang
import com.mentalcoach.media.PhotoStore
import com.mentalcoach.media.SpeechOutput
import com.mentalcoach.ui.Env
import com.mentalcoach.ui.VisitStrings
import java.io.File
import java.time.Instant

object TestLibrary {
    /** The real bundled library, read from the app's assets. */
    val library: ExerciseLibrary by lazy {
        val f = listOf("src/main/assets/exercises.json", "app/src/main/assets/exercises.json").map(::File).first { it.exists() }
        ExerciseLibrary.parse(f.readText())
    }
}

class RecordingSpeech : SpeechOutput {
    val spoken = mutableListOf<String>()
    var stops = 0

    override fun speak(
        text: String,
        lang: Lang,
    ) {
        spoken += text
    }

    override fun stop() {
        stops++
    }
}

class FakePhotoStore : PhotoStore {
    val saved = mutableListOf<String>()
    val deleted = mutableListOf<String>()
    val deletedVisits = mutableListOf<String>()
    private var n = 0

    override suspend fun importCapture(
        visitId: String,
        source: File,
        mirror: Boolean,
    ): String = "/visits/$visitId/photo${n++}.jpg".also { saved += it }

    override suspend fun delete(path: String) {
        deleted += path
    }

    override fun deleteVisit(visitId: String) {
        deletedVisits += visitId
    }

    override fun deleteAllExcept(visitId: String?) = Unit

    override fun tempFile(): File = File("unused")
}

object TestStrings : VisitStrings {
    override fun coachOpener(
        lang: Lang,
        name: String,
    ) = "How do you feel now, $name?"

    override fun photoPrompt(
        lang: Lang,
        number: Int,
        prompt: String,
    ) = "Photo $number. $prompt"

    override fun allPhotos(lang: Lang) = "All photos taken."

    override fun crisisDefault(lang: Lang) = "Default crisis text."

    override fun crisisStaff(lang: Lang) = "Talk to the lab team."
}

class FixedEnv : Env {
    var t: Instant = Instant.parse("2026-11-19T10:00:00Z")
    private var id = 0

    override fun now(): Instant = t.also { t = t.plusSeconds(60) }

    override fun newId(): String = "visit-${++id}"
}
