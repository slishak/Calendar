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
import org.fossify.calendar.extensions.getFirstDayOfWeekDt
import org.fossify.calendar.extensions.getMonthCode
import org.fossify.calendar.helpers.BLOCK_MONTH_VIEW
import org.fossify.calendar.helpers.DAY_CODE
import org.fossify.calendar.helpers.Formatter
import org.fossify.calendar.interfaces.NavigationListener
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.setupDialogStuff
import org.joda.time.DateTime

class BlockMonthFragmentsHolder : MyFragmentHolder(), NavigationListener {
    private val PREFILLED_WEEKS = 261   // ~5 years of weeks

    private var _binding: FragmentBlockMonthsHolderBinding? = null
    private val binding get() = _binding!!

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: BlockMonthScrollAdapter
    private lateinit var codes: List<String>
    private var defaultPage = 0
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
        binding.blockMonthHeaderTitle.setTextColor(requireContext().getProperTextColor())
        recyclerView = binding.fragmentBlockMonthsRecycler
        setupFragment()
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupFragment() {
        codes = getWeeks(currentDayCode)
        defaultPage = codes.size / 2

        adapter = BlockMonthScrollAdapter(
            context = requireContext(),
            codes = codes,
            onDayClick = { day ->
                DayAgendaBottomSheet.newInstance(day.code)
                    .show(parentFragmentManager, "day_agenda")
            }
        )
        adapter.activeMonthCode = weekCodeToMonthCode(codes[defaultPage])

        recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            this.adapter = this@BlockMonthFragmentsHolder.adapter
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    val pos = centerItemPosition(recyclerView)
                    if (pos < 0 || pos >= codes.size) return
                    val newCode = codes[pos]

                    // Month highlight and header update whenever the derived month changes
                    val newMonthCode = weekCodeToMonthCode(newCode)
                    if (this@BlockMonthFragmentsHolder.adapter.activeMonthCode != newMonthCode) {
                        updateHeaderTitle(newCode)
                        updateActiveMonthHighlight(newMonthCode)
                    } else {
                        // Month unchanged, but views restored from RecyclerView's scroll cache
                        // won't have had onBindViewHolder called — sync any that drifted.
                        syncActiveMonthToVisibleViews()
                    }

                    if (newCode != currentDayCode) {
                        currentDayCode = newCode
                        val shouldBeVisible = shouldGoToTodayBeVisible()
                        if (isGoToTodayVisible != shouldBeVisible) {
                            (activity as? MainActivity)?.toggleGoToTodayVisibility(shouldBeVisible)
                            isGoToTodayVisible = shouldBeVisible
                        }
                    }
                }
            })
            scrollToPositionCentered(defaultPage)
        }

        updateHeaderTitle(codes[defaultPage])
    }

    /** Returns the YYYYMM month code for the week — uses the week's starting day so that a week
     *  spanning a month boundary is always attributed to the month it starts in. */
    private fun weekCodeToMonthCode(weekCode: String): String = weekCode.substring(0, 6)

    private fun updateHeaderTitle(weekCode: String) {
        val b = _binding ?: return
        val dt = Formatter.getDateTimeFromCode(weekCode)
        var label = Formatter.getMonthName(requireContext(), dt.monthOfYear)
        val targetYear = dt.toString("YYYY")
        if (targetYear != DateTime().toString("YYYY")) label += " $targetYear"
        b.blockMonthHeaderTitle.text = label
    }

    /** Generates PREFILLED_WEEKS week-start day codes centred on the week containing [code]. */
    private fun getWeeks(code: String): List<String> {
        val weeks = ArrayList<String>(PREFILLED_WEEKS)
        val weekStart = requireContext().getFirstDayOfWeekDt(Formatter.getDateTimeFromCode(code))
        var current = weekStart.minusWeeks(PREFILLED_WEEKS / 2)
        repeat(PREFILLED_WEEKS) {
            weeks.add(Formatter.getDayCodeFromDateTime(current))
            current = current.plusWeeks(1)
        }
        return weeks
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
        val midMonth = dateTime.withDayOfMonth(15)
        currentDayCode = Formatter.getDayCodeFromDateTime(
            requireContext().getFirstDayOfWeekDt(midMonth)
        )
        setupFragment()
    }

    override fun goToToday() {
        val midMonth = DateTime().withDayOfMonth(15)
        currentDayCode = Formatter.getDayCodeFromDateTime(
            requireContext().getFirstDayOfWeekDt(midMonth)
        )
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
        goToDateTime(dateTime.withDate(year, month, 1))
    }

    override fun refreshEvents() {
        val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return
        val first = lm.findFirstVisibleItemPosition()
        val last = lm.findLastVisibleItemPosition()
        if (first >= 0 && last >= 0) {
            adapter.notifyItemRangeChanged(first, last - first + 1, BlockMonthScrollAdapter.PAYLOAD_REFRESH_EVENTS)
        }
    }

    private fun updateActiveMonthHighlight(monthCode: String) {
        adapter.activeMonthCode = monthCode
        syncActiveMonthToVisibleViews()
    }

    /** Pushes the adapter's current activeMonthCode to any visible view that has drifted
     *  (e.g. restored from RecyclerView's scroll cache without a fresh onBindViewHolder). */
    private fun syncActiveMonthToVisibleViews() {
        val monthCode = adapter.activeMonthCode
        for (i in 0 until recyclerView.childCount) {
            val vh = recyclerView.getChildViewHolder(recyclerView.getChildAt(i)) as? BlockMonthScrollAdapter.ViewHolder
            val view = vh?.binding?.blockMonthView ?: continue
            if (view.activeMonthCode != monthCode) {
                view.activeMonthCode = monthCode
            }
        }
    }

    private fun scrollToPositionCentered(position: Int) {
        recyclerView.post {
            val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return@post
            val offset = (recyclerView.height / 5) * 2
            lm.scrollToPositionWithOffset(position, offset)
        }
    }

    private fun centerItemPosition(recyclerView: RecyclerView): Int {
        val centerView = recyclerView.findChildViewUnder(
            recyclerView.width / 2f,
            recyclerView.height / 2f
        )
        return if (centerView != null) {
            recyclerView.getChildAdapterPosition(centerView)
        } else {
            (recyclerView.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()
        }
    }

    override fun shouldGoToTodayBeVisible(): Boolean {
        val todayWeekCode = Formatter.getDayCodeFromDateTime(requireContext().getFirstDayOfWeekDt(DateTime()))
        return currentDayCode != todayWeekCode
    }

    override fun getNewEventDayCode() = if (shouldGoToTodayBeVisible()) currentDayCode else todayDayCode

    override fun printView() {
        // Printing not supported for scrollable block month view
    }

    override fun getCurrentDate(): DateTime? {
        return if (currentDayCode != "") Formatter.getLocalDateTimeFromCode(currentDayCode) else null
    }
}
