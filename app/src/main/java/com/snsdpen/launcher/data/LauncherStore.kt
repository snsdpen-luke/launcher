package com.snsdpen.launcher.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.snsdpen.launcher.model.LayoutState
import com.snsdpen.launcher.model.Page
import com.snsdpen.launcher.model.defaultLayoutState
import com.snsdpen.launcher.model.migrateLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

private val Context.layoutStore by preferencesDataStore(name = "layout")

/**
 * アプリ全体の状態(アプリ一覧 + レイアウト)。Application 直下に 1 個。
 * Activity でも Service(将来のオーバーレイ)でも同じものを StateFlow で読む。
 */
class LauncherStore(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    // encodeDefaults は必須。既定値と同じ version が省略されると、次の版で「移行不要」と誤判定する
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val key = stringPreferencesKey("layout_json")
    private val pageKey = stringPreferencesKey("page")
    private fun themeKey(p: Page) = stringPreferencesKey("theme_" + p.name)

    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps

    /** null = 読み込み前 */
    private val _layout = MutableStateFlow<LayoutState?>(null)
    val layout: StateFlow<LayoutState?> = _layout

    /** 現在ページ(最後に見ていたページを次回も開く) */
    private val _page = MutableStateFlow(Page.PRIVATE)
    val page: StateFlow<Page> = _page

    /** ページごとに選んだ色味の名前(未設定なら既定) */
    private val _themes = MutableStateFlow<Map<Page, String>>(emptyMap())
    val themes: StateFlow<Map<Page, String>> = _themes

    init {
        // PACKAGE_* は addDataScheme("package") が無いと届かない。起動時 1 回だけにしない
        context.registerReceiver(
            object : BroadcastReceiver() {
                override fun onReceive(c: Context?, i: Intent?) {
                    scope.launch { refreshApps() }
                }
            },
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addDataScheme("package")
            },
        )
        scope.launch {
            refreshApps()
            loadLayout()
        }
    }

    suspend fun refreshApps() {
        val next = withContext(Dispatchers.Default) { loadLaunchableApps(context) }
        if (next != _apps.value) _apps.value = next   // 同じなら差し替えない(復帰のたびに再構成しない)
    }

    private suspend fun loadLayout() {
        val prefs = context.layoutStore.data.first()
        _page.value = prefs[pageKey]?.let { n -> Page.entries.firstOrNull { it.name == n } } ?: Page.PRIVATE
        _themes.value = Page.entries.mapNotNull { p -> prefs[themeKey(p)]?.let { p to it } }.toMap()
        val stored = prefs[key]?.let {
            runCatching { json.decodeFromString(LayoutState.serializer(), it) }.getOrNull()
        }
        val migrated = stored?.let { migrateLayout(it, _apps.value) }
        val state = if (migrated != null) {
            // 移行が起きたら即保存(次回起動で再移行しない)
            if (migrated.version != stored.version) persist(migrated)
            migrated
        } else {
            defaultLayoutState(_apps.value).also { persist(it) }
        }
        _layout.value = state
    }

    /** レイアウトを更新して保存。読み込み前は無視 */
    fun update(transform: (LayoutState) -> LayoutState) {
        val cur = _layout.value ?: return
        val next = transform(cur)
        if (next == cur) return
        // タスクが減る更新は経路を記録する(2026-09-19 に完了タスクが 1 件、経路不明で消えた)
        if (next.tasks.size < cur.tasks.size) {
            android.util.Log.w("LauncherStore", "tasks ${cur.tasks.size} -> ${next.tasks.size}", Throwable("trace"))
        }
        _layout.value = next
        scope.launch { persist(next) }
    }

    fun setTheme(page: Page, name: String) {
        _themes.value = _themes.value + (page to name)
        scope.launch { context.layoutStore.edit { it[themeKey(page)] = name } }
    }

    fun setPage(p: Page) {
        if (_page.value == p) return
        _page.value = p
        scope.launch { context.layoutStore.edit { it[pageKey] = p.name } }
    }

    private suspend fun persist(s: LayoutState) {
        context.layoutStore.edit { it[key] = json.encodeToString(LayoutState.serializer(), s) }
    }
}
