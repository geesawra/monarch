package industries.geesawra.monarch.datalayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreadSplitterTest {

    @Test
    fun shortTextReturnsSingleFragment() {
        val result = splitTextForThread("Hello world", 300)
        assertEquals(1, result.size)
        assertEquals("Hello world", result[0])
    }

    @Test
    fun exactLimitReturnsSingleFragment() {
        val text = "a".repeat(300)
        val result = splitTextForThread(text, 300)
        assertEquals(1, result.size)
        assertEquals(text, result[0])
    }

    @Test
    fun splitAtWhitespaceBoundary() {
        val word1 = "a".repeat(200)
        val word2 = "b".repeat(200)
        val text = "$word1 $word2"
        val result = splitTextForThread(text, 300)
        assertEquals(2, result.size)
        assertEquals(word1, result[0])
        assertEquals(word2, result[1])
        result.forEach { assertTrue(it.length <= 300) }
    }

    @Test
    fun threeWaySplit() {
        val word1 = "a".repeat(250)
        val word2 = "b".repeat(250)
        val word3 = "c".repeat(250)
        val text = "$word1 $word2 $word3"
        val result = splitTextForThread(text, 300)
        assertEquals(3, result.size)
        result.forEach { assertTrue(it.length <= 300) }
        val joined = result.joinToString(" ")
        assertEquals(text, joined)
    }

    @Test
    fun pathologicalLongTokenHardWraps() {
        val token = "x".repeat(500)
        val result = splitTextForThread(token, 300)
        assertTrue(result.size >= 2)
        result.forEach { assertTrue(it.length <= 300) }
    }

    @Test
    fun leadingAndTrailingWhitespaceTrimmed() {
        val result = splitTextForThread("  hello world  ", 300)
        assertEquals(1, result.size)
        assertEquals("hello world", result[0])
    }

    @Test
    fun noLeadingWhitespaceOnFragments() {
        val word1 = "a".repeat(200)
        val word2 = "b".repeat(200)
        val text = "$word1 $word2"
        val result = splitTextForThread(text, 300)
        result.forEach { frag ->
            assertTrue(!frag.startsWith(" ") && !frag.endsWith(" "))
        }
    }

    @Test
    fun emojiAtLimitSplitsCorrectly() {
        val emoji = "\uD83D\uDE00"
        val base = "a".repeat(299)
        val text = "$base $emoji extra"
        val result = splitTextForThread(text, 300)
        result.forEach { assertTrue(it.length <= 300) }
        assertTrue(result.size >= 2)
    }
}
