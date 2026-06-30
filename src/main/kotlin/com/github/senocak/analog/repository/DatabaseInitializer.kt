package com.github.senocak.analog.repository

import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class DatabaseInitializer(
    private val jdbc: JdbcTemplate,
    private val postRepository: PostRepository,
) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        jdbc.execute(
            """
            CREATE TABLE IF NOT EXISTS users (
                id         TEXT NOT NULL PRIMARY KEY,
                email      TEXT NOT NULL UNIQUE,
                nickname   TEXT NOT NULL UNIQUE,
                password   TEXT NOT NULL,
                bio        TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        jdbc.execute(
            """
            CREATE TABLE IF NOT EXISTS posts (
                id           TEXT NOT NULL PRIMARY KEY,
                title        TEXT NOT NULL,
                slug         TEXT NOT NULL,
                excerpt      TEXT NOT NULL,
                author_id    TEXT NOT NULL,
                password     TEXT NOT NULL,
                visibility   TEXT NOT NULL,
                content      TEXT NOT NULL,
                pinned_at    INTEGER NOT NULL,
                published_at INTEGER NOT NULL,
                created_at   INTEGER NOT NULL,
                updated_at   INTEGER NOT NULL,
                trashed_at   INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        jdbc.execute(
            """
            CREATE TABLE IF NOT EXISTS navigations (
                id       TEXT NOT NULL PRIMARY KEY,
                url      TEXT NOT NULL,
                name     TEXT NOT NULL,
                sequence INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        jdbc.execute(
            """
            CREATE TABLE IF NOT EXISTS tags (
                id          TEXT NOT NULL PRIMARY KEY,
                slug        TEXT NOT NULL,
                name        TEXT NOT NULL,
                description TEXT NOT NULL,
                created_at  INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        jdbc.execute(
            """
            CREATE TABLE IF NOT EXISTS post_tags (
                tag_id  TEXT NOT NULL,
                post_id TEXT NOT NULL,
                PRIMARY KEY (tag_id, post_id)
            )
            """.trimIndent(),
        )
    }

    @Scheduled(fixedDelay = 86_400_000, initialDelay = 86_400_000)
    fun clearExpiredTrashPosts() {
        postRepository.clearExpiredTrashPosts()
    }
}
