package com.github.senocak.analog.domain

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import java.io.File
import java.math.BigInteger
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

enum class Visibility(@JsonValue val value: String) {
    PUBLIC(value = "public"),
    PRIVATE(value = "private"),
    PASSWORD(value = "password"),
    DRAFT(value = "draft"),
    TRASH(value = "trash"),
    UNKNOWN(value = "");

    companion object {
        @JsonCreator
        @JvmStatic
        fun from(value: String?): Visibility =
            entries.firstOrNull { it.value == value } ?: UNKNOWN
    }
}

enum class ColorScheme(@JsonValue val value: String) {
    LIGHT(value = "light"),
    DARK(value = "dark"),
    AUTO(value = "");

    companion object {
        @JsonCreator
        @JvmStatic
        fun from(value: String?): ColorScheme =
            entries.firstOrNull { it.value == value } ?: AUTO
    }
}

enum class FontFamily(@JsonValue val value: String) {
    SANS(value = "sans"),
    SERIF(value = "serif");

    companion object {
        @JsonCreator
        @JvmStatic
        fun from(value: String?): FontFamily =
            entries.firstOrNull { it.value == value } ?: SANS
    }
}

enum class AuthorBlock(@JsonValue val value: String) {
    NONE(value = "none"),
    START(value = "start"),
    END(value = "end");

    companion object {
        @JsonCreator
        @JvmStatic
        fun from(value: String?): AuthorBlock =
            entries.firstOrNull { it.value == value } ?: START
    }
}

data class AnalogConfig(
    var name: String = "",
    var description: String = "",
    var isPublic: Boolean = true,
    var dateFormat: String = "yyyy-MM-dd",
    var timeFormat: String = "HH:mm",
    var timezone: Int = 0,
    var injectedHead: String = "",
    var injectedFoot: String = "",
    var injectedPostStart: String = "",
    var injectedPostEnd: String = "",
    var footerText: String = """Powered by <a href="https://analog.org" target="_blank">Analog</a>""",
    var colorScheme: ColorScheme = ColorScheme.AUTO,
    var containerWidth: String = "medium",
    var fontFamily: FontFamily = FontFamily.SANS,
    var fontSize: String = "medium",
    var highlightJS: Boolean = true,
    var authorBlock: AuthorBlock = AuthorBlock.START,
    var postsPerPage: Int = 10,
    var theme: String = "default",
    var locale: String = "en-us",
) {
    fun isCustomTimeFormat(): Boolean = timeFormat !in setOf("PM 03:04", "15:04", "03:04 PM", "HH:mm")

    fun isCustomDateFormat(): Boolean = dateFormat !in setOf("2006-01-02", "01/02/2006", "02/01/2006", "yyyy-MM-dd")
}

data class User(
    val id: String,
    val email: String,
    val nickname: String,
    val password: String,
    val bio: String,
    val createdAt: Long,
) {
    fun gravatar(): String {
        val digest: ByteArray? = MessageDigest.getInstance("MD5").digest(email.toByteArray())
        return "http://www.gravatar.com/avatar/${BigInteger(1, digest).toString(16).padStart(length = 32, padChar = '0')}"
    }
}

data class Tag(
    val id: String,
    val slug: String,
    val name: String,
    val description: String = "",
    val createdAt: Long,
    val postCount: Int = 0,
)

data class Navigation(
    val id: String,
    val url: String,
    val name: String,
    val sequence: Int,
)

data class Post(
    val id: String,
    val title: String,
    val slug: String,
    val originalExcerpt: String,
    val authorId: String,
    val password: String,
    val visibility: Visibility,
    val content: String,
    val pinnedAt: Long,
    val publishedAt: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val trashedAt: Long,
    val author: User,
    val tags: List<Tag> = emptyList(),
) {
    fun tagsStr(): String = tags.joinToString(separator = ",") { it.name }
    fun tagNames(): List<String> = tags.map { it.name }
    fun publishedDate(): String = format(pattern = "yyyy-MM-dd")
    fun publishedAtIso(): String = format(pattern = "yyyy-MM-dd'T'HH:mm")
    fun publishedYear(): String = format(pattern = "yyyy")
    fun publishedMonth(): String = format(pattern = "MM")
    fun publishedDay(): String = format(pattern = "dd")
    fun isPublished(): Boolean = Instant.now().epochSecond >= publishedAt
    fun cover(): String = if (File("data/uploads/covers/$id.jpg").exists()) "uploads/covers/$id.jpg" else ""
    fun excerpt(): String =
        originalExcerpt.ifBlank {
            val stripped: String = content
                .replace(Regex(pattern = "```[\\s\\S]*?```"), replacement = "")
                .replace(Regex(pattern = "`([^`]*)`"), replacement = "$1")
                .replace(Regex(pattern = "[#>*_\\-\\[\\]()]"), replacement = "")
                .trim()
            if (stripped.length > 200) stripped.take(200) + "..." else stripped
        }

    private fun format(pattern: String): String =
        DateTimeFormatter.ofPattern(pattern).withZone(ZoneOffset.UTC).format(Instant.ofEpochSecond(publishedAt))
}

