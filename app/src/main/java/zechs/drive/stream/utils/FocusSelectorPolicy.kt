package zechs.drive.stream.utils

/** Identifies whether a StateListDrawable explicitly owns the focused visual state. */
internal object FocusSelectorPolicy {
    fun hasFocusedState(stateSpecs: List<IntArray>, focusedStateAttribute: Int): Boolean =
        stateSpecs.any { spec -> spec.any { it == focusedStateAttribute } }
}
