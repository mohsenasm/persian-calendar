package com.byagowi.persiancalendar.ui.calendar.dialogs

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.text.buildSpannedString
import androidx.core.text.color
import androidx.core.view.isVisible
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.byagowi.persiancalendar.EN_DASH
import com.byagowi.persiancalendar.R
import com.byagowi.persiancalendar.databinding.MonthOverviewItemBinding
import com.byagowi.persiancalendar.entities.CalendarEvent
import com.byagowi.persiancalendar.entities.Jdn
import com.byagowi.persiancalendar.global.isShowWeekOfYearEnabled
import com.byagowi.persiancalendar.global.language
import com.byagowi.persiancalendar.global.mainCalendar
import com.byagowi.persiancalendar.global.secondaryCalendar
import com.byagowi.persiancalendar.global.secondaryCalendarDigits
import com.byagowi.persiancalendar.global.spacedColon
import com.byagowi.persiancalendar.ui.utils.copyToClipboard
import com.byagowi.persiancalendar.ui.utils.getCompatDrawable
import com.byagowi.persiancalendar.ui.utils.layoutInflater
import com.byagowi.persiancalendar.ui.utils.showHtml
import com.byagowi.persiancalendar.utils.applyAppLanguage
import com.byagowi.persiancalendar.utils.applyWeekStartOffsetToWeekDay
import com.byagowi.persiancalendar.utils.calendarType
import com.byagowi.persiancalendar.utils.dayTitleSummary
import com.byagowi.persiancalendar.utils.formatNumber
import com.byagowi.persiancalendar.utils.getEvents
import com.byagowi.persiancalendar.utils.getEventsTitle
import com.byagowi.persiancalendar.utils.getShiftWorkTitle
import com.byagowi.persiancalendar.utils.getWeekDayName
import com.byagowi.persiancalendar.utils.isRtl
import com.byagowi.persiancalendar.utils.logException
import com.byagowi.persiancalendar.utils.monthFormatForSecondaryCalendar
import com.byagowi.persiancalendar.utils.monthName
import com.byagowi.persiancalendar.utils.readMonthDeviceEvents
import com.byagowi.persiancalendar.utils.revertWeekStartOffsetFromWeekDay
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton
import io.github.persiancalendar.calendar.AbstractDate
import kotlinx.html.body
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.head
import kotlinx.html.html
import kotlinx.html.meta
import kotlinx.html.script
import kotlinx.html.small
import kotlinx.html.span
import kotlinx.html.stream.createHTML
import kotlinx.html.style
import kotlinx.html.sub
import kotlinx.html.table
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.tr
import kotlinx.html.unsafe
import kotlin.math.ceil

fun showMonthOverviewDialog(activity: Activity, date: AbstractDate) {
    applyAppLanguage(activity)
    val events = createEventsList(activity, date)

    BottomSheetDialog(activity, R.style.TransparentBottomSheetDialog).also { dialog ->
        dialog.setContentView(
            RecyclerView(activity).also { recyclerView ->
                recyclerView.layoutManager = LinearLayoutManager(activity)
                recyclerView.adapter = ConcatAdapter(
                    object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                        override fun getItemCount() = 1
                        override fun onBindViewHolder(
                            holder: RecyclerView.ViewHolder, position: Int
                        ) = Unit

                        override fun onCreateViewHolder(
                            parent: ViewGroup, viewType: Int
                        ) = object : RecyclerView.ViewHolder(FrameLayout(activity).also { root ->
                            root.addView(
                                FloatingActionButton(activity).also {
                                    it.contentDescription = "Print"
                                    it.setImageDrawable(activity.getCompatDrawable(R.drawable.ic_print))
                                    it.setOnClickListener {
                                        runCatching {
                                            val html = createEventsReport(activity, date, events)
                                            activity.showHtml(html)
                                        }.onFailure(logException)
                                        dialog.dismiss()
                                    }
                                    it.layoutParams = FrameLayout.LayoutParams(
                                        FrameLayout.LayoutParams.MATCH_PARENT,
                                        FrameLayout.LayoutParams.WRAP_CONTENT
                                    ).also { p -> p.gravity = Gravity.CENTER_HORIZONTAL }
                                }
                            )
                            root.layoutParams = FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.WRAP_CONTENT
                            )
                        }) {}
                    },
                    MonthOverviewItemAdapter(activity, formatEventsList(events, false))
                )
            }
        )
    }.show()
}

private fun createEventsList(
    context: Context, date: AbstractDate
): Map<Jdn, List<CalendarEvent<*>>> {
    val baseJdn = Jdn(date)
    val deviceEvents = context.readMonthDeviceEvents(baseJdn)
    return (0 until mainCalendar.getMonthLength(date.year, date.month)).map {
        val jdn = baseJdn + it
        val events = getEvents(jdn, deviceEvents)
        jdn to events
    }.toMap()
}

private fun formatEventsList(events: Map<Jdn, List<CalendarEvent<*>>>, isPrint: Boolean):
        List<Pair<Jdn, CharSequence>> {
    val result = events.toList().sortedBy { (jdn, _) -> jdn.value }.mapNotNull { (jdn, events) ->
        val holidays = getEventsTitle(
            events, holiday = true, compact = isPrint, showDeviceCalendarEvents = false,
            insertRLM = false, addIsHoliday = isPrint
        )
        val nonHolidays = getEventsTitle(
            events, holiday = false, compact = isPrint, showDeviceCalendarEvents = true,
            insertRLM = false, addIsHoliday = isPrint
        )
        if (holidays.isEmpty() && nonHolidays.isEmpty()) null
        else jdn to buildSpannedString {
            if (holidays.isNotEmpty()) color(Color.RED) { append(holidays) }
            if (nonHolidays.isNotEmpty()) {
                if (holidays.isNotEmpty()) appendLine()
                append(nonHolidays)
            }
        }
    }
    return if (isPrint) result.map { (jdn, title) ->
        jdn to title.toString().replace("\n", " $EN_DASH ")
    } else result
}

