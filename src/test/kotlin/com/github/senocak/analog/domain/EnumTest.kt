package com.github.senocak.analog.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class VisibilityTest {

    @Test
    fun `from returns correct enum for known string values`() {
        assertEquals(Visibility.PUBLIC, Visibility.from("public"))
        assertEquals(Visibility.PRIVATE, Visibility.from("private"))
        assertEquals(Visibility.PASSWORD, Visibility.from("password"))
        assertEquals(Visibility.DRAFT, Visibility.from("draft"))
        assertEquals(Visibility.TRASH, Visibility.from("trash"))
    }

    @Test
    fun `from returns UNKNOWN for null value`() {
        assertEquals(Visibility.UNKNOWN, Visibility.from(null))
    }

    @Test
    fun `from returns UNKNOWN for unknown value`() {
        assertEquals(Visibility.UNKNOWN, Visibility.from("unknown"))
        assertEquals(Visibility.UNKNOWN, Visibility.from("xxx"))
    }

    @Test
    fun `enum values have correct string values`() {
        assertEquals("public", Visibility.PUBLIC.value)
        assertEquals("private", Visibility.PRIVATE.value)
        assertEquals("password", Visibility.PASSWORD.value)
        assertEquals("draft", Visibility.DRAFT.value)
        assertEquals("trash", Visibility.TRASH.value)
        assertEquals("", Visibility.UNKNOWN.value)
    }
}

class ColorSchemeTest {

    @Test
    fun `from returns correct enum for known string values`() {
        assertEquals(ColorScheme.LIGHT, ColorScheme.from("light"))
        assertEquals(ColorScheme.DARK, ColorScheme.from("dark"))
        assertEquals(ColorScheme.AUTO, ColorScheme.from(""))
    }

    @Test
    fun `from returns AUTO for null value`() {
        assertEquals(ColorScheme.AUTO, ColorScheme.from(null))
    }

    @Test
    fun `from returns AUTO for unknown value`() {
        assertEquals(ColorScheme.AUTO, ColorScheme.from("unknown"))
    }
}

class FontFamilyTest {

    @Test
    fun `from returns correct enum for known string values`() {
        assertEquals(FontFamily.SANS, FontFamily.from("sans"))
        assertEquals(FontFamily.SERIF, FontFamily.from("serif"))
    }

    @Test
    fun `from returns SANS for null value`() {
        assertEquals(FontFamily.SANS, FontFamily.from(null))
    }

    @Test
    fun `from returns SANS for unknown value`() {
        assertEquals(FontFamily.SANS, FontFamily.from("unknown"))
    }
}

class AuthorBlockTest {

    @Test
    fun `from returns correct enum for known string values`() {
        assertEquals(AuthorBlock.NONE, AuthorBlock.from("none"))
        assertEquals(AuthorBlock.START, AuthorBlock.from("start"))
        assertEquals(AuthorBlock.END, AuthorBlock.from("end"))
    }

    @Test
    fun `from returns START for null value`() {
        assertEquals(AuthorBlock.START, AuthorBlock.from(null))
    }

    @Test
    fun `from returns START for unknown value`() {
        assertEquals(AuthorBlock.START, AuthorBlock.from("unknown"))
    }
}
