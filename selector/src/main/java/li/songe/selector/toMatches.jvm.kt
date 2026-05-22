package li.songe.selector

fun String.toMatches(): (input: CharSequence) -> Boolean {
    val regex = Regex(this)
    return { input -> regex.matches(input) }
}

@Suppress("unused")
fun setWasmToMatches(wasmToMatches: (String) -> (String) -> Boolean) {
}