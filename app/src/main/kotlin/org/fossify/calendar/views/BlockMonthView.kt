package org.fossify.calendar.views

import android.content.Context
import android.graphics.*
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import org.fossify.calendar.R
import org.fossify.calendar.extensions.*
import org.fossify.calendar.helpers.*
import org.fossify.calendar.models.DayMonthly
import org.fossify.calendar.models.Event
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.FontHelper
import org.fossify.commons.helpers.LOWER_ALPHA
import org.fossify.commons.helpers.MEDIUM_ALPHA
import org.joda.time.DateTime

/**
 * Custom View for the Block Month View (Business Calendar 2 style).
 * Draws a 6-row × 7-column monthly grid where each day cell shows events as
 * proportionally-sized colored blocks based on the configured visible time window.
 * Concurrent events are rendered side-by-side using a greedy column algorithm.
 */
class BlockMonthView(context: Context, attrs: AttributeSet, defStyle: Int) : View(context, attrs, defStyle) {

    companion object {
        private const val BG_CORNER_RADIUS = 3f
        private const val HEADER_FRACTION = 0.22f   // fraction of day height used for the day number
        private const val ALL_DAY_FRACTION = 0.12f  // fraction of content height for all-day events
        private const val MIN_BLOCK_HEIGHT_FOR_TEXT = 14f
    }

    constructor(context: Context, attrs: AttributeSet) : this(context, attrs, 0)

    private val config = context.config
    private var textPaint: Paint
    private var headerTextPaint: Paint
    private var gridPaint: Paint
    private var eventPaint: Paint
    private var eventTitlePaint: TextPaint
    private var todayCirclePaint: Paint
    private var circleBorderPaint: Paint

    private var dayWidth = 0f
    private var dayHeight = 0f
    private var weekDaysLetterHeight = 0f
    private var primaryColor = 0
    private var textColor = 0
    private var weekendsTextColor = 0
    private var horizontalOffset = 0
    private var showWeekNumbers = false
    private var highlightWeekends = false
    private var dimPastEvents = true
    private var startHour = 0
    private var endHour = 24

    private var days = ArrayList<DayMonthly>()
    private var dayLetters = ArrayList<String>()
    private var currDayOfWeek = -1
    private val bgRectF = RectF()
    private val dayTextRect = Rect()

    private var dayClickCallback: ((DayMonthly) -> Unit)? = null

    init {
        primaryColor = context.getProperPrimaryColor()
        textColor = context.getProperTextColor()
        weekendsTextColor = config.highlightWeekendsColor
        showWeekNumbers = config.showWeekNumbers
        highlightWeekends = config.highlightWeekends
        dimPastEvents = config.dimPastEvents
        startHour = config.blockMonthViewStartHour
        endHour = config.blockMonthViewEndHour

        val normalTextSize = resources.getDimensionPixelSize(org.fossify.commons.R.dimen.normal_text_size)
        val smallerTextSize = resources.getDimensionPixelSize(org.fossify.commons.R.dimen.smaller_text_size)
        weekDaysLetterHeight = normalTextSize * 2f

        textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            textSize = normalTextSize.toFloat()
            textAlign = Paint.Align.CENTER
            typeface = FontHelper.getTypeface(context)
        }

        headerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            textSize = normalTextSize * 0.85f
            textAlign = Paint.Align.CENTER
            typeface = FontHelper.getTypeface(context)
        }

        eventTitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = smallerTextSize * 0.8f
            textAlign = Paint.Align.LEFT
            typeface = FontHelper.getTypeface(context)
        }

        gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor.adjustAlpha(LOWER_ALPHA)
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }

        eventPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        todayCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = primaryColor
            style = Paint.Style.FILL
        }

        circleBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = primaryColor
            style = Paint.Style.STROKE
            strokeWidth = resources.getDimension(R.dimen.circle_stroke_width)
        }

        initWeekDayLetters()
        setupCurrentDayOfWeekIndex()
    }

    fun setDayClickCallback(callback: (DayMonthly) -> Unit) {
        dayClickCallback = callback
    }

    fun updateDays(newDays: ArrayList<DayMonthly>) {
        days = newDays
        showWeekNumbers = config.showWeekNumbers
        horizontalOffset = context.getWeekNumberWidth()
        highlightWeekends = config.highlightWeekends
        dimPastEvents = config.dimPastEvents
        startHour = config.blockMonthViewStartHour
        endHour = config.blockMonthViewEndHour
        weekendsTextColor = config.highlightWeekendsColor
        initWeekDayLetters()
        setupCurrentDayOfWeekIndex()
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> return true   // claim the gesture so ACTION_UP is delivered
            MotionEvent.ACTION_UP -> {
                val x = event.x - horizontalOffset
                val y = event.y - weekDaysLetterHeight
                if (x >= 0 && y >= 0 && dayWidth > 0 && dayHeight > 0) {
                    val col = (x / dayWidth).toInt().coerceIn(0, COLUMN_COUNT - 1)
                    val row = (y / dayHeight).toInt().coerceIn(0, ROW_COUNT - 1)
                    val index = row * COLUMN_COUNT + col
                    days.getOrNull(index)?.let { dayClickCallback?.invoke(it) }
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        measureDaySize(canvas)
        drawGrid(canvas)
        drawWeekDayLetters(canvas)
        if (showWeekNumbers && days.isNotEmpty()) {
            drawWeekNumbers(canvas)
        }
        for (i in days.indices) {
            drawDay(canvas, days[i], i % COLUMN_COUNT, i / COLUMN_COUNT)
        }
    }

    private fun measureDaySize(canvas: Canvas) {
        dayWidth = (canvas.width - horizontalOffset) / COLUMN_COUNT.toFloat()
        dayHeight = (canvas.height - weekDaysLetterHeight) / ROW_COUNT.toFloat()
    }

    private fun drawGrid(canvas: Canvas) {
        for (i in 0..COLUMN_COUNT) {
            val x = i * dayWidth + horizontalOffset
            canvas.drawLine(x, weekDaysLetterHeight, x, height.toFloat(), gridPaint)
        }
        for (i in 0..ROW_COUNT) {
            val y = weekDaysLetterHeight + i * dayHeight
            canvas.drawLine(horizontalOffset.toFloat(), y, width.toFloat(), y, gridPaint)
        }
        // Day-of-week header separator
        canvas.drawLine(0f, weekDaysLetterHeight, width.toFloat(), weekDaysLetterHeight, gridPaint)
    }

    private fun drawWeekDayLetters(canvas: Canvas) {
        for (i in 0 until COLUMN_COUNT) {
            val xPos = horizontalOffset + i * dayWidth + dayWidth / 2
            val paint = when {
                i == currDayOfWeek -> getColoredPaint(primaryColor, textPaint)
                highlightWeekends && context.isWeekendIndex(i) -> getColoredPaint(weekendsTextColor, textPaint)
                else -> textPaint
            }
            canvas.drawText(dayLetters[i], xPos, weekDaysLetterHeight * 0.7f, paint)
        }
    }

    private fun drawWeekNumbers(canvas: Canvas) {
        if (days.size < 7) return
        val weekPaint = Paint(textPaint).apply { textSize = textPaint.textSize * 0.7f }
        for (row in 0 until ROW_COUNT) {
            val startIdx = row * 7
            val weekOfYear = days.getOrNull(startIdx + 3)?.weekOfYear ?: continue
            weekPaint.color = if (days.subList(startIdx, minOf(startIdx + 7, days.size)).any { it.isToday }) {
                primaryColor
            } else {
                textColor
            }
            val xPos = horizontalOffset * 0.5f
            val yPos = weekDaysLetterHeight + row * dayHeight + dayHeight * 0.4f
            canvas.drawText("$weekOfYear:", xPos, yPos, weekPaint)
        }
    }

    private fun drawDay(canvas: Canvas, day: DayMonthly, col: Int, row: Int) {
        val cellLeft = horizontalOffset + col * dayWidth
        val cellTop = weekDaysLetterHeight + row * dayHeight
        val headerHeight = dayHeight * HEADER_FRACTION
        val contentTop = cellTop + headerHeight
        val contentHeight = dayHeight - headerHeight

        // Day number
        val dayNumStr = day.value.toString()
        val numPaint = getTextPaintForDay(day)
        numPaint.getTextBounds(dayNumStr, 0, dayNumStr.length, dayTextRect)
        val numX = cellLeft + dayWidth / 2
        val numY = cellTop + headerHeight * 0.72f

        if (day.isToday) {
            canvas.drawCircle(numX, numY - dayTextRect.height() / 2f, headerTextPaint.textSize * 0.85f, todayCirclePaint)
            numPaint.color = primaryColor.getContrastColor()
        }

        canvas.drawText(dayNumStr, numX, numY, numPaint)

        if (day.dayEvents.isNotEmpty() && contentHeight > 4f) {
            drawDayEvents(canvas, day, cellLeft, contentTop, contentHeight)
        }
    }

    private fun drawDayEvents(canvas: Canvas, day: DayMonthly, cellLeft: Float, contentTop: Float, contentHeight: Float) {
        val totalMinutes = ((endHour - startHour) * 60).toFloat()
        if (totalMinutes <= 0f) return

        val pad = 1.5f
        val allDayEvents = day.dayEvents.filter { it.getIsAllDay() }
        val timedEvents = day.dayEvents.filter { !it.getIsAllDay() }

        // All-day band
        val allDayBandHeight = if (allDayEvents.isNotEmpty()) {
            minOf(contentHeight * ALL_DAY_FRACTION * allDayEvents.size, contentHeight * 0.3f)
        } else {
            0f
        }
        val timedTop = contentTop + allDayBandHeight
        val timedHeight = contentHeight - allDayBandHeight

        // Draw all-day events as stacked thin bars
        if (allDayEvents.isNotEmpty()) {
            val barH = allDayBandHeight / allDayEvents.size - pad
            allDayEvents.forEachIndexed { idx, event ->
                val top = contentTop + idx * (barH + pad)
                val bot = top + barH
                bgRectF.set(cellLeft + pad, top, cellLeft + dayWidth - pad, bot)
                eventPaint.color = resolveEventColor(event)
                canvas.drawRoundRect(bgRectF, BG_CORNER_RADIUS, BG_CORNER_RADIUS, eventPaint)
                if (barH >= MIN_BLOCK_HEIGHT_FOR_TEXT) {
                    drawBlockTitle(canvas, event.title, cellLeft + pad, top, dayWidth - pad * 2, barH)
                }
            }
        }

        if (timedEvents.isEmpty() || timedHeight < 4f) return

        // Layout timed events into columns to handle overlaps.
        // Apply minimum duration before column assignment so zero-duration events
        // are treated as overlapping with events at the same time.
        val minDurationSeconds = 30 * 60L
        data class Layout(val event: Event, val effectiveEndTS: Long, var col: Int, var totalCols: Int)

        val sorted = timedEvents.sortedWith(compareBy({ it.startTS }, { it.endTS }))
        val layouts = mutableListOf<Layout>()
        val colEnds = mutableListOf<Long>()

        for (event in sorted) {
            val effectiveEndTS = maxOf(event.endTS, event.startTS + minDurationSeconds)
            val colIdx = colEnds.indexOfFirst { it <= event.startTS }
            if (colIdx == -1) {
                colEnds.add(effectiveEndTS)
                layouts.add(Layout(event, effectiveEndTS, colEnds.size - 1, 0))
            } else {
                colEnds[colIdx] = maxOf(colEnds[colIdx], effectiveEndTS)
                layouts.add(Layout(event, effectiveEndTS, colIdx, 0))
            }
        }

        // Compute totalCols per event based on overlap groups (using effective end times)
        for (layout in layouts) {
            var maxCol = layout.col
            for (other in layouts) {
                val overlaps = other.event.startTS < layout.effectiveEndTS &&
                    other.effectiveEndTS > layout.event.startTS
                if (overlaps) maxCol = maxOf(maxCol, other.col)
            }
            layout.totalCols = maxCol + 1
        }

        // Draw timed events
        for (layout in layouts) {
            val event = layout.event
            val startMin = clampedMinutes(event.startTS, totalMinutes)
            val endMin = clampedMinutes(layout.effectiveEndTS, totalMinutes)

            val top = timedTop + (startMin / totalMinutes) * timedHeight
            val bot = timedTop + (endMin / totalMinutes) * timedHeight
            val blockH = bot - top

            val colW = (dayWidth - pad * 2) / layout.totalCols
            val left = cellLeft + pad + layout.col * colW
            val right = left + colW - pad

            bgRectF.set(left, top, right, bot)
            eventPaint.color = resolveEventColor(event)
            canvas.drawRoundRect(bgRectF, BG_CORNER_RADIUS, BG_CORNER_RADIUS, eventPaint)

            if (blockH >= MIN_BLOCK_HEIGHT_FOR_TEXT) {
                drawBlockTitle(canvas, event.title, left, top + 1f, colW - pad, blockH)
            }
        }
    }

    private fun clampedMinutes(tsSeconds: Long, totalMinutes: Float): Float {
        val dt = Formatter.getDateTimeFromTS(tsSeconds)
        val mins = ((dt.hourOfDay - startHour) * 60 + dt.minuteOfHour).toFloat()
        return mins.coerceIn(0f, totalMinutes)
    }

    private fun resolveEventColor(event: Event): Int {
        val base = event.color
        return if (dimPastEvents && event.isPastEvent) base.adjustAlpha(MEDIUM_ALPHA) else base
    }

    private fun drawBlockTitle(canvas: Canvas, title: String, left: Float, top: Float, availW: Float, availH: Float) {
        if (availW < 6f || availH < 6f) return
        val paint = eventTitlePaint
        paint.color = Color.WHITE
        val ellipsized = TextUtils.ellipsize(title, paint, availW - 3f, TextUtils.TruncateAt.END)
        val textY = top + paint.textSize
        if (textY <= top + availH) {
            canvas.drawText(title, 0, ellipsized.length, left + 2f, textY, paint)
        }
    }

    private fun getTextPaintForDay(day: DayMonthly): Paint {
        var color = when {
            day.isToday -> primaryColor
            highlightWeekends && day.isWeekend -> weekendsTextColor
            else -> textColor
        }
        if (!day.isThisMonth) color = color.adjustAlpha(MEDIUM_ALPHA)
        return getColoredPaint(color, headerTextPaint)
    }

    private fun getColoredPaint(color: Int, base: Paint): Paint {
        return Paint(base).apply { this.color = color }
    }

    private fun initWeekDayLetters() {
        dayLetters = context.withFirstDayOfWeekToFront(
            context.resources.getStringArray(org.fossify.commons.R.array.week_days_short).toList()
        )
    }

    private fun setupCurrentDayOfWeekIndex() {
        currDayOfWeek = if (days.any { it.isToday && it.isThisMonth }) {
            context.getProperDayIndexInWeek(DateTime())
        } else {
            -1
        }
    }
}
