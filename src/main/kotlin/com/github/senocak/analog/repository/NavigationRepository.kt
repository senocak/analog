package com.github.senocak.analog.repository

import com.github.senocak.analog.domain.Navigation
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository

@Repository
class NavigationRepository(private val jdbc: JdbcTemplate) {
    private val mapper = RowMapper { rs, _ ->
        Navigation(
            id = rs.getString("id"),
            url = rs.getString("url"),
            name = rs.getString("name"),
            sequence = rs.getInt("sequence"),
        )
    }

    fun list(): List<Navigation> =
        jdbc.query("SELECT id, url, name, sequence FROM navigations ORDER BY sequence ASC", mapper)

    fun clear() {
        jdbc.update("DELETE FROM navigations")
    }

    fun create(navigation: Navigation) {
        jdbc.update(
            "INSERT INTO navigations (id, url, name, sequence) VALUES (?, ?, ?, ?)",
            navigation.id,
            navigation.url,
            navigation.name,
            navigation.sequence,
        )
    }
}
