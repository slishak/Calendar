package org.fossify.calendar.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.fossify.calendar.databinding.ItemDayAgendaEventBinding
import org.fossify.calendar.helpers.Formatter
import org.fossify.calendar.models.Event

class DayAgendaAdapter(
    private val events: List<Event>,
    private val onEventClick: (Event) -> Unit
) : RecyclerView.Adapter<DayAgendaAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemDayAgendaEventBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDayAgendaEventBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun getItemCount() = events.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val event = events[position]
        val context = holder.binding.root.context
        holder.binding.apply {
            eventColorBar.setBackgroundColor(event.color)
            eventTitle.text = event.title
            eventTime.text = if (event.getIsAllDay()) {
                context.getString(org.fossify.calendar.R.string.all_day)
            } else {
                val start = Formatter.getTimeFromTS(context, event.startTS)
                val end = Formatter.getTimeFromTS(context, event.endTS)
                if (start == end) start else "$start \u2013 $end"
            }
            root.setOnClickListener { onEventClick(event) }
        }
    }
}
