package org.fossify.calendar.adapters

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.fossify.calendar.databinding.ItemBlockMonthBinding
import org.fossify.calendar.helpers.COLUMN_COUNT
import org.fossify.calendar.helpers.Formatter
import org.fossify.calendar.helpers.MonthlyCalendarImpl
import org.fossify.calendar.interfaces.MonthlyCalendar
import org.fossify.calendar.models.DayMonthly
import org.joda.time.DateTime

class BlockMonthScrollAdapter(
    private val context: Context,
    private val codes: List<String>,
    private val onDayClick: (DayMonthly) -> Unit
) : RecyclerView.Adapter<BlockMonthScrollAdapter.ViewHolder>() {

    var activeMonthCode: String = ""

    private val mainHandler = Handler(Looper.getMainLooper())

    inner class ViewHolder(val binding: ItemBlockMonthBinding) : RecyclerView.ViewHolder(binding.root) {
        var boundCode = ""
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemBlockMonthBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        // Each item fills the RecyclerView height; weekday header is drawn externally
        val h = parent.height
        if (h > 0) {
            binding.root.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h)
        }
        binding.blockMonthView.showWeekDayHeader = false
        return ViewHolder(binding)
    }

    override fun getItemCount() = codes.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val code = codes[position]
        holder.boundCode = code
        holder.binding.blockMonthView.activeMonthCode = activeMonthCode
        holder.binding.blockMonthView.updateDays(ArrayList())
        holder.binding.blockMonthView.setDayClickCallback(onDayClick)

        val impl = MonthlyCalendarImpl(object : MonthlyCalendar {
            override fun updateMonthlyCalendar(
                ctx: Context,
                month: String,
                days: ArrayList<DayMonthly>,
                checkedEvents: Boolean,
                currTargetDate: DateTime
            ) {
                val trimmed = trimRows(days)
                mainHandler.post {
                    if (holder.boundCode == code) {
                        holder.binding.blockMonthView.activeMonthCode = activeMonthCode
                        holder.binding.blockMonthView.updateDays(trimmed)
                    }
                }
            }
        }, context)

        impl.mTargetDate = Formatter.getDateTimeFromCode(code)
        impl.getDays(false)
        impl.updateMonthlyCalendar(Formatter.getDateTimeFromCode(code))
    }

    /**
     * Trims leading/trailing rows where this month is the minority (< 4 of 7 days belong here),
     * but never below 5 rows (35 days) so every month always fills a consistent 5-week grid.
     * Border rows kept to preserve the 5-row minimum will appear as inactive (shaded) cells.
     */
    private fun trimRows(days: ArrayList<DayMonthly>): ArrayList<DayMonthly> {
        val result = days.toMutableList()
        val majority = COLUMN_COUNT / 2 + 1  // 4 for a 7-day week
        val minDays = 5 * COLUMN_COUNT        // always keep at least 5 rows

        while (result.size > minDays) {
            if (result.take(COLUMN_COUNT).count { it.isThisMonth } < majority) {
                repeat(COLUMN_COUNT) { result.removeFirst() }
            } else break
        }
        while (result.size > minDays) {
            if (result.takeLast(COLUMN_COUNT).count { it.isThisMonth } < majority) {
                repeat(COLUMN_COUNT) { result.removeLast() }
            } else break
        }
        return ArrayList(result)
    }
}
