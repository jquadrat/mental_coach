package com.mentalcoach.domain

/** Logbook photo rules: at most [MAX_SELECTED] per exercise; the default is the first and the last photo. */
object PhotoSelection {
    const val MAX_SELECTED = 2
    const val MAX_PHOTOS = 5

    fun default(photoCount: Int): List<Int> =
        when {
            photoCount <= 0 -> emptyList()
            photoCount == 1 -> listOf(0)
            else -> listOf(0, photoCount - 1)
        }

    /** Tapping a selected photo removes it; tapping another adds it, dropping the oldest pick if already at the limit. */
    fun toggle(
        selected: List<Int>,
        index: Int,
    ): List<Int> =
        if (index in selected) {
            selected - index
        } else {
            (selected + index).takeLast(MAX_SELECTED).sorted()
        }

    /** Re-maps selection after deleting the photo at [removed]. */
    fun afterRemoval(
        selected: List<Int>,
        removed: Int,
    ): List<Int> = selected.filter { it != removed }.map { if (it > removed) it - 1 else it }
}
