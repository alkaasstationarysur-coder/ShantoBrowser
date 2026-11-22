package bd.shanto.browser

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.*
import kotlin.math.roundToInt

internal fun Context.dp(n: Int) = (n * resources.displayMetrics.density).roundToInt()
internal var darkPalette=false
private val nightColors=mapOf("#F6F5EF" to "#181C1B","#173E36" to "#EDF4EE","#23634E" to "#9FD9B9","#FFFFFF" to "#252B29","#52685D" to "#B4C2B9","#DCE3D9" to "#414C46","#E5F1E7" to "#30483D","#8E342C" to "#F5B4A8","#F7E9E5" to "#4A302D",
    // 1.8: status banner (amber) and empty-state icon now follow the dark palette instead of staying bright.
    "#FFF0CE" to "#3A2F17","#593D16" to "#F6DFA6","#799E88" to "#8DB89F",
    // 1.9: border of inputs and segmented controls; 3:1 or better against every surface it sits on, in both palettes.
    "#7A8A81" to "#728579")
internal fun color(hex:String)=Color.parseColor(if(darkPalette) nightColors[hex] ?: hex else hex)
internal const val ink = "#173E36"
internal const val leaf = "#23634E"
internal const val paper = "#F6F5EF"
internal const val ctrl = "#7A8A81"
internal fun Context.shape(fill: String, radius: Int, stroke: String? = null) = GradientDrawable().apply {
    setColor(color(fill)); cornerRadius = dp(radius).toFloat(); if (stroke != null) setStroke(dp(1), color(stroke))
}
/**
 * 1.9: a visible 2dp ring for keyboard / D-pad / Switch Access focus (the soft press ripple alone is only ~1.2:1).
 * [state] is android.R.attr.state_focused for ordinary views, state_selected for ListView rows.
 */
internal fun Context.focusRing(radius: Int = 14, state: Int = android.R.attr.state_focused): Drawable {
    val ring = GradientDrawable().apply { setColor(Color.TRANSPARENT); cornerRadius = dp(radius).toFloat(); setStroke(dp(2), color(leaf)) }
    return StateListDrawable().apply {
        addState(intArrayOf(state), ring)
        addState(intArrayOf(), GradientDrawable().apply { setColor(Color.TRANSPARENT) })
    }
}
internal fun View.tap(desc: String, action: () -> Unit) {
    contentDescription = desc; isFocusable = true
    foreground = LayerDrawable(arrayOf(RippleDrawable(ColorStateList.valueOf(0x282E725C), null, context.shape("#FFFFFF", 14)), context.focusRing()))
    setOnClickListener { action() }
    // 1.9: custom clickable views had no role, so TalkBack said only "double-tap to activate". Announce them as buttons.
    setAccessibilityDelegate(object : View.AccessibilityDelegate() {
        override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
            super.onInitializeAccessibilityNodeInfo(host, info); info.className = Button::class.java.name
        }
    })
}
/** Marks a title as a heading for TalkBack's heading navigation (API 28+; older versions simply ignore it). */
internal fun <T : View> T.heading(): T { if (Build.VERSION.SDK_INT >= 28) isAccessibilityHeading = true; return this }
/**
 * Bengali conjuncts and matras need more room than Latin text: in the Bengali UI, text is never smaller than 14sp
 * and gets slightly looser line spacing. Pass bnFit=false for Latin-only labels (URLs, the brand eyebrow, digit badges).
 */
internal fun Context.words(s: String, size: Int, fg: String = ink, bold: Boolean = false, bnFit: Boolean = true) = TextView(this).apply {
    val bn = bnFit && resources.configuration.locales[0].language == "bn"
    text = s; textSize = (if (bn) maxOf(size, 14) else size).toFloat(); setTextColor(color(fg)); if (bold) setTypeface(typeface, Typeface.BOLD)
    if (bn) setLineSpacing(0f, 1.15f)
}
internal fun Context.stack() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
internal fun View.pad(x: Int, y: Int) = setPadding(context.dp(x), context.dp(y), context.dp(x), context.dp(y))
internal fun LinearLayout.gap(n: Int) = addView(Space(context), LinearLayout.LayoutParams(context.dp(n), context.dp(n)))

