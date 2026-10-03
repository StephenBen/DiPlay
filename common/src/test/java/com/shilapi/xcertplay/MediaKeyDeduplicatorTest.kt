package com.shilapi.xcertplay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaKeyDeduplicatorTest {
    @Test
    fun sameKeyWithinWindowIsDropped() {
        val deduplicator = MediaKeyDeduplicator(windowMillis = 500)

        assertTrue(deduplicator.shouldSend(KEYCODE_NEXT, eventTime = 1000))
        assertFalse(deduplicator.shouldSend(KEYCODE_NEXT, eventTime = 1400))
    }

    @Test
    fun differentKeysDoNotSuppressEachOther() {
        val deduplicator = MediaKeyDeduplicator(windowMillis = 500)

        assertTrue(deduplicator.shouldSend(KEYCODE_NEXT, eventTime = 1000))
        assertTrue(deduplicator.shouldSend(KEYCODE_PREVIOUS, eventTime = 1200))
        assertTrue(deduplicator.shouldSend(KEYCODE_NEXT, eventTime = 1300))
    }

    @Test
    fun sameKeyOutsideWindowIsSent() {
        val deduplicator = MediaKeyDeduplicator(windowMillis = 500)

        assertTrue(deduplicator.shouldSend(KEYCODE_NEXT, eventTime = 1000))
        assertTrue(deduplicator.shouldSend(KEYCODE_NEXT, eventTime = 1500))
    }

    @Test
    fun pressExactlyAtWindowEdgeIsSent() {
        val deduplicator = MediaKeyDeduplicator(windowMillis = 500)

        assertTrue(deduplicator.shouldSend(KEYCODE_NEXT, eventTime = 1000))
        assertTrue(deduplicator.shouldSend(KEYCODE_NEXT, eventTime = 1500))
        assertFalse(deduplicator.shouldSend(KEYCODE_NEXT, eventTime = 1999))
    }

    private companion object {
        const val KEYCODE_NEXT = 87
        const val KEYCODE_PREVIOUS = 88
    }
}
