package com.superchant.data

/** How the user counts: hardware volume keys (needs the accessibility service), on-screen taps, or both. */
enum class InputMode(val usesVolumeKeys: Boolean, val usesScreen: Boolean) {
    VOLUME(usesVolumeKeys = true, usesScreen = false),
    ON_SCREEN(usesVolumeKeys = false, usesScreen = true),
    BOTH(usesVolumeKeys = true, usesScreen = true);

    companion object {
        /**
         * Mode used until the user picks one. Existing users who already enabled the accessibility
         * service keep volume-only counting (no screen kept awake in a pocket); everyone else starts
         * with the permission-free on-screen mode.
         */
        fun default(accessibilityEnabled: Boolean): InputMode =
            if (accessibilityEnabled) VOLUME else ON_SCREEN

        fun fromName(name: String?): InputMode? = entries.firstOrNull { it.name == name }
    }
}
