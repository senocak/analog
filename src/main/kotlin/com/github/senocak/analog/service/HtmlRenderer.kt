package com.github.senocak.analog.service

import com.github.senocak.analog.domain.Navigation
import com.github.senocak.analog.domain.Pagination
import com.github.senocak.analog.domain.Post
import com.github.senocak.analog.domain.PostCount
import com.github.senocak.analog.domain.Tag
import com.github.senocak.analog.domain.AnalogConfig
import com.github.senocak.analog.domain.User
import com.github.senocak.analog.domain.Visibility
import com.github.senocak.analog.domain.locales
import com.github.senocak.analog.domain.timezones
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Service
class HtmlRenderer {
    fun wizard(message: String = "", defaultLocale: String = "en-us"): String =
        document("Analog") {
            append(
                """
                <form method="POST" class="ts-container is-very-narrow has-vertically-spaced-big">
                  ${notice(message)}
                  <div class="ts-header is-big is-heavy">Welcome to Analog</div>
                  <div class="ts-text is-secondary">Create the site and first administrator.</div>
                  <div class="ts-box has-top-spaced-large"><div class="ts-content is-padded">
                    ${select("locale", "Locale", locales.values.toList(), defaultLocale)}
                    ${input("name", "Site name", "Analog")}
                    ${input("description", "Slogan", "A simple blog")}
                    <input type="hidden" id="timezone-input" name="timezone" value="0" />
                    ${input("email", "Admin email", "admin@example.com", "email")}
                    ${input("nickname", "Nickname", "Admin")}
                    ${input("password", "Password", "admin", "password")}
                    <button class="ts-button has-top-spaced-large" type="submit">Finish setup</button>
                  </div></div>
                </form>
                <script>document.getElementById('timezone-input').value = new Date().getTimezoneOffset() * -60</script>
                """.trimIndent(),
            )
        }

    fun login(config: AnalogConfig, message: String): String =
        document(config.name) {
            append(
                """
                <form method="POST" class="ts-app-center">
                  <div class="content" style="width: 300px">
                    <a class="ts-header is-large is-heavy is-undecorated" href="/">${h(config.name)}</a>
                    ${notice(message)}
                    <div class="ts-box has-top-spaced-large"><div class="ts-content is-padded">
                      ${input("email", "Email", "", "email")}
                      ${input("password", "Password", "", "password")}
                      <div class="ts-grid has-top-spaced">
                        <div class="column"><a class="ts-button is-ghost" href="/">Back</a></div>
                        <div class="column is-fluid"><button class="ts-button is-fluid" type="submit">Login</button></div>
                      </div>
                    </div></div>
                  </div>
                </form>
                """.trimIndent(),
            )
        }

    fun index(
        config: AnalogConfig,
        posts: List<Post>,
        pagination: Pagination,
        navigations: List<Navigation>,
        filterText: String = "",
    ): String =
        publicPage(config, navigations, config.name) {
            append("""<section class="ts-content is-secondary is-center-aligned"><h1 class="ts-header is-massive">${h(config.name)}</h1><p>${h(config.description)}</p></section>""")
            if (filterText.isNotBlank()) {
                append("""<div class="ts-container has-top-spaced"><div class="ts-notice"><div class="content">${h(filterText)} <a href="/">Reset</a></div></div></div>""")
            }
            append("""<main class="ts-container has-top-spaced-big">""")
            if (posts.isEmpty()) {
                append("""<div class="ts-blankslate"><div class="header">No posts found</div></div>""")
            }
            posts.forEach { post ->
                append(
                    """
                    <article class="has-bottom-spaced-large">
                      <a class="ts-header is-large is-link is-undecorated" href="/post/${u(post.slug)}">${visibilityPrefix(post)}${h(post.title)}</a>
                      <div class="ts-text is-secondary has-top-spaced">${if (post.visibility == Visibility.PASSWORD) "This post is password protected." else h(post.excerpt())}</div>
                      <div class="ts-meta is-secondary has-top-spaced-small">
                        <a class="item" href="/archive/${post.publishedYear()}/${post.publishedMonth()}">${date(post.publishedAt, config.dateFormat)}</a>
                        <a class="item" href="/author/${u(post.author.id)}">${h(post.author.nickname)}</a>
                      </div>
                    </article>
                    <div class="ts-divider has-vertically-spaced-large"></div>
                    """.trimIndent(),
                )
            }
            append(paginationHtml(pagination))
            append("</main>")
        }

