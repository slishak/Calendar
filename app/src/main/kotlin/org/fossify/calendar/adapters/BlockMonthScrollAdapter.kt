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
                val trimmed = trimTrailingRows(days)
                mainHandler.post {
                    if (holder.boundCode == code) {
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
     * Removes trailing week rows that contain no days from the current month.
     * This prevents the same "overflow" days appearing at the bottom of one month
     * and again at the top of the next.
     */
    private fun trimTrailingRows(days: ArrayList<DayMonthly>): ArrayList<DayMonthly> {
        val result = days.toMutableList()
        while (result.size >= COLUMN_COUNT) {
            val lastRow = result.takeLast(COLUMN_COUNT)
            if (lastRow.none { it.isThisMonth }) {
                repeat(COLUMN_COUNT) { result.removeLast() }
            } else {
                break
            }
        }
        return ArrayList(result)
    }
}
