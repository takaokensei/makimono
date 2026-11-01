package zechs.drive.stream.domain.profile

/** Zero is deliberately complete: existing profiles never acquire a new setup gate. */
internal object ProfileSetupPolicy {
    const val COMPLETE = 0
    const val AUDIO = 1
    const val SUBTITLES = 2
    const val LIBRARY = 3
    fun normalize(step: Int): Int = step.takeIf { it in AUDIO..LIBRARY } ?: COMPLETE
    fun previous(step: Int): Int = (step - 1).coerceAtLeast(AUDIO)
}
