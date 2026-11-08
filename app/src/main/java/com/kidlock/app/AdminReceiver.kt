package com.kidlock.app

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

/**
 * While this device admin is active, Android refuses to uninstall KidLock.
 * Turning it off happens in Settings, which LockService guards with the PIN.
 */
class AdminReceiver : DeviceAdminReceiver() {
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        "Turning this off allows KidLock to be uninstalled."
}
