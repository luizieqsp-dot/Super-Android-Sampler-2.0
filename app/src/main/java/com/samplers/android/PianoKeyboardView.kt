
package com.samplers.android

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.floor

class PianoKeyboardView(
    context: Context,
    private val onNoteOn: (Int, Int) -> Unit,
    private val onNoteOff: (Int) -> Unit
) : View(context) {

    private val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
    }

    private val blackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(20, 22, 28)
    }

    private val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(91, 110, 245)
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GRAY
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }

    private val whiteWidth = 48f * resources.displayMetrics.density
    private val blackWidth = 29f * resources.displayMetrics.density
    private val blackHeight = 0.62f

    private val whiteNotes = (21..108).filter {
        it % 12 in listOf(0, 2, 4, 5, 7, 9, 11)
    }

    private val activePointers = mutableMapOf<Int, Int>()

    private val activeNotes = mutableSetOf<Int>()

    private val blackAfter = setOf(0, 2, 5, 7, 9)

    init {
        isClickable = true
        contentDescription = "Teclado virtual de 88 teclas"
    }

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int
    ) {
        val desiredWidth = (whiteNotes.size * whiteWidth).toInt()
        val desiredHeight =
            (220 * resources.displayMetrics.density).toInt()

        setMeasuredDimension(
            desiredWidth,
            resolveSize(desiredHeight, heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val height = height.toFloat()

        // Desenhar teclas brancas
        whiteNotes.forEachIndexed { index, midi ->
            val left = index * whiteWidth
            val rect = RectF(
                left,
                0f,
                left + whiteWidth,
                height
            )

            val paint = if (midi in activeNotes) {
                activePaint
            } else {
                whitePaint
            }

            canvas.drawRoundRect(rect, 3f, 3f, paint)
            canvas.drawRoundRect(rect, 3f, 3f, borderPaint)
        }

        // Desenhar teclas pretas
        whiteNotes.forEachIndexed { index, midi ->

            if (midi % 12 !in blackAfter) return@forEachIndexed

            val left = (index + 1) * whiteWidth -
                    blackWidth / 2f

            val rect = RectF(
                left,
                0f,
                left + blackWidth,
                height * blackHeight.toFloat()
            )

            val blackMidi = midi + 1

            val paint = if (blackMidi in activeNotes) {
                activePaint
            } else {
                blackPaint
            }

            canvas.drawRoundRect(rect, 4f, 4f, paint)
            canvas.drawRoundRect(rect, 4f, 4f, borderPaint)
        }
    }

    private fun findNote(x: Float): Int {
        // Verificar primeiro as teclas pretas
        whiteNotes.forEachIndexed { index, midi ->

            if (midi % 12 !in blackAfter) {
                return@forEachIndexed
            }

            val boundary = (index + 1) * whiteWidth

            val left = boundary - blackWidth / 2f
            val right = boundary + blackWidth / 2f

            if (x >= left && x <= right) {
                return midi + 1
            }
        }

        // Depois verificar as teclas brancas
        val index = floor(x / whiteWidth).toInt()

        return whiteNotes.getOrNull(index) ?: -1
    }

    private fun press(pointerId: Int, x: Float) {
        val midi = findNote(x)

        if (midi < 0) return

        val previous = activePointers[pointerId]

        if (previous == midi) return

        if (previous != null) {
            release(pointerId)
        }

        activePointers[pointerId] = midi

        if (activeNotes.add(midi)) {
            onNoteOn(midi, 100)
        }

        invalidate()
    }

    private fun release(pointerId: Int) {
        val midi = activePointers.remove(pointerId) ?: return

        val stillPressed = activePointers.values.contains(midi)

        if (!stillPressed) {
            activeNotes.remove(midi)
            onNoteOff(midi)
        }

        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {

        when (event.actionMasked) {

            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_POINTER_DOWN -> {

                val index = event.actionIndex

                press(
                    event.getPointerId(index),
                    event.getX(index)
                )
            }

            MotionEvent.ACTION_MOVE -> {

                for (i in 0 until event.pointerCount) {
                    press(
                        event.getPointerId(i),
                        event.getX(i)
                    )
                }
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_POINTER_UP -> {

                val index = event.actionIndex
                release(event.getPointerId(index))
            }

            MotionEvent.ACTION_CANCEL -> {

                activePointers.keys.toList().forEach {
                    release(it)
                }
            }
        }

        return true
    }

    fun releaseAllNotes() {
        activePointers.keys.toList().forEach {
            release(it)
        }
    }
}