/** In-app brand mark (1.8): same calm green palette as the UI, echoing the rings of the quick-menu etching. Drawn locally. */
internal class BrandMark(ctx: Context) : View(ctx) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = color(leaf) }
    private val pen = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = ctx.dp(1) * 1.2f; color = color(if (darkPalette) "#233720" else "#C7EC8A") }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = color(if (darkPalette) "#233720" else "#C7EC8A") }
    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat(); val corner = w * .3f
        val clip = Path().apply { addRoundRect(0f, 0f, w, h, corner, corner, Path.Direction.CW) }
        canvas.save(); canvas.clipPath(clip); canvas.drawPath(clip, fill)
        canvas.drawCircle(w * .5f, h * .5f, w * .075f, dot)
        canvas.drawCircle(w * .5f, h * .5f, w * .2f, pen)
        canvas.drawCircle(w * .5f, h * .5f, w * .33f, pen)
        canvas.restore()
    }
}

internal class Glyph(c: Context, private val idName:String) : View(c) {
    private val p=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=color(ink); style=Paint.Style.STROKE; strokeWidth=1.8f; strokeCap=Paint.Cap.ROUND; strokeJoin=Paint.Join.ROUND }
    fun tint(hex:String) { p.color=color(hex); invalidate() }
    override fun onDraw(c:Canvas) {
        c.save(); c.scale(width/24f,height/24f)
        fun line(vararg a:Int) { val path=Path(); path.moveTo(a[0].toFloat(),a[1].toFloat()); for(i in 2 until a.size step 2) path.lineTo(a[i].toFloat(),a[i+1].toFloat()); c.drawPath(path,p) }
        when(idName) {
            "back" -> {line(10,5,3,12,10,19);line(3,12,21,12)}
            "next" -> {line(14,5,21,12,14,19);line(3,12,21,12)}
            "reload" -> { c.drawArc(4f,4f,20f,20f,0f,299f,false,p); line(14,7,19,7,19,2) }
            "home" -> { line(2,11,12,3,22,11); line(5,10,5,21,10,21,10,15,14,15,14,21,19,21,19,10) }
            "tune" -> { line(3,7,10,7); line(15,7,21,7); c.drawCircle(12.5f,7f,2.5f,p); line(3,17,7,17); line(12,17,21,17); c.drawCircle(9.5f,17f,2.5f,p) }
            "newtab" -> { line(3,12,21,12); line(12,3,12,21) }
            "minus" -> line(3,12,21,12)
            "check" -> line(3,12,9,18,21,5)
            "cross" -> {line(5,5,19,19);line(5,19,19,5)}
            "qr" -> {c.drawRect(3f,3f,9f,9f,p);c.drawRect(15f,3f,21f,9f,p);c.drawRect(3f,15f,9f,21f,p);line(15,15,18,15,18,18,21,18);line(15,20,15,21);line(21,14,21,15)}
            "trash" -> { line(3,6,21,6);line(9,6,9,3,15,3,15,6);line(6,7,7,21,17,21,18,7);line(10,10,10,17);line(14,10,14,17) }
            "mark" -> line(6,3,18,3,18,21,12,17,6,21,6,3)
            "menu" -> { line(4,6,20,6); line(4,12,20,12); line(4,18,20,18) }
            "more" -> { val keep=p.style; p.style=Paint.Style.FILL; c.drawCircle(12f,5f,1.8f,p); c.drawCircle(12f,12f,1.8f,p); c.drawCircle(12f,19f,1.8f,p); p.style=keep }
            else -> { c.drawCircle(10f,10f,6f,p); line(15,15,21,21) }
        }; c.restore()
    }
}
