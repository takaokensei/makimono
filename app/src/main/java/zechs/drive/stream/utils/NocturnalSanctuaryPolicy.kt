package zechs.drive.stream.utils

import java.util.Calendar

enum class NocturnalSchedule(val value: String) {
    AUTOMATIC("auto"),
    ALWAYS_ON("always"),
    OFF("off");

    companion object {
        fun fromValue(value: String?): NocturnalSchedule {
            return entries.firstOrNull { it.value.equals(value, ignoreCase = true) } ?: AUTOMATIC
        }
    }
}

object NocturnalSanctuaryPolicy {

    const val NIGHT_START_HOUR = 22 // 22:00 (10 PM)
    const val NIGHT_END_HOUR = 5   // 05:00 (5 AM)

    /**
     * Returns true if late-night sanctuary mode should be active based on policy and schedule.
     */
    fun isNocturnalActive(
        schedule: NocturnalSchedule,
        currentHour: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    ): Boolean {
        return when (schedule) {
            NocturnalSchedule.ALWAYS_ON -> true
            NocturnalSchedule.OFF -> false
            NocturnalSchedule.AUTOMATIC -> {
                currentHour >= NIGHT_START_HOUR || currentHour < NIGHT_END_HOUR
            }
        }
    }

    /**
     * Resolves the OLED eye-comfort dimming opacity (0.0f = no dim, 0.18f = late-night anti-glare).
     */
    fun resolveOledDimAlpha(isNocturnal: Boolean): Float {
        return if (isNocturnal) 0.18f else 0.0f
    }
}
