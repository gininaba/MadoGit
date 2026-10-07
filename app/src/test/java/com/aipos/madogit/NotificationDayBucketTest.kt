package com.aipos.madogit

import com.aipos.madogit.ui.components.notificationDayBucket
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class NotificationDayBucketTest {

    private lateinit var originalTimeZone: TimeZone

    @Before
    fun setUp() {
        originalTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Manila"))
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(originalTimeZone)
    }

    private fun at(day: Int, hour: Int, minute: Int = 0): Long = Calendar.getInstance().apply {
        clear()
        set(2026, Calendar.OCTOBER, day, hour, minute)
    }.timeInMillis

    @Test
    fun `items are bucketed by calendar day, not by 24 hour windows`() {
        val now = at(7, 0, 30)

        assertEquals("Today", notificationDayBucket(at(7, 0, 5), now))
        assertEquals("Yesterday", notificationDayBucket(at(6, 1, 0), now))
        // 25.5 hours ago but two calendar days back: previously mislabelled "Yesterday".
        assertEquals("This Week", notificationDayBucket(at(5, 23, 0), now))
        assertEquals("This Week", notificationDayBucket(at(1, 0, 0), now))
        assertEquals("Earlier", notificationDayBucket(at(1, 0, 0) - 1, now))
    }
}
