package com.spacegoose.carlauncher.automation

/** The timing rules, pure so they can be unit tested. */
object CarStartRules {
    /** Off for at least this long before the screen (or power) comes back counts as the car starting. */
    const val MIN_OFF_MS = 2 * 60_000L
    const val MIN_BETWEEN_STARTS_MS = 60_000L

    /** [offAt] 0 means we never saw it go off (say the app was installed with the screen on): not a start. */
    fun longEnough(offAt: Long, now: Long): Boolean = offAt > 0 && now - offAt >= MIN_OFF_MS

    fun notTooSoon(lastStartAt: Long, now: Long): Boolean = lastStartAt <= 0 || now - lastStartAt >= MIN_BETWEEN_STARTS_MS || now < lastStartAt
}
