package com.kidlock.app

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

/** Stores the parent PIN (salted hash only) and the set of locked apps. */
object Prefs {
    private const val FILE = "kidlock"
    private const val MAX_TRIES = 5
    private const val LOCKOUT_MS = 30_000L

    private fun sp(c: Context): SharedPreferences =
        c.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun hasPin(c: Context): Boolean = sp(c).contains("pin_hash")

    fun setPin(c: Context, pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val saltStr = Base64.encodeToString(salt, Base64.NO_WRAP)
        sp(c).edit()
            .putString("pin_salt", saltStr)
            .putString("pin_hash", hash(saltStr, pin))
            .putInt("fails", 0)
            .apply()
        lockoutUntil = 0L
    }

    fun checkPin(c: Context, pin: String): Boolean {
        val salt = sp(c).getString("pin_salt", null) ?: return false
        val stored = sp(c).getString("pin_hash", null) ?: return false
        return MessageDigest.isEqual(stored.toByteArray(), hash(salt, pin).toByteArray())
    }

    private fun hash(salt: String, pin: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest((salt + pin).toByteArray())
            .joinToString("") { "%02x".format(it) }

    fun lockedApps(c: Context): Set<String> =
        sp(c).getStringSet("locked", emptySet())?.toSet() ?: emptySet()

    fun setLocked(c: Context, pkg: String, locked: Boolean) {
        val set = lockedApps(c).toMutableSet()
        if (locked) set.add(pkg) else set.remove(pkg)
        sp(c).edit().putStringSet("locked", set).apply()
    }

    // ---- Wrong-PIN cooldown (5 wrong tries = 30 second wait) ----
    @Volatile
    private var lockoutUntil = 0L

    fun lockoutRemainingMs(): Long =
        (lockoutUntil - SystemClock.elapsedRealtime()).coerceAtLeast(0L)

    /** Returns true if this failure started a cooldown. */
    fun recordFailure(c: Context): Boolean {
        val fails = sp(c).getInt("fails", 0) + 1
        return if (fails >= MAX_TRIES) {
            sp(c).edit().putInt("fails", 0).apply()
            lockoutUntil = SystemClock.elapsedRealtime() + LOCKOUT_MS
            true
        } else {
            sp(c).edit().putInt("fails", fails).apply()
            false
        }
    }

    fun recordSuccess(c: Context) {
        sp(c).edit().putInt("fails", 0).apply()
    }
}
