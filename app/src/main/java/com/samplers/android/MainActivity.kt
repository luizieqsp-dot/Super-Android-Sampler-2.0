
package com.samplers.android

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File

class MainActivity : Activity() {

    companion object {
        init {
            System.loadLibrary("fluidsynth_bridge")
        }

        private const val SAMPLE_RATE = 44100
        private const val REQUEST_SOUNDFONT = 1001
        private const val BLOCK_FRAMES = 512
    }

    private external fun nativeFluidSynthVersion(): String
    private external fun nativeInit(sampleRate: Int): Boolean
    private external fun nativeLoadSoundFont(path: String): Int
    private external fun nativeNoteOn(channel: Int, key: Int, velocity: Int): Boolean
    private external fun nativeNoteOff(channel: Int, key: Int): Boolean
    private external fun nativeRenderAudio(output: ShortArray, frames: Int): Boolean
    private external fun nativeClose()

    private var audioTrack: AudioTrack? = null
    private var audioThread: Thread? = null
    private var audioRunning = false
    private var soundFontLoaded = false
    private var keyboardOpen = false

    private lateinit var status: TextView

    private val backgroundColor = Color.rgb(18, 20, 28)
    private val accentColor = Color.rgb(91, 110, 245)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = backgroundColor
        window.navigationBarColor = backgroundColor

        showMainScreen()

        val initialized = nativeInit(SAMPLE_RATE)

        if (initialized) {
            status.text =
                "FLUIDSYNTH: ${nativeFluidSynthVersion()}\nAGUARDANDO SOUNDFONT"
            status.setTextColor(Color.GREEN)
        } else {
            status.text = "ERRO AO INICIALIZAR FLUIDSYNTH"
            status.setTextColor(Color.RED)
        }
    }

    private fun showMainScreen() {
        keyboardOpen = false

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
            text = "FLUIDSYNTH AUDIO ENGINE"
            textSize = 14f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 25)
        }
        layout.addView(subtitle)

        addButton(layout, "CARREGAR SOUNDFONT (.SF2)") {
            openSoundFontPicker()
        }

        addButton(layout, "VIRTUAL KEYBOARD - 88 KEYS") {
            showKeyboardScreen()
        }

        addButton(layout, "TOCAR DÓ4") {
            if (soundFontLoaded) nativeNoteOn(0, 60, 100)
        }

        addButton(layout, "PARAR DÓ4") {
            if (soundFontLoaded) nativeNoteOff(0, 60)
        }

        addButton(layout, "INSTRUMENT LIBRARY") {}
        addButton(layout, "MIXER & EFFECTS") {}
        addButton(layout, "PRESETS") {}
        addButton(layout, "MIDI SETTINGS") {}

        status = TextView(this).apply {
            text = "INICIALIZANDO FLUIDSYNTH..."
            textSize = 14f
            setTextColor(Color.YELLOW)
            gravity = Gravity.CENTER
            setPadding(0, 30, 0, 0)
        }
        layout.addView(status)

        setContentView(layout)
    }

    private fun showKeyboardScreen() {
        keyboardOpen = true

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 16, 12, 12)
            setBackgroundColor(backgroundColor)
        }

        val title = TextView(this).apply {
            text = "VIRTUAL KEYBOARD - 88 KEYS"
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 16)
        }
        layout.addView(title)

        val instructions = TextView(this).apply {
            text = if (soundFontLoaded) {
                "SoundFont carregado • Toque nas teclas para tocar"
            } else {
                "Carregue um SoundFont na tela principal"
            }
            textSize = 13f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 12)
        }
        layout.addView(instructions)

        val keyboard = PianoKeyboardView(
            this,
            onNoteOn = { midi, velocity ->
                if (soundFontLoaded) {
                    nativeNoteOn(0, midi, velocity)
                }
            },
            onNoteOff = { midi ->
                if (soundFontLoaded) {
                    nativeNoteOff(0, midi)
                }
            }
        )

        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = true
            isFillViewport = false
            addView(
                keyboard,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }

        layout.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        addButton(layout, "VOLTAR À TELA PRINCIPAL") {
            keyboard.releaseAllNotes()
            showMainScreen()
        }

        setContentView(layout)
    }

    private fun openSoundFontPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }

        startActivityForResult(intent, REQUEST_SOUNDFONT)
    }

    @Deprecated("Using activity result callback for compatibility")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_SOUNDFONT &&
            resultCode == RESULT_OK
        ) {
            val uri: Uri = data?.data ?: return
            copyAndLoadSoundFont(uri)
        }
    }

    private fun copyAndLoadSoundFont(uri: Uri) {
        try {
            val soundFontFile = File(cacheDir, "active_soundfont.sf2")

            contentResolver.openInputStream(uri)?.use { input ->
                soundFontFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: throw Exception("Não foi possível abrir o arquivo")

            if (soundFontFile.length() == 0L) {
                throw Exception("Arquivo SoundFont vazio")
            }

            stopAudio()

            val id = nativeLoadSoundFont(soundFontFile.absolutePath)

            if (id >= 0) {
                soundFontLoaded = true
                startAudio()

                status.text = "SOUNDFONT CARREGADO!\nID: $id"
                status.setTextColor(Color.GREEN)
            } else {
                soundFontLoaded = false
                status.text = "ERRO AO CARREGAR SOUNDFONT"
                status.setTextColor(Color.RED)
            }

        } catch (e: Exception) {
            status.text = "ERRO: ${e.message}"
            status.setTextColor(Color.RED)
        }
    }

    private fun startAudio() {
        val minBuffer = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        if (minBuffer <= 0) {
            status.text = "ERRO NA CONFIGURAÇÃO DE ÁUDIO"
            return
        }

        val bufferSize = maxOf(minBuffer, BLOCK_FRAMES * 2 * 2 * 4)

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack = track
        audioRunning = true
        track.play()

        audioThread = Thread {
            val pcm = ShortArray(BLOCK_FRAMES * 2)

            while (audioRunning) {
                val rendered = nativeRenderAudio(pcm, BLOCK_FRAMES)

                if (!rendered) pcm.fill(0)

                track.write(
                    pcm,
                    0,
                    pcm.size,
                    AudioTrack.WRITE_BLOCKING
                )
            }
        }.apply {
            name = "FluidSynth-Audio"
            start()
        }
    }

    private fun stopAudio() {
        audioRunning = false

        try {
            audioThread?.join(1000)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }

        audioThread = null

        audioTrack?.let {
            try {
                it.pause()
                it.flush()
                it.stop()
            } catch (_: Exception) {
            }
            it.release()
        }

        audioTrack = null
    }

    private fun addButton(
        layout: LinearLayout,
        label: String,
        action: () -> Unit
    ) {
        val button = Button(this).apply {
            text = label
            setTextColor(Color.WHITE)
            setBackgroundColor(accentColor)
            setOnClickListener { action() }
        }

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, 8, 0, 8)
        }

        layout.addView(button, params)
    }

    @Deprecated("Using legacy back callback for compatibility")
    override fun onBackPressed() {
        if (keyboardOpen) {
            showMainScreen()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        stopAudio()
        nativeClose()
        super.onDestroy()
    }
}
