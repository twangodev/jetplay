package dev.twango.jetplay.star

object StarReminderPolicy {

    fun shouldShow(dismissed: Boolean, lastShownEpochDay: Long?, todayEpochDay: Long): Boolean =
        !dismissed && lastShownEpochDay != todayEpochDay
}
