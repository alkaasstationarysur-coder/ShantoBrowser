package bd.shanto.browser

/** Local hostname matching only. Never inspect query text, page content or credentials. */
internal class AdDomains(lines: Sequence<String>) {
    private val rules = lines.filter { it.isNotEmpty() && !it.startsWith('#') }.toHashSet()
    fun block(host: String, page: String?, mainFrame: Boolean, enabled: Boolean, exceptions: Set<String>): Boolean {
        if (!enabled || mainFrame || page == null || page in exceptions || host == page) return false
        var name = host
        while ('.' in name) {
            if (name in rules) return true
            name = name.substringAfter('.')
        }
        return false
    }
}
