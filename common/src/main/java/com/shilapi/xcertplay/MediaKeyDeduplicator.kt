package com.shilapi.xcertplay

/**
 * Drops repeated hardware media-key presses that arrive through several paths (media session,
 * activity dispatch, accessibility service) within a short window. The same physical press can
 * reach two or even all three paths on some head units, so they share one deduplicator.
 */
internal class MediaKeyDeduplicator(private val windowMillis: Long = DEFAULT_WINDOW_MILLIS) {
    private var lastKeyCode = UNSET
    private var lastEventTime = -1L

    /** True when the press should be forwarded; false when it duplicates a recent one. */
    @Synchronized
    fun shouldSend(keyCode: Int, eventTime: Long): Boolean {
        if (keyCode == lastKeyCode && lastEventTime >= 0 && eventTime - lastEventTime < windowMillis) {
            return false
        }
        lastKeyCode = keyCode
        lastEventTime = eventTime
        return true
    }

    companion object {
        const val DEFAULT_WINDOW_MILLIS = 500L
        private const val UNSET = -1
    }
}
