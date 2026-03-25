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

    // Pending holders waiting for a batched event load. Key = week-start day code.
    private val pendingHolders = LinkedHashMap<String, ViewHolder>()
    private val batchLoadRunnable = Runnable { executeBatchLoad() }

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
            scheduleBatchLoad(holder, codes[position])
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
        // Show structure immediately; events filled in via the batched load below.
        holder.binding.blockMonthView.updateDays(days)

        scheduleBatchLoad(holder, weekCode)
    }

    /**
     * Adds this holder to the pending batch and (re)schedules the batch runnable.
     * Because all onBindViewHolder calls during a single layout pass run synchronously
     * on the main thread, posting the runnable means it executes after all of them
     * have completed — so one DB query covers every pending week.
     */
    private fun scheduleBatchLoad(holder: ViewHolder, weekCode: String) {
        pendingHolders[weekCode] = holder
        mainHandler.removeCallbacks(batchLoadRunnable)
        mainHandler.post(batchLoadRunnable)
    }

    private fun executeBatchLoad() {
        if (pendingHolders.isEmpty()) return
        val toLoad = LinkedHashMap(pendingHolders)
        pendingHolders.clear()

        val startTS = toLoad.keys.minOf { Formatter.getLocalDateTimeFromCode(it).seconds() }
        val endTS = toLoad.keys.maxOf { Formatter.getLocalDateTimeFromCode(it).plusDays(7).seconds() }

        context.eventsHelper.getEvents(startTS, endTS) { events ->
            // Distribute events to each week's day list (background thread).
            toLoad.forEach { (weekCode, holder) ->
                val days = holder.currentDays ?: return@forEach
                if (holder.boundCode != weekCode) return@forEach
                days.forEach { it.dayEvents.clear() }
                attachEventsToWeekDays(days, events)
            }
            // Redraw all at once on the main thread.
            mainHandler.post {
                toLoad.forEach { (weekCode, holder) ->
                    val days = holder.currentDays ?: return@forEach
                    if (holder.boundCode == weekCode) {
                        holder.binding.blockMonthView.updateDays(days)
                    }
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
            // If endTS is exactly midnight, the event has zero duration on that day — exclude it.
            val endIsMidnight = endDT.hourOfDay == 0 && endDT.minuteOfHour == 0
            var curr = startDT
            while (true) {
                val code = Formatter.getDayCodeFromDateTime(curr)
                val isLastDay = code == endCode
                if (code >= firstCode && code <= lastCode) {
                    if (!isLastDay || !endIsMidnight) {
                        days.firstOrNull { it.code == code }?.dayEvents?.add(event)
                    }
                }
                if (isLastDay || code > lastCode) break
                curr = curr.plusDays(1)
            }
        }
    }
}