private fun createEventsReport(
    context: Context, date: AbstractDate, events: Map<Jdn, List<CalendarEvent<*>>>
) = createHTML().html {
    attributes["lang"] = language.language
    attributes["dir"] = if (context.resources.isRtl) "rtl" else "ltr"
    head {
        meta(charset = "utf8")
        style {
            unsafe {
                val calendarColumnsPercent = 100 / if (isShowWeekOfYearEnabled) 8 else 7
                +"""
                    body { font-family: system-ui }
                    td { vertical-align: top }
                    table.calendar td, table.calendar th {
                        width: $calendarColumnsPercent%; text-align: center; height: 1.5em;
                    }
                    .holiday { color: red; font-weight: bold }
                    .hasEvents { border-bottom: 1px dotted; }
                    table.events td { width: 50%; padding: 0 1em }
                    table { width: 100% }
                    h1 { text-align: center }
                """.trimIndent()
            }
        }
    }
    fun generateDayClasses(jdn: Jdn, weekEndsAsHoliday: Boolean): String {
        val dayEvents = events[jdn] ?: emptyList()
        return listOf(
            "holiday" to ((jdn.isWeekEnd() && weekEndsAsHoliday) || dayEvents.any { it.isHoliday }),
            "hasEvents" to dayEvents.isNotEmpty()
        ).filter { it.second }.joinToString(" ") { it.first }
    }
    body {
        h1 {
            +language.my.format(date.monthName, formatNumber(date.year))
            val title = monthFormatForSecondaryCalendar(date, secondaryCalendar ?: return@h1)
            small { +" ($title)" }
        }
        table("calendar") {
            tr {
                if (isShowWeekOfYearEnabled) th {}
                (0..6).forEach { th { +getWeekDayName(revertWeekStartOffsetFromWeekDay(it)) } }
            }
            val monthLength = date.calendarType.getMonthLength(date.year, date.month)
            val monthStartJdn = Jdn(date)
            val startingDayOfWeek = monthStartJdn.dayOfWeek
            val fixedStartingDayOfWeek = applyWeekStartOffsetToWeekDay(startingDayOfWeek)
            val startOfYearJdn = Jdn(date.calendarType, date.year, 1, 1)
            (0 until (6 * 7)).map {
                val index = it - fixedStartingDayOfWeek
                if (index !in (0 until monthLength)) return@map null
                (index + 1) to (monthStartJdn + index)
            }.chunked(7).map { row ->
                val firstJdnInWeek = row.firstNotNullOfOrNull { it?.second/*jdn*/ } ?: return@map
                tr {
                    if (isShowWeekOfYearEnabled) {
                        val weekOfYear = firstJdnInWeek.getWeekOfYear(startOfYearJdn)
                        th { sub { small { +formatNumber(weekOfYear) } } }
                    }
                    row.map { pair ->
                        td {
                            val (dayOfMonth, jdn) = pair ?: return@td
                            span(generateDayClasses(jdn, true)) {
                                +formatNumber(dayOfMonth)
                            }
                            val secondaryCalendar = secondaryCalendar
                            if (secondaryCalendar != null) {
                                val secondaryDateDay = jdn.toCalendar(secondaryCalendar).dayOfMonth
                                val digits = secondaryCalendarDigits
                                sub { small { +" ${formatNumber(secondaryDateDay, digits)}" } }
                            }
                            val shiftWork = getShiftWorkTitle(jdn, false)
                            if (shiftWork.isNotEmpty()) sub { small { +" $shiftWork" } }
                        }
                    }
                }
            }
        }
        table("events") {
            tr {
                val eventsTitle = formatEventsList(events, true)
                if (eventsTitle.isEmpty()) return@tr
                eventsTitle.chunked(ceil(eventsTitle.size / 2.0).toInt()).forEach {
                    td {
                        it.forEach { (jdn, title) ->
                            div {
                                span(generateDayClasses(jdn, false)) {
                                    +formatNumber(jdn.toCalendar(mainCalendar).dayOfMonth)
                                }
                                +spacedColon
                                +title.toString()
                            }
                        }
                    }
                }
            }
        }
        script { unsafe { +"print()" } }
    }
}

private class MonthOverviewItemAdapter(
    private val context: Context,
    private val rows: List<Pair<Jdn, CharSequence>>
) : RecyclerView.Adapter<MonthOverviewItemAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        MonthOverviewItemBinding.inflate(parent.context.layoutInflater, parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(position)

    override fun getItemCount(): Int = if (rows.isEmpty()) 1 else rows.size

    inner class ViewHolder(private val binding: MonthOverviewItemBinding) :
        RecyclerView.ViewHolder(binding.root), View.OnClickListener {

        init {
            binding.root.setOnClickListener(this)
        }

        private var title = ""

        fun bind(position: Int) = if (rows.isEmpty()) {
            binding.title.text = context.getString(R.string.warn_if_events_not_set)
            binding.body.isVisible = false
        } else rows[position].let { (jdn, body) ->
            title = dayTitleSummary(jdn, jdn.toCalendar(mainCalendar))
            binding.title.text = title
            binding.body.text = body
            binding.body.isVisible = body.isNotEmpty()
        }

        override fun onClick(v: View?) {
            if (rows.isEmpty()) return
            val (_, body) = rows[bindingAdapterPosition]
            if (body.isNotEmpty()) v?.context.copyToClipboard(title + "\n" + body)
        }
    }
}
