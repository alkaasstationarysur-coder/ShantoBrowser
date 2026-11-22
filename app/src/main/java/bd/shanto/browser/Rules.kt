package bd.shanto.browser

import java.net.URI
import java.net.URLEncoder
import java.util.Locale

object Rules {
    private val label = Regex("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?")
    fun domain(input: String): String? {
        val raw = input.trim().removeSuffix(".")
        if (raw.any { it.code > 127 }) return null
        val h = raw.lowercase(Locale.ROOT)
        val parts = h.split('.')
        return h.takeIf { h.length <= 253 && parts.size >= 2 &&
            parts.all { label.matches(it) && !it.startsWith("xn--") } &&
            parts.last().matches(Regex("[a-z]{2,63}")) }
    }
    fun host(url: String): String? = runCatching {
        val u = URI(url)
        require(u.scheme.equals("https", true) && u.rawUserInfo == null)
        val h = requireNotNull(u.host)
        require(u.rawAuthority.equals(h, true) || u.rawAuthority.equals("$h:443", true))
        domain(h)
    }.getOrNull()
    fun blocked(h: String, list: Set<String>) = list.any { h == it || h.endsWith(".$it") }

    fun address(text: String, google: Boolean): String? {
        val s = text.trim()
        if (s.isEmpty() || s.any { it.isISOControl() }) return null
        val scheme = Regex("^[A-Za-z][A-Za-z0-9+.-]*:").containsMatchIn(s)
        val urlLike = scheme || (!s.any { it.isWhitespace() } && s.any { it in "./:@\\" })
        if (urlLike) {
            val url = if (scheme) s else "https://$s"
            return url.takeIf { host(it) != null }
        }
        val q = URLEncoder.encode(s, "UTF-8")
        return if (google) "https://www.google.com/search?safe=active&q=$q"
               else "https://html.duckduckgo.com/html/?kp=1&q=$q"
    }
}