    fun post(
        config: AnalogConfig,
        post: Post,
        navigations: List<Navigation>,
        previous: Post?,
        next: Post?,
        unlocked: Boolean,
        self: User?,
        message: String,
    ): String =
        publicPage(config, navigations, "${post.title} - ${config.name}") {
            append("""<main class="ts-container has-vertically-spaced-big">""")
            post.cover().takeIf { it.isNotBlank() }?.let { append("""<img class="ts-image is-rounded" src="/post/$it" alt="">""") }
            append("""<div class="ts-meta is-secondary"><span class="item">${date(post.publishedAt, config.dateFormat)}</span>""")
            if (self != null) append("""<a class="item" href="/admin/post/${u(post.id)}">Edit</a>""")
            append("</div>")
            append("""<h1 class="ts-header is-massive">${visibilityPrefix(post)}${h(post.title)}</h1>""")
            if (unlocked) {
                append("""<article class="has-top-spaced-large">${markdown(post.content)}</article>""")
                if (post.tags.isNotEmpty()) {
                    append("""<div class="ts-wrap has-top-spaced-big">${post.tags.joinToString("") { """<a href="/tag/${u(it.slug)}" class="ts-badge">#${h(it.name)}</a>""" }}</div>""")
                }
            } else {
                append(
                    """
                    ${notice(message)}
                    <form method="POST" class="ts-box has-top-spaced-large"><div class="ts-content is-padded">
                      ${input("password", "Password", "", "text")}
                      <button class="ts-button has-top-spaced" type="submit">Unlock</button>
                    </div></form>
                    """.trimIndent(),
                )
            }
            append("""<div class="ts-grid has-top-spaced-large">""")
            if (next != null) append("""<a class="column ts-box" href="/post/${u(next.slug)}"><div class="ts-content">Newer<br><b>${h(next.title)}</b></div></a>""")
            if (previous != null) append("""<a class="column ts-box" href="/post/${u(previous.slug)}"><div class="ts-content">Older<br><b>${h(previous.title)}</b></div></a>""")
            append("</div></main>")
        }

    fun adminUsers(config: AnalogConfig, self: User, message: String, users: List<User>): String =
        adminPage(config, self, "user", "Users", message) {
            append("""<form method="POST" class="ts-box"><div class="ts-content is-padded">${input("email", "Email", "", "email")}${input("password", "Password", "", "password")}<button class="ts-button has-top-spaced">Create</button></div></form>""")
            table(listOf("Email", "Nickname", "Created"), users) {
                "<td>${h(it.email)}</td><td>${h(it.nickname)}</td><td>${date(it.createdAt, config.dateFormat)}</td><td><a class=\"ts-button is-small\" href=\"/admin/user/${u(it.id)}\">Edit</a></td>"
            }
        }

    fun adminUserEdit(config: AnalogConfig, self: User, message: String, user: User, users: List<User>, postCount: Int): String =
        adminPage(config, self, "user", "Edit User", message) {
            append("""<form method="POST" class="ts-box"><div class="ts-content is-padded">${input("email", "Email", user.email, "email")}${input("nickname", "Nickname", user.nickname)}${input("password", "New password", "", "password")}<label class="ts-text is-label">Bio</label><textarea class="ts-input" name="bio">${h(user.bio)}</textarea><button class="ts-button has-top-spaced">Save</button></div></form>""")
            if (users.size > 1 && user.id != self.id) {
                append("""<form method="POST" action="${u(user.id)}/delete" class="ts-box has-top-spaced"><div class="ts-content is-padded"><p>Delete this user. Existing posts: $postCount</p><select name="transfer_to_id"><option value="">Delete posts</option>${users.filter { it.id != user.id }.joinToString("") { "<option value=\"${u(it.id)}\">${h(it.nickname)}</option>" }}</select><button class="ts-button is-negative has-top-spaced">Delete user</button></div></form>""")
            }
        }

    fun adminTags(config: AnalogConfig, self: User, message: String, tags: List<Tag>, pagination: Pagination, keyword: String): String =
        adminPage(config, self, "tag", "Tags", message) {
            append("""<form method="POST" class="ts-box"><div class="ts-content is-padded">${input("name", "Name")}${input("description", "Description")}<button class="ts-button has-top-spaced">Create</button></div></form>""")
            append("""<form class="has-top-spaced"><input class="ts-input" name="keyword" value="${h(keyword)}" placeholder="Filter tags"><button class="ts-button has-top-spaced-small">Filter</button></form>""")
            table(listOf("Name", "Slug", "Posts"), tags) {
                "<td>${h(it.name)}</td><td><a href=\"/tag/${u(it.slug)}\">${h(it.slug)}</a></td><td>${it.postCount}</td><td><a class=\"ts-button is-small\" href=\"/admin/tag/${u(it.id)}\">Edit</a></td>"
            }
            append(paginationHtml(pagination))
        }

    fun adminTagEdit(config: AnalogConfig, self: User, message: String, tag: Tag): String =
        adminPage(config, self, "tag", "Edit Tag", message) {
            append("""<form method="POST" class="ts-box"><div class="ts-content is-padded">${input("name", "Name", tag.name)}${input("slug", "Slug", tag.slug)}<label class="ts-text is-label">Description</label><textarea class="ts-input" name="description">${h(tag.description)}</textarea><button class="ts-button has-top-spaced">Save</button></div></form>""")
            append("""<form method="POST" action="${u(tag.id)}/delete" class="has-top-spaced"><button class="ts-button is-negative">Delete tag</button></form>""")
        }

    fun adminNavigations(config: AnalogConfig, self: User, message: String, navs: List<Navigation>): String =
        adminPage(config, self, "navigation", "Navigations", message) {
            append("""<form method="POST" class="ts-box"><div class="ts-content is-padded">${input("name", "Name")}${input("url", "URL", "https://", "url")}<button class="ts-button has-top-spaced">Create</button></div></form>""")
            table(listOf("Name", "URL", "Order"), navs) { "<td>${h(it.name)}</td><td>${h(it.url)}</td><td>${it.sequence}</td>" }
        }

    fun adminSettings(config: AnalogConfig, self: User, message: String, version: String, runtime: String): String =
        adminPage(config, self, "settings", "Settings", message) {
            append("""<form method="POST" class="ts-box"><div class="ts-content is-padded">${input("name", "Name", config.name)}${input("description", "Description", config.description)}<label><input type="checkbox" name="is_public" value="true" ${checked(config.isPublic)}> Public site</label>${select("timezone", "Timezone", timezones.values.map { it.toString() }, config.timezone.toString())}${select("locale", "Locale", locales.values.toList(), config.locale)}${input("date_format", "Date format", config.dateFormat)}${input("time_format", "Time format", config.timeFormat)}<button class="ts-button has-top-spaced">Save</button></div></form><p class="ts-text is-secondary">Analog $version on Java $runtime</p>""")
        }

    fun adminAppearances(config: AnalogConfig, self: User, message: String, themes: List<String>): String =
        adminPage(config, self, "appearances", "Appearances", message) {
            append("""<form method="POST" class="ts-box"><div class="ts-content is-padded">${select("color_scheme", "Color scheme", listOf("", "light", "dark"), config.colorScheme.value)}${select("container_width", "Container width", listOf("small", "medium", "large"), config.containerWidth)}${select("font_family", "Font family", listOf("sans", "serif"), config.fontFamily.value)}${select("font_size", "Font size", listOf("small", "medium", "large"), config.fontSize)}${input("posts_per_page", "Posts per page", config.postsPerPage.toString(), "number")}<label class="ts-text is-label">Footer text</label><textarea class="ts-input" name="footer_text">${h(config.footerText)}</textarea>${select("theme", "Theme", themes, config.theme)}<button class="ts-button has-top-spaced">Save</button></div></form>""")
            append("""<form method="POST" action="/admin/appearances/injected" class="ts-box has-top-spaced"><div class="ts-content is-padded"><label>Injected head</label><textarea class="ts-input" name="injected_head">${h(config.injectedHead)}</textarea><label>Injected foot</label><textarea class="ts-input" name="injected_foot">${h(config.injectedFoot)}</textarea><button class="ts-button has-top-spaced">Save injected code</button></div></form>""")
        }

    fun adminPosts(config: AnalogConfig, self: User, message: String, posts: List<Post>, counts: PostCount, pagination: Pagination): String =
        adminPage(config, self, "post", "Posts", message) {
            append("""<a class="ts-button" href="/admin/post/create">Create post</a><div class="ts-wrap has-top-spaced"><a href="/admin/posts">All ${counts.nonTrash}</a><a href="?visibility=public">Public ${counts.public}</a><a href="?visibility=draft">Draft ${counts.draft}</a><a href="?visibility=trash">Trash ${counts.trash}</a></div>""")
            table(listOf("Title", "Author", "Date"), posts) {
                val actions = if (it.trashedAt == 0L) {
                    """<form method="POST" action="/admin/post/${u(it.id)}/trash"><a class="ts-button is-small" href="/admin/post/${u(it.id)}">Edit</a> <a class="ts-button is-small" href="/post/${u(it.slug)}">View</a> <button class="ts-button is-small">Trash</button></form>"""
                } else {
                    """<form method="POST" action="/admin/post/${u(it.id)}/untrash"><button class="ts-button is-small">Untrash</button> <button class="ts-button is-small is-negative" formaction="/admin/post/${u(it.id)}/delete">Delete</button></form>"""
                }
                "<td>${h(it.title)}</td><td>${h(it.author.nickname)}</td><td>${date(it.publishedAt, config.dateFormat)}</td><td>$actions</td>"
            }
            append(paginationHtml(pagination))
        }

    fun adminPostForm(config: AnalogConfig, self: User, message: String, users: List<User>, tags: List<Tag>, post: Post? = null): String =
        adminPage(config, self, "post", if (post == null) "Create Post" else "Edit Post", message) {
            val action = post?.let { "/admin/post/${u(it.id)}" } ?: "/admin/post/create"
            append("""<link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/easymde/dist/easymde.min.css"><script src="https://cdn.jsdelivr.net/npm/easymde/dist/easymde.min.js"></script>""")
            append("""<form method="POST" enctype="multipart/form-data" action="$action" class="ts-box"><div class="ts-content is-padded">${input("title", "Title", post?.title ?: "")}${input("slug", "Slug", post?.slug ?: "")}<label class="ts-text is-label">Content</label><textarea id="content" name="content" rows="12">${h(post?.content ?: "")}</textarea><label class="ts-text is-label has-top-spaced">Excerpt</label><div id="excerpt-container" class="has-top-spaced"><textarea id="excerpt" name="excerpt" rows="5">${h(post?.originalExcerpt ?: "")}</textarea></div>${select("author_id", "Author", users.map { it.id }, post?.authorId ?: self.id)}${select("visibility", "Visibility", listOf("public", "private", "password", "draft"), post?.visibility?.value ?: "public")}${input("password", "Password", post?.password ?: "")}${input("published_at", "Published at timestamp", (post?.publishedAt ?: Instant.now().epochSecond).toString(), "number")}${input("tags", "Tags", post?.tagsStr() ?: "")}<label><input type="checkbox" name="is_pinned" value="true" ${checked((post?.pinnedAt ?: 0) > 0)}> Pinned</label><label class="ts-text is-label">Cover</label><input name="cover_file" type="file"><button class="ts-button has-top-spaced">Save</button></div></form>""")
            append("""<script>new EasyMDE({element:document.getElementById("content"),spellChecker:false,status:false,toolbar:["heading","bold","italic","quote","code","link","|","unordered-list","ordered-list","|","table","|","preview"],renderingConfig:{singleLineBreaks:false}});new EasyMDE({element:document.getElementById("excerpt"),spellChecker:false,status:false,minHeight:"120px",toolbar:["bold","italic","link","|","preview"],renderingConfig:{singleLineBreaks:false}});</script>""")
            if (tags.isNotEmpty()) append("""<p class="ts-text is-secondary">Existing tags: ${tags.joinToString(", ") { h(it.name) }}</p>""")
        }

    fun adminPhotos(config: AnalogConfig, self: User, message: String, groups: List<Map<String, Any?>>, pagination: Pagination): String =
        adminPage(config, self, "media", "Photos", message) {
            append("""<form method="POST" enctype="multipart/form-data" class="ts-box"><div class="ts-content is-padded"><input type="file" name="photo_file" multiple><button class="ts-button has-top-spaced">Upload</button></div></form>""")
            groups.forEach { group ->
                val year = group["year"].toString()
                val month = group["month"].toString()
                @Suppress("UNCHECKED_CAST")
                val filenames = group["filenames"] as List<String>
                append("""<h3 class="ts-header has-top-spaced">$year / $month</h3><div class="ts-grid is-4-columns">""")
                filenames.forEach { file ->
                    append("""<div class="column"><img class="ts-image is-rounded" src="/uploads/images/$year/$month/${u(file)}"><form method="POST" action="/admin/photo/delete"><input type="hidden" name="path" value="$year/$month/${h(file)}"><button class="ts-button is-small is-negative has-top-spaced-small">Delete</button></form></div>""")
                }
                append("</div>")
            }
            append(paginationHtml(pagination))
        }

    private fun document(title: String, body: StringBuilder.() -> Unit): String =
        buildString {
            append(
                """
                <!doctype html>
                <html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
                <title>${h(title)}</title>
                <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/tocas/5.0.1/tocas.min.css">
                <script src="https://cdnjs.cloudflare.com/ajax/libs/tocas/5.0.1/tocas.min.js"></script>
                <link rel="stylesheet" href="/admin/assets/style.css">
                <link rel="stylesheet" href="/assets/style.css">
                <link rel="icon" type="image/png" href="/assets/favicon.png">
                </head><body>
                """.trimIndent(),
            )
            body()
            append("</body></html>")
        }

    private fun publicPage(config: AnalogConfig, navigations: List<Navigation>, title: String, body: StringBuilder.() -> Unit): String =
        document(title) {
            if (navigations.isNotEmpty()) {
                append("""<nav class="ts-content is-tertiary"><div class="ts-container ts-wrap">${navigations.joinToString("") { """<a href="${h(it.url)}">${h(it.name)}</a>""" }}</div></nav>""")
            }
            body()
            if (config.footerText.isNotBlank()) append("""<footer class="ts-content"><div class="ts-container">${config.footerText}</div></footer>""")
        }

    private fun adminPage(config: AnalogConfig, self: User, active: String, title: String, message: String, body: StringBuilder.() -> Unit): String =
        document("$title - ${config.name}") {
            append("""<div class="ts-app-layout is-fullscreen is-horizontal"><aside class="cell is-scrollable has-flex-column" style="width:260px"><div class="ts-content"><a class="ts-header is-big is-heavy" href="/">${h(config.name)}</a><div class="ts-text is-description">Signed in as ${h(self.nickname)}</div><a class="ts-button is-outlined has-top-spaced" href="/admin/post/create">Create post</a></div><div class="ts-app-sidebar">""")
            listOf("user" to "/admin/users", "post" to "/admin/posts", "tag" to "/admin/tags", "media" to "/admin/photos", "navigation" to "/admin/navigations", "settings" to "/admin/settings", "appearances" to "/admin/appearances").forEach { (key, href) ->
                append("""<a class="item ${if (active == key) "is-active" else ""}" href="$href">${key.replaceFirstChar { it.uppercase() }}</a>""")
            }
            append("""</div><div class="ts-content has-top-spaced-auto"><form method="POST" action="/admin/logout"><button class="ts-button is-outlined is-fluid">Logout</button></form></div></aside><main class="cell is-fluid is-secondary is-scrollable"><div class="ts-content"><h1 class="ts-header">${h(title)}</h1>${notice(message)}""")
            body()
            append("</div></main></div>")
        }

    private fun <T> StringBuilder.table(headers: List<String>, rows: List<T>, row: (T) -> String) {
        append("""<table class="ts-table is-celled has-top-spaced"><thead><tr>${headers.joinToString("") { "<th>${h(it)}</th>" }}<th></th></tr></thead><tbody>""")
        rows.forEach { append("<tr>${row(it)}</tr>") }
        append("</tbody></table>")
    }

    private fun input(name: String, label: String, value: String = "", type: String = "text"): String =
        """<label class="ts-text is-label has-top-spaced">$label</label><div class="ts-input"><input type="$type" name="$name" value="${h(value)}"></div>"""

    private fun select(name: String, label: String, options: List<String>, selected: String): String =
        """<label class="ts-text is-label has-top-spaced">$label</label><div class="ts-select"><select name="$name">${options.joinToString("") { "<option value=\"${h(it)}\" ${if (it == selected) "selected" else ""}>${h(it.ifBlank { "Auto" })}</option>" }}</select></div>"""

    private fun notice(message: String): String =
        if (message.isBlank()) "" else """<div class="ts-notice has-top-spaced"><div class="content">${h(message)}</div></div>"""

    private fun paginationHtml(pagination: Pagination): String =
        if (pagination.totalPages <= 1) "" else """<div class="ts-pagination has-top-spaced"><a class="item" href="?${pagination.query}page=${(pagination.currentPage - 1).coerceAtLeast(1)}">Prev</a><span class="item is-active">${pagination.currentPage} / ${pagination.totalPages}</span><a class="item" href="?${pagination.query}page=${(pagination.currentPage + 1).coerceAtMost(pagination.totalPages)}">Next</a></div>"""

    private fun visibilityPrefix(post: Post): String =
        when (post.visibility) {
            Visibility.PRIVATE -> "[Private] "
            Visibility.PASSWORD -> "[Password] "
            Visibility.DRAFT -> "[Draft] "
            else -> ""
        }

    private fun markdown(value: String): String =
        h(value).replace("\n\n", "</p><p>").replace("\n", "<br>").let { "<p>$it</p>" }

    private fun date(epoch: Long, ignoredPattern: String): String =
        DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC).format(Instant.ofEpochSecond(epoch))

    private fun checked(value: Boolean): String = if (value) "checked" else ""
    private fun h(value: Any?): String = value?.toString()?.replace("&", "&amp;")?.replace("<", "&lt;")?.replace(">", "&gt;")?.replace("\"", "&quot;") ?: ""
    private fun u(value: String): String = java.net.URLEncoder.encode(value, Charsets.UTF_8).replace("+", "%20")
}
