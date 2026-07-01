package com.github.senocak.analog.service

import com.github.senocak.analog.domain.Tag
import com.github.senocak.analog.repository.TagRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import kotlin.test.assertEquals

@ExtendWith(MockitoExtension::class)
class BlogServiceTest {

    @Mock
    private lateinit var tags: TagRepository

    @InjectMocks
    private lateinit var blog: BlogService

    @Test
    fun `toSlug converts to lowercase and replaces spaces with dashes`() {
        assertEquals("hello-world", blog.toSlug("Hello World"))
    }

    @Test
    fun `toSlug preserves non-ASCII characters`() {
        assertEquals("héllo-wörld", blog.toSlug("Héllo Wörld"))
    }

    @Test
    fun `toSlug removes characters not in allowed set`() {
        assertEquals("test-post", blog.toSlug("Test@ Post"))
    }

    @Test
    fun `toSlug preserves allowed special characters`() {
        // ! is in the allowed regex, ? is not
        assertEquals("test!-post", blog.toSlug("Test! Post?"))
    }

    @Test
    fun `toSlug handles multiple spaces`() {
        // Multiple spaces become multiple dashes
        assertEquals("a--b--c", blog.toSlug("a  b  c"))
    }

    @Test
    fun `totalPages returns 0 when perPage is zero`() {
        assertEquals(0, blog.totalPages(totalItems = 100, itemsPerPage = 0))
    }

    @Test
    fun `totalPages returns 1 when total is less than perPage`() {
        assertEquals(1, blog.totalPages(totalItems = 5, itemsPerPage = 10))
    }

    @Test
    fun `totalPages rounds up partial pages`() {
        assertEquals(4, blog.totalPages(totalItems = 35, itemsPerPage = 10))
    }

    @Test
    fun `totalPages returns exact number when evenly divisible`() {
        assertEquals(3, blog.totalPages(totalItems = 30, itemsPerPage = 10))
    }

    @Test
    fun `createTags returns existing tag ids without creating new ones`() {
        val existingTag = Tag(
            id = "tag-1",
            slug = "kotlin",
            name = "Kotlin",
            createdAt = 1L,
        )
        `when`(tags.findByNames(any())).thenReturn(listOf(existingTag))

        val result = blog.createTags("Kotlin")
        assertEquals(1, result.size)
        assertEquals("tag-1", result[0])
    }

    @Test
    fun `createTags trims and filters empty names`() {
        `when`(tags.findByNames(any())).thenReturn(emptyList())
        `when`(tags.create(any())).then { }

        blog.createTags(" Kotlin ,  ,  Java  ")
        // Should create "Kotlin" and "Java", no empty tag
        verify(tags).findByNames(eq(listOf("Kotlin", "Java")))
    }

    @Test
    fun `createTags returns empty list for blank input`() {
        val result = blog.createTags("  ")
        assertEquals(0, result.size)
    }

    @Test
    fun `createTags removes duplicates`() {
        `when`(tags.findByNames(any())).thenReturn(
            listOf(Tag(id = "t1", slug = "kotlin", name = "Kotlin", createdAt = 1L)),
        )
        val result = blog.createTags("Kotlin, Kotlin, Kotlin")
        assertEquals(1, result.size)
        assertEquals("t1", result[0])
    }
}
