package li.songe.selector

import kotlin.js.JsExport


data class MatchOption(
    val fastQuery: Boolean = false,
) {
    companion object {
        val default = MatchOption()
    }
}
