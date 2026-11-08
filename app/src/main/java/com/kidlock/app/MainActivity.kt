package com.kidlock.app

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Parent settings: PIN gate, protection status and the list of apps to lock. */
class MainActivity : Activity() {

    private data class AppInfo(val pkg: String, val label: String, val icon: Drawable)

    private enum class Screen { SETUP, ENTER, CHANGE, DASHBOARD }

    private var authed = false
    private var screen = Screen.ENTER
    private var firstPin: String? = null

    private var allApps: List<AppInfo>? = null
    private var shownApps: List<AppInfo> = emptyList()
    private var locked: MutableSet<String> = mutableSetOf()
    private var query = ""

    private lateinit var adapter: AppAdapter
    private var a11yStatus: TextView? = null
    private var a11yButton: Button? = null
    private var a11yHelp: View? = null
    private var adminStatus: TextView? = null
    private var adminButton: Button? = null
    private var loading: ProgressBar? = null

    private val adminComponent by lazy { ComponentName(this, AdminReceiver::class.java) }

    private val recommended = mapOf(
        "com.android.settings" to "Recommended: stops kids turning KidLock off",
        "com.android.vending" to "Recommended: stops installing or removing apps",
        "com.sec.android.app.samsungapps" to "Recommended: stops installing or removing apps",
    )

    // ---------------- Lifecycle ----------------

    override fun onStart() {
        super.onStart()
        render()
    }

    override fun onResume() {
        super.onResume()
        LockService.endParentGrace()
        if (screen == Screen.DASHBOARD) refreshStatus()
    }

    override fun onStop() {
        super.onStop()
        // Leaving the app requires the PIN again, unless the parent just jumped
        // into a Settings page from here.
        if (!LockService.inParentGrace()) authed = false
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (screen == Screen.CHANGE) showDashboard()
        else super.onBackPressed()
    }

    private fun render() {
        when {
            !Prefs.hasPin(this) -> showPinSetup(Screen.SETUP)
            !authed -> showPinEntry()
            screen == Screen.CHANGE -> Unit // keep the change-PIN screen as is
            else -> showDashboard()
        }
    }

    // ---------------- PIN screens ----------------

