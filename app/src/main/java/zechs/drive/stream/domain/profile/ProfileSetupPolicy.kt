package zechs.drive.stream.domain.profile

/** Zero is deliberately complete: existing profiles never acquire a new setup gate. */
internal object ProfileSetupPolicy {
    const val COMPLETE = 0
    const val LANGUAGE = 1
    const val AUDIO = 2
    const val SUBTITLES = 3
    const val LIBRARY = 4
    const val TOTAL_STEPS = 4

    fun normalize(step: Int): Int = step.takeIf { it in LANGUAGE..LIBRARY } ?: COMPLETE
    fun previous(step: Int): Int = (step - 1).coerceAtLeast(LANGUAGE)
}
