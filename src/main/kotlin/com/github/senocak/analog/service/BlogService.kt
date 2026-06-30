package com.github.senocak.analog.service

import com.github.senocak.analog.domain.Tag
import com.github.senocak.analog.repository.TagRepository
import org.springframework.stereotype.Service
import java.text.Normalizer
import java.time.Instant
import java.util.UUID

@Service
class BlogService(private val tags: TagRepository) {
    private val invalidSlugChars = Regex("""[^A-Za-z0-9\-._~!$&'()*+,;=\p{L}\p{N}]""")

    fun toSlug(value: String): String {
        val lower = Normalizer.normalize(value.lowercase().replace(" ", "-"), Normalizer.Form.NFC)
        return invalidSlugChars.replace(lower, "")
    }

    fun createTags(tagNames: String): List<String> {
        val names = tagNames
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        if (names.isEmpty()) return emptyList()

        val existing = tags.findByNames(names)
        return names.map { name ->
            existing.firstOrNull { it.name == name }?.id ?: run {
                val tag = Tag(
                    id = UUID.randomUUID().toString(),
                    slug = toSlug(name),
                    name = name,
                    description = "",
                    createdAt = Instant.now().epochSecond,
                )
                tags.create(tag)
                tag.id
            }
        }
    }

    fun totalPages(totalItems: Int, itemsPerPage: Int): Int =
        if (itemsPerPage <= 0) 0 else kotlin.math.ceil(totalItems.toDouble() / itemsPerPage.toDouble()).toInt()
}
