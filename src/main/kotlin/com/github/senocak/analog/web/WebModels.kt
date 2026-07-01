package com.github.senocak.analog.web

import com.github.senocak.analog.domain.AuthorBlock
import com.github.senocak.analog.domain.ColorScheme
import com.github.senocak.analog.domain.FontFamily
import com.github.senocak.analog.domain.Visibility
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.web.bind.annotation.BindParam

data class WizardRequest(
    @field:NotBlank @field:Size(max = 48) var name: String = "",
    @field:Size(max = 128) var description: String = "",
    @field:NotBlank @field:Email var email: String = "",
    @field:NotBlank @field:Size(max = 128) var password: String = "",
    @field:NotBlank @field:Size(max = 32) var nickname: String = "",
    @field:Min(value = -43_200) @field:Max(value = 50_400) var timezone: Int = 0,
    @field:NotBlank var locale: String = "en-us",
)

data class LoginRequest(
    @field:NotBlank @field:Email var email: String = "",
    @field:NotBlank @field:Size(max = 128) var password: String = "",
)

data class UserCreateRequest(
    @field:NotBlank @field:Email var email: String = "",
    @field:NotBlank @field:Size(max = 128) var password: String = "",
)

data class UserEditRequest(
    @field:NotBlank @field:Email var email: String = "",
    @field:Size(max = 128) var password: String = "",
    @field:NotBlank @field:Size(max = 32) var nickname: String = "",
    @field:Size(max = 255) var bio: String = "",
)

data class UserDeleteRequest(@field:BindParam("transfer_to_id") var transferToId: String = "")

data class TagCreateRequest(
    @field:NotBlank @field:Size(max = 64) var name: String = "",
    @field:Size(max = 255) var description: String = "",
)

data class TagEditRequest(
    @field:NotBlank @field:Size(max = 64) var slug: String = "",
    @field:NotBlank @field:Size(max = 64) var name: String = "",
    @field:Size(max = 255) var description: String = "",
)

data class NavigationCreateRequest(
    @field:NotBlank @field:Size(max = 64) var name: String = "",
    @field:NotBlank var url: String = "",
)

data class NavigationEditRequest(
    @field:BindParam(value = "name[]") var name: List<String> = emptyList(),
    @field:BindParam(value = "url[]") var url: List<String> = emptyList(),
    @field:BindParam(value = "sequence[]") var sequence: List<Int> = emptyList(),
    @field:BindParam(value = "is_deleted[]") var isDeleted: List<Boolean> = emptyList(),
)

data class PostCreateRequest(
    @field:NotBlank @field:Size(max = 128) var title: String = "",
    @field:NotBlank var slug: String = "",
    var excerpt: String = "",
    @field:BindParam(value = "author_id")
    @field:NotBlank var authorId: String = "",
    @field:Size(max = 128) var password: String = "",
    var visibility: Visibility = Visibility.PUBLIC,
    var content: String = "",
    @field:BindParam(value = "published_at")
    var publishedAt: Long = 0,
    @field:BindParam(value = "is_pinned")
    var isPinned: Boolean = false,
    var tags: String = "",
)

data class PostEditRequest(
    @field:NotBlank @field:Size(max = 128) var title: String = "",
    @field:NotBlank var slug: String = "",
    var excerpt: String = "",
    @field:BindParam(value = "author_id")
    @field:NotBlank var authorId: String = "",
    @field:Size(max = 128) var password: String = "",
    var visibility: Visibility = Visibility.PUBLIC,
    var content: String = "",
    @field:BindParam(value = "published_at")
    var publishedAt: Long = 0,
    @field:BindParam(value = "is_pinned")
    var isPinned: Boolean = false,
    @field:BindParam(value = "is_clear_cover")
    var isClearCover: Boolean = false,
    var tags: String = "",
)

data class SettingsEditRequest(
    @field:NotBlank @field:Size(max = 64) var name: String = "",
    @field:NotBlank @field:Size(max = 128) var description: String = "",
    @field:BindParam(value = "is_public")
    var isPublic: Boolean = false,
    @field:Min(value = -43_200) @field:Max(value = 50_400) var timezone: Int = 0,
    @field:BindParam(value = "date_format")
    @field:NotBlank var dateFormat: String = "",
    @field:BindParam(value = "date_format_custom")
    var dateFormatCustom: String = "",
    @field:BindParam(value = "time_format")
    @field:NotBlank var timeFormat: String = "",
    @field:BindParam(value = "time_format_custom")
    var timeFormatCustom: String = "",
    @field:NotBlank var locale: String = "en-us",
)

data class AppearancesEditRequest(
    @field:BindParam(value = "footer_text")
    var footerText: String = "",
    @field:BindParam(value = "color_scheme")
    var colorScheme: ColorScheme = ColorScheme.AUTO,
    @field:BindParam(value = "container_width")
    var containerWidth: String = "medium",
    @field:BindParam(value = "font_family")
    var fontFamily: FontFamily = FontFamily.SANS,
    @field:BindParam(value = "font_size")
    var fontSize: String = "medium",
    @field:BindParam(value = "highlight_js")
    var highlightJS: Boolean = false,
    @field:BindParam(value = "author_block")
    var authorBlock: AuthorBlock = AuthorBlock.START,
    @field:BindParam(value = "posts_per_page")
    @field:Min(value = 1) @field:Max(value = 999) var postsPerPage: Int = 10,
    @field:NotBlank var theme: String = "default",
)

data class AppearancesInjectedRequest(
    @field:BindParam(value = "injected_head")
    var injectedHead: String = "",
    @field:BindParam(value = "injected_foot")
    var injectedFoot: String = "",
    @field:BindParam(value = "injected_post_start")
    var injectedPostStart: String = "",
    @field:BindParam(value = "injected_post_end")
    var injectedPostEnd: String = "",
)

data class PhotoCreateResponse(val path: String)
data class PhotoDeleteRequest(@field:NotBlank var path: String = "")
