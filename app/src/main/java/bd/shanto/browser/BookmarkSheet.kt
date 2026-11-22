package bd.shanto.browser

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.graphics.drawable.ColorDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.*

/** The normal and selection views share a single list and the existing local bookmark store. */
internal fun Activity.bookmarkSheet(read: () -> Set<String>,
                                    write: (Set<String>) -> Unit, navigate: (String) -> Unit,nameOf:(String)->String,rename:(String,String)->Unit,backup:(Boolean)->Unit) {
    val ctx=this
    val dialog=Dialog(this).apply { requestWindowFeature(Window.FEATURE_NO_TITLE); setTitle(getString(R.string.ui_bookmarks)) }   // 1.9: window name for TalkBack
    val panel=stack().apply { pad(16,16); background=shape(paper,22) }; dialog.setContentView(panel)
    val header=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL }; panel.addView(header)
    header.addView(words(getString(R.string.ui_bookmarks),22,ink,true).heading(),LinearLayout.LayoutParams(0,-2,1f))
    // 1.8: the three dots are drawn like every other icon instead of a text character that looks different per phone/font.
    val more=FrameLayout(this);more.addView(Glyph(this,"more").apply {tint(leaf)},FrameLayout.LayoutParams(dp(20),dp(20),Gravity.CENTER));header.addView(more,LinearLayout.LayoutParams(dp(48),dp(48)))
    more.tap(getString(R.string.backup_menu)){PopupMenu(this,more).apply {menu.add(0,1,0,getString(R.string.export_marks));menu.add(0,2,1,getString(R.string.import_marks));setOnMenuItemClickListener {dialog.dismiss();backup(it.itemId==1);true};show()}}
    val exit=words(getString(R.string.ui_close),14,leaf,true).apply { gravity=Gravity.CENTER; pad(12,0); minHeight=dp(48) }; header.addView(exit)
    val actions=LinearLayout(this); panel.addView(actions); val picked=mutableSetOf<String>(); var selecting=false
    val trash=LinearLayout(this).apply {gravity=Gravity.CENTER;minimumHeight=dp(48);background=shape("#F7E9E5",12);pad(14,4)}
    val trashText=words("",14,"#8E342C",true);trash.addView(Glyph(this,"trash").apply {tint("#8E342C")},dp(20),dp(20));trash.gap(6);trash.addView(trashText);actions.addView(trash,LinearLayout.LayoutParams(-1,-2))
    panel.gap(10); val hint=words("",13,"#52685D").apply { accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE };panel.addView(hint);panel.gap(6)
    val list=ListView(this).apply { divider=null; setPadding(0,0,0,0); background=shape("#FFFFFF",14); clipToOutline=true }
    val well=FrameLayout(this); panel.addView(well,LinearLayout.LayoutParams(-1,0,1f)); well.addView(list)
    val empty=stack().apply { gravity=Gravity.CENTER; background=shape("#FFFFFF",14); addView(Glyph(ctx,"mark").apply { tint("#799E88") },dp(34),dp(34));gap(8);addView(words(getString(R.string.ui_home_empty_title),15,ink,true).heading().apply {gravity=Gravity.CENTER});gap(4)
        addView(words(getString(R.string.ui_sheet_empty_body),14,"#52685D").apply {gravity=Gravity.CENTER;pad(24,0)}) }; well.addView(empty)   // 1.9.1: says what is missing AND how to start (it used to repeat itself without a next step)
    var items=read().sorted()
    fun enable(view:View,ok:Boolean){ view.isEnabled=ok;view.alpha=if(ok) 1f else .42f }
    fun controls(){
        empty.visibility=if(items.isEmpty()) View.VISIBLE else View.GONE
        // 1.9.1: the first tap only starts selecting, so the button says so; its spoken name now begins with exactly the written label (WCAG 2.5.3).
        trashText.text=if(selecting) getString(R.string.ui_delete_picked_size, picked.size) else getString(R.string.ui_select_bookmarks_to_delete)
        trash.contentDescription=if(selecting) getString(R.string.ui_delete_picked_desc, trashText.text) else trashText.text
        enable(trash,if(selecting) picked.isNotEmpty() else items.isNotEmpty())
        exit.text=if(selecting) getString(R.string.ui_cancel) else getString(R.string.ui_close);exit.contentDescription=exit.text
        hint.text=when { selecting -> getString(R.string.ui_select_to_delete_picked_size_selected, picked.size); items.isEmpty() -> getString(R.string.ui_import_hint); else -> getString(R.string.ui_saved_pages_items_size, items.size)+"\n"+getString(R.string.ui_bookmark_tips) }
    }
    val adapter=object:BaseAdapter(){
        override fun getCount()=items.size
        override fun getItem(position:Int)=items[position]
        override fun getItemId(position:Int)=position.toLong()
        override fun getView(position:Int,recycled:View?,parent:ViewGroup):View {
            val url=items[position]; val checked=url in picked
            val row=LinearLayout(ctx).apply { gravity=Gravity.CENTER_VERTICAL;pad(10,8);minimumHeight=dp(58);setBackgroundColor(color(if(checked) "#E5F1E7" else "#FFFFFF")) }
            if(selecting) row.addView(CheckBox(ctx).apply { isChecked=checked; isClickable=false; isFocusable=false; importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO },dp(38),dp(40))
            else {row.addView(ctx.siteTile(Rules.host(url) ?: "?",34),dp(34),dp(34));row.gap(10)}
            val col=stack();row.addView(col,LinearLayout.LayoutParams(0,-2,1f));col.addView(words(nameOf(url).ifBlank {Rules.host(url) ?: getString(R.string.ui_saved_page)},16,ink,true));col.addView(words(url,12,"#52685D",false,false).apply { maxLines=2;ellipsize=TextUtils.TruncateAt.END });
            // 1.9: TalkBack now hears the bookmark's name and site (it used to read only the raw address); in selection mode the row is a real checkbox,
            // and a keyboard-selected row gets a visible focus ring.
            val shownName=nameOf(url).ifBlank {Rules.host(url) ?: getString(R.string.ui_saved_page)};val site=Rules.host(url) ?: url
            row.contentDescription=if(shownName==site) shownName else "$shownName, $site"
            row.foreground=ctx.focusRing(0,android.R.attr.state_selected)
            if(selecting) row.setAccessibilityDelegate(object:View.AccessibilityDelegate(){override fun onInitializeAccessibilityNodeInfo(host:View,info:AccessibilityNodeInfo){super.onInitializeAccessibilityNodeInfo(host,info);info.className="android.widget.CheckBox";info.isCheckable=true;info.isChecked=checked}})
            return row
        }
    }
    list.adapter=adapter
    fun refresh(){ items=read().sorted(); picked.retainAll(items.toSet()); controls();adapter.notifyDataSetChanged() }
    fun cancelPick(){ selecting=false; picked.clear(); refresh() }
    list.setOnItemClickListener { _,_,i,_-> val url=items[i];if(selecting){ if(!picked.add(url)) picked.remove(url);controls();adapter.notifyDataSetChanged() }else{dialog.dismiss();navigate(url)} }
    list.setOnItemLongClickListener {_,_,i,_->if(selecting)false else {val u=items[i];renameMark(u,nameOf(u)){rename(u,it);refresh()};true}}
    exit.tap(getString(R.string.ui_close)){ if(selecting) cancelPick() else dialog.dismiss() }
    trash.tap(getString(R.string.ui_select_bookmarks_to_delete)){
        if(!selecting){ selecting=true; refresh() }else if(picked.isNotEmpty()) {
            // 1.9.1: buttons name their consequence ("Keep" / "Delete bookmarks"), the message says it cannot be undone, the title is a plural-aware count.
            val batch=picked.toSet();AlertDialog.Builder(ctx).setTitle(resources.getQuantityString(R.plurals.delete_bookmarks_title, batch.size, batch.size))
                .setMessage(getString(R.string.ui_only_the_selected_pages_will_be_removed)).setNegativeButton(getString(R.string.ui_keep_bookmarks),null)
                .setPositiveButton(getString(R.string.ui_delete_bookmarks)){_,_->write(read()-batch);cancelPick()}.show()
        }
    }
    controls(); dialog.show(); dialog.window?.apply {setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT)); val height=ctx.window.decorView.height.let { if(it>0) it else resources.displayMetrics.heightPixels };setLayout((resources.displayMetrics.widthPixels-dp(28)).coerceAtMost(dp(480)),(height*.84f).toInt().coerceAtMost(dp(560))) }
}

internal fun Activity.renameMark(url:String,name:String,save:(String)->Unit){
    val field=EditText(this).apply{setSingleLine();filters=arrayOf(android.text.InputFilter.LengthFilter(100));hint=Rules.host(url);setText(name);selectAll()}
    AlertDialog.Builder(this).setTitle(R.string.rename_mark).setView(field).setPositiveButton(R.string.apply_name){_,_->save(field.text.toString().trim())}.setNegativeButton(R.string.ui_cancel,null).show()
}
