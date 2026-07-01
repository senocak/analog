package com.github.senocak.analog.service

import com.github.senocak.analog.domain.Visibility
import java.net.URLEncoder
import kotlin.test.Test
import kotlin.test.assertEquals

class TemplateUtilsTest {

    private val utils = TemplateUtils()

    @Test
    fun `date formats epoch to ISO local date`() {
        // 2023-11-14T00:00:00Z
        assertEquals("2023-11-14", utils.date(1_700_000_000L))
    }

    @Test
    fun `date handles epoch zero`() {
        assertEquals("1970-01-01", utils.date(0))
    }

    @Test
    fun `u URL-encodes string`() {
        assertEquals("hello%20world", utils.u("hello world"))
    }

    @Test
    fun `u handles special characters`() {
        assertEquals("test%26me", utils.u("test&me"))
        assertEquals("a%2Fb", utils.u("a/b"))
    }

    @Test
    fun `u replaces plus with percent20`() {
        // URLEncoder encodes space as + which Thymeleaf's u() replaces with %20
        val encoded = URLEncoder.encode("hello world", Charsets.UTF_8)
        assertEquals("hello+world", encoded)
        assertEquals("hello%20world", utils.u("hello world"))
    }

    @Test
    fun `visibilityPrefix returns correct prefix`() {
        assertEquals("[Private] ", utils.visibilityPrefix(Visibility.PRIVATE))
        assertEquals("[Password] ", utils.visibilityPrefix(Visibility.PASSWORD))
        assertEquals("[Draft] ", utils.visibilityPrefix(Visibility.DRAFT))
        assertEquals("", utils.visibilityPrefix(Visibility.PUBLIC))
        assertEquals("", utils.visibilityPrefix(Visibility.TRASH))
    }

    @Test
    fun `markdown escapes HTML and converts newlines`() {
        val result = utils.markdown("Hello\n\nWorld")
        assertEquals("<p>Hello</p><p>World</p>", result)
    }

    @Test
    fun `markdown escapes HTML entities`() {
        val result = utils.markdown("5 < 10 & 20 > 15")
        assertEquals("<p>5 &lt; 10 &amp; 20 &gt; 15</p>", result)
    }

    @Test
    fun `markdown converts single newlines to br`() {
        val result = utils.markdown("Line 1\nLine 2")
        assertEquals("<p>Line 1<br>Line 2</p>", result)
    }

    @Test
    fun `markdown escapes quotes`() {
        val result = utils.markdown("She said \"hello\"")
        assertEquals("<p>She said &quot;hello&quot;</p>", result)
    }
}
