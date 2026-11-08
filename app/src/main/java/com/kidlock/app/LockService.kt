package com.kidlock.app

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager

/**
 * Watches which app comes to the front. If it's a locked app (or a Settings /
 * uninstall screen that could be used to remove KidLock), it shows the PIN screen.
 */
class LockService : AccessibilityService() {

    companion object {
        /** App the parent just unlocked; stays open until another app comes to the front. */
        @Volatile
        var unlockedPkg: String? = null

        @Volatile
        private var parentGraceUntil = 0L

        private const val SETTINGS_PKG = "com.android.settings"

        private val GUARDED_PKGS = setOf(
            SETTINGS_PKG,
            "com.google.android.packageinstaller",
            "com.android.packageinstaller",
            "com.samsung.android.packageinstaller",
        )

        private val IGNORED_PKGS = setOf(
            "android",
            "com.android.systemui",
            "com.google.android.permissioncontroller",
            "com.android.permissioncontroller",
        )

        fun isEnabled(ctx: Context): Boolean {
            val enabled = Settings.Secure.getString(
                ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val me = ComponentName(ctx, LockService::class.java)
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
        }

        /** Called when the parent (already PIN-verified) opens a Settings page from KidLock. */
        fun startParentGrace() {
            parentGraceUntil = SystemClock.elapsedRealtime() + 3 * 60_000L
            unlockedPkg = SETTINGS_PKG
        }

        fun endParentGrace() {
            parentGraceUntil = 0L
        }

        fun inParentGrace(): Boolean = SystemClock.elapsedRealtime() < parentGraceUntil
    }

    private var keyboardPkgs: Set<String> = emptySet()
    private var lastContentCheck = 0L
    private val appLabel by lazy { getString(R.string.app_name) }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        keyboardPkgs = imm.inputMethodList.map { it.packageName }.toSet()
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val e = event ?: return
        val pkg = e.packageName?.toString() ?: return
        if (pkg == packageName || pkg in IGNORED_PKGS || pkg in keyboardPkgs) return
        if (!Prefs.hasPin(this)) return

        when (e.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                // Leaving the unlocked app re-locks it.
                if (pkg != unlockedPkg) unlockedPkg = null
                if (pkg == unlockedPkg) return

                if (pkg in Prefs.lockedApps(this)) {
                    showLock(pkg)
                } else if (isGuarded(pkg) && mentionsKidLock(e)) {
                    showLock(pkg)
                }
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // Backup check (e.g. waking the screen on a locked app, or scrolling
                // Settings to KidLock's page). Throttled to keep battery use low.
                if (pkg == unlockedPkg) return
                val locked = pkg in Prefs.lockedApps(this)
                val guarded = isGuarded(pkg)
                if (!locked && !guarded) return
                val now = SystemClock.elapsedRealtime()
                if (now - lastContentCheck < 400) return
                lastContentCheck = now
                if (rootInActiveWindow?.packageName?.toString() != pkg) return
                if (locked || mentionsKidLock(e)) showLock(pkg)
            }
        }
    }

    private fun isGuarded(pkg: String): Boolean = pkg in GUARDED_PKGS && !inParentGrace()

    /** True if the screen shows KidLock's name (its App info, uninstall dialog, admin or accessibility page). */
    private fun mentionsKidLock(e: AccessibilityEvent): Boolean {
        if (e.text.any { it?.contains(appLabel, ignoreCase = true) == true }) return true
        return try {
            val root = rootInActiveWindow ?: return false
            root.findAccessibilityNodeInfosByText(appLabel).isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }

    private fun showLock(pkg: String) {
        if (LockActivity.visibleFor == pkg) return
        val i = Intent(this, LockActivity::class.java)
            .putExtra(LockActivity.EXTRA_PKG, pkg)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        startActivity(i)
    }
}
