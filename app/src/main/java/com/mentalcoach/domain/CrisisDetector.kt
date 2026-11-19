package com.mentalcoach.domain

/** On-device keyword backstop so crisis handling never depends on the model alone. */
object CrisisDetector {
    private val WORDS =
        listOf(
            "suicide",
            "suicidal",
            "kill myself",
            "end my life",
            "hurt myself",
            "self-harm",
            "self harm",
            "want to die",
            "selbstmord",
            "suizid",
            "umbringen",
            "mir das leben nehmen",
            "nicht mehr leben",
            "ritzen",
        )

    fun matches(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val t = text.lowercase()
        return WORDS.any { t.contains(it) }
    }
}