data class PostWrite(
    val id: String,
    val title: String,
    val slug: String,
    val excerpt: String,
    val authorId: String,
    val password: String,
    val visibility: Visibility,
    val content: String,
    val pinnedAt: Long = 0,
    val publishedAt: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val trashedAt: Long = 0,
    val tagIds: List<String> = emptyList(),
)

data class PostCount(
    var all: Int = 0,
    var nonTrash: Int = 0,
    var public: Int = 0,
    var private: Int = 0,
    var password: Int = 0,
    var draft: Int = 0,
    var trash: Int = 0,
)

data class Pagination(
    val currentPage: Int,
    val totalCount: Int,
    val totalPages: Int,
    val query: String,
)

data class ListPostsQuery(
    var authorId: String = "",
    var tagId: String = "",
    var title: String = "",
    var query: String = "",
    var visibilities: List<Visibility> = emptyList(),
    var isPublished: Boolean? = null,
    var isTrashed: Boolean? = null,
    var publishedYear: String = "",
    var publishedMonth: String = "",
    var publishedDay: String = "",
    var publishedDate: String = "",
    var offset: Int = -1,
    var limit: Int = 0,
)

data class IndexFilter(
    val tag: String = "",
    val author: String = "",
    val date: String = "",
) {
    fun isEmpty(): Boolean = tag.isBlank() && author.isBlank() && date.isBlank()
}

val timezones: Map<String, Int> = mapOf(
    "UTC-12:00" to -43200,
    "UTC-11:30" to -41400,
    "UTC-11:00" to -39600,
    "UTC-10:30" to -37800,
    "UTC-10:00" to -36000,
    "UTC-09:30" to -34200,
    "UTC-09:00" to -32400,
    "UTC-08:30" to -30600,
    "UTC-08:00" to -28800,
    "UTC-07:30" to -27000,
    "UTC-07:00" to -25200,
    "UTC-06:30" to -23400,
    "UTC-06:00" to -21600,
    "UTC-05:30" to -19800,
    "UTC-05:00" to -18000,
    "UTC-04:30" to -16200,
    "UTC-04:00" to -14400,
    "UTC-03:30" to -12600,
    "UTC-03:00" to -10800,
    "UTC-02:30" to -9000,
    "UTC-02:00" to -7200,
    "UTC-01:30" to -5400,
    "UTC-01:00" to -3600,
    "UTC-00:30" to -1800,
    "UTC+00:00" to 0,
    "UTC+00:30" to 1800,
    "UTC+01:00" to 3600,
    "UTC+01:30" to 5400,
    "UTC+02:00" to 7200,
    "UTC+02:30" to 9000,
    "UTC+03:00" to 10800,
    "UTC+03:30" to 12600,
    "UTC+04:00" to 14400,
    "UTC+04:30" to 16200,
    "UTC+05:00" to 18000,
    "UTC+05:30" to 19800,
    "UTC+05:45" to 20700,
    "UTC+06:00" to 21600,
    "UTC+06:30" to 23400,
    "UTC+07:00" to 25200,
    "UTC+07:30" to 27000,
    "UTC+08:00" to 28800,
    "UTC+08:30" to 30600,
    "UTC+08:45" to 31500,
    "UTC+09:00" to 32400,
    "UTC+09:30" to 34200,
    "UTC+10:00" to 36000,
    "UTC+10:30" to 37800,
    "UTC+11:00" to 39600,
    "UTC+11:30" to 41400,
    "UTC+12:00" to 43200,
    "UTC+12:45" to 45900,
    "UTC+13:00" to 46800,
    "UTC+13:45" to 49500,
    "UTC+14:00" to 50400,
)

val locales: Map<String, String> = mapOf(
    "台灣正體" to "zh-tw",
    "English" to "en-us",
    "简体中文" to "zh-cn",
    "Français" to "fr-fr",
)
