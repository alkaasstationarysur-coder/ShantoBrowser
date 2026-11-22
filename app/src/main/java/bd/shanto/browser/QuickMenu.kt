package bd.shanto.browser

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.ColorDrawable
import android.graphics.Typeface
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.LocaleSpan
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.*
import java.util.Locale
import kotlin.math.abs

// A small, cropped line engraving; drawn locally, never downloaded.
private class Etching(c:Context,val hue:String):View(c){
    val pen=Paint(Paint.ANTI_ALIAS_FLAG).apply{style=Paint.Style.STROKE;strokeWidth=context.dp(1)*0.85f;color=color(hue)}
    override fun onDraw(c:Canvas){c.save(); c.scale(width/132f,height/82f);c.rotate(-23f,95f,0f)
        for(i in 0..9){val r=18f+i*6f;c.drawOval(96f-r,-r*.65f,96f+r,r*.65f,pen)};c.restore()}
}
internal fun Activity.quickMenu(page:String?,stored:Boolean,engineGoogle:Boolean,ads:Boolean,except:Boolean,blockedCount:()->Int,onAds:(Boolean,Boolean)->Unit,onMark:()->Unit,onQr:()->Unit,onEngine:(Boolean)->Unit,onLanguage:(String)->Unit,textZoom:Int,onZoom:(Int)->Unit){
    fun tone(light:String,dark:String)=if(darkPalette) dark else light
    val surface=tone("#FCFBF5","#19241F"); val fg=tone("#172C23","#F2F4E9");val muted=tone("#516457","#B3C0B4")
    val line=tone("#DADFD2","#3E4D41");val accent=tone("#C7EC8A","#C4E990");val accentInk="#233720"
    val rail=tone("#ECF0E5","#26332B")
    val dialog=Dialog(this);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
    dialog.setTitle(getString(R.string.ui_quick_menu))   // 1.9: gives TalkBack a window name to announce when the menu opens
    val sc=ScrollView(this).apply {background=shape(surface,24,line);clipToOutline=true;isVerticalScrollBarEnabled=false};val content=stack();sc.addView(content);dialog.setContentView(sc)
    val cap=FrameLayout(this);content.addView(cap);cap.addView(Etching(this,tone("#C3D1B4","#415C46")),FrameLayout.LayoutParams(dp(130),dp(80),Gravity.TOP or Gravity.RIGHT))
    val heading=stack().apply {setPadding(dp(22),dp(19),dp(56),dp(16))};cap.addView(heading)
    val brand=LinearLayout(this).apply {gravity=Gravity.CENTER_VERTICAL};heading.addView(brand);brand.addView(View(this).apply {background=shape(tone("#608D47","#B5D990"),4)},dp(16),dp(4));brand.gap(7)
    brand.addView(words("SHANTO  /  BROWSER",11,muted,true,false).apply {letterSpacing=.13f;importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO});heading.gap(4);heading.addView(words(getString(R.string.ui_quick_menu),27,fg,true).heading())
    val close=FrameLayout(this).apply {tap(getString(R.string.ui_close_menu)){dialog.dismiss()}};cap.addView(close,FrameLayout.LayoutParams(dp(48),dp(48),Gravity.TOP or Gravity.RIGHT).apply{setMargins(0,dp(8),dp(7),0)})
    val disc=FrameLayout(this).apply {background=shape(surface,16,line)};close.addView(disc,FrameLayout.LayoutParams(dp(30),dp(30),Gravity.CENTER));disc.addView(Glyph(this,"cross").apply {tint(fg)},FrameLayout.LayoutParams(dp(14),dp(14),Gravity.CENTER))
    val body=stack().apply {setPadding(dp(20),0,dp(20),dp(20))};content.addView(body);body.addView(View(this).apply {setBackgroundColor(color(line))},-1,dp(1))
    val mark=LinearLayout(this).apply {gravity=Gravity.CENTER_VERTICAL;setPadding(0,dp(14),0,dp(14))};body.addView(mark)
    val enabled=page!=null && !stored
    val sign=FrameLayout(this).apply{background=shape(if(enabled) accent else tone("#E6EBDD","#314036"),14)};mark.addView(sign,dp(40),dp(42));sign.addView(Glyph(this,if(stored)"check" else "mark").apply {tint(if(enabled)accentInk else muted)},FrameLayout.LayoutParams(dp(19),dp(19),Gravity.CENTER));mark.gap(12)
    val copy=stack();mark.addView(copy,LinearLayout.LayoutParams(0,-2,1f));copy.addView(words(if(stored) getString(R.string.ui_bookmarked) else getString(R.string.ui_bookmark_this_page),16,fg,true));copy.addView(words(when{page==null->getString(R.string.ui_open_a_page_first);stored->getString(R.string.ui_already_in_your_collection);else->Rules.host(page) ?: getString(R.string.ui_current_page)},13,muted).apply{setSingleLine();ellipsize=android.text.TextUtils.TruncateAt.END})
    if(enabled)mark.addView(Glyph(this,"newtab").apply {tint(fg)},dp(16),dp(16))
    // 1.9: the spoken name now matches the state (before, an already-bookmarked page still said "bookmark the current page").
    mark.tap(when{stored->getString(R.string.ui_bookmarked);page==null->getString(R.string.ui_open_a_page_first);else->getString(R.string.ui_bookmark_the_current_page)}){dialog.dismiss();onMark()};mark.isEnabled=enabled
    if(page!=null){val qr=LinearLayout(this).apply {gravity=Gravity.CENTER_VERTICAL;minimumHeight=dp(48);tap(getString(R.string.page_qr)){dialog.dismiss();onQr()}};body.addView(qr);qr.addView(Glyph(this,"qr").apply{tint(fg)},dp(20),dp(20));qr.gap(12);qr.addView(words(getString(R.string.page_qr),15,fg,true),LinearLayout.LayoutParams(0,-2,1f));qr.addView(Glyph(this,"next").apply{tint(muted)},dp(17),dp(17))}

    // The speech engine switches voice for text tagged with a language (WCAG 3.1.2), e.g. "বাংলা" inside the English UI.
    fun spoken(text:String,tag:String):CharSequence=SpannableString(text).apply {setSpan(LocaleSpan(Locale.forLanguageTag(tag)),0,text.length,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)}
    fun note(res:Int)=words(getString(res),12,muted).apply {pad(0,2)}
    fun segmented(group:String,names:List<String>,tags:List<String>,second:Boolean,change:(Boolean)->Unit){
    val track=LinearLayout(this).apply {background=shape(rail,15,ctrl);pad(3,3)};body.addView(track)
    val choices=mutableListOf<Pair<LinearLayout,TextView>>();val ticks=mutableListOf<Glyph>()
    fun select(google:Boolean){choices.forEachIndexed { i,(row,label)->val active=(i==1)==google;row.isSelected=active;row.background=if(active)shape(accent,12) else null;label.setTextColor(color(if(active)accentInk else fg));ticks[i].visibility=if(active)View.VISIBLE else View.GONE
        // 1.9: name + which setting it belongs to; "selected" comes from the radio-button state instead of an appended word.
        row.contentDescription=SpannableStringBuilder().append(spoken(names[i],tags[i])).append(", ").append(group) }}
    names.forEachIndexed{i,name->val row=object:LinearLayout(this){override fun onInitializeAccessibilityNodeInfo(info:AccessibilityNodeInfo){super.onInitializeAccessibilityNodeInfo(info);info.className="android.widget.RadioButton";info.isCheckable=true;info.isChecked=isSelected}}.apply {gravity=Gravity.CENTER;minimumHeight=dp(48)};track.addView(row,LinearLayout.LayoutParams(0,-2,1f));val tick=Glyph(this,"check").apply {tint(accentInk)};ticks.add(tick);row.addView(tick,dp(13),dp(13));val label=words(name,14,fg,true).apply {pad(3,0);text=spoken(name,tags[i])};row.addView(label);choices.add(row to label);row.tap(name){select(i==1);change(i==1)} };select(second)
    }

    // Order: page actions (bookmark, QR) -> ad blocking (used most) -> page text size -> search engine and language (rarely changed).
    body.addView(View(this).apply {setBackgroundColor(color(line))},-1,dp(1))
    var switchOn=ads;var bypass=except
    val master=Switch(this).apply {text=getString(R.string.ad_blocking);textSize=16f;setTextColor(color(fg));minimumHeight=dp(52);isChecked=ads};body.addView(master)
    val host=page?.let(Rules::host)
    if(host!=null){val count=words("",13,muted);body.addView(count);val update=object:Runnable {override fun run(){count.text=getString(R.string.blocked_count,blockedCount());count.postDelayed(this,500)}};count.text=getString(R.string.blocked_count,blockedCount());count.post(update);dialog.setOnDismissListener {count.removeCallbacks(update)}}
    // Both switches read the same way: ON = blocking is active.
    val perSite=if(host==null)null else Switch(this).apply {text=getString(R.string.ad_block_site,host);textSize=14f;setTextColor(color(muted));minimumHeight=dp(48);isChecked=!except;visibility=if(ads)View.VISIBLE else View.GONE;body.addView(this)}
    master.setOnCheckedChangeListener { _,on->switchOn=on;perSite?.visibility=if(on)View.VISIBLE else View.GONE; onAds(on,bypass) }
    perSite?.setOnCheckedChangeListener { _,blockHere->bypass=!blockHere;onAds(switchOn,bypass) }
    body.addView(note(R.string.ui_ads_note))   // 1.9 (WCAG 3.2.2): say up front that flipping these reloads the page

    body.gap(8);body.addView(View(this).apply {setBackgroundColor(color(line))},-1,dp(1));body.gap(14)
    // 1.9: a visible +/- control for page text size (the pinch gesture had no single-pointer alternative besides double-tap).
    body.addView(words(getString(R.string.ui_page_text_size),14,muted,true).heading());body.gap(8)
    val steps=listOf(80,90,100,115,130,150,175,200)
    var at=steps.indices.minByOrNull {abs(steps[it]-textZoom)} ?: 2
    val zoomTrack=LinearLayout(this).apply {gravity=Gravity.CENTER_VERTICAL;background=shape(rail,15,ctrl);pad(3,3)};body.addView(zoomTrack)
    val minus=FrameLayout(this).apply {background=shape(surface,12)};minus.addView(Glyph(this,"minus").apply {tint(fg)},FrameLayout.LayoutParams(dp(18),dp(18),Gravity.CENTER))
    val plus=FrameLayout(this).apply {background=shape(surface,12)};plus.addView(Glyph(this,"newtab").apply {tint(fg)},FrameLayout.LayoutParams(dp(18),dp(18),Gravity.CENTER))
    val level=words("",16,fg,true).apply {gravity=Gravity.CENTER;accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE}
    zoomTrack.addView(minus,LinearLayout.LayoutParams(dp(60),dp(48)));zoomTrack.addView(level,LinearLayout.LayoutParams(0,-2,1f));zoomTrack.addView(plus,LinearLayout.LayoutParams(dp(60),dp(48)))
    fun showLevel(){level.text=java.text.NumberFormat.getPercentInstance(resources.configuration.locales[0]).format(steps[at]/100.0)
        minus.isEnabled=at>0;minus.alpha=if(minus.isEnabled) 1f else .38f;plus.isEnabled=at<steps.lastIndex;plus.alpha=if(plus.isEnabled) 1f else .38f}
    minus.tap(getString(R.string.ui_text_smaller)){if(at>0){at--;showLevel();onZoom(steps[at])}}
    plus.tap(getString(R.string.ui_text_larger)){if(at<steps.lastIndex){at++;showLevel();onZoom(steps[at])}}
    showLevel()

    body.gap(14);body.addView(View(this).apply {setBackgroundColor(color(line))},-1,dp(1));body.gap(14)
    val engineLabel=getString(R.string.ui_search_engine);body.addView(words(engineLabel,14,muted,true).heading());body.gap(8)
    segmented(engineLabel,listOf("DuckDuckGo","Google"),listOf("en","en"),engineGoogle,onEngine)
    body.gap(14);val languageLabel=getString(R.string.ui_app_language);body.addView(words(languageLabel,14,muted,true).heading());body.gap(8)
    val en=resources.configuration.locales[0].language=="en"
    segmented(languageLabel,listOf("বাংলা","English"),listOf("bn","en"),en){next->if(next!=en){dialog.dismiss();onLanguage(if(next)"en" else "bn")}}
    body.gap(6);body.addView(note(R.string.ui_language_note))   // 1.9 (WCAG 3.2.2): the app restarts when the language changes

    val width=minOf(dp(350),(resources.displayMetrics.widthPixels*.90f).toInt());dialog.setCanceledOnTouchOutside(true);dialog.window?.apply {setGravity(Gravity.RIGHT or Gravity.BOTTOM);setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT));setDimAmount(.32f);attributes=attributes.apply {x=dp(10);y=dp(62)}}
    sc.translationX=dp(64).toFloat();sc.alpha=0f;dialog.show();dialog.window?.setLayout(width,-2);sc.animate().translationX(0f).alpha(1f).setDuration(200).start()
}
