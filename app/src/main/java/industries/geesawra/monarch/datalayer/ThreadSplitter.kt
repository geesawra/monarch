package industries.geesawra.monarch.datalayer

internal fun splitTextForThread(text: String, maxChars: Int): List<String> {
    val trimmed = text.trim()
    if (trimmed.length <= maxChars) return listOf(trimmed)

    val fragments = mutableListOf<String>()
    var cursor = 0
    val n = trimmed.length

    while (cursor < n) {
        while (cursor < n && trimmed[cursor].isWhitespace()) cursor++
        if (cursor >= n) break

        val hardEnd = minOf(cursor + maxChars, n)
        if (hardEnd == n) {
            fragments.add(trimmed.substring(cursor, n).trimEnd())
            break
        }

        var splitAt = -1
        for (i in (hardEnd - 1) downTo cursor) {
            if (trimmed[i].isWhitespace()) { splitAt = i; break }
        }

        if (splitAt <= cursor) {
            splitAt = hardEnd
        }

        fragments.add(trimmed.substring(cursor, splitAt).trimEnd())
        cursor = splitAt
    }

    return fragments
}
