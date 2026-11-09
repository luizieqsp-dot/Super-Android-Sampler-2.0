
package com.samplers.android

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    companion object {
        init {
            System.loadLibrary("fluidsynth_bridge")
        }
    }

    private external fun nativeFluidSynthVersion(): String

    private val backgroundColor = Color.rgb(18, 20, 28)
    private val accentColor = Color.rgb(91, 110, 245)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = backgroundColor
        window.navigationBarColor = backgroundColor

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            setBackgroundColor(backgroundColor)
        }

        val title = TextView(this).apply {
            text = "ANDROID SAMPLER 2.0"
            textSize = 25f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        layout.addView(title)

        val subtitle = TextView(this).apply {
            text = "PROFESSIONAL SOUND ENGINE"
            textSize = 14f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 30)
        }
        layout.addView(subtitle)

        addButton(layout, "INSTRUMENT LIBRARY")
        addButton(layout, "VIRTUAL KEYBOARD - 88 KEYS")
        addButton(layout, "MIXER & EFFECTS")
        addButton(layout, "PRESETS")
        addButton(layout, "MIDI SETTINGS")

        val status = TextView(this).apply {
            text = "FLUIDSYNTH: ${nativeFluidSynthVersion()}"
            textSize = 13f
            setTextColor(Color.GREEN)
            gravity = Gravity.CENTER
            setPadding(0, 35, 0, 0)
        }
        layout.addView(status)

        setContentView(layout)
    }

    private fun addButton(layout: LinearLayout, label: String) {
        val button = Button(this).apply {
            text = label
            setTextColor(Color.WHITE)
            setBackgroundColor(accentColor)
        }

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, 8, 0, 8)
        }

        layout.addView(button, params)
    }
}
