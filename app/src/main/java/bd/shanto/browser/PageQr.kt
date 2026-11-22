package bd.shanto.browser

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

internal fun Activity.showPageQr(url:String){
    val host=Rules.host(url) ?: return
    // Encode the exact URL (including query and fragment), never shorten or upload it.
    val matrix=runCatching {
        require(url.length<=3000 && url.toByteArray(Charsets.UTF_8).size<=2953)
        QRCodeWriter().encode(url,BarcodeFormat.QR_CODE,0,0,mapOf(EncodeHintType.CHARACTER_SET to "UTF-8", EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 4))
    }.getOrElse { Toast.makeText(this,getString(R.string.qr_too_long),Toast.LENGTH_LONG).show();return }
    val body=stack().apply {pad(16,12);gravity=Gravity.CENTER_HORIZONTAL}
    val code=object:View(this){val brush=Paint().apply {color=Color.BLACK;isAntiAlias=false}
        override fun onDraw(canvas:Canvas){canvas.drawColor(Color.WHITE);val cell=minOf(width,height)/matrix.width;if(cell<1)return;val dx=(width-cell*matrix.width)/2;val dy=(height-cell*matrix.height)/2
            for(y in 0 until matrix.height)for(x in 0 until matrix.width)if(matrix[x,y]){val l=(dx+x*cell).toFloat();val t=(dy+y*cell).toFloat();canvas.drawRect(l,t,l+cell,t+cell,brush)} }
    };code.contentDescription=getString(R.string.page_qr)+": "+host
    body.addView(code,LinearLayout.LayoutParams(-1,dp(272)));body.gap(12);body.addView(words(host,16,ink,true));body.gap(4);body.addView(words(getString(R.string.qr_hint),14,"#52685D").apply{gravity=Gravity.CENTER})
    // 1.8: copy / share the same address from this sheet (user-initiated; nothing leaves the phone otherwise).
    AlertDialog.Builder(this).setTitle(R.string.page_qr).setView(ScrollView(this).apply{addView(body)}).setPositiveButton(R.string.ui_close,null)
        .setNeutralButton(R.string.link_copy){_,_->
            (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("URL",url))
            Toast.makeText(this,getString(R.string.link_copied),Toast.LENGTH_SHORT).show()}
        .setNegativeButton(R.string.link_share){_,_->
            val send=Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,url)
            startActivity(Intent.createChooser(send,getString(R.string.link_share)))}
        .show()
}
