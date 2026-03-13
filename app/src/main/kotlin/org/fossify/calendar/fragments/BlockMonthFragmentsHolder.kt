package org.fossify.calendar.fragments

import android.content.res.Resources
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.DatePicker
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.fossify.calendar.activities.MainActivity
import org.fossify.calendar.adapters.BlockMonthScrollAdapter
import org.fossify.calendar.databinding.FragmentBlockMonthsHolderBinding
import org.fossify.calendar.dialogs.DayAgendaBottomSheet
import org.fossify.calendar.extensions.getMonthCode
import org.fossify.calendar.helpers.BLOCK_MONTH_VIEW
import org.fossify.calendar.helpers.DAY_CODE
import org.fossify.calendar.helpers.Formatter
import org.fossify.calendar.interfaces.NavigationListener
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.setupDialogStuff
import org.joda.time.DateTime

class BlockMonthFragmentsHolder : MyFragmentHolder(), NavigationListener {
    private val PREFILLED_MONTHS = 251

    private var _binding: FragmentBlockMonthsHolderBinding? = null
    private val binding get() = _binding!!

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: BlockMonthScrollAdapter
    private lateinit var codes: List<String>
    private var defaultMonthlyPage = 0
    private var todayDayCode = ""
    private var currentDayCode = ""
    private var isGoToTodayVisible = false

    override val viewType = BLOCK_MONTH_VIEW

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentDayCode = arguments?.getString(DAY_CODE) ?: ""
        todayDayCode = Formatter.getTodayCode()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentBlockMonthsHolderBinding.inflate(inflater, container, false)
        binding.root.background = ColorDrawable(requireContext().getProperBackgroundColor())
        recyclerView = binding.fragmentBlockMonthsRecycler
        setupFragment()
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupFragment() {
        codes = getMonths(currentDayCode)
        defaultMonthlyPage = codes.size / 2

        adapter = BlockMonthScrollAdapter(
            context = requireContext(),
            codes = codes,
            onDayClick = { day ->
                DayAgendaBottomSheet.newInstance(day.code)
                    .show(parentFragmentManager, "day_agenda")
            }
        )

        recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            this.adapter = this@BlockMonthFragmentsHolder.adapter
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    val firstVisible = (recyclerView.layoutManager as LinearLayoutManager)
                        .findFirstVisibleItemPosition()
                    if (firstVisible >= 0 && firstVisible < codes.size) {
                        val newCode = codes[firstVisible]
                        if (newCode != currentDayCode) {
                            currentDayCode = newCode
                            updateHeaderTitle(newCode)
                            val shouldBeVisible = shouldGoToTodayBeVisible()
                            if (isGoToTodayVisible != shouldBeVisible) {
                                (activity as? MainActivity)?.toggleGoToTodayVisibility(shouldBeVisible)
                                isGoToTodayVisible = shouldBeVisible
                            }
                        }
                    }
                }
            })
            scrollToPosition(defaultMonthlyPage)
        }

        updateHeaderTitle(codes[defaultMonthlyPage])
    }

    private fun updateHeaderTitle(code: String) {
        val b = _binding ?: return
        val dt = Formatter.getDateTimeFromCode(code)
        var label = Formatter.getMonthName(requireContext(), dt.monthOfYear)
        val targetYear = dt.toString("YYYY")
        if (targetYear != DateTime().toString("YYYY")) label += " $targetYear"
        b.blockMonthHeaderTitle.text = label
    }

    private fun getMonths(code: String): List<String> {
        val months = ArrayList<String>(PREFILLED_MONTHS)
        val today = Formatter.getDateTimeFromCode(code).withDayOfMonth(1)
        for (i in -PREFILLED_MONTHS / 2..PREFILLED_MONTHS / 2) {
            months.add(Formatter.getDayCodeFromDateTime(today.plusMonths(i)))
        }
        return months
    }

    override fun goLeft() {
        val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return
        val pos = lm.findFirstVisibleItemPosition()
        if (pos > 0) recyclerView.smoothScrollToPosition(pos - 1)
    }

    override fun goRight() {
        val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return
        val pos = lm.findFirstVisibleItemPosition()
        if (pos < codes.size - 1) recyclerView.smoothScrollToPosition(pos + 1)
    }

    override fun goToDateTime(dateTime: DateTime) {
        currentDayCode = Formatter.getDayCodeFromDateTime(dateTime)
        setupFragment()
    }

    override fun goToToday() {
        currentDayCode = todayDayCode
        setupFragment()
    }

    override fun showGoToDateDialog() {
        if (activity == null) return

        val datePicker = getDatePickerView()
        datePicker.findViewById<View>(Resources.getSystem().getIdentifier("day", "id", "android")).beGone()

        val dateTime = getCurrentDate()!!
        datePicker.init(dateTime.year, dateTime.monthOfYear - 1, 1, null)

        activity?.getAlertDialogBuilder()!!
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .setPositiveButton(org.fossify.commons.R.string.ok) { _, _ -> datePicked(dateTime, datePicker) }
            .apply {
                activity?.setupDialogStuff(datePicker, this)
            }
    }

    private fun datePicked(dateTime: DateTime, datePicker: DatePicker) {
        val month = datePicker.month + 1
        val year = datePicker.year
        val newDateTime = dateTime.withDate(year, month, 1)
        goToDateTime(newDateTime)
    }

    override fun refreshEvents() {
        val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return
        val first = lm.findFirstVisibleItemPosition()
        val last = lm.findLastVisibleItemPosition()
        if (first >= 0 && last >= 0) {
            adapter.notifyItemRangeChanged(first, last - first + 1)
        }
    }

    override fun shouldGoToTodayBeVisible() = currentDayCode.getMonthCode() != todayDayCode.getMonthCode()

    override fun getNewEventDayCode() = if (shouldGoToTodayBeVisible()) currentDayCode else todayDayCode

    override fun printView() {
        // Printing not supported for scrollable block month view
    }

    override fun getCurrentDate(): DateTime? {
        return if (currentDayCode != "") Formatter.getLocalDateTimeFromCode(currentDayCode) else null
    }
}
