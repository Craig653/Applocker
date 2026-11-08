package com.kidlock.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Full-screen PIN prompt shown on top of a locked app. */
class LockActivity : Activity() {

    companion object {
        const val EXTRA_PKG = "pkg"

        @Volatile
        var visibleFor: String? = null
    }

    private var pkg: String? = null
    private lateinit var pad: PinPad
    private lateinit var iconView: ImageView
    private lateinit var nameView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val d = resources.displayMetrics.density

        iconView = ImageView(this)
        nameView = TextView(this).apply {
            textSize = 18f
            setTextColor(0xCCFFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(0, (8 * d).toInt(), 0, (8 * d).toInt())
        }
        pad = PinPad(this) { onPin(it) }

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(0, (24 * d).toInt(), 0, (24 * d).toInt())
            addView(iconView, LinearLayout.LayoutParams((72 * d).toInt(), (72 * d).toInt()))
            addView(nameView)
            addView(pad)
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(0xFF1B2440.toInt())
            addView(column)
        }
        setContentView(scroll)
        bind(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        bind(intent)
    }

    private fun bind(i: Intent) {
        pkg = i.getStringExtra(EXTRA_PKG)
        val pm = packageManager
        try {
            val info = pm.getApplicationInfo(pkg ?: "", 0)
            iconView.setImageDrawable(pm.getApplicationIcon(info))
            nameView.text = "${pm.getApplicationLabel(info)} is locked"
        } catch (_: Exception) {
            iconView.setImageResource(R.drawable.ic_launcher)
            nameView.text = "This is locked"
        }
        pad.setTitle("Enter parent PIN")
        pad.setMessage("")
        pad.clear()
        visibleFor = pkg
    }

    private fun onPin(pin: String) {
        val wait = Prefs.lockoutRemainingMs()
        if (wait > 0) {
            pad.setMessage("Too many tries. Wait ${(wait + 999) / 1000} seconds.")
            pad.clear()
            return
        }
        if (Prefs.checkPin(this, pin)) {
            Prefs.recordSuccess(this)
            LockService.unlockedPkg = pkg
            visibleFor = null
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        } else {
            val cooldown = Prefs.recordFailure(this)
            pad.setMessage(if (cooldown) "Too many tries. Wait 30 seconds." else "Wrong PIN")
            pad.clear()
        }
    }

    override fun onResume() {
        super.onResume()
        visibleFor = pkg
    }

    override fun onPause() {
        super.onPause()
        visibleFor = null
    }

    /** Back button sends the child to the home screen instead of into the locked app. */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        goHome()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        finish()
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }
}
