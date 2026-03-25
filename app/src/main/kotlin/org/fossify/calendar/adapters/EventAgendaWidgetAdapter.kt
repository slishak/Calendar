package org.fossify.calendar.adapters

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import org.fossify.calendar.R
import org.fossify.calendar.extensions.*
import org.fossify.calendar.helpers.*
import org.fossify.calendar.models.Event
import org.fossify.calendar.models.ListEvent
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.MEDIUM_ALPHA
import org.joda.time.DateTime

class EventAgendaWidgetAdapter(val context: Context, val intent: Intent) : RemoteViewsService.RemoteViewsFactory {

    private val allDayString = context.resources.getString(R.string.all_day)
    private var events = ArrayList<ListEvent>()
    private var textColor = context.config.widgetTextColor
    private var weakTextColor = textColor.adjustAlpha(MEDIUM_ALPHA)
    private var dimPastEvents = context.config.dimPastEvents
    private var dimCompletedTasks = context.config.dimCompletedTasks
    private var hidePastEvents = context.config.agendaWidgetHidePastEvents
    private var mediumFontSize = context.getAgendaWidgetFontSize()
    private var smallFontSize = mediumFontSize - 2f

    init {
        initConfigValues()
    }

    private fun initConfigValues() {
        textColor = context.config.widgetTextColor
        weakTextColor = textColor.adjustAlpha(MEDIUM_ALPHA)
        dimPastEvents = context.config.dimPastEvents
        dimCompletedTasks = context.config.dimCompletedTasks
        hidePastEvents = context.config.agendaWidgetHidePastEvents
        mediumFontSize = context.getAgendaWidgetFontSize()
        smallFontSize = mediumFontSize - 2f
    }

    override fun getViewAt(position: Int): RemoteViews {
        val item = events.getOrNull(position) ?: return RemoteViews(context.packageName, R.layout.event_agenda_item_widget)
        return RemoteViews(context.packageName, R.layout.event_agenda_item_widget).also { rv ->
            setupAgendaEvent(rv, item)
        }
    }

    private fun setupAgendaEvent(rv: RemoteViews, item: ListEvent) {
        var curTextColor = textColor

        if (item.isTask && item.isTaskCompleted && dimCompletedTasks || dimPastEvents && item.isPastEvent && !item.isTask) {
            curTextColor = weakTextColor
        }

        // Coloured bar
        rv.setBackgroundColor(R.id.event_agenda_color_bar, item.color)

        // Title
        rv.setText(R.id.event_agenda_title, item.title)
        rv.setTextColor(R.id.event_agenda_title, curTextColor)
        rv.setTextSize(R.id.event_agenda_title, mediumFontSize)

        if (item.shouldStrikeThrough()) {
            rv.setInt(R.id.event_agenda_title, "setPaintFlags", Paint.ANTI_ALIAS_FLAG or Paint.STRIKE_THRU_TEXT_FLAG)
        } else {
            rv.setInt(R.id.event_agenda_title, "setPaintFlags", Paint.ANTI_ALIAS_FLAG)
        }

        // Task image
        rv.setVisibleIf(R.id.event_agenda_task_image, item.isTask)
        rv.applyColorFilter(R.id.event_agenda_task_image, curTextColor)

        // Date (bold) and time/location
        val startDT = Formatter.getDateTimeFromTS(item.startTS)
        rv.setText(R.id.event_agenda_date, startDT.toString("d MMM"))
        rv.setTextColor(R.id.event_agenda_date, curTextColor)
        rv.setTextSize(R.id.event_agenda_date, smallFontSize)

        rv.setText(R.id.event_agenda_time_location, buildTimeLocationText(item))
        rv.setTextColor(R.id.event_agenda_time_location, curTextColor)
        rv.setTextSize(R.id.event_agenda_time_location, smallFontSize)

        // Click intent
        Intent().apply {
            putExtra(EVENT_ID, item.id)
            putExtra(EVENT_OCCURRENCE_TS, item.startTS)
            putExtra(IS_TASK, item.isTask)
            rv.setOnClickFillInIntent(R.id.event_agenda_item_holder, this)
        }
    }

    private fun buildTimeLocationText(item: ListEvent): String {
        val timeText = if (item.isAllDay) {
            allDayString
        } else {
            val startTime = Formatter.getTimeFromTS(context, item.startTS)
            val endTime = Formatter.getTimeFromTS(context, item.endTS)
            if (item.startTS == item.endTS) startTime else "$startTime – $endTime"
        }

        val sb = StringBuilder("  $timeText")
        if (item.location.isNotEmpty()) {
            sb.append("  ${item.location}")
        }
        return sb.toString()
    }

    override fun onDataSetChanged() {
        initConfigValues()
        val currentDate = DateTime()
        val fromTS = if (hidePastEvents) currentDate.seconds()
                     else currentDate.seconds() - context.config.displayPastEvents * 60
        val toTS = currentDate.plusYears(1).seconds()

        context.eventsHelper.getEventsSync(fromTS, toTS, applyTypeFilter = true) { rawEvents ->
            val replaceDescription = context.config.replaceDescription
            val sorted = rawEvents.sortedWith(
                compareBy<Event> { event ->
                    if (event.getIsAllDay()) Formatter.getDayStartTS(Formatter.getDayCodeFromTS(event.startTS)) - 1
                    else event.startTS
                }.thenBy { event ->
                    if (event.getIsAllDay()) Formatter.getDayEndTS(Formatter.getDayCodeFromTS(event.endTS))
                    else event.endTS
                }.thenBy { it.title }
                .thenBy { if (replaceDescription) it.location else it.description }
            )

            val listEvents = sorted.map { event ->
                ListEvent(
                    id = event.id!!,
                    startTS = event.startTS,
                    endTS = event.endTS,
                    title = event.title,
                    description = event.description,
                    isAllDay = event.getIsAllDay(),
                    color = event.color,
                    location = event.location,
                    isPastEvent = event.isPastEvent,
                    isRepeatable = event.repeatInterval > 0,
                    isTask = event.isTask(),
                    isTaskCompleted = event.isTaskCompleted(),
                    isAttendeeInviteDeclined = event.isAttendeeInviteDeclined(),
                    isEventCanceled = event.isEventCanceled()
                )
            }

            this@EventAgendaWidgetAdapter.events = ArrayList(listEvents)
        }
    }

    override fun onCreate() {}
    override fun onDestroy() {}
    override fun getCount() = events.size
    override fun getItemId(position: Int) = position.toLong()
    override fun hasStableIds() = true
    override fun getLoadingView() = null
    override fun getViewTypeCount() = 1
}
