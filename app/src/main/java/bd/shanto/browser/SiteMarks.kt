package bd.shanto.browser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import java.io.File
import java.security.MessageDigest

private fun siteKey(h:String)=when(h){"youtube.com","www.youtube.com","m.youtube.com","youtu.be"->"youtube";"google.com","www.google.com"->"google";"dorar.net","www.dorar.net"->"dorar";else->""}
internal fun siteLabel(h:String)=when(siteKey(h)){"youtube"->"YouTube";"google"->"Google";"dorar"->"Dorar";else->h.removePrefix("www.")}
private fun Context.iconDir()=File(filesDir,"bookmark-icons")
private fun iconKey(h:String)=MessageDigest.getInstance("SHA-256").digest(h.toByteArray()).joinToString(""){"%02x".format(it)}+".png"
internal fun Context.storeSiteIcon(host:String,icon:Bitmap){
    if(siteKey(host)!="" || icon.isRecycled || icon.width<1 || icon.height<1)return
    runCatching { val dir=iconDir();dir.mkdirs();val ratio=minOf(1f,80f/maxOf(icon.width,icon.height)); val small=Bitmap.createScaledBitmap(icon,(icon.width*ratio).toInt().coerceAtLeast(1),(icon.height*ratio).toInt().coerceAtLeast(1),true)
        val out=android.util.AtomicFile(File(dir,iconKey(host)));val stream=out.startWrite();try{check(small.compress(Bitmap.CompressFormat.PNG,100,stream));out.finishWrite(stream)}catch(e:Exception){out.failWrite(stream)} }
}
internal fun Context.pruneSiteIcons(hosts:Set<String>){val keys=hosts.map(::iconKey).toSet();iconDir().listFiles()?.forEach {if(it.name !in keys)it.delete()} }
internal fun Context.siteTile(host:String,size:Int=52):View {
    val key=siteKey(host);val tile=FrameLayout(this);val drawable=if(key.isNotEmpty()) resources.getIdentifier("site_$key","drawable",packageName) else 0
    val bitmap=if(drawable==0)runCatching { val f=File(iconDir(),iconKey(host));if(f.isFile)BitmapFactory.decodeFile(f.path) else null }.getOrNull() else null
    val has=drawable!=0 || bitmap!=null;tile.background=shape(if(has)"#F9FAF5" else "#E5F1E7",size/3,if(darkPalette)"#414C46" else "#DCE3D9");tile.clipToOutline=true
    if(has){val side=dp(size*2/3);tile.addView(ImageView(this).apply {scaleType=ImageView.ScaleType.FIT_CENTER;if(drawable!=0)setImageResource(drawable) else setImageBitmap(bitmap)},FrameLayout.LayoutParams(side,side,Gravity.CENTER))}
    else tile.addView(words(siteLabel(host).take(1).uppercase(),size*2/5,leaf,true).apply{gravity=Gravity.CENTER});return tile
}
