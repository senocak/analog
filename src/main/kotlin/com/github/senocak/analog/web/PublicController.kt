package com.github.senocak.analog.web

import com.github.senocak.analog.domain.IndexFilter
import com.github.senocak.analog.domain.ListPostsQuery
import com.github.senocak.analog.domain.Post
import com.github.senocak.analog.domain.Visibility
import com.github.senocak.analog.repository.NavigationRepository
import com.github.senocak.analog.repository.PostRepository
import com.github.senocak.analog.repository.TagRepository
import com.github.senocak.analog.repository.UserRepository
import com.github.senocak.analog.service.BlogService
import com.github.senocak.analog.service.ConfigService
import com.github.senocak.analog.service.FileStorageService
import com.github.senocak.analog.service.HtmlRenderer
import com.github.senocak.analog.service.SessionService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpSession
import org.springframework.core.io.Resource
import org.springframework.http.CacheControl
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

@RestController
class PublicController(
    blog: BlogService,
    private val config: ConfigService,
    private val posts: PostRepository,
    private val tags: TagRepository,
    private val users: UserRepository,
    private val navigations: NavigationRepository,
    private val sessions: SessionService,
    private val files: FileStorageService,
    private val html: HtmlRenderer,
) : BaseController(blog) {
    @GetMapping("/")
    fun index(
        request: HttpServletRequest,
        session: HttpSession,
        @RequestParam(required = false) page: Int?,
        @RequestParam(required = false, defaultValue = "") title: String,
    ): ResponseEntity<String> = listPublicPosts(request, session, page, title)

    @GetMapping("/tag/{tagSlug}", "/author/{authorId}", "/archive/{year}", "/archive/{year}/{month}", "/archive/{year}/{month}/{day}")
    fun filteredIndex(
        request: HttpServletRequest,
        session: HttpSession,
        @RequestParam(required = false) page: Int?,
        @RequestParam(required = false, defaultValue = "") title: String,
        @PathVariable(required = false) tagSlug: String?,
        @PathVariable(required = false) authorId: String?,
        @PathVariable(required = false) year: String?,
        @PathVariable(required = false) month: String?,
        @PathVariable(required = false) day: String?,
    ): ResponseEntity<String> = listPublicPosts(request, session, page, title, tagSlug, authorId, year, month, day)

    @GetMapping("/post/{slug}")
    fun post(@PathVariable slug: String, session: HttpSession): ResponseEntity<String> =
        postBySlug(slug, session, null)

    @PostMapping("/post/{slug}")
    fun unlockPost(@PathVariable slug: String, @RequestParam password: String?, session: HttpSession): ResponseEntity<String> =
        postBySlug(slug, session, password)

    @GetMapping("/rss.xml", produces = [MediaType.APPLICATION_XML_VALUE])
    fun rss(request: HttpServletRequest): String {
        val cfg = config.current() ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        val items = posts.list(
            ListPostsQuery(
                isPublished = true,
                isTrashed = false,
                visibilities = listOf(Visibility.PUBLIC),
            ),
        ).joinToString("") { post ->
            """
            <item>
              <guid>${sitemapUrl(request, post)}</guid>
              <title><![CDATA[${post.title}]]></title>
              <link>${sitemapUrl(request, post)}</link>
              <description><![CDATA[${post.content}]]></description>
              <pubDate>${rfc1123(post.updatedAt)}</pubDate>
            </item>
            """.trimIndent()
        }
        val root = rootUrl(request)
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:atom="http://www.w3.org/2005/Atom">
              <channel>
                <atom:link href="$root/rss.xml" rel="self" type="application/rss+xml"/>
                <title><![CDATA[${cfg.name}]]></title>
                <link>$root</link>
                <description><![CDATA[${cfg.description}]]></description>
                <language>${cfg.locale}</language>
                <pubDate>${rfc1123(Instant.now().epochSecond)}</pubDate>
                $items
              </channel>
            </rss>
        """.trimIndent()
    }

    @GetMapping("/sitemap.xml", produces = [MediaType.APPLICATION_XML_VALUE])
    fun sitemap(request: HttpServletRequest): String {
        val urls = posts.list(
            ListPostsQuery(
                isPublished = true,
                isTrashed = false,
                visibilities = listOf(Visibility.PUBLIC),
            ),
        ).joinToString("") { post ->
            "<url><loc>${sitemapUrl(request, post)}</loc><lastmod>${ymd(post.updatedAt)}</lastmod></url>"
        }
        return """<?xml version="1.0" encoding="UTF-8"?><urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">$urls</urlset>"""
    }

    @GetMapping("/assets/{*asset}")
    fun themeAsset(@PathVariable asset: String): ResponseEntity<Resource> {
        val theme = config.current()?.theme ?: "default"
        return ResponseEntity.ok()
            .contentType(mediaType(asset))
            .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS))
            .body(files.resourceUnder(Path.of("data/themes/$theme/assets"), asset))
    }

    @GetMapping("/uploads/{*path}", "/post/uploads/{*path}", "/admin/uploads/{*path}", "/admin/post/uploads/{*path}")
    fun upload(@PathVariable path: String): ResponseEntity<Resource> =
        ResponseEntity.ok().contentType(mediaType(path)).body(files.resourceUnder(Path.of("data/uploads"), path))

    private fun listPublicPosts(
        request: HttpServletRequest,
        session: HttpSession,
        requestedPage: Int?,
        title: String,
        tagSlug: String? = null,
        authorId: String? = null,
        year: String? = null,
        month: String? = null,
        day: String? = null,
    ): ResponseEntity<String> {
        val cfg = config.current() ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        if (!cfg.isPublic && sessions.currentUser(session) == null) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Site is private")
        }
        val currentPage = page(requestedPage)
        val query = ListPostsQuery(
            offset = (currentPage - 1) * cfg.postsPerPage,
            limit = cfg.postsPerPage,
            title = title,
            isPublished = true,
            isTrashed = false,
            visibilities = if (sessions.currentUser(session) == null) {
                listOf(Visibility.PUBLIC, Visibility.PASSWORD)
            } else {
                listOf(Visibility.PUBLIC, Visibility.PASSWORD, Visibility.PRIVATE)
            },
        )
        var filter = IndexFilter()
        tagSlug?.let {
            val tag = tags.findBySlug(it) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
            query.tagId = tag.id
            filter = filter.copy(tag = tag.name)
        }
        authorId?.let {
            val user = users.findById(it) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
            query.authorId = user.id
            filter = filter.copy(author = user.nickname)
        }
        year?.let {
            query.publishedYear = it
            filter = filter.copy(date = it)
        }
        month?.let {
            query.publishedMonth = it
            filter = filter.copy(date = "${filter.date}/$it")
        }
        day?.let {
            query.publishedDay = it
            filter = filter.copy(date = "${filter.date}/$it")
        }
        val total = posts.count(query)
        val filterText = listOfNotNull(
            filter.tag.takeIf { it.isNotBlank() }?.let { "Tag: $it" },
            filter.author.takeIf { it.isNotBlank() }?.let { "Author: $it" },
            filter.date.takeIf { it.isNotBlank() }?.let { "Date: $it" },
        ).joinToString(" ")
        return htmlResponse(
            html.index(
                config = cfg,
                posts = posts.list(query),
                pagination = pagination(request, currentPage, total, cfg.postsPerPage),
                navigations = navigations.list(),
                filterText = filterText,
            ),
        )
    }

    private fun postBySlug(slug: String, session: HttpSession, password: String?): ResponseEntity<String> {
        val post = posts.findBySlug(slug) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        val self = sessions.currentUser(session)
        if (self == null && post.visibility !in listOf(Visibility.PUBLIC, Visibility.PASSWORD)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND)
        }
        if (self == null && post.publishedAt > Instant.now().epochSecond) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND)
        }
        val unlocked = self != null || post.visibility == Visibility.PUBLIC || password == post.password
        val message = if (!unlocked && password != null) "Incorrect password" else ""
        return htmlResponse(
            html.post(
                config = config.current() ?: throw ResponseStatusException(HttpStatus.NOT_FOUND),
                post = post,
                navigations = navigations.list(),
                previous = posts.previous(post.id),
                next = posts.next(post.id),
                unlocked = unlocked,
                self = self,
                message = message,
            ),
        )
    }

    private fun htmlResponse(body: String): ResponseEntity<String> =
        ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(body)

    private fun sitemapUrl(request: HttpServletRequest, post: Post): String = "${rootUrl(request)}/post/${post.slug}"

    private fun rfc1123(epoch: Long): String =
        DateTimeFormatter.RFC_1123_DATE_TIME.withZone(ZoneOffset.UTC).format(Instant.ofEpochSecond(epoch))

    private fun ymd(epoch: Long): String =
        DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC).format(Instant.ofEpochSecond(epoch))
}
