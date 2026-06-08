package dev.twango.jetplay.star

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StarReminderPolicyTest {

    private val today = 20_250L

    @Test
    fun showsWhenNeverShownBefore() {
        assertTrue(StarReminderPolicy.shouldShow(dismissed = false, lastShownEpochDay = null, todayEpochDay = today))
    }

    @Test
    fun skipsWhenAlreadyShownToday() {
        assertFalse(StarReminderPolicy.shouldShow(dismissed = false, lastShownEpochDay = today, todayEpochDay = today))
    }

    @Test
    fun showsAgainOnANewDay() {
        assertTrue(StarReminderPolicy.shouldShow(dismissed = false, lastShownEpochDay = today - 1, todayEpochDay = today))
    }

    @Test
    fun neverShowsOnceDismissed() {
        assertFalse(StarReminderPolicy.shouldShow(dismissed = true, lastShownEpochDay = null, todayEpochDay = today))
        assertFalse(StarReminderPolicy.shouldShow(dismissed = true, lastShownEpochDay = today - 1, todayEpochDay = today))
    }
}
