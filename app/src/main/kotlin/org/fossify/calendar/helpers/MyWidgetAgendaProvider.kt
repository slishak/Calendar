package org.fossify.calendar.helpers

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import org.fossify.calendar.R
import org.fossify.calendar.activities.SplashActivity
import org.fossify.calendar.extensions.config
import org.fossify.calendar.extensions.getWidgetFontSize
import org.fossify.calendar.extensions.launchNewEventOrTaskActivity
import org.fossify.calendar.services.WidgetAgendaService
import org.fossify.calendar.services.WidgetServiceEmpty
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.ensureBackgroundThread
import org.joda.time.DateTime
import java.util.Locale

class MyWidgetAgendaProvider : AppWidgetProvider() {
    private val NEW_EVENT = "new_event_agenda"
    private val GO_TO_TODAY = "go_to_today_agenda"

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        performUpdate(context)
    }

    private fun performUpdate(context: Context) {
        val textColor = context.config.widgetTextColor
        val appWidgetManager = AppWidgetManager.getInstance(context) ?: return

        ensureBackgroundThread {
            appWidgetManager.getAppWidgetIds(getComponentName(context)).forEach { widgetId ->
                val now = DateTime()
                val views = RemoteViews(context.packageName, R.layout.widget_agenda).apply {
                    applyColorFilter(R.id.widget_agenda_background, context.config.widgetBgColor)

                    // Date strip texts
                    setTextColor(R.id.widget_agenda_day_name, textColor)
                    setTextColor(R.id.widget_agenda_day_number, textColor)
                    setTextColor(R.id.widget_agenda_month_name, textColor)
                    setTextColor(R.id.widget_agenda_empty, textColor)
                    setTextSize(R.id.widget_agenda_empty, context.getWidgetFontSize())

                    setText(R.id.widget_agenda_day_name, now.dayOfWeek().getAsShortText(Locale.getDefault()))
                    setText(R.id.widget_agenda_day_number, now.dayOfMonth.toString())
                    setText(R.id.widget_agenda_month_name, now.monthOfYear().getAsText(Locale.getDefault()))

                    // Action icons
                    setImageViewBitmap(
                        R.id.widget_agenda_go_to_today,
                        context.resources.getColoredBitmap(R.drawable.ic_today_vector, textColor)
                    )
                    setImageViewBitmap(
                        R.id.widget_agenda_new_event,
                        context.resources.getColoredBitmap(org.fossify.commons.R.drawable.ic_plus_vector, textColor)
                    )
                }

                setupIntent(context, views, GO_TO_TODAY, R.id.widget_agenda_go_to_today)
                setupIntent(context, views, NEW_EVENT, R.id.widget_agenda_new_event)

                // Tapping the date strip opens the calendar at today
                val launchIntent = (context.getLaunchIntent() ?: Intent(context, SplashActivity::class.java)).apply {
                    putExtra(DAY_CODE, Formatter.getDayCodeFromDateTime(DateTime()))
                    putExtra(VIEW_TO_OPEN, context.config.listWidgetViewToOpen)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                views.setOnClickPendingIntent(
                    R.id.widget_agenda_date_strip,
                    PendingIntent.getActivity(context, 0, launchIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
                )

                // Remote adapter for the events list
                Intent(context, WidgetAgendaService::class.java).apply {
                    data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
                    views.setRemoteAdapter(R.id.widget_agenda_list, this)
                }

                val startActivityIntent = context.getLaunchIntent() ?: Intent(context, SplashActivity::class.java)
                val startActivityPendingIntent = PendingIntent.getActivity(
                    context, 0, startActivityIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
                views.setPendingIntentTemplate(R.id.widget_agenda_list, startActivityPendingIntent)
                views.setEmptyView(R.id.widget_agenda_list, R.id.widget_agenda_empty)

                appWidgetManager.updateAppWidget(widgetId, views)
                appWidgetManager.notifyAppWidgetViewDataChanged(widgetId, R.id.widget_agenda_list)
            }
        }
    }

    private fun setupIntent(context: Context, views: RemoteViews, action: String, id: Int) {
        Intent(context, MyWidgetAgendaProvider::class.java).apply {
            this.action = action
            val pendingIntent = PendingIntent.getBroadcast(context, 0, this, PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(id, pendingIntent)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            NEW_EVENT -> context.launchNewEventOrTaskActivity()
            GO_TO_TODAY -> goToToday(context)
            else -> super.onReceive(context, intent)
        }
    }

    // Reset list scroll position to today then refresh
    private fun goToToday(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context) ?: return
        ensureBackgroundThread {
            appWidgetManager.getAppWidgetIds(getComponentName(context)).forEach { widgetId ->
                val views = RemoteViews(context.packageName, R.layout.widget_agenda)
                Intent(context, WidgetServiceEmpty::class.java).apply {
                    data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
                    views.setRemoteAdapter(R.id.widget_agenda_list, this)
                }
                appWidgetManager.updateAppWidget(widgetId, views)
            }
            performUpdate(context)
        }
    }

    private fun getComponentName(context: Context) = ComponentName(context, MyWidgetAgendaProvider::class.java)
}
