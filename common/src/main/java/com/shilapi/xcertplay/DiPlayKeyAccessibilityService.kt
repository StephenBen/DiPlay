package com.shilapi.xcertplay

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.shilapi.xcertplay.airplay.CarPlayMediaButton

/**
 * Last-resort path for steering-wheel media keys the head unit does not route to the audio-focus
 * owner's media session (e.g. BYD DiLink 3.0). Keys go through [CarPlayMediaKeys.sendHardwareKey],
 * sharing the deduplicator with the media session and the activity so one press sends once.
 *
 * The user enables it once in the head unit's Settings → Accessibility; [CarPlayMediaKeys]
 * tolerates the service outliving the activity: without a controller the send is a no-op.
 */
class DiPlayKeyAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent) = Unit

    override fun onInterrupt() = Unit

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (AirPlayPersistence.loadKeyDiagnosticsEnabled(this)) {
            Log.i(TAG, "Key diag: a11y key=${event.keyCode} action=${event.action}")
            CarPlayMediaKeys.logDiagnostic("Key diag: a11y key=${event.keyCode} action=${event.action}")
        }
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 &&
            CarPlayMediaButton.forKeyCode(event.keyCode) != null
        ) {
            return CarPlayMediaKeys.sendHardwareKey(event.keyCode, event.eventTime, "a11y")
        }
        return false
    }

    companion object {
        private const val TAG = "DiPlay-KeyA11y"
    }
}
