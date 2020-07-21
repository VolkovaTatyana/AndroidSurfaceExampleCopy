package com.example.surfaces

import android.graphics.*
import android.graphics.drawable.Drawable
import android.util.Log
import com.example.surfaces.utils.TimeUtils
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*

class CanvasDrawable : Drawable() {
    private var prevTimestamp = TimeUtils.now()
    private var step = POS_STEP
    private var posX = 0f
    private var posY = 0f

    private val backgroundPaint = Paint().apply {
//        color = Color.GREEN
        color = Color.TRANSPARENT
    }

    private val circlePaint = Paint().apply {
        isAntiAlias = true
        color = Color.RED
    }

    private val textPaint = Paint().apply {
        isAntiAlias = true
        color = Color.WHITE

        textSize = 70f
    }

    override fun draw(canvas: Canvas) {
        val currentTimestamp = TimeUtils.now()
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        canvas.drawRect(bounds, backgroundPaint)
        val width = bounds.width()
        val height = bounds.height()

        val x = (currentTimestamp - prevTimestamp) / 1000

        if (currentTimestamp - prevTimestamp > FRAME_DURATION) {
            val ration = width.toFloat() / height.toFloat()

            when {
                posX > width -> {
                    step = -POS_STEP
                }

                posX < 0 -> {
                    step = POS_STEP
                }
            }

//            posX += step.toFloat()
//            posY = (posX / ration)
            posX = width / 2f
            posY = height / 2f
        }

        val fontPaint = Paint(Paint.ANTI_ALIAS_FLAG);
        fontPaint.textSize = 30f;
        fontPaint.style = Paint.Style.STROKE;

//        val text = (currentTimestamp/1000).toString()
//        val text = x.toString()

        val hours = x / 3600
        val minutes = x / 60
        val seconds = x % 60
//        val text = "$hours:$minutes:$seconds"
        val text = "%02d".format(hours) +":%02d".format(minutes) + ":%02d".format(seconds)
//        val minutes =
//        canvas.drawCircle(posX, posY, 0.1f * width, circlePaint)
//        canvas.drawCircle(width.toFloat(), height.toFloat(), 0.1f * width, circlePaint)

        if (x % 2 == 0L) {
            canvas.drawCircle(0.9f * posX, posY - 0.01f * height, 0.02f * height, circlePaint)

            if (x % 3 == 0L) {

            }
        }

        canvas.drawText(text, posX, posY, textPaint)
        if (x > 3) {
            val text1 = "text 1"
            canvas.drawText(text1, posX, posY + 10 * 0.01f * height, textPaint)
        }

        if (x > 5) {
            val text2 = "text 2"
            canvas.drawText(text2, posX, posY + 14 * 0.01f * height, textPaint)
        }

        if (x > 7) {
            val text3 = "text 3"
            canvas.drawText(text3, posX, posY + 18 * 0.01f * height, textPaint)
        }


//        canvas.drawText("TEST test test", posX, posY, circlePaint)
//        canvas.drawText((currentTimestamp/1000).toString(), width.toFloat(), height.toFloat(), fontPaint)

    }

    override fun setAlpha(alpha: Int) {
        backgroundPaint.alpha = alpha
    }

    override fun getOpacity(): Int {
        return PixelFormat.TRANSLUCENT
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        backgroundPaint.colorFilter = colorFilter
    }

    private companion object {
        const val FRAME_DURATION = 60
        const val POS_STEP = 4
    }
}