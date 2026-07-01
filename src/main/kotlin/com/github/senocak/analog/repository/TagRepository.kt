package com.github.senocak.analog.repository

import com.github.senocak.analog.domain.Tag
import java.sql.ResultSet
import java.sql.SQLException
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository

@Repository
class TagRepository(private val jdbc: JdbcTemplate) {
    private val mapper = RowMapper { rs, _ ->
        Tag(
            id = rs.getString("id"),
            slug = rs.getString("slug"),
            name = rs.getString("name"),
            description = rs.getString("description"),
            createdAt = rs.getLong("created_at"),
            postCount = safeInt(rs, "post_count"),
        )
    }

    fun list(offset: Int, limit: Int, keyword: String): List<Tag> {
        val sql: String =
            if (keyword.isNotBlank()) {
                """
                SELECT t.id, t.slug, t.name, t.description, t.created_at, COUNT(pt.post_id) AS post_count
                FROM tags t
                LEFT JOIN post_tags pt ON t.id = pt.tag_id
                WHERE t.name LIKE ?
                GROUP BY t.id, t.slug, t.name, t.description, t.created_at
                ORDER BY t.created_at DESC
                LIMIT ? OFFSET ?
                """.trimIndent()
            } else {
                """
                SELECT t.id, t.slug, t.name, t.description, t.created_at, COUNT(pt.post_id) AS post_count
                FROM tags t
                LEFT JOIN post_tags pt ON t.id = pt.tag_id
                GROUP BY t.id, t.slug, t.name, t.description, t.created_at
                ORDER BY t.created_at DESC
                LIMIT ? OFFSET ?
                """.trimIndent()
            }
        return if (keyword.isNotBlank()) {
            jdbc.query(sql, mapper, "%$keyword%", limit, offset)
        } else {
            jdbc.query(sql, mapper, limit, offset)
        }
    }

    fun count(keyword: String): Int =
        if (keyword.isNotBlank()) {
            jdbc.queryForObject("SELECT COUNT(*) FROM tags WHERE name LIKE ?", Int::class.java, "%$keyword%") ?: 0
        } else {
            jdbc.queryForObject("SELECT COUNT(*) FROM tags", Int::class.java) ?: 0
        }

    fun create(tag: Tag) {
        jdbc.update(
            "INSERT INTO tags (id, slug, name, description, created_at) VALUES (?, ?, ?, ?, ?)",
            tag.id,
            tag.slug,
            tag.name,
            tag.description,
            tag.createdAt,
        )
    }

    fun findById(id: String): Tag? =
        queryOne(
            sql = """
            SELECT t.id, t.slug, t.name, t.description, t.created_at, COUNT(pt.post_id) AS post_count
            FROM tags t
            LEFT JOIN post_tags pt ON t.id = pt.tag_id
            WHERE t.id = ?
            GROUP BY t.id, t.slug, t.name, t.description, t.created_at
            """.trimIndent(),
            id,
        )

    fun findBySlug(slug: String): Tag? =
        queryOne(sql = "SELECT id, slug, name, description, created_at, 0 AS post_count FROM tags WHERE slug = ?", slug)

    fun findByNames(names: List<String>): List<Tag> {
        if (names.isEmpty())
            return emptyList()
        val placeholders: String = names.joinToString(",") { "?" }
        return jdbc.query(
            "SELECT id, slug, name, description, created_at, 0 AS post_count FROM tags WHERE name IN ($placeholders)",
            mapper,
            *names.toTypedArray(),
        )
    }

    fun listMostUsed(): List<Tag> =
        jdbc.query(
            """
            SELECT t.id, t.slug, t.name, t.description, t.created_at, COUNT(pt.post_id) AS post_count
            FROM tags t
            JOIN post_tags pt ON t.id = pt.tag_id
            GROUP BY t.id, t.slug, t.name, t.description, t.created_at
            ORDER BY post_count DESC
            LIMIT 10
            """.trimIndent(),
            mapper,
        )

    fun listByPost(postId: String): List<Tag> =
        jdbc.query(
            """
            SELECT t.id, t.slug, t.name, t.description, t.created_at, 0 AS post_count
            FROM tags t
            JOIN post_tags pt ON t.id = pt.tag_id
            WHERE pt.post_id = ?
            """.trimIndent(),
            mapper,
            postId,
        )

    fun update(tag: Tag) {
        jdbc.update(
            "UPDATE tags SET slug = ?, name = ?, description = ?, created_at = ? WHERE id = ?",
            tag.slug,
            tag.name,
            tag.description,
            tag.createdAt,
            tag.id,
        )
    }

    fun delete(id: String) {
        jdbc.update("DELETE FROM post_tags WHERE tag_id = ?", id)
        jdbc.update("DELETE FROM tags WHERE id = ?", id)
    }

    private fun queryOne(sql: String, vararg args: Any): Tag? =
        try {
            jdbc.queryForObject(sql, mapper, *args)
        } catch (_: EmptyResultDataAccessException) {
            null
        }

    private fun safeInt(rs: ResultSet, column: String): Int =
        try {
            rs.getInt(column)
        } catch (_: SQLException) {
            0
        }
}
