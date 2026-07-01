package com.github.senocak.analog.web

import com.github.senocak.analog.domain.AnalogConfig
import com.github.senocak.analog.domain.IndexFilter
import com.github.senocak.analog.domain.ListPostsQuery
import com.github.senocak.analog.domain.Post
import com.github.senocak.analog.domain.Tag
import com.github.senocak.analog.domain.User
import com.github.senocak.analog.domain.Visibility
import com.github.senocak.analog.repository.NavigationRepository
import com.github.senocak.analog.repository.PostRepository
import com.github.senocak.analog.repository.TagRepository
import com.github.senocak.analog.repository.UserRepository
import com.github.senocak.analog.service.BlogService
import com.github.senocak.analog.service.ConfigService
import com.github.senocak.analog.service.FileStorageService
import com.github.senocak.analog.service.SessionService
import com.github.senocak.analog.service.TemplateUtils
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpSession
import org.springframework.core.io.Resource
import org.springframework.http.CacheControl
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.server.ResponseStatusException
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

@Controller
class PublicController(
    blog: BlogService,
    private val config: ConfigService,
    private val posts: PostRepository,
    private val tags: TagRepository,
    private val users: UserRepository,
    private val navigations: NavigationRepository,
    private val sessions: SessionService,
    private val files: FileStorageService,
    private val utils: TemplateUtils,
) : BaseController(blog) {

    @ModelAttribute
    fun addCommonAttributes(model: Model, session: HttpSession) {
        config.current()?.let { model.addAttribute("config", it) }
        model.addAttribute("self", sessions.currentUser(session = session))
        model.addAttribute("utils", utils)
    }

    @GetMapping(value = ["/"])
    fun index(request: HttpServletRequest, session: HttpSession, model: Model,
        @RequestParam(required = false) page: Int?,
        @RequestParam(required = false, defaultValue = "") title: String,
    ): String {
        listPublicPosts(request = request, session = session, model = model, requestedPage = page, title = title)
        return "index"
    }

    @GetMapping(value = ["/tag/{tagSlug}", "/author/{authorId}", "/archive/{year}",
        "/archive/{year}/{month}", "/archive/{year}/{month}/{day}"])
    fun filteredIndex(request: HttpServletRequest, session: HttpSession, model: Model,
        @RequestParam(required = false) page: Int?,
        @RequestParam(required = false, defaultValue = "") title: String,
        @PathVariable(required = false) tagSlug: String?,
        @PathVariable(required = false) authorId: String?,
        @PathVariable(required = false) year: String?,
        @PathVariable(required = false) month: String?,
        @PathVariable(required = false) day: String?,
    ): String {
        listPublicPosts(request = request, session = session, model = model, requestedPage = page, title = title,
            tagSlug = tagSlug, authorId = authorId, year = year, month = month, day = day)
        return "index"
    }

    @GetMapping(value = ["/post/{slug}"])
    fun getPost(session: HttpSession, model: Model, @PathVariable slug: String): String {
        return postBySlug(slug = slug, session = session, model = model, password = null)
    }

    @PostMapping(value = ["/post/{slug}"])
    fun unlockPost(session: HttpSession, model: Model,
        @PathVariable slug: String,
        @RequestParam password: String?,
    ): String {
        return postBySlug(slug = slug, session = session, model = model, password = password)
    }

    @GetMapping("/rss.xml", produces = [MediaType.APPLICATION_XML_VALUE])
    @ResponseBody
    fun rss(request: HttpServletRequest): String {
        val cfg: AnalogConfig = config.current() ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        val items: String = posts.list(query =
            ListPostsQuery(
                isPublished = true,
                isTrashed = false,
                visibilities = listOf(Visibility.PUBLIC),
            ),
        ).joinToString(separator = "") { post: Post ->
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
        val root: String = rootUrl(request)
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:atom="http://www.w3.org/2005/Atom">
              <channel>
                <atom:link href="$root/rss.xml" rel="self" type="application/rss+xml"/>
                <title><![CDATA[${cfg.name}]]></title>
                <link>$root</link>
                <description><![CDATA[${cfg.description}]]></description>
                <language>${cfg.locale}</language>
                <pubDate>${rfc1123(epoch = Instant.now().epochSecond)}</pubDate>
                $items
              </channel>
            </rss>
        """.trimIndent()
    }

    @GetMapping(value = ["/sitemap.xml"], produces = [MediaType.APPLICATION_XML_VALUE])
    @ResponseBody
    fun sitemap(request: HttpServletRequest): String {
        val urls: String = posts.list(query =
            ListPostsQuery(
                isPublished = true,
                isTrashed = false,
                visibilities = listOf(Visibility.PUBLIC),
            ),
        ).joinToString(separator = "") { post: Post ->
            "<url><loc>${sitemapUrl(request = request, post = post)}</loc><lastmod>${ymd(epoch = post.updatedAt)}</lastmod></url>"
        }
        return """<?xml version="1.0" encoding="UTF-8"?><urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">$urls</urlset>"""
    }

    @GetMapping(value = ["/assets/{*asset}"])
    @ResponseBody
    fun themeAsset(@PathVariable asset: String): ResponseEntity<Resource> {
        val theme: String = config.current()?.theme ?: "default"
        return ResponseEntity.ok()
            .contentType(mediaType(path = asset))
            .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS))
            .body(files.resourceUnder(Path.of("data/themes/$theme/assets"), asset))
    }

    @GetMapping(value = ["/uploads/{*path}", "/post/uploads/{*path}", "/admin/uploads/{*path}", "/admin/post/uploads/{*path}"])
    @ResponseBody
    fun upload(@PathVariable path: String): ResponseEntity<Resource> =
        ResponseEntity
            .ok()
            .contentType(mediaType(path = path))
            .body(files.resourceUnder(Path.of("data/uploads"), path))

    private fun listPublicPosts(
        request: HttpServletRequest,
        session: HttpSession,
        model: Model,
        requestedPage: Int?,
        title: String,
        tagSlug: String? = null,
        authorId: String? = null,
        year: String? = null,
        month: String? = null,
        day: String? = null,
    ) {
        val cfg: AnalogConfig = config.current() ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        if (!cfg.isPublic && sessions.currentUser(session = session) == null) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Site is private")
        }
        val currentPage: Int = page(requestedPage = requestedPage)
        val query = ListPostsQuery(
            offset = (currentPage - 1) * cfg.postsPerPage,
            limit = cfg.postsPerPage,
            title = title,
            isPublished = true,
            isTrashed = false,
            visibilities = if (sessions.currentUser(session = session) == null) {
                listOf(Visibility.PUBLIC, Visibility.PASSWORD)
            } else {
                listOf(Visibility.PUBLIC, Visibility.PASSWORD, Visibility.PRIVATE)
            },
        )
        var filter = IndexFilter()
        tagSlug?.let { it: String ->
            val tag: Tag = tags.findBySlug(slug = it) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
            query.tagId = tag.id
            filter = filter.copy(tag = tag.name)
        }
        authorId?.let { it: String ->
            val user: User = users.findById(id = it) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
            query.authorId = user.id
            filter = filter.copy(author = user.nickname)
        }
        year?.let { it: String ->
            query.publishedYear = it
            filter = filter.copy(date = it)
        }
        month?.let { it: String ->
            query.publishedMonth = it
            filter = filter.copy(date = "${filter.date}/$it")
        }
        day?.let { it: String ->
            query.publishedDay = it
            filter = filter.copy(date = "${filter.date}/$it")
        }
        val total: Int = posts.count(query = query)
        val filterText: String = listOfNotNull(
            filter.tag.takeIf { it.isNotBlank() }?.let { "Tag: $it" },
            filter.author.takeIf { it.isNotBlank() }?.let { "Author: $it" },
            filter.date.takeIf { it.isNotBlank() }?.let { "Date: $it" },
        ).joinToString(separator = " ")
        model.addAttribute("posts", posts.list(query))
        model.addAttribute("pagination", pagination(request, currentPage, total, cfg.postsPerPage))
        model.addAttribute("navigations", navigations.list())
        model.addAttribute("filterText", filterText)
        // config already set by @ModelAttribute
    }

    private fun postBySlug(slug: String, session: HttpSession, model: Model, password: String?): String {
        val post: Post = posts.findBySlug(slug = slug) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        val self: User? = sessions.currentUser(session = session)
        if (self == null && post.visibility !in listOf(Visibility.PUBLIC, Visibility.PASSWORD)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND)
        }
        if (self == null && post.publishedAt > Instant.now().epochSecond) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND)
        }
        val unlocked: Boolean = self != null || post.visibility == Visibility.PUBLIC || password == post.password
        val message: String = if (!unlocked && password != null) "Incorrect password" else ""
        model.addAttribute("post", post)
        model.addAttribute("navigations", navigations.list())
        model.addAttribute("previous", posts.previous(id = post.id))
        model.addAttribute("next", posts.next(id = post.id))
        model.addAttribute("unlocked", unlocked)
        model.addAttribute("message", message)
        return "post"
    }

    private fun sitemapUrl(request: HttpServletRequest, post: Post): String = "${rootUrl(request)}/post/${post.slug}"

    private fun rfc1123(epoch: Long): String =
        DateTimeFormatter.RFC_1123_DATE_TIME.withZone(ZoneOffset.UTC).format(Instant.ofEpochSecond(epoch))

    private fun ymd(epoch: Long): String =
        DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC).format(Instant.ofEpochSecond(epoch))
}
