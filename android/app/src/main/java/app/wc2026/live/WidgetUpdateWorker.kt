package app.wc2026.live

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class WidgetUpdateWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val all = fetchAll()
            if (all.isEmpty()) return@withContext Result.success()
            val featured = all.firstOrNull { it.state == "in" }
                ?: all.firstOrNull { it.state == "pre" } ?: all.first()
            val today = dayFmt.format(Date())
            val rows = all.filter { it !== featured && it.date == today }
                .sortedBy { if (it.state == "in") 0 else if (it.state == "pre") 1 else 2 }
                .take(3)
            push(featured, rows)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun fetchAll(): List<Match> {
        val url = "https://site.api.espn.com/apis/site/v2/sports/soccer/fifa.world/" +
            "scoreboard?dates=20260611-20260719&_=" + System.currentTimeMillis()
        val events = JSONObject(URL(url).readText()).optJSONArray("events") ?: return emptyList()
        val out = ArrayList<Match>()
        for (i in 0 until events.length()) {
            val e = events.getJSONObject(i)
            val c = e.getJSONArray("competitions").getJSONObject(0)
            val cs = c.getJSONArray("competitors")
            val a0 = cs.getJSONObject(0); val a1 = cs.getJSONObject(1)
            val home = if (a0.optString("homeAway") == "home") a0 else a1
            val away = if (a0.optString("homeAway") == "home") a1 else a0
            val type = c.getJSONObject("status").getJSONObject("type")
            val d = runCatching { inFmt.parse(c.optString("date").removeSuffix("Z").take(16)) }.getOrNull() ?: Date()
            out.add(
                Match(
                    e.optString("id"),
                    home.getJSONObject("team").optString("displayName"),
                    away.getJSONObject("team").optString("displayName"),
                    home.optString("score", ""), away.optString("score", ""),
                    type.optString("state"), type.optString("shortDetail"),
                    dayFmt.format(d), timeFmt.format(d)
                )
            )
        }
        return out
    }

    private fun push(featured: Match, rows: List<Match>) {
        val mgr = AppWidgetManager.getInstance(applicationContext)
        val ids = mgr.getAppWidgetIds(ComponentName(applicationContext, ScoreWidget::class.java))
        if (ids.isEmpty()) return
        val hf = flag(featured.home); val af = flag(featured.away)
        val rowFlags = rows.map { flag(it.home) }
        for (id in ids) mgr.updateAppWidget(id, build(featured, rows, hf, af, rowFlags))
    }

    /** Deep link wc26://match/<id> -> app opens that match (handled by @capacitor/app appUrlOpen). */
    private fun openIntent(matchId: String, req: Int): PendingIntent {
        val ctx = applicationContext
        val i = Intent(Intent.ACTION_VIEW, Uri.parse("wc26://match/$matchId")).apply {
            setPackage(ctx.packageName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            ctx, req, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    /** Broadcast back to the provider to force an immediate refresh (reload button). */
    private fun refreshIntent(): PendingIntent {
        val ctx = applicationContext
        val i = Intent(ctx, ScoreWidget::class.java).setAction(ScoreWidget.ACTION_REFRESH)
        return PendingIntent.getBroadcast(
            ctx, 99, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun build(m: Match, rows: List<Match>, hf: Bitmap?, af: Bitmap?, rf: List<Bitmap?>): RemoteViews {
        val ctx = applicationContext

        fun featured(v: RemoteViews) {
            val live = m.state == "in"
            v.setTextViewText(R.id.w_home, m.home.uppercase())
            v.setTextViewText(R.id.w_away, m.away.uppercase())
            v.setTextViewText(R.id.w_score, if (m.state == "pre") "vs" else "${m.homeScore} - ${m.awayScore}")
            v.setTextViewText(R.id.w_status, when (m.state) { "in" -> m.detail; "post" -> "FT"; else -> "" })
            v.setViewVisibility(R.id.w_live, if (live) View.VISIBLE else View.GONE)
            hf?.let { v.setImageViewBitmap(R.id.w_home_flag, it) }
            af?.let { v.setImageViewBitmap(R.id.w_away_flag, it) }
            v.setOnClickPendingIntent(R.id.w_root, openIntent(m.id, 0))
            v.setOnClickPendingIntent(R.id.w_emblem, refreshIntent())
        }

        fun fill(layout: Int): RemoteViews {
            val v = RemoteViews(ctx.packageName, layout); featured(v); return v
        }

        fun fillXl(): RemoteViews {
            val v = RemoteViews(ctx.packageName, R.layout.widget_xl); featured(v)
            val rowIds = listOf(
                Quad(R.id.m1_flag, R.id.m1_text, R.id.m1_score, R.id.m1_box),
                Quad(R.id.m2_flag, R.id.m2_text, R.id.m2_score, R.id.m2_box),
                Quad(R.id.m3_flag, R.id.m3_text, R.id.m3_score, R.id.m3_box)
            )
            rowIds.forEachIndexed { i, q ->
                val r = rows.getOrNull(i)
                if (r == null) { v.setViewVisibility(q.box, View.GONE) } else {
                    v.setViewVisibility(q.box, View.VISIBLE)
                    v.setTextViewText(q.text, "${r.home} v ${r.away}")
                    val right = when (r.state) {
                        "pre" -> r.kickoff
                        "post" -> "${r.homeScore}-${r.awayScore}  FT"
                        else -> "${r.homeScore}-${r.awayScore}  ${r.detail}"
                    }
                    v.setTextViewText(q.score, right)
                    rf.getOrNull(i)?.let { v.setImageViewBitmap(q.flag, it) }
                    v.setOnClickPendingIntent(q.box, openIntent(r.id, i + 1))
                }
            }
            return v
        }

        return if (Build.VERSION.SDK_INT >= 31) {
            RemoteViews(
                mapOf(
                    SizeF(110f, 110f) to fill(R.layout.widget_small),
                    SizeF(250f, 70f) to fill(R.layout.widget_row),
                    SizeF(250f, 110f) to fill(R.layout.widget_large),
                    SizeF(300f, 180f) to fillXl()
                )
            )
        } else {
            fillXl()
        }
    }

    private fun flag(team: String): Bitmap? {
        val iso = ISO[team] ?: ISO[team.replace("&", "and")] ?: return null
        return try {
            BitmapFactory.decodeStream(URL("https://flagcdn.com/w80/$iso.png").openStream())
        } catch (e: Exception) { null }
    }

    data class Match(
        val id: String, val home: String, val away: String, val homeScore: String, val awayScore: String,
        val state: String, val detail: String, val date: String, val kickoff: String
    )
    private data class Quad(val flag: Int, val text: Int, val score: Int, val box: Int)

    companion object {
        private val inFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC"); isLenient = true }
        private val timeFmt = SimpleDateFormat("HH:mm", Locale.US)
        private val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        val ISO = mapOf(
            "Mexico" to "mx", "South Africa" to "za", "Canada" to "ca", "United States" to "us",
            "Paraguay" to "py", "Brazil" to "br", "Argentina" to "ar", "France" to "fr",
            "England" to "gb-eng", "Scotland" to "gb-sct", "Spain" to "es", "Germany" to "de",
            "Netherlands" to "nl", "Portugal" to "pt", "Morocco" to "ma", "Japan" to "jp",
            "Croatia" to "hr", "Belgium" to "be", "Uruguay" to "uy", "Colombia" to "co",
            "Switzerland" to "ch", "Korea Republic" to "kr", "Czechia" to "cz", "Australia" to "au",
            "Senegal" to "sn", "Norway" to "no", "Egypt" to "eg", "Nigeria" to "ng",
            "Bosnia & Herzegovina" to "ba", "Qatar" to "qa", "Haiti" to "ht", "Türkiye" to "tr",
            "Curaçao" to "cw", "Côte d'Ivoire" to "ci", "Ecuador" to "ec", "Sweden" to "se",
            "Tunisia" to "tn", "Iran" to "ir", "New Zealand" to "nz", "Cabo Verde" to "cv",
            "Saudi Arabia" to "sa", "Iraq" to "iq", "Algeria" to "dz", "Austria" to "at",
            "Jordan" to "jo", "Congo DR" to "cd", "Uzbekistan" to "uz", "Ghana" to "gh", "Panama" to "pa"
        )
    }
}
