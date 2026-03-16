package org.fossify.calendar.adapters

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.fossify.calendar.databinding.ItemBlockMonthBinding
import org.fossify.calendar.extensions.eventsHelper
import org.fossify.calendar.extensions.isWeekendIndex
import org.fossify.calendar.extensions.seconds
import org.fossify.calendar.helpers.COLUMN_COUNT
import org.fossify.calendar.helpers.Formatter
import org.fossify.calendar.models.DayMonthly
import org.fossify.calendar.models.Event

class BlockMonthScrollAdapter(
    private val context: Context,
    private val codes: List<String>,   // week-start day codes (YYYYMMDD)
    private val onDayClick: (DayMonthly) -> Unit
) : RecyclerView.Adapter<BlockMonthScrollAdapter.ViewHolder>() {

    companion object {
        const val PAYLOAD_REFRESH_EVENTS = "refresh_events"
    }

    var activeMonthCode: String = ""

    private val mainHandler = Handler(Looper.getMainLooper())

    inner class ViewHolder(val binding: ItemBlockMonthBinding) : RecyclerView.ViewHolder(binding.root) {
        var boundCode = ""
        var currentDays: ArrayList<DayMonthly>? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemBlockMonthBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        val h = parent.height / 5   // always show exactly 5 rows on screen
        if (h > 0) {
            binding.root.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h)
        }
        binding.blockMonthView.showWeekDayHeader = false
        return ViewHolder(binding)
    }

    override fun getItemCount() = codes.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty() && payloads.all { it == PAYLOAD_REFRESH_EVENTS }) {
            reloadEvents(holder, codes[position])
        } else {
            onBindViewHolder(holder, position)
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val weekCode = codes[position]
        holder.boundCode = weekCode
        holder.binding.blockMonthView.activeMonthCode = activeMonthCode
        holder.binding.blockMonthView.setDayClickCallback(onDayClick)

        val days = generateWeekDays(weekCode)
        holder.currentDays = days
        // Show structure immediately; events fill in async below
        holder.binding.blockMonthView.updateDays(days)
        reloadEvents(holder, weekCode)
    }

    private fun reloadEvents(holder: ViewHolder, weekCode: String) {
        val days = holder.currentDays ?: return

        val weekStartDT = Formatter.getLocalDateTimeFromCode(weekCode)
        val startTS = weekStartDT.seconds()
        val endTS = weekStartDT.plusDays(7).seconds()

        context.eventsHelper.getEvents(startTS, endTS) { events ->
            // Clear and repopulate inside the async callback so the main thread
            // never sees an intermediate state with events cleared but not yet refilled.
            days.forEach { it.dayEvents.clear() }
            attachEventsToWeekDays(days, events)
            mainHandler.post {
                if (holder.boundCode == weekCode) {
                    holder.binding.blockMonthView.updateDays(days)
                }
            }
        }
    }

    private fun generateWeekDays(weekCode: String): ArrayList<DayMonthly> {
        val result = ArrayList<DayMonthly>(COLUMN_COUNT)
        val todayCode = Formatter.getTodayCode()
        var current = Formatter.getLocalDateTimeFromCode(weekCode)
        for (i in 0 until COLUMN_COUNT) {
            val code = Formatter.getDayCodeFromDateTime(current)
            result.add(
                DayMonthly(
                    value = current.dayOfMonth,
                    isThisMonth = true,  // activeMonthCode controls dimming in BlockMonthView
                    isToday = code == todayCode,
                    code = code,
                    weekOfYear = current.weekOfWeekyear,
                    dayEvents = ArrayList(),
                    indexOnMonthView = i,
                    isWeekend = context.isWeekendIndex(i)
                )
            )
            current = current.plusDays(1)
        }
        return result
    }

    private fun attachEventsToWeekDays(days: ArrayList<DayMonthly>, events: ArrayList<Event>) {
        val firstCode = days.first().code
        val lastCode = days.last().code
        events.forEach { event ->
            val startDT = Formatter.getDateTimeFromTS(event.startTS)
            val endDT = Formatter.getDateTimeFromTS(event.endTS)
            val endCode = Formatter.getDayCodeFromDateTime(endDT)
            var curr = startDT
            while (true) {
                val code = Formatter.getDayCodeFromDateTime(curr)
                if (code >= firstCode && code <= lastCode) {
                    days.firstOrNull { it.code == code }?.dayEvents?.add(event)
                }
                if (code == endCode || code > lastCode) break
                curr = curr.plusDays(1)
            }
        }
    }
}
