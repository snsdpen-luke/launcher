package com.snsdpen.launcher.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.content.MutableContextWrapper
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.snsdpen.launcher.data.Trinity

/**
 * TASK ページ: 全面が Trinity の「秘書とタスク」(?embed=1)。画面の中身は Trinity が配るので、Trinity を直せば APK を焼き直さずに変わる。
 * - WebView は 1 個だけ作って使い回す(ページを行き来するたびに読み直さない。打ちかけの文も消えない)
 * - WebView は指を先に取るので、ランチャーの横スワイプが効かない。代わりに Trinity の画面が横スワイプを拾い、
 *   JS の Launcher.page(next) でここへ知らせる(Trinity 側 public/index.html の EMBED)
 * - Trinity 以外の URL(Claude アプリの入口など)は WebView で開かず、外のアプリへ渡す
 */
object TrinityWeb {
    @SuppressLint("StaticFieldLeak") private var view: WebView? = null
    /** Trinity の画面からの横スワイプ。2026-10-25 からページ送りはランチャー自身が受けるので、ここは何もしない(古い Trinity の画面が呼んでも無害) */
    var onSwipe: ((next: Boolean) -> Unit)? = null
    /** 読み込めなかった理由(Mac に届かない等)。null なら表示できている。Compose が見て、届かない画面を出す */
    var failed by androidx.compose.runtime.mutableStateOf<String?>(null)
    private var errInThisLoad = false
    fun reload() { failed = null; view?.reload() }
    private val main = Handler(Looper.getMainLooper())

    // 確認ダイアログ(confirm/alert/prompt)は Activity の上にしか出せない。WebView は 1 個を使い回すので、
    // 中身の Context を今の Activity に差し替えられる包みで作る(畳む・開くで Activity が作り直されても古いのを抱えない)
    private val ctx = MutableContextWrapper(null)

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    fun get(context: Context): WebView { ctx.baseContext = context; return view ?: WebView(ctx).also { w ->
        if (com.snsdpen.launcher.BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)   // Mac の Chrome の chrome://inspect から覗ける
        w.settings.javaScriptEnabled = true
        w.settings.domStorageEnabled = true            // Trinity は localStorage に開いた部屋などを覚える
        w.overScrollMode = WebView.OVER_SCROLL_NEVER
        w.addJavascriptInterface(object {
            @JavascriptInterface fun page(next: Boolean) { main.post { onSwipe?.invoke(next) } }
        }, "Launcher")
        w.webViewClient = object : WebViewClient() {
            override fun onPageStarted(v: WebView, url: String?, favicon: android.graphics.Bitmap?) { errInThisLoad = false }
            override fun onPageFinished(v: WebView, url: String?) { if (!errInThisLoad) failed = null }
            override fun onReceivedError(v: WebView, req: WebResourceRequest, err: android.webkit.WebResourceError) {
                if (req.isForMainFrame) { errInThisLoad = true; failed = err.description?.toString() ?: "接続できません" } }
            override fun onReceivedHttpError(v: WebView, req: WebResourceRequest, res: android.webkit.WebResourceResponse) {
                if (req.isForMainFrame && res.statusCode >= 400) { errInThisLoad = true; failed = "HTTP ${res.statusCode}" } }
            override fun shouldOverrideUrlLoading(v: WebView, req: WebResourceRequest): Boolean {
                val u = req.url.toString()
                if (u.startsWith(Trinity.url)) return false
                runCatching { v.context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                return true
            }
        }
        // 何もしないと WebView は confirm を常に「いいえ」で返し、Trinity の「済んだ」等が全部効かなくなる
        w.webChromeClient = object : WebChromeClient() {
            private fun dlg() = android.app.AlertDialog.Builder(ctx.baseContext)
            override fun onJsAlert(v: WebView, url: String, msg: String, r: JsResult): Boolean {
                dlg().setMessage(msg).setPositiveButton("OK") { _, _ -> r.confirm() }.setOnCancelListener { r.cancel() }.show(); return true }
            override fun onJsConfirm(v: WebView, url: String, msg: String, r: JsResult): Boolean {
                dlg().setMessage(msg).setPositiveButton("OK") { _, _ -> r.confirm() }.setNegativeButton("やめる") { _, _ -> r.cancel() }
                    .setOnCancelListener { r.cancel() }.show(); return true }
            override fun onJsPrompt(v: WebView, url: String, msg: String, def: String?, r: JsPromptResult): Boolean {
                val input = android.widget.EditText(ctx.baseContext).apply { setText(def.orEmpty()) }
                dlg().setMessage(msg).setView(input).setPositiveButton("OK") { _, _ -> r.confirm(input.text.toString()) }
                    .setNegativeButton("やめる") { _, _ -> r.cancel() }.setOnCancelListener { r.cancel() }.show(); return true }
        }
        w.loadUrl("${Trinity.url}/?embed=1")
        view = w
    } }

    /** ページに戻ってきたとき: 前の読み込みが失敗していたら読み直す。成功していればそのまま(Trinity 側が自分で新着を取りに行く) */
    fun resume() { view?.let { if (failed != null || it.url.isNullOrEmpty() || it.progress < 100) reload() else it.evaluateJavascript("window.checkUiVer && checkUiVer(); window.loadTasks && loadTasks()", null) } }
}

@Composable
fun TrinityPage(onSwipe: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    if (!Trinity.enabled) {
        Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("TRINITY が未設定\nMac の ~/launcher/local.properties に trinity.url を書いて焼き直す", color = p.fgDim, fontSize = 13.sp)
        }
        return
    }
    val swipe = rememberUpdatedState(onSwipe)
    DisposableEffect(Unit) {
        // ページの切り替えでは「新しい TASK が登録 → 古い TASK が片付け」の順に動くことがある。
        // 片付けで無条件に消すと、新しい方の登録まで消えて横スワイプが効かなくなる(2026-10-24 実機で発覚)。自分の登録だけ消す
        val mine: (Boolean) -> Unit = { swipe.value(it) }
        TrinityWeb.onSwipe = mine
        TrinityWeb.resume()
        onDispose { if (TrinityWeb.onSwipe === mine) TrinityWeb.onSwipe = null }
    }
    val context = LocalContext.current
    Box(modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { TrinityWeb.get(context).also { (it.parent as? ViewGroup)?.removeView(it) } },
        )
        // Mac(Trinity)に届かないとき：WebView のエラー画面ではなく、ランチャー自身が理由と再読み込みを出す。左右のスワイプはランチャーが受けるので、ここからいつでも出られる
        TrinityWeb.failed?.let { why ->
            androidx.compose.foundation.layout.Column(
                Modifier.fillMaxSize().background(p.bg).padding(24.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            ) {
                Text("Mac に届きません", color = p.fg, fontSize = 16.sp)
                androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
                Text("Tailscale がオンか、Mac が起きているかを確かめてください。\n左右にスワイプすると、ほかのページへ移れます。", color = p.fgDim, fontSize = 13.sp)
                Text("（$why）", color = p.fgDim, fontSize = 11.sp)
                androidx.compose.foundation.layout.Spacer(Modifier.height(16.dp))
                Box(
                    Modifier.background(p.fg).clickable { TrinityWeb.reload() }.padding(horizontal = 18.dp, vertical = 10.dp),
                ) { Text("再読み込み", color = p.bg, fontSize = 14.sp) }
            }
        }
    }
}
