package app.wc2026.live

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * WC2026 home-screen widget. Never blank: onUpdate paints an immediate "Loading…"
 * state, then a WorkManager job fills the live ESPN score. Reload button forces a
 * fresh fetch. System floor ~15 min; the app also calls refreshNow() on launch.
 */
class ScoreWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) mgr.updateAppWidget(id, loadingViews(context))
        ScoreWidget.refreshNow(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, ScoreWidget::class.java))
            for (id in ids) mgr.updateAppWidget(id, loadingViews(context))
            refreshNow(context)
        }
    }

    override fun onEnabled(context: Context) {
        val req = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    override fun onDisabled(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK)
    }

    companion object {
        const val ACTION_REFRESH = "app.wc2026.live.WIDGET_REFRESH"
        private const val WORK = "wc26_widget"

        /** Always-renderable placeholder so the widget is never blank / "couldn't add". */
        fun loadingViews(context: Context): RemoteViews {
            val v = RemoteViews(context.packageName, R.layout.widget_xl)
            v.setTextViewText(R.id.w_status, "Loading live…")
            v.setTextViewText(R.id.w_home, "WC2026")
            v.setTextViewText(R.id.w_away, "")
            v.setTextViewText(R.id.w_score, "—")
            v.setViewVisibility(R.id.w_live, android.view.View.GONE)
            v.setViewVisibility(R.id.m1_box, android.view.View.GONE)
            v.setViewVisibility(R.id.m2_box, android.view.View.GONE)
            v.setViewVisibility(R.id.m3_box, android.view.View.GONE)
            return v
        }

        /** Expedited one-off fetch (force-fresh; bypasses the 15-min floor). */
        fun refreshNow(context: Context) {
            WorkManager.getInstance(context).enqueue(
                OneTimeWorkRequestBuilder<WidgetUpdateWorker>()
                    .setConstraints(
                        Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                    )
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .build()
            )
        }
    }
}
