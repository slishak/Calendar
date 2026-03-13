package org.fossify.calendar.dialogs

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.fossify.calendar.adapters.DayAgendaAdapter
import org.fossify.calendar.databinding.BottomSheetDayAgendaBinding
import org.fossify.calendar.extensions.eventsHelper
import org.fossify.calendar.extensions.launchNewEventIntent
import org.fossify.calendar.extensions.seconds
import org.fossify.calendar.helpers.DAY_CODE
import org.fossify.calendar.helpers.EVENT_ID
import org.fossify.calendar.helpers.EVENT_OCCURRENCE_TS
import org.fossify.calendar.helpers.Formatter
import org.fossify.calendar.helpers.getActivityToOpen
import org.fossify.calendar.models.Event
import org.fossify.commons.extensions.beGoneIf
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperTextColor

class DayAgendaBottomSheet : BottomSheetDialogFragment() {

    companion object {
        fun newInstance(dayCode: String): DayAgendaBottomSheet {
            return DayAgendaBottomSheet().apply {
                arguments = Bundle().apply { putString(DAY_CODE, dayCode) }
            }
        }
    }

    private var _binding: BottomSheetDayAgendaBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetDayAgendaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val bgColor = requireContext().getProperBackgroundColor()
        val textColor = requireContext().getProperTextColor()
        binding.root.setBackgroundColor(bgColor)
        binding.dayAgendaAdd.setColorFilter(textColor)
        binding.dayAgendaTitle.setTextColor(textColor)

        val dayCode = requireArguments().getString(DAY_CODE)!!

        binding.dayAgendaList.layoutManager = LinearLayoutManager(requireContext())

        val dayStart = Formatter.getDateTimeFromCode(dayCode).withTimeAtStartOfDay()
        val startTS = dayStart.seconds()
        val endTS = dayStart.plusDays(1).seconds()

        requireContext().eventsHelper.getEvents(startTS, endTS) { events ->
            val sorted = events.sortedWith(compareBy({ !it.getIsAllDay() }, { it.startTS }))
            activity?.runOnUiThread {
                updateTitle(dayCode, sorted.size)
                binding.dayAgendaList.beVisibleIf(sorted.isNotEmpty())
                binding.dayAgendaEmpty.beGoneIf(sorted.isNotEmpty())
                binding.dayAgendaList.adapter = DayAgendaAdapter(sorted) { event ->
                    openEvent(event)
                }
            }
        }

        binding.dayAgendaAdd.setOnClickListener {
            requireContext().launchNewEventIntent(dayCode)
            dismiss()
        }
    }

    private fun updateTitle(dayCode: String, count: Int) {
        val dateStr = Formatter.getDateFromCode(requireContext(), dayCode)
        binding.dayAgendaTitle.text = if (count == 0) {
            dateStr
        } else {
            "$dateStr  \u00b7  $count"
        }
    }

    private fun openEvent(event: Event) {
        Intent(requireContext(), getActivityToOpen(event.isTask())).apply {
            putExtra(EVENT_ID, event.id)
            putExtra(EVENT_OCCURRENCE_TS, event.startTS)
            startActivity(this)
        }
        dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
