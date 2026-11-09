
package com.samplers.android

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
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

    private external fun nativeGetPresets(): Array<String>
    private external fun nativeGetSoundFonts(): Array<String>
    private external fun nativeSelectSoundFont(soundFontId: Int): Boolean

    private external fun nativeSelectPreset(
        channel: Int,
        bank: Int,
        program: Int
    ): Boolean

    private var audioTrack: AudioTrack? = null
    private var audioThread: Thread? = null
    private var audioRunning = false

    private var soundFontLoaded = false
    private var keyboardOpen = false
    private var libraryOpen = false

    private var activeSoundFontId = -1
    private var showOnlyFavorites = false

    private lateinit var status: TextView

    private val presetPrefs by lazy {
        getSharedPreferences("sampler_presets", MODE_PRIVATE)
    }

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
                "FLUIDSYNTH: ${nativeFluidSynthVersion()}\nVERIFICANDO BIBLIOTECAS..."

            status.setTextColor(Color.GREEN)

            restoreSoundFonts()
        } else {
            status.text = "ERRO AO INICIALIZAR FLUIDSYNTH"
            status.setTextColor(Color.RED)
        }
    }

    private fun showMainScreen() {
        keyboardOpen = false
        libraryOpen = false

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

        addButton(layout, "SOUNDFONT MANAGER") {
            showSoundFontManager()
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

        addButton(layout, "INSTRUMENT LIBRARY") {
            showInstrumentLibrary()
        }

        addButton(layout, "MIXER & EFFECTS") {
            showComingSoon("MIXER & EFFECTS")
        }

        addButton(layout, "PRESETS") {
            showPresetManager()
        }

        addButton(layout, "MIDI SETTINGS") {
            showComingSoon("MIDI SETTINGS")
        }

        status = TextView(this).apply {
            text = if (soundFontLoaded) {
                "SOUNDFONT CARREGADO"
            } else {
                "INICIALIZANDO FLUIDSYNTH..."
            }
            textSize = 14f
            setTextColor(Color.YELLOW)
            gravity = Gravity.CENTER
            setPadding(0, 30, 0, 0)
        }
        layout.addView(status)

        setContentView(layout)
    }

    private fun showComingSoon(module: String) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
            setBackgroundColor(backgroundColor)
        }

        val title = TextView(this).apply {
            text = module
            textSize = 23f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 20)
        }

        layout.addView(title)

        val message = TextView(this).apply {
            text = "MÓDULO EM DESENVOLVIMENTO"
            textSize = 16f
            setTextColor(Color.YELLOW)
            gravity = Gravity.CENTER
            setPadding(0, 30, 0, 30)
        }

        layout.addView(message)

        addButton(layout, "VOLTAR") {
            showMainScreen()
        }

        setContentView(layout)
    }

    private fun showSoundFontManager() {
        keyboardOpen = false
        libraryOpen = true

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 12)
            setBackgroundColor(backgroundColor)
        }

        val title = TextView(this).apply {
            text = "SOUNDFONT MANAGER"
            textSize = 22f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 16)
        }
        layout.addView(title)

        val info = TextView(this).apply {
            text = "Bibliotecas de áudio carregadas"
            textSize = 14f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 12)
        }
        layout.addView(info)

        val scroll = ScrollView(this)

        val fontList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        scroll.addView(fontList)

        layout.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        try {
            val fonts = nativeGetSoundFonts()

            if (fonts.isEmpty()) {
                info.text = "Nenhum SoundFont carregado."
            } else {
                info.text = "${fonts.size} SoundFont(s) carregado(s)"

                fonts.forEach { font ->
                    val parts = font.split("|", limit = 2)

                    if (parts.size == 2) {
                        val id = parts[0].toIntOrNull()
                        val name = parts[1]

                        if (id != null) {
                            addButton(fontList, "SELECIONAR: $name") {
                                val selected = nativeSelectSoundFont(id)

                                if (selected) {
                                    soundFontLoaded = true
                                    activeSoundFontId = id

                                    info.text = "SoundFont ativo: $name"
                                    status.text = "SOUNDFONT ATIVO: $name"
                                    status.setTextColor(Color.GREEN)
                                } else {
                                    info.text =
                                        "Não foi possível selecionar: $name"
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            info.text = "Erro ao consultar bibliotecas: ${e.message}"
        }

        addButton(layout, "CARREGAR OUTRO SOUNDFONT") {
            openSoundFontPicker()
        }

        addButton(layout, "ABRIR INSTRUMENT LIBRARY") {
            showInstrumentLibrary()
        }

        addButton(layout, "VOLTAR À TELA PRINCIPAL") {
            showMainScreen()
        }

        setContentView(layout)
    }

    private fun showKeyboardScreen() {
        keyboardOpen = true
        libraryOpen = false

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

    private fun showInstrumentLibrary() {
        keyboardOpen = false
        libraryOpen = true

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 12)
            setBackgroundColor(backgroundColor)
        }

        val title = TextView(this).apply {
            text = "INSTRUMENT LIBRARY"
            textSize = 22f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 16)
        }
        layout.addView(title)

        val info = TextView(this).apply {
            text = "Selecione um instrumento do SoundFont"
            textSize = 14f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 12)
        }
        layout.addView(info)

        val scroll = ScrollView(this)

        val instrumentList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        scroll.addView(instrumentList)

        layout.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        if (!soundFontLoaded) {
            info.text = "Carregue um SoundFont na tela principal."
        } else {
            try {
                val presets = nativeGetPresets()

                if (presets.isEmpty()) {
                    info.text = "Nenhum instrumento encontrado."
                } else {
                    info.text = "${presets.size} instrumentos encontrados"

                    presets.forEach { preset ->
                        val parts = preset.split("|", limit = 3)

                        if (parts.size == 3) {
                            val bank = parts[0].toIntOrNull()
                            val program = parts[1].toIntOrNull()
                            val instrumentName = parts[2]

                            if (bank != null && program != null) {
                                addButton(
                                    instrumentList,
                                    instrumentName
                                ) {
                                    val selected = nativeSelectPreset(
                                        0,
                                        bank,
                                        program
                                    )

                                    if (selected) {
                                        presetPrefs.edit()
                                            .putInt("last_soundfont", activeSoundFontId)
                                            .putInt("last_bank", bank)
                                            .putInt("last_program", program)
                                            .putString("last_name", instrumentName)
                                            .apply()

                                        info.text =
                                            "Instrumento selecionado: $instrumentName"

                                        status.text =
                                            "TIMBRE ATIVO: $instrumentName"
                                        status.setTextColor(Color.GREEN)
                                    } else {
                                        info.text =
                                            "Não foi possível selecionar o instrumento."
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                info.text =
                    "Erro ao consultar instrumentos: ${e.message}"
            }
        }

        addButton(layout, "VOLTAR À TELA PRINCIPAL") {
            showMainScreen()
        }

        setContentView(layout)
    }

    private fun showPresetManager() {
        keyboardOpen = false
        libraryOpen = true

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 12)
            setBackgroundColor(backgroundColor)
        }

        val title = TextView(this).apply {
            text = "PRESET MANAGER"
            textSize = 23f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 16)
        }
        layout.addView(title)

        val info = TextView(this).apply {
            text = "Gerenciamento de instrumentos"
            textSize = 14f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 12)
        }
        layout.addView(info)

        addButton(layout, "MOSTRAR TODOS / FAVORITOS") {
            showOnlyFavorites = !showOnlyFavorites
            showPresetManager()
        }

        val scroll = ScrollView(this)

        val presetList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        scroll.addView(presetList)

        layout.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        if (!soundFontLoaded) {
            info.text = "Carregue um SoundFont primeiro."
        } else {
            try {
                val presets = nativeGetPresets()

                val favorites = presetPrefs.getStringSet(
                    "favorites",
                    emptySet()
                )?.toMutableSet() ?: mutableSetOf()

                val validPresets = presets.mapNotNull { preset ->
                    val parts = preset.split("|", limit = 3)

                    if (parts.size != 3) return@mapNotNull null

                    val bank = parts[0].toIntOrNull()
                        ?: return@mapNotNull null

                    val program = parts[1].toIntOrNull()
                        ?: return@mapNotNull null

                    Triple(bank, program, parts[2])
                }

                val visiblePresets = validPresets.filter { item ->
                    val key =
                        "${activeSoundFontId}|${item.first}|${item.second}"

                    !showOnlyFavorites || favorites.contains(key)
                }

                info.text =
                    "${visiblePresets.size} instrumentos disponíveis"

                if (visiblePresets.isEmpty()) {
                    val empty = TextView(this).apply {
                        text = if (showOnlyFavorites) {
                            "Nenhum favorito salvo."
                        } else {
                            "Nenhum preset encontrado."
                        }
                        setTextColor(Color.LTGRAY)
                        gravity = Gravity.CENTER
                        setPadding(12, 24, 12, 24)
                    }
                    presetList.addView(empty)
                }

                visiblePresets.forEach { item ->
                    val bank = item.first
                    val program = item.second
                    val name = item.third

                    val key =
                        "${activeSoundFontId}|$bank|$program"

                    addButton(presetList, name) {
                        val selected = nativeSelectPreset(
                            0,
                            bank,
                            program
                        )

                        if (selected) {
                            presetPrefs.edit()
                                .putInt("last_soundfont", activeSoundFontId)
                                .putInt("last_bank", bank)
                                .putInt("last_program", program)
                                .putString("last_name", name)
                                .apply()

                            info.text = "TIMBRE ATIVO: $name"
                            status.text = "PRESET ATIVO: $name"
                            status.setTextColor(Color.GREEN)
                        } else {
                            info.text =
                                "Não foi possível selecionar: $name"
                        }
                    }

                    val isFavorite = favorites.contains(key)

                    addButton(
                        presetList,
                        if (isFavorite) {
                            "★ REMOVER DOS FAVORITOS"
                        } else {
                            "☆ ADICIONAR AOS FAVORITOS"
                        }
                    ) {
                        val updated = presetPrefs.getStringSet(
                            "favorites",
                            emptySet()
                        )?.toMutableSet() ?: mutableSetOf()

                        if (updated.contains(key)) {
                            updated.remove(key)
                        } else {
                            updated.add(key)
                        }

                        presetPrefs.edit()
                            .putStringSet("favorites", updated)
                            .apply()

                        showPresetManager()
                    }
                }

            } catch (e: Exception) {
                info.text = "Erro ao carregar presets: ${e.message}"
            }
        }

        addButton(layout, "VOLTAR À TELA PRINCIPAL") {
            showOnlyFavorites = false
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

    private fun getOriginalFileName(uri: Uri): String {
        var name: String? = null

        contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(
                    OpenableColumns.DISPLAY_NAME
                )

                if (index >= 0) {
                    name = cursor.getString(index)
                }
            }
        }

        return name ?: "SoundFont.sf2"
    }

    private fun createUniqueSoundFontFile(
        directory: File,
        originalName: String
    ): File {
        val safeName = originalName
            .replace(Regex("[^A-Za-z0-9._-]"), "_")

        val baseName = if (safeName.lowercase().endsWith(".sf2")) {
            safeName.dropLast(4)
        } else {
            safeName
        }

        var destination = File(directory, "$baseName.sf2")
        var counter = 2

        while (destination.exists()) {
            destination = File(
                directory,
                "${baseName}_$counter.sf2"
            )
            counter++
        }

        return destination
    }

    private fun restoreSoundFonts() {
        val directory = File(filesDir, "soundfonts")

        if (!directory.exists()) {
            directory.mkdirs()
        }

        val soundFonts = directory.listFiles()
            ?.filter {
                it.isFile && it.extension.equals("sf2", true)
            }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()

        var loadedCount = 0

        soundFonts.forEach { file ->
            val id = nativeLoadSoundFont(file.absolutePath)

            if (id >= 0) {
                loadedCount++
                activeSoundFontId = id
            }
        }

        if (loadedCount > 0) {
            soundFontLoaded = true

            startAudio()

            status.text =
                "$loadedCount SOUNDFONT(S) RESTAURADO(S)"

            status.setTextColor(Color.GREEN)

            restoreLastPreset()
        } else {
            soundFontLoaded = false
            activeSoundFontId = -1

            status.text = "AGUARDANDO SOUNDFONT"
            status.setTextColor(Color.YELLOW)
        }
    }

    private fun restoreLastPreset() {
        val savedSoundFont = presetPrefs.getInt("last_soundfont", -1)
        val bank = presetPrefs.getInt("last_bank", -1)
        val program = presetPrefs.getInt("last_program", -1)
        val name = presetPrefs.getString("last_name", null)

        if (savedSoundFont == activeSoundFontId &&
            bank >= 0 &&
            program >= 0
        ) {
            val restored = nativeSelectPreset(
                0,
                bank,
                program
            )

            if (restored && name != null) {
                status.text = "PRESET RESTAURADO: $name"
                status.setTextColor(Color.GREEN)
            }
        }
    }

    private fun copyAndLoadSoundFont(uri: Uri) {
        try {
            val directory = File(filesDir, "soundfonts")

            if (!directory.exists() && !directory.mkdirs()) {
                throw Exception("Não foi possível criar a pasta")
            }

            val originalName = getOriginalFileName(uri)

            val soundFontFile = createUniqueSoundFontFile(
                directory,
                originalName
            )

            contentResolver.openInputStream(uri)?.use { input ->
                soundFontFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: throw Exception("Não foi possível abrir o arquivo")

            if (soundFontFile.length() == 0L) {
                soundFontFile.delete()
                throw Exception("Arquivo SoundFont vazio")
            }

            val id = nativeLoadSoundFont(
                soundFontFile.absolutePath
            )

            if (id >= 0) {
                soundFontLoaded = true
                activeSoundFontId = id

                if (audioTrack == null) {
                    startAudio()
                }

                status.text =
                    "SOUNDFONT SALVO!\n${soundFontFile.name}\nID: $id"

                status.setTextColor(Color.GREEN)

                if (libraryOpen) {
                    showSoundFontManager()
                }
            } else {
                soundFontFile.delete()

                status.text = "ERRO AO CARREGAR SOUNDFONT"
                status.setTextColor(Color.RED)
            }

        } catch (e: Exception) {
            status.text = "ERRO: ${e.message}"
            status.setTextColor(Color.RED)
        }
    }

    private fun startAudio() {
        if (audioTrack != null) return

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
        if (keyboardOpen || libraryOpen) {
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
