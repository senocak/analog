package com.github.senocak.analog.domain

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class PostTest {

    private fun post(
        content: String = "Hello world",
        originalExcerpt: String = "",
    ): Post = Post(
        id = "post-1",
        title = "Test Post",
        slug = "test-post",
        originalExcerpt = originalExcerpt,
        authorId = "user-1",
        password = "",
        visibility = Visibility.PUBLIC,
        content = content,
        pinnedAt = 0,
        publishedAt = 1_700_000_000L,
        createdAt = 1_700_000_000L,
        updatedAt = 1_700_000_000L,
        trashedAt = 0,
        author = testUser(),
        tags = emptyList(),
    )

    private fun testUser(): User = User(
        id = "user-1",
        email = "test@example.com",
        nickname = "TestUser",
        password = "hashed",
        bio = "",
        createdAt = 1_700_000_000L,
    )

    @Test
    fun `tagsStr returns empty string when no tags`() {
        assertEquals("", post().tagsStr())
    }

    @Test
    fun `tagsStr joins multiple tag names with comma`() {
        val p = post().copy(
            tags = listOf(
                Tag(id = "t1", slug = "kotlin", name = "Kotlin", createdAt = 1L),
                Tag(id = "t2", slug = "java", name = "Java", createdAt = 1L),
            ),
        )
        assertEquals("Kotlin,Java", p.tagsStr())
    }

    @Test
    fun `publishedYear returns UTC year from epoch`() {
        assertEquals("2023", post().publishedYear())
    }

    @Test
    fun `publishedMonth returns zero-padded month`() {
        // 2023-11-14
        assertEquals("11", post().publishedMonth())
    }

    @Test
    fun `publishedDay returns zero-padded day`() {
        assertEquals("14", post().publishedDay())
    }

    @Test
    fun `excerpt returns original excerpt if not blank`() {
        val p = post(originalExcerpt = "Custom excerpt text")
        assertEquals("Custom excerpt text", p.excerpt())
    }

    @Test
    fun `excerpt strips markdown and limits to 200 chars`() {
        val longContent = "# Title\n\nThis is **bold** and _italic_. " +
            "`code` here. " +
            "```kotlin\nval x = 1\n```\n" +
            "[link](http://example.com). " +
            "Some more text to reach 200 characters. " +
            "Lorem ipsum dolor sit amet. Consectetur adipiscing elit. " +
            "Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.".repeat(3)
        val p = post(content = longContent)
        val excerpt = p.excerpt()
        assertFalse(excerpt.contains("#"))
        assertFalse(excerpt.contains("**"))
        assertFalse(excerpt.contains("```"))
        assertTrue(excerpt.contains("This is"))
        assertEquals(3, excerpt.takeLast(3).length) // "..."
    }

    @Test
    fun `cover returns empty string when no cover file exists`() {
        assertEquals("", post().cover())
    }

    @Test
    fun `isPublished returns true when current time is after publishedAt`() {
        assertTrue(post().copy(publishedAt = 1L).isPublished())
    }

    @Test
    fun `isPublished returns false when current time is before publishedAt`() {
        assertFalse(post().copy(publishedAt = Long.MAX_VALUE).isPublished())
    }
}

class UserTest {

    @Test
    fun `gravatar returns md5 hash based email url`() {
        val user = User(
            id = "u1",
            email = "test@example.com",
            nickname = "test",
            password = "",
            bio = "",
            createdAt = 1L,
        )
        val gravatar = user.gravatar()
        assertTrue(gravatar.startsWith("http://www.gravatar.com/avatar/"))
        assertEquals(63, gravatar.length) // base URL + 32 hex chars
    }
}

class AnalogConfigTest {

    @Test
    fun `default config has expected values`() {
        val cfg = AnalogConfig()
        assertEquals("", cfg.name)
        assertEquals("", cfg.description)
        assertEquals(true, cfg.isPublic)
        assertEquals("medium", cfg.containerWidth)
        assertEquals(FontFamily.SANS, cfg.fontFamily)
        assertEquals(10, cfg.postsPerPage)
        assertEquals("default", cfg.theme)
    }

    @Test
    fun `isCustomDateFormat returns false for known format`() {
        val cfg = AnalogConfig(dateFormat = "yyyy-MM-dd")
        assertFalse(cfg.isCustomDateFormat())
    }

    @Test
    fun `isCustomDateFormat returns true for custom format`() {
        val cfg = AnalogConfig(dateFormat = "dd-MM-yyyy")
        assertTrue(cfg.isCustomDateFormat())
    }
}

class PaginationTest {

    @Test
    fun `pagination holds all values`() {
        val p = Pagination(currentPage = 2, totalCount = 50, totalPages = 5, query = "type=admin&")
        assertEquals(2, p.currentPage)
        assertEquals(50, p.totalCount)
        assertEquals(5, p.totalPages)
        assertEquals("type=admin&", p.query)
    }
}

class IndexFilterTest {

    @Test
    fun `isEmpty returns true for defaults`() {
        assertTrue(IndexFilter().isEmpty())
    }

    @Test
    fun `isEmpty returns false when tag is set`() {
        assertFalse(IndexFilter(tag = "kotlin").isEmpty())
    }

    @Test
    fun `isEmpty returns false when author is set`() {
        assertFalse(IndexFilter(author = "john").isEmpty())
    }

    @Test
    fun `isEmpty returns false when date is set`() {
        assertFalse(IndexFilter(date = "2023-11").isEmpty())
    }
}
