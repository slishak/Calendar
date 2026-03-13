package org.fossify.calendar.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import org.fossify.calendar.extensions.*
import org.fossify.calendar.helpers.COLUMN_COUNT
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.helpers.FontHelper
import org.fossify.commons.helpers.LOWER_ALPHA
import org.joda.time.DateTime

/**
 * Draws only the weekday-letter header row (Mon, Tue, …) that matches BlockMonthView's columns.
 * Intended to be pinned above a scrolling list of BlockMonthView items.
 */
class BlockMonthHeaderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val config = context.config
    private val textPaint: Paint
    private val gridPaint: Paint
    private val headerHeight: Float

    private var primaryColor = 0
    private var textColor = 0
    private var weekendsTextColor = 0
    private var highlightWeekends = false
    private var currDayOfWeek = -1
    private var dayLetters = ArrayList<String>()

    init {
        primaryColor = context.getProperPrimaryColor()
        textColor = context.getProperTextColor()
        weekendsTextColor = config.highlightWeekendsColor
        highlightWeekends = config.highlightWeekends

        val normalTextSize = resources.getDimensionPixelSize(org.fossify.commons.R.dimen.normal_text_size)
        headerHeight = normalTextSize * 2f

        textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            textSize = normalTextSize.toFloat()
            textAlign = Paint.Align.CENTER
            typeface = FontHelper.getTypeface(context)
        }

        gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor.adjustAlpha(LOWER_ALPHA)
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }

        initDayLetters()
        currDayOfWeek = context.getProperDayIndexInWeek(DateTime())
    }

    fun update() {
        primaryColor = context.getProperPrimaryColor()
        textColor = context.getProperTextColor()
        weekendsTextColor = config.highlightWeekendsColor
        highlightWeekends = config.highlightWeekends
        textPaint.color = textColor
        gridPaint.color = textColor.adjustAlpha(LOWER_ALPHA)
        initDayLetters()
        currDayOfWeek = context.getProperDayIndexInWeek(DateTime())
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            MeasureSpec.getSize(widthMeasureSpec),
            resolveSize(headerHeight.toInt(), heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val offset = context.getWeekNumberWidth()
        val dayWidth = (canvas.width - offset) / COLUMN_COUNT.toFloat()

        for (i in 0 until COLUMN_COUNT) {
            val xPos = offset + i * dayWidth + dayWidth / 2
            val paint = when {
                i == currDayOfWeek -> Paint(textPaint).apply { color = primaryColor }
                highlightWeekends && context.isWeekendIndex(i) -> Paint(textPaint).apply { color = weekendsTextColor }
                else -> textPaint
            }
            canvas.drawText(dayLetters[i], xPos, headerHeight * 0.7f, paint)
        }

        // Bottom separator line
        canvas.drawLine(0f, headerHeight, width.toFloat(), headerHeight, gridPaint)
    }

    private fun initDayLetters() {
        dayLetters = context.withFirstDayOfWeekToFront(
            context.resources.getStringArray(org.fossify.commons.R.array.week_days_short).toList()
        )
    }
}
