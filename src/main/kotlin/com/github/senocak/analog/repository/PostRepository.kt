package com.github.senocak.analog.repository

import com.github.senocak.analog.domain.ListPostsQuery
import com.github.senocak.analog.domain.Post
import com.github.senocak.analog.domain.PostCount
import com.github.senocak.analog.domain.PostWrite
import com.github.senocak.analog.domain.User
import com.github.senocak.analog.domain.Visibility
import com.github.senocak.analog.service.ConfigService
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet
import java.time.Instant

@Repository
class PostRepository(
    private val jdbc: JdbcTemplate,
    private val tags: TagRepository,
    private val config: ConfigService,
) {
    fun countByUser(userId: String): Int =
        jdbc.queryForObject("SELECT COUNT(*) FROM posts WHERE author_id = ?", Int::class.java, userId) ?: 0

    fun transferPosts(fromUserId: String, toUserId: String) {
        jdbc.update("UPDATE posts SET author_id = ? WHERE author_id = ?", toUserId, fromUserId)
    }

    @Transactional
    fun deleteByUser(userId: String) {
        jdbc.update("DELETE FROM post_tags WHERE post_id IN (SELECT id FROM posts WHERE author_id = ?)", userId)
        jdbc.update("DELETE FROM posts WHERE author_id = ?", userId)
    }

    @Transactional
    fun create(post: PostWrite) {
        jdbc.update(
            """
            INSERT INTO posts
            (id, title, slug, excerpt, author_id, password, visibility, content, published_at, created_at, updated_at, pinned_at, trashed_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            post.id,
            post.title,
            post.slug,
            post.excerpt,
            post.authorId,
            post.password,
            post.visibility.value,
            post.content,
            post.publishedAt,
            post.createdAt,
            post.updatedAt,
            post.pinnedAt,
            post.trashedAt,
        )
        replaceTags(postId = post.id, tagIds = post.tagIds)
    }

    fun trash(id: String) {
        jdbc.update("UPDATE posts SET trashed_at = ? WHERE id = ?", Instant.now().epochSecond, id)
    }

    fun untrash(id: String) {
        jdbc.update("UPDATE posts SET trashed_at = ? WHERE id = ?", 0, id)
    }

    @Transactional
    fun delete(id: String) {
        jdbc.update("DELETE FROM post_tags WHERE post_id = ?", id)
        jdbc.update("DELETE FROM posts WHERE id = ? AND trashed_at != ?", id, 0)
    }

    @Transactional
    fun clearTrashPosts() {
        jdbc.update("DELETE FROM post_tags WHERE post_id IN (SELECT id FROM posts WHERE trashed_at != ?)", 0)
        jdbc.update("DELETE FROM posts WHERE trashed_at != ?", 0)
    }

    @Transactional
    fun clearExpiredTrashPosts() {
        val threshold: Long = Instant.now().epochSecond - 86_400L * 30
        jdbc.update(
            "DELETE FROM post_tags WHERE post_id IN (SELECT id FROM posts WHERE trashed_at != ? AND trashed_at < ?)",
            0,
            threshold,
        )
        jdbc.update("DELETE FROM posts WHERE trashed_at != ? AND trashed_at < ?", 0, threshold)
    }

    @Transactional(readOnly = true)
    fun previous(id: String): Post? =
        queryOne(
            sql = """
            SELECT ${selectColumns()}
            FROM posts p
            JOIN users u ON p.author_id = u.id
            WHERE p.published_at < ?
              AND (p.published_at < (SELECT published_at FROM posts WHERE id = ?)
                   OR (p.published_at = (SELECT published_at FROM posts WHERE id = ?)
                       AND p.created_at < (SELECT created_at FROM posts WHERE id = ?)))
              AND (p.visibility = ? OR p.visibility = ?)
              AND p.trashed_at = 0
            ORDER BY p.published_at DESC, p.created_at DESC
            LIMIT 1
            """.trimIndent(),
            Instant.now().epochSecond,
            id,
            id,
            id,
            Visibility.PUBLIC.value,
            Visibility.PASSWORD.value,
        )

    @Transactional(readOnly = true)
    fun next(id: String): Post? =
        queryOne(
            """
            SELECT ${selectColumns()}
            FROM posts p
            JOIN users u ON p.author_id = u.id
            WHERE p.published_at < ?
              AND (p.published_at > (SELECT published_at FROM posts WHERE id = ?)
                   OR (p.published_at = (SELECT published_at FROM posts WHERE id = ?)
                       AND p.created_at > (SELECT created_at FROM posts WHERE id = ?)))
              AND (p.visibility = ? OR p.visibility = ?)
              AND p.trashed_at = 0
            ORDER BY p.published_at ASC, p.created_at ASC
            LIMIT 1
            """.trimIndent(),
            Instant.now().epochSecond,
            id,
            id,
            id,
            Visibility.PUBLIC.value,
            Visibility.PASSWORD.value,
        )

    @Transactional(readOnly = true)
    fun count(query: ListPostsQuery): Int {
        val (whereSql: String, args: MutableList<Any>) = build(query = query)
        return jdbc.queryForObject(
            "SELECT COUNT(*) FROM posts p JOIN users u ON p.author_id = u.id $whereSql",
            Int::class.java,
            *args.toTypedArray(),
        ) ?: 0
    }

    @Transactional(readOnly = true)
    fun countByType(): PostCount {
        val counts = PostCount()
        jdbc.query("SELECT visibility, COUNT(*) AS count FROM posts WHERE trashed_at = 0 GROUP BY visibility") { rs ->
            when (rs.getString("visibility")) {
                Visibility.PUBLIC.value -> counts.public = rs.getInt("count")
                Visibility.PRIVATE.value -> counts.private = rs.getInt("count")
                Visibility.PASSWORD.value -> counts.password = rs.getInt("count")
                Visibility.DRAFT.value -> counts.draft = rs.getInt("count")
            }
        }
        counts.trash = jdbc.queryForObject("SELECT COUNT(*) FROM posts WHERE trashed_at != 0", Int::class.java) ?: 0
        counts.all = counts.public + counts.private + counts.password + counts.draft + counts.trash
        counts.nonTrash = counts.all - counts.trash
        return counts
    }

    @Transactional(readOnly = true)
    fun listPostDates(): List<String> =
        jdbc.queryForList(
            "SELECT strftime('%Y-%m', datetime(published_at, 'unixepoch')) FROM posts GROUP BY strftime('%Y-%m', datetime(published_at, 'unixepoch'))",
            String::class.java,
        )

    @Transactional(readOnly = true)
    fun list(query: ListPostsQuery): List<Post> {
        val (whereSql: String, args: MutableList<Any>) = build(query = query)
        val pagingArgs: MutableList<Any> = args.toMutableList()
        val sql: StringBuilder = StringBuilder("SELECT ${selectColumns()} FROM posts p JOIN users u ON p.author_id = u.id ")
            .append(whereSql)
            .append(" ORDER BY p.pinned_at DESC, p.published_at DESC, p.created_at DESC")
        if (query.limit > 0 && query.offset >= 0) {
            sql.append(" LIMIT ? OFFSET ?")
            pagingArgs += query.limit
            pagingArgs += query.offset
        } else if (query.limit > 0) {
            sql.append(" LIMIT ?")
            pagingArgs += query.limit
        }
        return jdbc.query(sql.toString(), { rs: ResultSet, _: Int -> mapPost(rs = rs) }, *pagingArgs.toTypedArray())
    }

    @Transactional(readOnly = true)
    fun findById(id: String): Post? =
        queryOne(
            sql = "SELECT ${selectColumns()} FROM posts p JOIN users u ON p.author_id = u.id WHERE p.id = ? AND p.trashed_at = 0",
            id,
        )

    @Transactional(readOnly = true)
    fun findBySlug(slug: String): Post? =
        queryOne(
            sql = "SELECT ${selectColumns()} FROM posts p JOIN users u ON p.author_id = u.id WHERE p.slug = ? AND p.trashed_at = 0",
            slug,
        )

    @Transactional
    fun update(post: PostWrite) {
        jdbc.update(
            """
            UPDATE posts
            SET title = ?, slug = ?, excerpt = ?, author_id = ?, password = ?, visibility = ?, content = ?,
                published_at = ?, created_at = ?, updated_at = ?, pinned_at = ?
            WHERE id = ?
            """.trimIndent(),
            post.title,
            post.slug,
            post.excerpt,
            post.authorId,
            post.password,
            post.visibility.value,
            post.content,
            post.publishedAt,
            post.createdAt,
            post.updatedAt,
            post.pinnedAt,
            post.id,
        )
        replaceTags(post.id, post.tagIds)
    }

    private fun queryOne(sql: String, vararg args: Any): Post? =
        try {
            jdbc.queryForObject(sql, { rs: ResultSet, _: Int -> mapPost(rs) }, *args)
        } catch (_: EmptyResultDataAccessException) {
            null
        }

    private fun build(query: ListPostsQuery): Pair<String, MutableList<Any>> {
        val args: MutableList<Any> = mutableListOf<Any>()
        val sql = StringBuilder()
        if (query.tagId.isNotBlank()) sql.append(" JOIN post_tags pt ON p.id = pt.post_id")
        sql.append(" WHERE 1 = 1")
        if (query.tagId.isNotBlank()) {
            sql.append(" AND pt.tag_id = ?")
            args += query.tagId
        }
        if (query.authorId.isNotBlank()) {
            sql.append(" AND p.author_id = ?")
            args += query.authorId
        }
        if (query.title.isNotBlank()) {
            sql.append(" AND p.title LIKE ?")
            args += "%${query.title}%"
        }
        if (query.query.isNotBlank()) {
            sql.append(" AND (p.title LIKE ? OR p.content LIKE ?)")
            args += "%${query.query}%"
            args += "%${query.query}%"
        }
        query.isPublished?.let { it: Boolean ->
            sql.append(if (it) " AND p.published_at <= ?" else " AND p.published_at > ?")
            args += Instant.now().epochSecond
        }
        query.isTrashed?.let { it: Boolean ->
            sql.append(if (it) " AND p.trashed_at != 0" else " AND p.trashed_at = 0")
        }
        if (query.visibilities.isNotEmpty()) {
            sql.append(" AND p.visibility IN (${query.visibilities.joinToString(", ") { "?" }})")
            args.addAll(query.visibilities.map { it.value })
        }
        val timezone = config.currentOrDefault().timezone
        if (query.publishedYear.isNotBlank()) {
            sql.append(" AND strftime('%Y', datetime(published_at + ?, 'unixepoch')) = ?")
            args += timezone
            args += query.publishedYear
        }
        if (query.publishedMonth.isNotBlank()) {
            sql.append(" AND strftime('%m', datetime(published_at + ?, 'unixepoch')) = ?")
            args += timezone
            args += query.publishedMonth
        }
        if (query.publishedDay.isNotBlank()) {
            sql.append(" AND strftime('%d', datetime(published_at + ?, 'unixepoch')) = ?")
            args += timezone
            args += query.publishedDay
        }
        if (query.publishedDate.isNotBlank()) {
            sql.append(" AND strftime('%Y-%m', datetime(published_at + ?, 'unixepoch')) = ?")
            args += timezone
            args += query.publishedDate
        }
        return sql.toString() to args
    }

    private fun replaceTags(postId: String, tagIds: List<String>) {
        jdbc.update("DELETE FROM post_tags WHERE post_id = ?", postId)
        tagIds.forEach { tagId: String ->
            jdbc.update("INSERT INTO post_tags (post_id, tag_id) VALUES (?, ?)", postId, tagId)
        }
    }

    private fun selectColumns(): String =
        """
        p.id, p.title, p.slug, p.excerpt, p.author_id, p.password, p.visibility, p.content,
        p.published_at, p.created_at, p.updated_at, p.pinned_at, p.trashed_at,
        u.id AS user_id, u.nickname AS user_nickname, u.email AS user_email, u.password AS user_password,
        u.bio AS user_bio, u.created_at AS user_created_at
        """.trimIndent()

    private fun mapPost(rs: ResultSet): Post {
        val postId = rs.getString("id")
        val author = User(
            id = rs.getString("user_id"),
            email = rs.getString("user_email"),
            nickname = rs.getString("user_nickname"),
            password = rs.getString("user_password"),
            bio = rs.getString("user_bio"),
            createdAt = rs.getLong("user_created_at"),
        )
        return Post(
            id = postId,
            title = rs.getString("title"),
            slug = rs.getString("slug"),
            originalExcerpt = rs.getString("excerpt"),
            authorId = rs.getString("author_id"),
            password = rs.getString("password"),
            visibility = Visibility.from(rs.getString("visibility")),
            content = rs.getString("content"),
            pinnedAt = rs.getLong("pinned_at"),
            publishedAt = rs.getLong("published_at"),
            createdAt = rs.getLong("created_at"),
            updatedAt = rs.getLong("updated_at"),
            trashedAt = rs.getLong("trashed_at"),
            author = author,
            tags = tags.listByPost(postId),
        )
    }
}
