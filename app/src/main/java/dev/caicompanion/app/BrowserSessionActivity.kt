package dev.caicompanion.app

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent

/** A separate browser-owned session; neither embedded renderer receives its cookies. */
class BrowserSessionActivity : Activity() {
    companion object {
        private const val EXTRA_PAGE = "safe_page"
        private const val EXTRA_OPEN = "open_now"

        fun enter(activity: Activity, page: String?, openNow: Boolean) {
            if (activity.isFinishing || activity.isDestroyed) return
            val store = SettingsStore(activity)
            store.browserSessionPreferred = true
            activity.startActivity(Intent(activity, BrowserSessionActivity::class.java).apply {
                putExtra(EXTRA_PAGE, BrowserSessionPolicy.safeTabUrl(page))
                putExtra(EXTRA_OPEN, openNow)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            })
            activity.finish()
        }
    }

    private lateinit var store: SettingsStore
    private var destination = BrowserSessionPolicy.HOME
    private val fg = Color.rgb(228, 231, 240)
    private val bg = Color.rgb(17, 19, 25)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = SettingsStore(this)
        if (store.protectScreen) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val scroll = ScrollView(this).apply { setBackgroundColor(bg); isFillViewport = true }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(20))
        }
        scroll.addView(column, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT))
        if (Build.VERSION.SDK_INT >= 30) scroll.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        setContentView(scroll)
        scroll.requestApplyInsets()
        column.addView(TextView(this).apply {
            text = getString(R.string.browser_mode_title); textSize = 24f; setTextColor(fg)
        })
        column.addView(TextView(this).apply {
            text = getString(R.string.browser_mode_summary); textSize = 15f; setTextColor(fg)
            setPadding(0, dp(16), 0, dp(16))
        })
        column.addView(action(R.string.browser_mode_continue) { openTab() })
        column.addView(action(R.string.voice_title) {
            startActivity(Intent(this, VoiceDraftActivity::class.java))
        })
        column.addView(action(R.string.browser_mode_embedded) { returnToEmbedded() })
        column.addView(action(R.string.browser_mode_change_provider) {
            android.app.AlertDialog.Builder(this).setMessage(R.string.browser_mode_change_warning)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.browser_mode_change_provider) { _, _ ->
                    store.browserSessionProvider = ""
                    openTab()
                }.show()
        })
        column.addView(action(R.string.shell_session_info) {
            android.app.AlertDialog.Builder(this).setTitle(R.string.browser_mode_title)
                .setMessage(R.string.browser_mode_explanation)
                .setPositiveButton(R.string.close, null).show()
        })
        // A restored Activity must not relaunch a browser or claim that sign-in succeeded.
        consumeLaunch(intent, allowOpen = savedInstanceState == null)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        consumeLaunch(intent, allowOpen = true)
    }

    private fun consumeLaunch(incoming: Intent?, allowOpen: Boolean) {
        destination = BrowserSessionPolicy.safeTabUrl(incoming?.getStringExtra(EXTRA_PAGE))
        val openNow = allowOpen && incoming?.getBooleanExtra(EXTRA_OPEN, false) == true
        setIntent(Intent(this, BrowserSessionActivity::class.java))
        if (openNow) openTab()
    }

    private fun openTab() {
        // Pin the preferred Custom Tabs provider so an installed app link handler
        // cannot consume the first-party URL before the browser session opens.
        val pinned = store.browserSessionProvider.takeIf { it.isNotBlank() }
        val provider = if (pinned == null) CustomTabsClient.getPackageName(this, null)
            else CustomTabsClient.getPackageName(this, listOf(pinned), true)?.takeIf { it == pinned }
        if (provider == null) {
            Toast.makeText(this, if (pinned == null) R.string.browser_mode_no_provider
                else R.string.browser_mode_provider_unavailable, Toast.LENGTH_LONG).show()
            return
        }
        try {
            val colors = CustomTabColorSchemeParams.Builder()
                .setToolbarColor(Color.rgb(21, 22, 30)).setNavigationBarColor(Color.rgb(21, 22, 30)).build()
            val tab = CustomTabsIntent.Builder().setShowTitle(true)
                .setShareState(CustomTabsIntent.SHARE_STATE_OFF).setDefaultColorSchemeParams(colors).build()
            tab.intent.setPackage(provider)
            tab.launchUrl(this, Uri.parse(BrowserSessionPolicy.safeTabUrl(destination)))
            store.browserSessionProvider = provider
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.browser_mode_no_provider, Toast.LENGTH_LONG).show()
        } catch (_: SecurityException) {
            Toast.makeText(this, R.string.browser_mode_no_provider, Toast.LENGTH_LONG).show()
        }
    }

    private fun returnToEmbedded() {
        val launcher = packageManager.getLaunchIntentForPackage(packageName)
        if (launcher == null) {
            Toast.makeText(this, R.string.browser_mode_restart, Toast.LENGTH_LONG).show()
            store.browserSessionPreferred = false
            finish()
            return
        }
        store.browserSessionPreferred = false
        startActivity(launcher.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
        finish()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
    private fun action(label: Int, callback: () -> Unit): Button = Button(this).apply {
        setText(label); isAllCaps = false; setTextColor(fg)
        setBackgroundResource(R.drawable.settings_action)
        minHeight = dp(52)
        setOnClickListener { callback() }
    }
}
