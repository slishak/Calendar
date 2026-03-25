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
 * Draws a n-row × 7-column monthly grid where each day cell shows events as
 * proportionally-sized colored blocks based on the configured visible time window.
 * Concurrent timed events are rendered side-by-side using a greedy column algorithm.
 * Multi-day all-day events are drawn as spanning bars across day cell boundaries.
 *
 * Set [showWeekDayHeader] = false when using an external pinned header (BlockMonthHeaderView).
 */
class BlockMonthView(context: Context, attrs: AttributeSet, defStyle: Int) : View(context, attrs, defStyle) {

    companion object {
        private const val BG_CORNER_RADIUS = 3f
        private const val HEADER_FRACTION = 0.22f   // fraction of day height used for the day number
        private const val ALL_DAY_FRACTION = 0.12f  // fraction of content height per all-day track
        private const val MIN_BLOCK_HEIGHT_FOR_TEXT = 14f
        // Cell background shading (textColor overlay, so it works in both light and dark themes)
        private const val PAST_DAY_DIM_ALPHA = 0.02f
        private const val INACTIVE_MONTH_DIM_ALPHA = 0.16f
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
    private val fullWeekDaysLetterHeight: Float   // fixed height when header is shown
    private var weekDaysLetterHeight = 0f         // 0 when showWeekDayHeader = false
    private var primaryColor = 0
    private var textColor = 0
    private var weekendsTextColor = 0
    private var horizontalOffset = 0
    private var showWeekNumbers = false
    private var highlightWeekends = false
    private var dimPastEvents = true
    private var startHour = 0
    private var endHour = 24
    private var todayCode = Formatter.getTodayCode()

    /** Set to false when an external BlockMonthHeaderView provides the weekday labels. */
    var showWeekDayHeader = true
        set(value) {
            field = value
            weekDaysLetterHeight = if (value) fullWeekDaysLetterHeight else 0f
            invalidate()
        }

    /**
     * 6-digit month code (YYYYMM) of the currently active/centred month in the scroll view.
     * When non-empty, overrides [DayMonthly.isThisMonth] so that overflow days are dimmed
     * relative to the scrolled active month rather than the block's own target month.
     */
    var activeMonthCode: String = ""
        set(value) {
            field = value
            invalidate()
        }

    private var days = ArrayList<DayMonthly>()
    private var dayLetters = ArrayList<String>()
    private var currDayOfWeek = -1
    private val bgRectF = RectF()
    private val dayTextRect = Rect()

    /** Number of rows to draw — derived from the days array, trimmed of trailing next-month rows. */
    private val rowCount: Int get() = if (days.isEmpty()) 0 else days.size / COLUMN_COUNT

    private var dayClickCallback: ((DayMonthly) -> Unit)? = null

    // Pre-computed per-row all-day spanning data (sized to max ROW_COUNT = 6)
    private data class AllDaySpan(val event: Event, val startCol: Int, val endCol: Int, val track: Int)
    private val allDaySpansPerRow = Array(ROW_COUNT) { mutableListOf<AllDaySpan>() }
    private val maxTracksPerRow = IntArray(ROW_COUNT)

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
        fullWeekDaysLetterHeight = normalTextSize * 2f
        weekDaysLetterHeight = fullWeekDaysLetterHeight

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
        todayCode = Formatter.getTodayCode()
        showWeekNumbers = config.showWeekNumbers
        horizontalOffset = context.getWeekNumberWidth()
        highlightWeekends = config.highlightWeekends
        dimPastEvents = config.dimPastEvents
        startHour = config.blockMonthViewStartHour
        endHour = config.blockMonthViewEndHour
        weekendsTextColor = config.highlightWeekendsColor
        initWeekDayLetters()
        setupCurrentDayOfWeekIndex()
        computeAllDaySpans()
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val rc = rowCount
        when (event.action) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_UP -> {
                val x = event.x - horizontalOffset
                val y = event.y - weekDaysLetterHeight
                if (x >= 0 && y >= 0 && dayWidth > 0 && dayHeight > 0 && rc > 0) {
                    val col = (x / dayWidth).toInt().coerceIn(0, COLUMN_COUNT - 1)
                    val row = (y / dayHeight).toInt().coerceIn(0, rc - 1)
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
        val rc = rowCount
        if (rc == 0) return
        measureDaySize(canvas, rc)
        drawCellBackgrounds(canvas)
        drawGrid(canvas, rc)
        if (showWeekDayHeader) drawWeekDayLetters(canvas)
        if (showWeekNumbers && days.isNotEmpty()) drawWeekNumbers(canvas, rc)
        for (i in days.indices) {
            drawDay(canvas, days[i], i % COLUMN_COUNT, i / COLUMN_COUNT)
        }
        drawAllDaySpans(canvas, rc)
    }

    private fun measureDaySize(canvas: Canvas, rc: Int) {
        dayWidth = (canvas.width - horizontalOffset) / COLUMN_COUNT.toFloat()
        dayHeight = (canvas.height - weekDaysLetterHeight) / rc.toFloat()
    }

    /**
     * Fills each cell with a semi-transparent textColor overlay to create three brightness levels:
     *   - Active month, today/future: no overlay (brightest — base background shows through)
     *   - Active month, before today: faint overlay
     *   - Inactive month (overflow days): stronger overlay
     * Using textColor as the overlay tint makes this automatically theme-aware.
     */
    private fun drawCellBackgrounds(canvas: Canvas) {
        val cellPaint = Paint(eventPaint)
        for (i in days.indices) {
            val day = days[i]
            val isInactive = if (activeMonthCode.isNotEmpty())
                day.code.getMonthCode() != activeMonthCode
            else
                !day.isThisMonth

            val overlayAlpha = when {
                isInactive -> INACTIVE_MONTH_DIM_ALPHA
                day.code < todayCode && !day.isToday -> PAST_DAY_DIM_ALPHA
                else -> continue
            }

            val col = i % COLUMN_COUNT
            val row = i / COLUMN_COUNT
            val cellLeft = horizontalOffset + col * dayWidth
            val cellTop = weekDaysLetterHeight + row * dayHeight
            bgRectF.set(cellLeft, cellTop, cellLeft + dayWidth, cellTop + dayHeight)
            cellPaint.color = textColor.adjustAlpha(overlayAlpha)
            canvas.drawRect(bgRectF, cellPaint)
        }
    }

    private fun drawGrid(canvas: Canvas, rc: Int) {
        for (i in 0..COLUMN_COUNT) {
            val x = i * dayWidth + horizontalOffset
            canvas.drawLine(x, weekDaysLetterHeight, x, height.toFloat(), gridPaint)
        }
        for (i in 0..rc) {
            val y = weekDaysLetterHeight + i * dayHeight
            canvas.drawLine(horizontalOffset.toFloat(), y, width.toFloat(), y, gridPaint)
        }
        if (showWeekDayHeader) {
            canvas.drawLine(0f, weekDaysLetterHeight, width.toFloat(), weekDaysLetterHeight, gridPaint)
        }
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

    private fun drawWeekNumbers(canvas: Canvas, rc: Int) {
        if (days.size < 7) return
        val weekPaint = Paint(textPaint).apply { textSize = textPaint.textSize * 0.7f }
        for (row in 0 until rc) {
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

        val timedEvents = day.dayEvents.filter { !it.getIsAllDay() }
        if (timedEvents.isNotEmpty() && contentHeight > 4f) {
            drawTimedEvents(canvas, timedEvents, cellLeft, contentTop, contentHeight, row)
        }
    }

    private fun drawTimedEvents(
        canvas: Canvas,
        timedEvents: List<Event>,
        cellLeft: Float,
        contentTop: Float,
        contentHeight: Float,
        row: Int
    ) {
        val totalMinutes = ((endHour - startHour) * 60).toFloat()
        if (totalMinutes <= 0f) return

        val pad = 1.5f
        val maxTracks = if (row < maxTracksPerRow.size) maxTracksPerRow[row] else 0
        val allDayBandH = if (maxTracks > 0) minOf(contentHeight * ALL_DAY_FRACTION * maxTracks, contentHeight * 0.3f) else 0f
        val timedTop = contentTop + allDayBandH
        val timedHeight = contentHeight - allDayBandH

        if (timedHeight < 4f) return

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

        for (layout in layouts) {
            var maxCol = layout.col
            for (other in layouts) {
                val overlaps = other.event.startTS < layout.effectiveEndTS &&
                    other.effectiveEndTS > layout.event.startTS
                if (overlaps) maxCol = maxOf(maxCol, other.col)
            }
            layout.totalCols = maxCol + 1
        }

        for (layout in layouts) {
            val event = layout.event
            val startMin = clampedMinutes(event.startTS, totalMinutes)
            // An effectiveEndTS of exactly 00:00 means "midnight = end of the previous day".
            // clampedMinutes would return 0 (start of day), producing an inverted rect.
            // Treat it as totalMinutes (end of day) instead.
            val endDT = Formatter.getDateTimeFromTS(layout.effectiveEndTS)
            val endMin = if (endDT.hourOfDay == 0 && endDT.minuteOfHour == 0) totalMinutes
                         else clampedMinutes(layout.effectiveEndTS, totalMinutes)

            val top = timedTop + (startMin / totalMinutes) * timedHeight
            val bot = timedTop + (endMin / totalMinutes) * timedHeight
            if (bot <= top) continue
            val blockH = bot - top

            val colW = (dayWidth - pad * 2) / layout.totalCols
            val left = cellLeft + pad + layout.col * colW
            val right = left + colW - pad

            val resolvedColor = resolveEventColor(event)
            bgRectF.set(left, top, right, bot)
            eventPaint.color = resolvedColor
            canvas.drawRoundRect(bgRectF, BG_CORNER_RADIUS, BG_CORNER_RADIUS, eventPaint)

            if (blockH >= MIN_BLOCK_HEIGHT_FOR_TEXT) {
                drawBlockTitle(canvas, event.title, left, top + 1f, colW - pad, blockH, Color.alpha(resolvedColor))
            }
        }
    }

    private fun computeAllDaySpans() {
        val rc = rowCount
        for (row in 0 until ROW_COUNT) {
            allDaySpansPerRow[row].clear()
            maxTracksPerRow[row] = 0
        }
        if (days.isEmpty()) return

        data class EventRange(val event: Event, val startIdx: Int, val endIdx: Int)
        val seen = mutableSetOf<Event>()
        val eventRanges = mutableListOf<EventRange>()

        for (event in days.flatMap { it.dayEvents }) {
            if (!event.getIsAllDay() || event in seen) continue
            seen.add(event)
            val startIdx = days.indexOfFirst { day -> day.dayEvents.any { it === event } }
            val endIdx = days.indexOfLast { day -> day.dayEvents.any { it === event } }
            if (startIdx >= 0 && endIdx >= startIdx) {
                eventRanges.add(EventRange(event, startIdx, endIdx))
            }
        }

        for (row in 0 until rc) {
            val rowStartIdx = row * COLUMN_COUNT
            val rowEndIdx = rowStartIdx + COLUMN_COUNT - 1

            val rowEvents = eventRanges
                .filter { it.startIdx <= rowEndIdx && it.endIdx >= rowStartIdx }
                .sortedWith(compareBy({ it.startIdx }, { -(it.endIdx - it.startIdx) }))

            val trackEnds = mutableListOf<Int>()

            for (range in rowEvents) {
                val startCol = (range.startIdx - rowStartIdx).coerceIn(0, COLUMN_COUNT - 1)
                val endCol = (range.endIdx - rowStartIdx).coerceIn(0, COLUMN_COUNT - 1)

                val trackIdx = trackEnds.indexOfFirst { it < startCol }
                val track = if (trackIdx == -1) {
                    trackEnds.add(endCol)
                    trackEnds.size - 1
                } else {
                    trackEnds[trackIdx] = endCol
                    trackIdx
                }
                allDaySpansPerRow[row].add(AllDaySpan(range.event, startCol, endCol, track))
            }

            maxTracksPerRow[row] = allDaySpansPerRow[row].maxOfOrNull { it.track + 1 } ?: 0
        }
    }

    private fun drawAllDaySpans(canvas: Canvas, rc: Int) {
        if (dayWidth == 0f || dayHeight == 0f) return
        val pad = 1.5f

        for (row in 0 until rc) {
            if (row >= allDaySpansPerRow.size || allDaySpansPerRow[row].isEmpty()) continue

            val rowTop = weekDaysLetterHeight + row * dayHeight
            val headerH = dayHeight * HEADER_FRACTION
            val contentTop = rowTop + headerH
            val contentH = dayHeight - headerH

            val maxTracks = maxTracksPerRow[row]
            val allDayBandH = if (maxTracks > 0) minOf(contentH * ALL_DAY_FRACTION * maxTracks, contentH * 0.3f) else continue
            val trackH = allDayBandH / maxTracks

            for (span in allDaySpansPerRow[row]) {
                val left = horizontalOffset + span.startCol * dayWidth + pad
                val right = horizontalOffset + (span.endCol + 1) * dayWidth - pad
                val top = contentTop + span.track * trackH + pad
                val bot = top + trackH - pad * 2

                if (bot <= top || right <= left) continue

                val resolvedColor = resolveEventColor(span.event)
                bgRectF.set(left, top, right, bot)
                eventPaint.color = resolvedColor
                canvas.drawRoundRect(bgRectF, BG_CORNER_RADIUS, BG_CORNER_RADIUS, eventPaint)

                val barH = bot - top
                if (barH >= MIN_BLOCK_HEIGHT_FOR_TEXT) {
                    drawBlockTitle(canvas, span.event.title, left + pad, top + 1f, right - left - pad * 2, barH, Color.alpha(resolvedColor))
                }
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

    private fun drawBlockTitle(canvas: Canvas, title: String, left: Float, top: Float, availW: Float, availH: Float, alpha: Int = 255) {
        if (availW < 6f || availH < 6f) return
        val paint = eventTitlePaint
        paint.color = Color.WHITE
        val ellipsized = TextUtils.ellipsize(title, paint, availW - 3f, TextUtils.TruncateAt.END)
        val textY = top + paint.textSize
        if (textY <= top + availH) {
            // saveLayerAlpha composites everything — including emoji bitmaps — at the given
            // opacity, so emoji are dimmed consistently with the rest of the text.
            if (alpha < 255) canvas.saveLayerAlpha(left, top, left + availW, textY + paint.descent(), alpha)
            canvas.drawText(title, 0, ellipsized.length, left + 2f, textY, paint)
            if (alpha < 255) canvas.restore()
        }
    }

    private fun getTextPaintForDay(day: DayMonthly): Paint {
        var color = when {
            day.isToday -> primaryColor
            highlightWeekends && day.isWeekend -> weekendsTextColor
            else -> textColor
        }
        val isActive = if (activeMonthCode.isNotEmpty()) day.code.getMonthCode() == activeMonthCode else day.isThisMonth
        if (!isActive) color = color.adjustAlpha(MEDIUM_ALPHA)
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
