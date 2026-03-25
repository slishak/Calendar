package org.fossify.calendar.services

import android.content.Intent
import android.widget.RemoteViewsService
import org.fossify.calendar.adapters.EventAgendaWidgetAdapter

class WidgetAgendaService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent) = EventAgendaWidgetAdapter(applicationContext, intent)
}
