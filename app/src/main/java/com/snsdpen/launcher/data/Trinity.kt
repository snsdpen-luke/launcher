package com.snsdpen.launcher.data

import android.content.Context
import com.snsdpen.launcher.BuildConfig
import com.snsdpen.launcher.model.TaskDef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Trinity(Mac の上の秘書とタスク帳)とのやりとり。タスクの正本は Trinity の帳面(all-db の BOARD.md)で、ランチャーは映すだけ。
 * URL(鍵つき)は local.properties の trinity.url から BuildConfig に焼く。空なら Trinity は無いものとして扱う。
 * Tailscale が切れている・Mac が寝ていると届かない。そのときは最後に取れた値を出し続ける。
 */
object Trinity {
    val url: String = BuildConfig.TRINITY_URL.trimEnd('/')
    val enabled: Boolean get() = url.startsWith("https://") || url.startsWith("http://")

    data class Item(val title: String, val due: String = "", val note: String = "", val link: String = "")
    data class Home(
        val waiting: List<Item> = emptyList(),
        val working: List<Item> = emptyList(),
        val mine: List<Item> = emptyList(),
        val mineCount: Int = 0,
        val aiCount: Int = 0,
        /** 最後に取れた時刻(ms)。0 = まだ一度も取れていない */
        val at: Long = 0L,
        /** 直近の取得が失敗していれば理由 */
        val error: String? = null,
    )

    private val _home = MutableStateFlow(Home())
    val home: StateFlow<Home> = _home

    /** WORK の窓の表示中に呼ぶ。失敗しても前の値は残す */
    suspend fun refreshHome() {
        if (!enabled) return
        runCatching { withContext(Dispatchers.IO) { get("$url/api/home") } }
            .onSuccess { body ->
                val j = JSONObject(body)
                fun items(key: String, title: String = "title") = j.optJSONArray(key)?.let { a ->
                    (0 until a.length()).map { i -> a.getJSONObject(i).let { o -> Item(o.optString(title), o.optString("due"), o.optString("note"), o.optString("url")) } }
                }.orEmpty()
                _home.value = Home(items("waiting"), items("working"), items("mine"), j.optInt("mineCount"), j.optInt("aiCount"), System.currentTimeMillis())
            }
            .onFailure { e -> _home.value = _home.value.copy(error = e.message ?: e.javaClass.simpleName) }
    }

    /**
     * 一本化の移し替え: ランチャーに残っている未完了のタスクを、一度だけ Trinity の帳面に足す(剛さんの番)。
     * 成功したら印を付け、二度と送らない。ランチャー側のタスクは消さない(表示しなくなるだけ)。
     */
    suspend fun migrateOnce(context: Context, tasks: List<TaskDef>) {
        if (!enabled) return
        val prefs = context.getSharedPreferences("trinity", Context.MODE_PRIVATE)
        if (prefs.getBoolean("migrated", false)) return
        val open = tasks.filter { !it.done && it.text.isNotBlank() }
        if (open.isEmpty()) { prefs.edit().putBoolean("migrated", true).apply(); return }
        val cards = JSONArray()
        open.forEach { t ->
            cards.put(JSONObject().put("title", t.text.take(80)).put("turn", "剛さん").put("note", t.people.take(200))
                .put("due", t.due ?: "").put("source", "ランチャーのタスク(移し替え)"))
        }
        runCatching { withContext(Dispatchers.IO) { post("$url/api/inbox/add", JSONObject().put("cards", cards).toString()) } }
            .onSuccess { prefs.edit().putBoolean("migrated", true).apply() }
    }

    private fun get(u: String): String = open(u, "GET", null)
    private fun post(u: String, body: String): String = open(u, "POST", body)
    private fun open(u: String, method: String, body: String?): String {
        val c = URL(u).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method; c.connectTimeout = 6000; c.readTimeout = 15000
            if (body != null) { c.doOutput = true; c.setRequestProperty("content-type", "application/json"); c.outputStream.use { it.write(body.toByteArray()) } }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error("HTTP $code")
            return text
        } finally { c.disconnect() }
    }
}
