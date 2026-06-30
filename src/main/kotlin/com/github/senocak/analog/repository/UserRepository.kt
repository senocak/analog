package com.github.senocak.analog.repository

import com.github.senocak.analog.domain.User
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository

@Repository
class UserRepository(private val jdbc: JdbcTemplate) {
    private val mapper = RowMapper { rs, _ ->
        User(
            id = rs.getString("id"),
            email = rs.getString("email"),
            nickname = rs.getString("nickname"),
            password = rs.getString("password"),
            bio = rs.getString("bio"),
            createdAt = rs.getLong("created_at"),
        )
    }

    fun create(user: User) {
        jdbc.update(
            "INSERT INTO users (id, email, nickname, password, bio, created_at) VALUES (?, ?, ?, ?, ?, ?)",
            user.id,
            user.email,
            user.nickname,
            user.password,
            user.bio,
            user.createdAt,
        )
    }

    fun findByEmail(email: String): User? =
        queryOne("SELECT id, email, nickname, password, bio, created_at FROM users WHERE email = ?", email)

    fun findById(id: String): User? =
        queryOne("SELECT id, email, nickname, password, bio, created_at FROM users WHERE id = ?", id)

    fun nicknameExists(nickname: String): Boolean =
        jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM users WHERE nickname = ?)", Boolean::class.java, nickname) ?: false

    fun exists(id: String): Boolean =
        jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM users WHERE id = ?)", Boolean::class.java, id) ?: false

    fun list(): List<User> =
        jdbc.query("SELECT id, email, nickname, password, bio, created_at FROM users", mapper)

    fun update(id: String, nickname: String, bio: String, email: String) {
        jdbc.update("UPDATE users SET nickname = ?, bio = ?, email = ? WHERE id = ?", nickname, bio, email, id)
    }

    fun updatePassword(id: String, password: String) {
        jdbc.update("UPDATE users SET password = ? WHERE id = ?", password, id)
    }

    fun delete(id: String) {
        jdbc.update("DELETE FROM users WHERE id = ?", id)
    }

    private fun queryOne(sql: String, vararg args: Any): User? =
        try {
            jdbc.queryForObject(sql, mapper, *args)
        } catch (_: EmptyResultDataAccessException) {
            null
        }
}
