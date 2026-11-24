package dev.caicompanion.app

import android.app.Activity
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/** One private, renderer-independent entry point for built-in tools. */
class ModuleCenterActivity : Activity() {
    private lateinit var results: LinearLayout
    private lateinit var search: EditText
    private var opening = false

    override fun onCreate(savedInstanceState: Bundle?) {
        ShellAppearance.apply(this)
        super.onCreate(savedInstanceState)
        if (SettingsStore(this).protectScreen) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ShellAppearance.color(this@ModuleCenterActivity, R.attr.shellBackground))
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            insets
        }
        val header = LinearLayout(this).apply {
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPaddingRelative(dp(8), dp(4), dp(16), dp(4))
        }
        header.addView(Button(this).apply {
            setText(R.string.back)
            setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_shell_back, 0, 0, 0)
            compoundDrawablePadding = dp(4)
            setOnClickListener { finish() }
        })
        header.addView(TextView(this).apply {
            setText(R.string.module_center_title)
            textSize = 22f
            setTextColor(ShellAppearance.color(this@ModuleCenterActivity, R.attr.shellText))
            setPaddingRelative(dp(12), 0, 0, 0)
            ViewCompat.setAccessibilityHeading(this, true)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(header)
        search = EditText(this).apply {
            setSingleLine(true)
            setHint(R.string.module_center_search)
            filters = arrayOf(InputFilter.LengthFilter(128))
            imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            isSaveEnabled = false
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        }
        root.addView(search, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            marginStart = dp(16); marginEnd = dp(16); bottomMargin = dp(8)
        })
        root.isFocusableInTouchMode = true
        root.requestFocus()
        val scroll = ScrollView(this).apply { isFillViewport = true }
        results = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPaddingRelative(dp(16), dp(4), dp(16), dp(16))
        }
        scroll.addView(results)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        ViewCompat.requestApplyInsets(root)
        search.setText(savedInstanceState?.getString("module_query").orEmpty().take(128))
        render(search.text.toString())
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { render(s.toString()) }
            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("module_query", search.text.toString())
        super.onSaveInstanceState(outState)
    }

    private fun dp(n: Int) = (n * resources.displayMetrics.density + 0.5f).toInt()

    private fun label(module: CompanionModules.Module): Int = when (module) {
        CompanionModules.Module.CHARACTER -> R.string.character_draft_title
        CompanionModules.Module.LOREBOOK -> R.string.lorebook_title
        CompanionModules.Module.PERSONA -> R.string.roleplay_persona
        CompanionModules.Module.SCENE -> R.string.roleplay_scene
        CompanionModules.Module.PROMPT -> R.string.roleplay_prompt
        CompanionModules.Module.MEMORY_BANK -> R.string.memory_bank_title
        CompanionModules.Module.CHAT_MEMORY -> R.string.roleplay_memory
        CompanionModules.Module.CONTEXT -> R.string.roleplay_context
        CompanionModules.Module.WIKIPEDIA -> R.string.wiki_research_title
        CompanionModules.Module.WEB_SEARCH -> R.string.roleplay_web_search
        CompanionModules.Module.MEMORY_BACKUP -> R.string.memory_transfer_title
        CompanionModules.Module.DICE -> R.string.roleplay_dice
        CompanionModules.Module.FREE_HELP -> R.string.free_experience_title
    }

    private fun groupLabel(group: CompanionModules.Group): Int = when (group) {
        CompanionModules.Group.CREATOR -> R.string.module_center_creator
        CompanionModules.Group.KNOWLEDGE -> R.string.module_center_knowledge
        CompanionModules.Group.UTILITIES -> R.string.module_center_utilities
    }

    private fun render(query: String) {
        val modules = CompanionModules.matching(query) { getString(label(it)) }
        results.removeAllViews()
        if (modules.isEmpty()) {
            results.addView(TextView(this).apply { setText(R.string.module_center_empty) })
            return
        }
        for (group in CompanionModules.Group.entries) {
            val members = modules.filter { it.group == group }
            if (members.isEmpty()) continue
            val panel = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundResource(R.drawable.panel_background)
                setPaddingRelative(dp(12), dp(10), dp(12), dp(12))
            }
            panel.addView(TextView(this).apply {
                setText(groupLabel(group))
                textSize = 14f
                setTextColor(ShellAppearance.color(this@ModuleCenterActivity, R.attr.shellAccent))
                setPaddingRelative(dp(4), dp(4), dp(4), dp(8))
                ViewCompat.setAccessibilityHeading(this, true)
            })
            for (module in members) {
                panel.addView(Button(this).apply {
                    setText(label(module))
                    minHeight = dp(48)
                    gravity = android.view.Gravity.CENTER_VERTICAL or android.view.Gravity.START
                    isAllCaps = false
                    setOnClickListener {
                        if (!opening && !isFinishing && !isDestroyed) {
                            opening = true
                            try { RoleplayToolkitUi.open(this@ModuleCenterActivity, module) }
                            finally { opening = false }
                        }
                    }
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(6)
                })
            }
            results.addView(panel, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(12)
            })
        }
    }
}