    private fun pinScreen(pad: PinPad, subtitle: String? = null) {
        val d = resources.displayMetrics.density
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(0, (24 * d).toInt(), 0, (24 * d).toInt())
            addView(TextView(this@MainActivity).apply {
                text = "KidLock"
                textSize = 30f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
            })
            if (subtitle != null) addView(TextView(this@MainActivity).apply {
                text = subtitle
                textSize = 15f
                setTextColor(0xCCFFFFFF.toInt())
                gravity = Gravity.CENTER
                setPadding((24 * d).toInt(), (8 * d).toInt(), (24 * d).toInt(), 0)
            })
            addView(pad)
        }
        setContentView(ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(0xFF1B2440.toInt())
            addView(column)
        })
    }

    private fun showPinSetup(mode: Screen) {
        screen = mode
        firstPin = null
        lateinit var pad: PinPad
        pad = PinPad(this) { pin ->
            val first = firstPin
            if (first == null) {
                firstPin = pin
                pad.setTitle("Enter the same PIN again")
                pad.setMessage("")
                pad.clear()
            } else if (first == pin) {
                Prefs.setPin(this, pin)
                authed = true
                Toast.makeText(this, "PIN saved", Toast.LENGTH_SHORT).show()
                showDashboard()
            } else {
                firstPin = null
                pad.setTitle(if (mode == Screen.SETUP) "Create a 4-digit parent PIN" else "Enter a new 4-digit PIN")
                pad.setMessage("PINs didn't match. Try again.")
                pad.clear()
            }
        }
        pad.setTitle(if (mode == Screen.SETUP) "Create a 4-digit parent PIN" else "Enter a new 4-digit PIN")
        pinScreen(
            pad,
            if (mode == Screen.SETUP) "Kids will need this PIN to open locked apps or remove KidLock." else null
        )
    }

    private fun showPinEntry() {
        screen = Screen.ENTER
        lateinit var pad: PinPad
        pad = PinPad(this) { pin ->
            val wait = Prefs.lockoutRemainingMs()
            if (wait > 0) {
                pad.setMessage("Too many tries. Wait ${(wait + 999) / 1000} seconds.")
                pad.clear()
            } else if (Prefs.checkPin(this, pin)) {
                Prefs.recordSuccess(this)
                authed = true
                showDashboard()
            } else {
                val cooldown = Prefs.recordFailure(this)
                pad.setMessage(if (cooldown) "Too many tries. Wait 30 seconds." else "Wrong PIN")
                pad.clear()
            }
        }
        pad.setTitle("Enter parent PIN")
        pinScreen(pad)
    }

    // ---------------- Dashboard ----------------

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun showDashboard() {
        screen = Screen.DASHBOARD
        locked = Prefs.lockedApps(this).toMutableSet()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFF5F6FA.toInt())
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(8))
        }
        header.addView(TextView(this).apply {
            text = "KidLock"
            textSize = 26f
            setTypeface(null, Typeface.BOLD)
            setTextColor(0xFF1B2440.toInt())
        })

        // Step 1: accessibility service (does the actual locking)
        val a11yRow = statusRow()
        a11yStatus = a11yRow.first
        a11yButton = a11yRow.second.apply {
            text = "Turn on"
            setOnClickListener {
                LockService.startParentGrace()
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                Toast.makeText(
                    this@MainActivity,
                    "Tap Installed apps → KidLock → turn it on",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        header.addView(a11yRow.third)

        a11yHelp = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(8))
            addView(TextView(this@MainActivity).apply {
                text = "If Android says \"Restricted setting\": tap App info below, then the ⋮ menu (top right) → Allow restricted settings, then try Turn on again."
                textSize = 13f
                setTextColor(0xFF5A6070.toInt())
            })
            addView(Button(this@MainActivity).apply {
                text = "Open KidLock app info"
                isAllCaps = false
                setOnClickListener {
                    LockService.startParentGrace()
                    startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", packageName, null)
                        )
                    )
                }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        header.addView(a11yHelp)

        // Step 2: device admin (blocks uninstalling)
        val adminRow = statusRow()
        adminStatus = adminRow.first
        adminButton = adminRow.second.apply {
            text = "Turn on"
            setOnClickListener {
                LockService.startParentGrace()
                startActivity(
                    Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                        .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                        .putExtra(
                            DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                            "Stops KidLock from being uninstalled without the parent PIN."
                        )
                )
            }
        }
        header.addView(adminRow.third)

        header.addView(Button(this).apply {
            text = "Change PIN"
            isAllCaps = false
            setOnClickListener { showPinSetup(Screen.CHANGE) }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        header.addView(TextView(this).apply {
            text = "Apps to lock"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(0xFF1B2440.toInt())
            setPadding(0, dp(12), 0, dp(4))
        })
        header.addView(EditText(this).apply {
            hint = "Search apps"
            setSingleLine()
            setText(query)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    query = s?.toString().orEmpty()
                    applyFilter()
                }
            })
        })
        root.addView(header)

        loading = ProgressBar(this)
        root.addView(loading, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(16) })

        adapter = AppAdapter()
        val list = ListView(this).apply {
            adapter = this@MainActivity.adapter
            setOnItemClickListener { _, _, position, _ ->
                val app = shownApps[position]
                val nowLocked = app.pkg !in locked
                if (nowLocked) locked.add(app.pkg) else locked.remove(app.pkg)
                Prefs.setLocked(this@MainActivity, app.pkg, nowLocked)
                this@MainActivity.adapter.notifyDataSetChanged()
            }
        }
        root.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        setContentView(root)
        refreshStatus()
        loadApps()
    }

    /** Returns (status text, button, row container). */
    private fun statusRow(): Triple<TextView, Button, LinearLayout> {
        val status = TextView(this).apply {
            textSize = 15f
            setTextColor(0xFF1B2440.toInt())
        }
        val button = Button(this).apply { isAllCaps = false }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, dp(6))
            addView(status, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(button)
        }
        return Triple(status, button, row)
    }

    private fun refreshStatus() {
        val a11yOn = LockService.isEnabled(this)
        a11yStatus?.text = if (a11yOn) "✅  App locking is ON" else "⚠️  Step 1: App locking is OFF"
        a11yButton?.visibility = if (a11yOn) View.GONE else View.VISIBLE
        a11yHelp?.visibility = if (a11yOn) View.GONE else View.VISIBLE

        val dpm = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminOn = dpm.isAdminActive(adminComponent)
        adminStatus?.text =
            if (adminOn) "✅  Uninstall protection is ON" else "⚠️  Step 2: Uninstall protection is OFF"
        adminButton?.visibility = if (adminOn) View.GONE else View.VISIBLE
    }

    // ---------------- App list ----------------

    private fun loadApps() {
        allApps?.let { applyFilter(); return }
        Thread {
            val pm = packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val apps = pm.queryIntentActivities(intent, 0)
                .distinctBy { it.activityInfo.packageName }
                .filter { it.activityInfo.packageName != packageName }
                .map {
                    AppInfo(
                        it.activityInfo.packageName,
                        it.loadLabel(pm).toString(),
                        it.activityInfo.applicationInfo.loadIcon(pm)
                    )
                }
                .sortedWith(compareBy({ it.pkg !in recommended }, { it.label.lowercase() }))
            runOnUiThread {
                allApps = apps
                applyFilter()
            }
        }.start()
    }

    private fun applyFilter() {
        val apps = allApps ?: return
        loading?.visibility = View.GONE
        val q = query.trim()
        shownApps = if (q.isEmpty()) apps else apps.filter { it.label.contains(q, ignoreCase = true) }
        if (::adapter.isInitialized) adapter.notifyDataSetChanged()
    }

    private inner class AppAdapter : BaseAdapter() {
        override fun getCount() = shownApps.size
        override fun getItem(position: Int) = shownApps[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val row = (convertView as? LinearLayout) ?: buildRow()
            val app = shownApps[position]
            val icon = row.getChildAt(0) as ImageView
            val texts = row.getChildAt(1) as LinearLayout
            val label = texts.getChildAt(0) as TextView
            val sub = texts.getChildAt(1) as TextView
            val check = row.getChildAt(2) as CheckBox

            icon.setImageDrawable(app.icon)
            label.text = app.label
            val note = recommended[app.pkg]
            sub.text = note.orEmpty()
            sub.visibility = if (note != null) View.VISIBLE else View.GONE
            val isLocked = app.pkg in locked
            check.isChecked = isLocked
            row.setBackgroundColor(if (isLocked) 0xFFE3E8FF.toInt() else Color.WHITE)
            return row
        }

        private fun buildRow(): LinearLayout = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(10), dp(20), dp(10))
            addView(ImageView(context), LinearLayout.LayoutParams(dp(44), dp(44)))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), 0, dp(8), 0)
                addView(TextView(context).apply {
                    textSize = 17f
                    setTextColor(0xFF1B2440.toInt())
                })
                addView(TextView(context).apply {
                    textSize = 12f
                    setTextColor(0xFF3D5AFE.toInt())
                })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(CheckBox(context).apply {
                isClickable = false
                isFocusable = false
            })
        }
    }
}
