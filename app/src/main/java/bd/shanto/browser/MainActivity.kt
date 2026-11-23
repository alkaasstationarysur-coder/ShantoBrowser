package bd.shanto.browser

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.net.http.SslError
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.*
import android.widget.*

class MainActivity : Activity() {
    override fun attachBaseContext(base:android.content.Context){
        val code=base.getSharedPreferences("local",MODE_PRIVATE).getString("uiLanguage","bn")
        val config=android.content.res.Configuration(base.resources.configuration)
        config.setLocale(java.util.Locale(if(code=="en") "en" else "bn"));super.attachBaseContext(base.createConfigurationContext(config))
    }
    private val prefs by lazy { getSharedPreferences("local", MODE_PRIVATE) }
    private lateinit var root: LinearLayout
    private lateinit var bar: EditText
    private lateinit var status: TextView
    private lateinit var web: WebView
    private lateinit var stage: FrameLayout
    private lateinit var landing: ScrollView
    private lateinit var progress: ProgressBar
    private fun compat() = prefs.getBoolean("compat", false)
    // Interception can run off the UI thread. Publish a fresh, immutable snapshot.
    @Volatile private var active: Set<String> = emptySet()
    private fun saved(key: String) = prefs.getStringSet(key, emptySet()).orEmpty().toSet()
    private fun markName(url:String)=prefs.getString("markName:"+url,"").orEmpty()
    private fun setName(url:String,name:String){prefs.edit().putString("markName:"+url,name.trim().take(100)).apply();refreshShortcuts()}
    private fun save(key: String, values: Set<String>) {
        val editor=prefs.edit();if(key=="marks")(saved(key)-values).forEach { editor.remove("markName:"+it) };editor.putStringSet(key,values.toSet()).apply()
        if(key=="marks"){
            val hosts=values.mapNotNull(Rules::host).toSet();pruneSiteIcons(hosts)
            if(::web.isInitialized){val h=web.url?.let(Rules::host);if(h!=null && h in hosts) web.favicon?.let {storeSiteIcon(h,it)}}
            if(::shortcuts.isInitialized)refreshShortcuts()
        }
    }
    private val adRules by lazy { resources.openRawResource(R.raw.ad_hosts).bufferedReader().use { AdDomains(it.lineSequence()) } }
    @Volatile private var pageHost:String?=null
    @Volatile private var blockAds=true
    @Volatile private var siteExceptions:Set<String> = emptySet()
    private fun filterOn() = prefs.getBoolean("filter", true)
    private fun policy(url: String): String? {
        val h = Rules.host(url) ?: return getString(R.string.address_not_supported)
        return if (Rules.blocked(h, active)) getString(R.string.site_on_block_list, h) else null
    }
    private fun note(s: String) {
        status.text = s
        status.visibility = if (s.isEmpty()) View.GONE else View.VISIBLE
    }
    private val buttons=mutableMapOf<String,FrameLayout>()
    private lateinit var wordmark: LinearLayout
    private lateinit var tabCount: TextView
    private lateinit var shortcuts: LinearLayout
    private fun syncNav() {
        val browsing=web.visibility==View.VISIBLE
        wordmark.visibility=if(browsing) View.GONE else View.VISIBLE
        for((key,button) in buttons){
            val ok=when(key){ "back"->browsing && web.canGoBack(); "next"->browsing && web.canGoForward(); "reload"->browsing && web.url?.let { policy(it)==null }==true;else->true }
            button.isEnabled=ok; button.alpha=if(ok) 1f else .38f
            // INVISIBLE (not GONE): the address field keeps its place when switching between the landing page and a web page.
            if(key=="home" || key=="reload") button.visibility=if(browsing) View.VISIBLE else View.INVISIBLE
        }
        scheduleSession()
        val count=tabs.count { it.url!=null }; tabCount.text=count.toString(); buttons["tabs"]?.contentDescription=getString(R.string.ui_open_tabs_count, count)
    }
    override fun onCreate(state: Bundle?) {
        darkPalette=(resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK)==android.content.res.Configuration.UI_MODE_NIGHT_YES
        super.onCreate(state); blockAds=prefs.getBoolean("blockAds",true);siteExceptions=saved("adExceptions"); active=if(filterOn()) saved("blocks") else emptySet()
        window.statusBarColor=color(paper); window.navigationBarColor=color("#FFFFFF")
        if(!darkPalette) window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        root=stack().apply { setBackgroundColor(color(paper)); isFocusableInTouchMode=true }; setContentView(root)
        val compact=resources.configuration.screenHeightDp<520
        wordmark=LinearLayout(this).apply {gravity=Gravity.CENTER_VERTICAL; setPadding(dp(21),dp(if(compact) 8 else 42),dp(18),dp(if(compact) 2 else 20))};root.addView(wordmark)
        val brand=stack();wordmark.addView(brand,LinearLayout.LayoutParams(0,-2,1f));val tag=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL};brand.addView(tag);tag.addView(View(this).apply {background=shape(if(darkPalette)"#C4E990" else leaf,3)},dp(17),dp(3));tag.gap(7);tag.addView(words("SHANTO  /  BROWSER",11,"#52685D",true,false).apply{letterSpacing=.15f;importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO});brand.gap(3);brand.addView(words(getString(R.string.ui_shanto_browser),28,ink,true).heading())
        // 1.8: the in-app mark follows the UI's calm green palette; the glossy launcher icon stays on the home screen only.
        wordmark.addView(BrandMark(this).apply {importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO},dp(42),dp(42))
        val top=LinearLayout(this).apply { pad(14,6); gravity=Gravity.CENTER_VERTICAL };root.addView(top)
        fun icon(parent:LinearLayout,key:String,title:String,wide:Boolean=false,act:()->Unit){
            val box=FrameLayout(this).apply {tap(title,act);tooltipText=title};buttons[key]=box
            parent.addView(box,if(wide) LinearLayout.LayoutParams(0,dp(54),1f) else LinearLayout.LayoutParams(dp(48),dp(48)))
            if(key=="tabs") { tabCount=words("1",13,ink,true,false).apply {gravity=Gravity.CENTER; background=shape("#FFFFFF",5,ink);includeFontPadding=false
                // 1.9: grows with the system font size instead of clipping the digits in a fixed 23x24dp box.
                minWidth=dp(23);minHeight=dp(24);setPadding(dp(3),0,dp(3),0)};box.addView(tabCount,FrameLayout.LayoutParams(-2,-2,Gravity.CENTER)) }
            else box.addView(Glyph(this,key),FrameLayout.LayoutParams(dp(23),dp(23),Gravity.CENTER))
        }
        icon(top,"home",getString(R.string.ui_home),act=::home)
        val field=LinearLayout(this).apply {gravity=Gravity.CENTER_VERTICAL;setPadding(dp(15),0,dp(3),0);background=fieldBg(false)}; top.addView(field,LinearLayout.LayoutParams(0,-2,1f));field.addView(Glyph(this,"search"),dp(18),dp(18))
        bar=EditText(this).apply { setSingleLine();setSelectAllOnFocus(true);background=null;pad(8,0);minHeight=dp(50);textSize=16f;hint=getString(R.string.ui_search_or_enter_an_address);setTextColor(color(ink));setHintTextColor(color("#52685D"));inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI;imeOptions=EditorInfo.IME_ACTION_GO
            setOnFocusChangeListener { _,has->field.background=fieldBg(has) }
            setOnEditorActionListener { _,id,_->if(id==EditorInfo.IME_ACTION_GO){submit();true}else false } };field.addView(bar,LinearLayout.LayoutParams(0,-2,1f))
        val go=FrameLayout(this).apply {tap(getString(R.string.ui_go_to_the_address_or_search),::submit);tooltipText=getString(R.string.ui_go)};field.addView(go,dp(48),dp(48));val chip=FrameLayout(this).apply {background=shape(if(darkPalette) "#C4E990" else "#CBE6A5",13)};go.addView(chip,FrameLayout.LayoutParams(dp(40),dp(40),Gravity.CENTER));chip.addView(Glyph(this,"next").apply {tint("#233720")},FrameLayout.LayoutParams(dp(21),dp(21),Gravity.CENTER))
        icon(top,"reload",getString(R.string.ui_reload)){web.url?.let {policy(it)?.let(::note) ?: web.reload()}}
        progress=ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply { max=100; progressTintList=android.content.res.ColorStateList.valueOf(color(leaf)); visibility=View.INVISIBLE }; root.addView(progress,-1,dp(3))
        status=words("",14,"#593D16").apply { pad(14,8); setBackgroundColor(color("#FFF0CE")); accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE }; root.addView(status)
        stage=FrameLayout(this); root.addView(stage,LinearLayout.LayoutParams(-1,0,1f))
        undoMain=undoButton(); root.addView(undoMain); refreshUndo()
        val nav=LinearLayout(this).apply { pad(4,3); setBackgroundColor(color("#FFFFFF")); elevation=dp(3).toFloat() }; root.addView(nav)
        icon(nav,"back",getString(R.string.ui_back),true){move(-1)};icon(nav,"next",getString(R.string.ui_forward),true){move(1)}
        icon(nav,"newtab",getString(R.string.ui_new_tab),true,::newTab);icon(nav,"tabs",getString(R.string.ui_open_tabs),true,::showTabs)
        icon(nav,"menu",getString(R.string.ui_menu),true,::sideMenu);restoreSession();sessionReady=true
    }
    // Only page addresses & short titles survive restart; never serialize form data or WebView state to disk.
    private var sessionReady=false
    private val sessionHandler=android.os.Handler(android.os.Looper.getMainLooper())
    private val sessionWrite=Runnable { persistSession() }
    private fun scheduleSession(){if(sessionReady){sessionHandler.removeCallbacks(sessionWrite);sessionHandler.postDelayed(sessionWrite,200)}}
    private fun persistSession(){
        if(!sessionReady)return
        val rows=org.json.JSONArray();var focus=-1
        tabs.forEachIndexed { i,t -> val url=t.url
            if(url!=null && policy(url)==null){if(selected==i)focus=rows.length();rows.put(org.json.JSONArray().put(url).put(t.title.take(120)))}
        }
        val value=org.json.JSONObject().put("pages",rows).put("focus",focus).toString()
        if(prefs.getString("session08",null)!=value)prefs.edit().putString("session08",value).apply()
    }
    private fun restoreSession(){
        val data=runCatching {org.json.JSONObject(prefs.getString("session08","{}") ?: "{}")}.getOrNull()
        val pages=data?.optJSONArray("pages");val wanted=data?.optInt("focus",-1) ?: -1
        tabs.clear();var focus=-1
        for(i in 0 until minOf(8,pages?.length() ?: 0)){val row=pages?.optJSONArray(i) ?: continue;val url=row.optString(0)
            if(policy(url)!=null)continue
            if(i==wanted)focus=tabs.size
            tabs.add(Tab(url=url,title=row.optString(1).take(120)))
        }
        if(focus<0){if(tabs.size<maxTabs)tabs.add(Tab());focus=tabs.lastIndex};displayTab(focus)
    }
    override fun onStop(){sessionHandler.removeCallbacks(sessionWrite);persistSession();super.onStop()}
    private data class Tab(var url:String?=null, var history:Bundle?=null,var title:String="")
    private val tabs=mutableListOf(Tab())
    private val maxTabs=8   // 1.9.1: one place for the limit; the message now takes the number instead of spelling it out
    private var selected=0
    private fun rememberTab() {
        val tab=tabs[selected]; tab.url=if(web.visibility==View.VISIBLE) web.url ?: tab.url else null
        if(tab.url!=null) tab.title=web.title?.take(120)?.trim().orEmpty().ifBlank { tab.title }
        tab.history=if(tab.url!=null) Bundle().takeIf { web.saveState(it)!=null } else null
    }
    private fun newTab() {
        rememberTab()
        if(tabs[selected].url!=null){
            if(tabs.size>=maxTabs){ note(getString(R.string.tab_limit_reached,maxTabs)); showTabs(); return }
            tabs.add(Tab()); selected=tabs.lastIndex; home()
        }
        bar.requestFocus(); (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(bar,InputMethodManager.SHOW_IMPLICIT)
    }
    private fun displayTab(index:Int) {
        selected=index; val tab=tabs[index]; val url=tab.url; val history=tab.history
        home(); if(url==null) return
        tabs[index].title=tab.title
        val denied=policy(url); if(denied!=null){ note(denied); return }
        pageHost=Rules.host(url)
        if(history==null || web.restoreState(history)==null) { open(url); return }
        tabs[index].url=url; landing.visibility=View.GONE; web.visibility=View.VISIBLE; bar.setText(url); syncNav()
    }
    private fun goTab(index:Int){
        if(index==selected) return
        rememberTab(); var next=index
        if(tabs[selected].url==null){tabs.removeAt(selected);if(selected<index)next--}
        displayTab(next)
    }
    private data class Closed(val tab:Tab,val index:Int,val active:Boolean)
    private val closes=mutableListOf<Closed>()
    private val timer=android.os.Handler(android.os.Looper.getMainLooper())
    private lateinit var undoMain:TextView
    private var sheetRefresh:(()->Unit)?=null
    private var sheetUndo:TextView?=null
    private fun undoButton()=words("",15,leaf,true).apply { gravity=Gravity.CENTER; minHeight=dp(48);background=shape("#E5F1E7",12);tap(getString(R.string.ui_restore_closed_tab),::undoClose);accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE }
    private fun refreshUndo(){for(v in listOfNotNull(undoMain,sheetUndo)){v.text=getString(R.string.ui_tab_closed_undo);v.visibility=if(closes.isEmpty()) View.GONE else View.VISIBLE}}
    private fun clearUndo(){timer.removeCallbacksAndMessages(null);closes.clear();refreshUndo()}
    private fun closeTab(i:Int){
        rememberTab(); val wasActive=i==selected; val removed=Closed(tabs.removeAt(i),i,wasActive)
        if(wasActive){if(tabs.isEmpty())tabs.add(Tab());displayTab(i.coerceAtMost(tabs.lastIndex))}
        else {if(i<selected)selected--;syncNav()}
        closes.add(removed);refreshUndo();sheetRefresh?.invoke()
        timer.postDelayed({closes.removeAll {it===removed};refreshUndo()},undoWindowMs())
    }
    // 1.9 (WCAG 2.2.1): a fixed 6 s was too short for TalkBack / Switch Access users. Use the system's
    // "time to take action" setting on Android 10+, and a longer window when touch exploration is on.
    private fun undoWindowMs():Long {
        val base=8000
        val am=getSystemService(ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
        return if(android.os.Build.VERSION.SDK_INT>=29)
            am.getRecommendedTimeoutMillis(base,android.view.accessibility.AccessibilityManager.FLAG_CONTENT_TEXT or android.view.accessibility.AccessibilityManager.FLAG_CONTENT_CONTROLS).toLong().coerceAtLeast(base.toLong())
        else if(am.isTouchExplorationEnabled) base*3L else base.toLong()
    }
    private fun fieldBg(focused:Boolean)=android.graphics.drawable.GradientDrawable().apply {setColor(color("#FFFFFF"));cornerRadius=dp(19).toFloat();setStroke(if(focused) dp(2) else dp(1),color(if(focused) leaf else ctrl))}
    private fun undoClose(){
        val item=closes.lastOrNull() ?: return
        rememberTab(); val blank=tabs[selected].url==null
        if(!blank && tabs.size>=maxTabs){Toast.makeText(this,getString(R.string.ui_close_a_tab_to_make_room_before),Toast.LENGTH_SHORT).show();return}
        val url=item.tab.url ?: return
        policy(url)?.let { note(it); return }
        closes.removeAt(closes.lastIndex)
        if(blank)tabs.removeAt(selected)
        val i=item.index.coerceIn(0,tabs.size);tabs.add(i,item.tab)
        if(blank || item.active)displayTab(i) else {if(i<=selected)selected++;syncNav()}
        refreshUndo();sheetRefresh?.invoke()
    }
    private fun showTabs() {
        rememberTab(); val d=android.app.Dialog(this);d.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        val body=stack().apply {pad(18,14);background=shape(paper,20)}; d.setContentView(body)
        d.setTitle(getString(R.string.ui_open_tabs))
        val title=words("",20,ink,true).heading(); body.addView(title);body.gap(8)
        val scroll=ScrollView(this);body.addView(scroll,LinearLayout.LayoutParams(-1,-2,1f));val rows=stack();scroll.addView(rows)
        val restore=undoButton();sheetUndo=restore;body.addView(restore)
        val plus=words(getString(R.string.ui_new_tab_2),16,leaf,true).apply {gravity=Gravity.CENTER; minHeight=dp(48);tap(getString(R.string.ui_new_tab)){d.dismiss();newTab()} };body.addView(plus)
        fun redraw(){
            rows.removeAllViews();val count=tabs.count {it.url!=null};title.text=getString(R.string.ui_open_tabs_count_2, count)
            if(count==0)rows.addView(words(getString(R.string.ui_no_pages_open_yet),15,"#52685D").apply { pad(4,14) })
            tabs.forEachIndexed { i,t -> val url=t.url ?: return@forEachIndexed
                val host=Rules.host(url) ?: url;val label=t.title.ifBlank {host};val line=LinearLayout(this).apply {gravity=Gravity.CENTER_VERTICAL};rows.addView(line)
                val current=i==selected;val info=stack().apply {pad(6,8);minimumHeight=dp(56);tap(if(current) getString(R.string.ui_current_tab_desc,label,host) else "$label, $host"){d.dismiss();goTab(i)} };line.addView(info,LinearLayout.LayoutParams(0,-2,1f))
                info.addView(words(label,16,if(current)leaf else ink,true).apply {setSingleLine();ellipsize=android.text.TextUtils.TruncateAt.END});info.addView(words((if(current) getString(R.string.ui_current) else "")+host,13,"#52685D").apply {setSingleLine();ellipsize=android.text.TextUtils.TruncateAt.END})
                val del=FrameLayout(this).apply {tap(getString(R.string.ui_close_tab_label, label)){closeTab(i)}};line.addView(del,dp(48),dp(48));del.addView(Glyph(this,"cross"),FrameLayout.LayoutParams(dp(18),dp(18),Gravity.CENTER))
            };refreshUndo()
        }
        sheetRefresh=::redraw;redraw();d.setOnDismissListener {sheetRefresh=null;sheetUndo=null};d.setCanceledOnTouchOutside(true);d.show();d.window?.apply {setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));setGravity(Gravity.BOTTOM);setLayout(-1,-2);setDimAmount(.45f)}
    }
    private fun landingPage(): ScrollView {
        val scroll=ScrollView(this);shortcuts=stack().apply {pad(18,12)};scroll.addView(shortcuts);refreshShortcuts();return scroll
    }
    private fun refreshShortcuts(){
        shortcuts.removeAllViews();val urls=saved("marks").sorted();val head=LinearLayout(this).apply {gravity=Gravity.CENTER_VERTICAL};shortcuts.addView(head)
        head.addView(words(getString(R.string.ui_bookmarks),17,ink,true).heading());head.gap(8);head.addView(words(java.text.NumberFormat.getInstance(resources.configuration.locales[0]).format(urls.size),12,"#52685D",true));head.addView(Space(this),LinearLayout.LayoutParams(0,1,1f))
        head.addView(words(getString(R.string.ui_view_all),14,leaf,true).apply {minHeight=dp(48);gravity=Gravity.CENTER;pad(6,0);tap(getString(R.string.ui_view_all_bookmarks),::bookmarks)})
        shortcuts.addView(View(this).apply {setBackgroundColor(color("#DCE3D9"))},-1,dp(1));shortcuts.gap(12)
        if(urls.isEmpty()){
            // 1.8: a real empty state (icon, title, where the menu is) instead of one grey line.
            val empty=stack().apply {gravity=Gravity.CENTER_HORIZONTAL;pad(12,22)};shortcuts.addView(empty,-1,-2)
            empty.addView(Glyph(this,"mark").apply {tint("#799E88")},dp(34),dp(34));empty.gap(10)
            empty.addView(words(getString(R.string.ui_home_empty_title),16,ink,true).heading().apply {gravity=Gravity.CENTER});empty.gap(4)
            empty.addView(words(getString(R.string.ui_open_a_page_and_use_the_menu),14,"#52685D").apply {gravity=Gravity.CENTER})
            return
        }
        val cols=when {resources.configuration.screenWidthDp>=600->6;resources.configuration.screenWidthDp>=350->4;else->3}
        urls.take(8).chunked(cols).forEach { batch->val row=LinearLayout(this);shortcuts.addView(row);batch.forEach {url->val h=Rules.host(url) ?: return@forEach;val label=markName(url).ifBlank {siteLabel(h)}
            val item=stack().apply {gravity=Gravity.TOP or Gravity.CENTER_HORIZONTAL;pad(2,4);tooltipText=url;tap("$label · $url"){open(url)}};item.setOnLongClickListener {renameMark(url,markName(url)){setName(url,it)};true};row.addView(item,LinearLayout.LayoutParams(0,-2,1f));item.addView(siteTile(h),dp(52),dp(52));item.gap(5);item.addView(words(label,13,ink,true).apply {gravity=Gravity.CENTER;maxLines=2;ellipsize=android.text.TextUtils.TruncateAt.END},-1,-2)
        };repeat(cols-batch.size){row.addView(Space(this),LinearLayout.LayoutParams(0,1,1f))};shortcuts.gap(6)}
    }
    private var blockedNow=java.util.concurrent.atomic.AtomicInteger()
    private fun home() {
        val meter=java.util.concurrent.atomic.AtomicReference(java.util.concurrent.atomic.AtomicInteger());blockedNow=meter.get()
        pageHost=null
        tabs[selected]=Tab()
        if (::web.isInitialized) { stage.removeView(web); web.stopLoading(); web.destroy() }
        stage.removeAllViews(); landing=landingPage(); stage.addView(landing); progress.visibility=View.INVISIBLE
        web = WebView(this).apply { importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS }
        val thisWeb=web
        web.setOnLongClickListener {
            val hit=thisWeb.hitTestResult
            when(hit.type){
                WebView.HitTestResult.SRC_ANCHOR_TYPE->{hit.extra?.let(::linkMenu);true}
                WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE->{val handler=android.os.Handler(android.os.Looper.getMainLooper()){m->if(web===thisWeb && !isDestroyed)m.data.getString("url")?.let(::linkMenu);true};thisWeb.requestFocusNodeHref(handler.obtainMessage());true}
                else->false
            }
        }
        with(web.settings) {
            javaScriptEnabled = compat(); domStorageEnabled = compat()
            javaScriptCanOpenWindowsAutomatically=false; setGeolocationEnabled(false)
            useWideViewPort=true; loadWithOverviewMode=true; builtInZoomControls=true; displayZoomControls=false
            textZoom=prefs.getInt("textZoom",100)   // 1.9: page text size is set from the quick menu (visible +/- control)
            allowFileAccess = false; allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            safeBrowsingEnabled = true; setSupportMultipleWindows(false)
            cacheMode = WebSettings.LOAD_NO_CACHE
        }
        CookieManager.getInstance().apply { setAcceptCookie(compat()); setAcceptThirdPartyCookies(web, false) }
        web.webChromeClient=object: WebChromeClient() {
            override fun onReceivedIcon(v:WebView,icon:android.graphics.Bitmap?) { if(v!==web || icon==null)return;val h=v.url?.let(Rules::host) ?: return;if(saved("marks").any {Rules.host(it)==h})storeSiteIcon(h,icon) }
            override fun onProgressChanged(v:WebView, n:Int) { if(v!==web || web.visibility!=View.VISIBLE) return; progress.progress=n; progress.visibility=if(n in 1..99) View.VISIBLE else View.INVISIBLE }
        }
        web.setDownloadListener { _, _, _, _, _ -> note(getString(R.string.ui_downloads_are_not_available_in_this_version)) }
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                val why = policy(r.url.toString()) ?: return false
                if (r.isForMainFrame && v === web) note(why)
                return true
            }
            override fun shouldInterceptRequest(v: WebView, r: WebResourceRequest): WebResourceResponse? {
                val pageCounter=meter.get();val address=r.url.toString(); val why=policy(address)
                if(why==null){
                    val h=r.url.host?.lowercase(java.util.Locale.ROOT)?.removeSuffix(".") ?: return null
                    if(adRules.block(h,pageHost,r.isForMainFrame,blockAds,siteExceptions)){pageCounter.incrementAndGet()
                        return WebResourceResponse("text/plain","UTF-8",403,"Filtered",mapOf("Cache-Control" to "no-store"),java.io.ByteArrayInputStream(ByteArray(0)))}
                    return null
                }
                return WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", emptyMap(), why.byteInputStream())
            }
            override fun onPageStarted(v: WebView, url: String, icon: android.graphics.Bitmap?) {
                if (v !== web) return
                blockedNow=java.util.concurrent.atomic.AtomicInteger();meter.set(blockedNow)
                val why = policy(url)
                if (why != null) { home(); note(why); return }
                pageHost=Rules.host(url);tabs[selected].url=url;bar.setText(url); note(""); syncNav()
            }
            override fun doUpdateVisitedHistory(v: WebView,url: String?,isReload:Boolean) { if(v===web && v.visibility==View.VISIBLE && url!=null && policy(url)==null){tabs[selected].url=url;syncNav()} }
            override fun onPageFinished(v: WebView,url:String) { if(v===web && v.visibility==View.VISIBLE && policy(url)==null){tabs[selected].title=v.title.orEmpty().take(120);syncNav()} }
            override fun onReceivedError(v: WebView, r: WebResourceRequest, e: WebResourceError) {
                if (v === web && r.isForMainFrame) note(when(e.errorCode) {
                    ERROR_HOST_LOOKUP -> getString(R.string.ui_site_not_found_check_the_address_and)
                    ERROR_TIMEOUT, ERROR_CONNECT -> getString(R.string.ui_could_not_connect_check_your_network_and)
                    ERROR_FAILED_SSL_HANDSHAKE -> getString(R.string.ui_could_not_establish_a_secure_connection)
                    else -> getString(R.string.ui_could_not_open_the_page_error_e, e.errorCode)
                })
            }
            override fun onReceivedHttpError(v: WebView, r: WebResourceRequest, response: WebResourceResponse) {
                if (v === web && r.isForMainFrame) note(policy(r.url.toString()) ?: getString(R.string.ui_the_site_could_not_serve_this_page, response.statusCode))
            }
            override fun onReceivedSslError(v: WebView, handler: SslErrorHandler, error: SslError) {
                handler.cancel(); if (v === web) note(getString(R.string.ui_the_secure_connection_could_not_be_verified))
            }
        }
        stage.addView(web); web.visibility=View.GONE; bar.setText(""); note(""); syncNav()
    }
    private fun submit() {
        val url = Rules.address(bar.text.toString(), prefs.getBoolean("google", false))
        if (url == null) { note(getString(R.string.address_not_supported)); return }
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(bar.windowToken, 0)
        open(url)
    }
    private fun open(url: String) {
        val why = policy(url); if (why != null) { note(why); return }
        pageHost=Rules.host(url);tabs[selected].url=url; landing.visibility=View.GONE; web.visibility=View.VISIBLE; bar.clearFocus(); note(""); web.loadUrl(url); syncNav()
    }
    private fun move(step: Int) {
        val history = web.copyBackForwardList(); val i = history.currentIndex + step
        if (i !in 0 until history.size) { note(getString(if (step < 0) R.string.no_earlier_page else R.string.no_next_page)); return }
        val why = policy(history.getItemAtIndex(i).url)
        if (why == null) web.goBackOrForward(step) else note(why)
    }
    @Deprecated("Platform back callback")
    override fun onBackPressed() { if (web.canGoBack()) move(-1) else super.onBackPressed() }
    private fun currentPage():String? = if(web.visibility==View.VISIBLE) web.url?.takeIf {policy(it)==null} else null
    // 1.9.2: one switch for site scripts (JavaScript), page storage and cookies. It is the existing "compat" preference, off by default.
    // Turning it off also wipes cookies and page storage, so nothing stays behind from the time it was on.
    private fun setScripts(on:Boolean){
        prefs.edit().putBoolean("compat",on).apply()
        if(!::web.isInitialized) return
        web.settings.javaScriptEnabled=on;web.settings.domStorageEnabled=on
        val cookies=CookieManager.getInstance();cookies.setAcceptCookie(on)
        if(!on){cookies.removeAllCookies(null);cookies.flush();WebStorage.getInstance().deleteAllData()}
        if(web.visibility==View.VISIBLE)web.reload()
    }
    private fun sideMenu(){
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(bar.windowToken,0)
        val url=currentPage()
        quickMenu(url,url!=null && url in saved("marks"),prefs.getBoolean("google",false),blockAds,url?.let(Rules::host) in siteExceptions,blockedCount={blockedNow.get()},onAds={ enabled,except ->
            val host=url?.let(Rules::host);blockAds=enabled
            if(host!=null) siteExceptions=if(except) siteExceptions+host else siteExceptions-host
            prefs.edit().putBoolean("blockAds",enabled).putStringSet("adExceptions",siteExceptions.toSet()).apply()
            if(url!=null && currentPage()==url)web.reload()
        },onMark={
            if(url!=null && url==currentPage()) {save("marks",saved("marks")+url);Toast.makeText(this,getString(R.string.ui_bookmark_saved),Toast.LENGTH_SHORT).show()}
            else note(getString(R.string.ui_the_page_changed_open_it_and_try))
        },onQr={if(url!=null && url==currentPage())showPageQr(url) else note(getString(R.string.ui_the_page_changed_open_it_and_try))},onEngine={google->prefs.edit().putBoolean("google",google).apply()},onLanguage={code->
            persistSession();prefs.edit().putString("uiLanguage",code).apply();recreate()
        },textZoom=prefs.getInt("textZoom",100),onZoom={zoom->
            prefs.edit().putInt("textZoom",zoom).apply();if(::web.isInitialized)web.settings.textZoom=zoom
        },scripts=compat(),onScripts=::setScripts)
    }
    private fun linkMenu(raw:String){
        val url=runCatching {java.net.URI(web.url ?: "").resolve(raw).toString()}.getOrNull() ?: return
        val deny=policy(url);if(deny!=null){note(deny);return}
        val options=arrayOf(getString(R.string.link_tab),getString(R.string.link_copy),getString(R.string.link_share))
        AlertDialog.Builder(this).setTitle(Rules.host(url)).setItems(options){_,i->when(i){
            0->{if(tabs.size>=maxTabs)note(getString(R.string.tab_limit_reached,maxTabs)) else {tabs.add(Tab(url=url));syncNav();Toast.makeText(this,getString(R.string.link_added),Toast.LENGTH_SHORT).show()}}
            1->{val clip=getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager;clip.setPrimaryClip(android.content.ClipData.newPlainText("URL",url));Toast.makeText(this,getString(R.string.link_copied),Toast.LENGTH_SHORT).show()}
            else->{val send=android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT,url);startActivity(android.content.Intent.createChooser(send,getString(R.string.link_share)))}
        }}.setNegativeButton(getString(R.string.ui_cancel),null).show()
    }
    private fun pickBackup(export:Boolean){
        val it=android.content.Intent(if(export) android.content.Intent.ACTION_CREATE_DOCUMENT else android.content.Intent.ACTION_OPEN_DOCUMENT).addCategory(android.content.Intent.CATEGORY_OPENABLE)
        it.type=if(export) "application/json" else "*/*";if(export)it.putExtra(android.content.Intent.EXTRA_TITLE,"shanto-bookmarks.json")
        try{startActivityForResult(it,if(export)701 else 702)}catch(_:android.content.ActivityNotFoundException){note(getString(R.string.backup_no_picker))}
    }
    @Deprecated("Platform document picker callback")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:android.content.Intent?){
        super.onActivityResult(requestCode,resultCode,data);if(requestCode !in 701..702 || resultCode!=RESULT_OK)return
        val export=requestCode==701   // 1.9.1: decided first so each failure can say which job failed (saving vs. reading)
        val failText=getString(if(export) R.string.backup_save_fail else R.string.backup_read_fail)
        val uri=data?.data ?: return;if(uri.scheme!="content"){note(failText);return}
        val snap=if(export)saved("marks").associateWith(::markName) else emptyMap()
        Thread {
            try{
                val imported=if(export){val bytes=BookmarkBackup.encode(snap);requireNotNull(contentResolver.openOutputStream(uri,"wt")).use {it.write(bytes)};emptyMap()}
                else requireNotNull(contentResolver.openInputStream(uri)).use(BookmarkBackup::decode)
                runOnUiThread {if(!isDestroyed && !isFinishing){if(export)note(getString(R.string.export_done)) else {
                    val old=saved("marks");val editor=prefs.edit();imported.forEach {(u,n)->if(n.isNotBlank() && markName(u).isBlank())editor.putString("markName:"+u,n)};editor.apply();save("marks",old+imported.keys);val added=(imported.keys-old).size
                    note(if(added==0) getString(R.string.import_none) else resources.getQuantityString(R.plurals.imported_bookmarks,added,added));bookmarks()
                }}}
            }catch(_:Exception){runOnUiThread {if(!isDestroyed && !isFinishing)note(failText)}}
        }.start()
    }
    private fun bookmarks(){bookmarkSheet(read={saved("marks")},write={save("marks",it)},navigate=::open,nameOf=::markName,rename=::setName,backup=::pickBackup)}
    override fun onDestroy() {sessionReady=false;sessionHandler.removeCallbacks(sessionWrite);timer.removeCallbacksAndMessages(null);stage.removeView(web); web.destroy(); super.onDestroy() }
}
