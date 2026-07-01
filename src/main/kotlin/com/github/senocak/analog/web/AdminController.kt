package com.github.senocak.analog.web

import com.github.senocak.analog.domain.AnalogConfig
import com.github.senocak.analog.domain.ListPostsQuery
import com.github.senocak.analog.domain.Navigation
import com.github.senocak.analog.domain.Pagination
import com.github.senocak.analog.domain.Post
import com.github.senocak.analog.domain.PostWrite
import com.github.senocak.analog.domain.Tag
import com.github.senocak.analog.domain.UnauthorizedException
import com.github.senocak.analog.domain.User
import com.github.senocak.analog.domain.Visibility
import com.github.senocak.analog.domain.locales
import com.github.senocak.analog.domain.timezones
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
import jakarta.validation.Valid
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.Resource
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import java.util.stream.Stream
import kotlin.io.path.name
import kotlin.math.ceil

@Controller
@RequestMapping(value = ["/admin"])
class AdminController(
    blog: BlogService,
    private val config: ConfigService,
    private val sessions: SessionService,
    private val users: UserRepository,
    private val posts: PostRepository,
    private val tags: TagRepository,
    private val navigations: NavigationRepository,
    private val files: FileStorageService,
    private val utils: TemplateUtils,
    private val encoder: BCryptPasswordEncoder,
    @param:Value(value = "\${analog.version}") private val version: String,
) : BaseController(blog) {

    @ModelAttribute
    fun addCommonAttributes(model: Model, session: HttpSession) {
        config.current()?.let { model.addAttribute("config", it) }
        model.addAttribute("self", sessions.currentUser(session = session))
        model.addAttribute("utils", utils)
    }

    @GetMapping(value = ["", "/"])
    fun adminRoot(): String = "redirect:/admin/posts"

    @GetMapping(value = ["/users"])
    fun users(session: HttpSession, model: Model): String {
        val self: User = requireLoggedIn(session = session)
        model.addAttribute("active", "user")
        model.addAttribute("users", users.list())
        model.addAttribute("message", sessions.message(session))
        return "admin_users"
    }

    @PostMapping(value = ["/users"], consumes = ["application/x-www-form-urlencoded"])
    fun createUserForm(@Valid @ModelAttribute request: UserCreateRequest, session: HttpSession): String {
        createUser(request = request, session = session)
        return "redirect:/admin/users"
    }

    @PostMapping(value = ["/api/users"])
    @ResponseBody
    fun createUserJson(@Valid @RequestBody request: UserCreateRequest, session: HttpSession): Map<String, Any> {
        createUser(request = request, session = session)
        return mapOf("ok" to true)
    }

    @GetMapping(value = ["/user/{id}"])
    fun user(@PathVariable id: String, session: HttpSession, model: Model): String {
        val self: User = requireLoggedIn(session = session)
        val user: User = users.findById(id = id) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        val allUsers: List<User> = users.list()
        model.addAttribute("active", "user")
        model.addAttribute("self", self)
        model.addAttribute("user", user)
        model.addAttribute("users", allUsers)
        model.addAttribute("postCount", posts.countByUser(userId = id))
        model.addAttribute("message", sessions.message(session = session))
        return "admin_user_edit"
    }

    @PostMapping(value = ["/user/{id}"], consumes = ["application/x-www-form-urlencoded"])
    fun updateUserForm(@PathVariable id: String, @Valid @ModelAttribute request: UserEditRequest, session: HttpSession): String {
        requireLoggedIn(session = session)
        users.findById(id = id) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        if (request.password.isNotBlank())
            users.updatePassword(id = id, password = encoder.encode(request.password) ?: throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR))
        users.update(id = id, nickname = request.nickname.trim(), bio = request.bio.trim(), email = request.email.trim())
        sessions.setMessage(session = session, value = "notice_user_updated")
        return "redirect:/admin/users"
    }

    //@Transactional
    @PostMapping(value = ["/user/{id}/delete"], consumes = ["application/x-www-form-urlencoded"])
    fun deleteUserForm(@PathVariable id: String, @ModelAttribute request: UserDeleteRequest, session: HttpSession): String {
        requireLoggedIn(session = session)
        if (request.transferToId.isNotBlank()) {
            users.findById(id = request.transferToId) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
            posts.transferPosts(fromUserId = id, toUserId = request.transferToId)
            sessions.setMessage(session = session, value = "notice_user_deletedwithposts")
        } else {
            posts.deleteByUser(userId = id)
            sessions.setMessage(session = session, value = "notice_user_deleted")
        }
        users.delete(id = id)
        return "redirect:/admin/users"
    }

    @GetMapping(value = ["/tags"])
    fun listTags(request: HttpServletRequest, session: HttpSession, model: Model,
        @RequestParam(required = false) page: Int?,
        @RequestParam(required = false, defaultValue = "") keyword: String,
    ): String {
        val self: User = requireLoggedIn(session = session)
        val currentPage: Int = page(requestedPage = page)
        val perPage = 50
        val total: Int = tags.count(keyword = keyword)
        model.addAttribute("active", "tag")
        model.addAttribute("self", self)
        model.addAttribute("tags", tags.list(offset = (currentPage - 1) * perPage, limit = perPage, keyword = keyword))
        model.addAttribute("pagination", pagination(request = request, page = currentPage, total = total, perPage = perPage))
        model.addAttribute("keyword", keyword)
        model.addAttribute("message", sessions.message(session = session))
        return "admin_tags"
    }

    @PostMapping("/tags", consumes = ["application/x-www-form-urlencoded"])
    fun createTagForm(@Valid @ModelAttribute request: TagCreateRequest, session: HttpSession): String {
        requireLoggedIn(session = session)
        tags.create(tag =
            Tag(
                id = UUID.randomUUID().toString(),
                slug = blog.toSlug(value = request.name),
                name = request.name.replace(oldValue = ",", newValue = "").trim(),
                description = request.description.trim(),
                createdAt = Instant.now().epochSecond,
            ),
        )
        sessions.setMessage(session = session, value = "notice_tag_created")
        return "redirect:/admin/tags"
    }

    @GetMapping("/tag/{id}")
    fun tag(@PathVariable id: String, session: HttpSession, model: Model): String {
        val self: User = requireLoggedIn(session = session)
        val tag: Tag = tags.findById(id = id) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        model.addAttribute("active", "tag")
        model.addAttribute("self", self)
        model.addAttribute("tag", tag)
        model.addAttribute("message", sessions.message(session = session))
        return "admin_tag_edit"
    }

    @PostMapping("/tag/{id}", consumes = ["application/x-www-form-urlencoded"])
    fun updateTagForm(@PathVariable id: String, @Valid @ModelAttribute request: TagEditRequest, session: HttpSession): String {
        requireLoggedIn(session = session)
        val existing: Tag = tags.findById(id = id) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        tags.update(tag =
            existing.copy(
                slug = blog.toSlug(value = request.slug),
                name = request.name.replace(oldValue = ",", newValue = "").trim(),
                description = request.description.trim(),
            ),
        )
        sessions.setMessage(session = session, value = "notice_tag_updated")
        return "redirect:/admin/tags"
    }

    @PostMapping(value = ["/tag/{id}/delete"])
    fun deleteTagForm(@PathVariable id: String, session: HttpSession): String {
        requireLoggedIn(session = session)
        tags.delete(id = id)
        sessions.setMessage(session = session, value = "notice_tag_deleted")
        return "redirect:/admin/tags"
    }

    @GetMapping(value = ["/navigations"])
    fun listNavigations(session: HttpSession, model: Model): String {
        val self: User = requireLoggedIn(session = session)
        model.addAttribute("active", "navigation")
        model.addAttribute("self", self)
        model.addAttribute("navigations", navigations.list())
        model.addAttribute("message", sessions.message(session))
        return "admin_navigations"
    }

    @PostMapping(value = ["/navigations"], consumes = ["application/x-www-form-urlencoded"])
    fun createNavigationForm(@Valid @ModelAttribute request: NavigationCreateRequest, session: HttpSession): String {
        requireLoggedIn(session = session)
        val existing: List<Navigation> = navigations.list()
        navigations.clear()
        existing.forEachIndexed { index: Int, nav: Navigation -> navigations.create(navigation = nav.copy(sequence = index + 1)) }
        navigations.create(Navigation(UUID.randomUUID().toString(), request.url.trim(), request.name.trim(), existing.size + 1))
        sessions.setMessage(session, "notice_nagivation_created")
        return "redirect:/admin/navigations"
    }

    @PostMapping(value = ["/navigations/edit"], consumes = ["application/x-www-form-urlencoded"])
    fun editNavigationsForm(@ModelAttribute request: NavigationEditRequest, session: HttpSession): String {
        requireLoggedIn(session = session)
        val items: List<Navigation> = request.name.indices.mapNotNull { index: Int ->
            val deleted: Boolean = request.isDeleted.getOrNull(index) ?: false
            if (deleted) null else {
                Navigation(
                    id = UUID.randomUUID().toString(),
                    name = request.name[index].trim(),
                    url = request.url.getOrElse(index) { "" }.trim(),
                    sequence = request.sequence.getOrElse(index) { index + 1 },
                )
            }
        }.sortedBy { it.sequence }
        navigations.clear()
        items.forEachIndexed { index: Int, nav: Navigation -> navigations.create(navigation = nav.copy(sequence = index + 1)) }
        sessions.setMessage(session = session, value = "notice_nagivation_updated")
        return "redirect:/admin/navigations"
    }

    @GetMapping(value = ["/settings"])
    fun settings(session: HttpSession, model: Model): String {
        val self: User = requireLoggedIn(session = session)
        model.addAttribute("active", "settings")
        model.addAttribute("self", self)
        model.addAttribute("timezones", timezones)
        model.addAttribute("locales", locales)
        model.addAttribute("version", version)
        model.addAttribute("runtime", System.getProperty("java.version"))
        model.addAttribute("message", sessions.message(session))
        return "admin_settings"
    }

    @PostMapping(value = ["/settings"], consumes = ["application/x-www-form-urlencoded"])
    fun updateSettingsForm(@Valid @ModelAttribute request: SettingsEditRequest, session: HttpSession): String {
        requireLoggedIn(session = session)
        val cfg: AnalogConfig = config.current() ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        cfg.name = request.name.trim()
        cfg.description = request.description.trim()
        cfg.isPublic = request.isPublic
        cfg.timezone = request.timezone
        cfg.locale = request.locale
        cfg.dateFormat = if (request.dateFormat == "custom") request.dateFormatCustom.trim() else request.dateFormat
        cfg.timeFormat = if (request.timeFormat == "custom") request.timeFormatCustom.trim() else request.timeFormat
        config.save()
        sessions.setMessage(session = session, value = "notice_settings_updated")
        return "redirect:/admin/settings"
    }

    @GetMapping(value = ["/appearances"])
    fun appearances(session: HttpSession, model: Model): String {
        val self: User = requireLoggedIn(session = session)
        model.addAttribute("active", "appearances")
        model.addAttribute("self", self)
        model.addAttribute("themes", config.themes())
        model.addAttribute("message", sessions.message(session))
        return "admin_appearances"
    }

    @PostMapping(value = ["/appearances"], consumes = ["application/x-www-form-urlencoded"])
    fun updateAppearancesForm(@Valid @ModelAttribute request: AppearancesEditRequest, session: HttpSession): String {
        requireLoggedIn(session = session)
        if (!config.themeExists(theme = request.theme))
            throw ResponseStatusException(HttpStatus.BAD_REQUEST)
        val cfg: AnalogConfig = config.current() ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        cfg.footerText = request.footerText.trim()
        cfg.colorScheme = request.colorScheme
        cfg.containerWidth = request.containerWidth
        cfg.fontFamily = request.fontFamily
        cfg.fontSize = request.fontSize
        cfg.highlightJS = request.highlightJS
        cfg.authorBlock = request.authorBlock
        cfg.postsPerPage = request.postsPerPage
        cfg.theme = request.theme
        config.save()
        sessions.setMessage(session = session, value = "notice_appearances_updated")
        return "redirect:/admin/appearances"
    }

    @PostMapping(value = ["/appearances/injected"], consumes = ["application/x-www-form-urlencoded"])
    fun updateInjectedForm(@ModelAttribute request: AppearancesInjectedRequest, session: HttpSession): String {
        requireLoggedIn(session = session)
        val cfg: AnalogConfig = config.current() ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        cfg.injectedHead = request.injectedHead.trim()
        cfg.injectedFoot = request.injectedFoot.trim()
        cfg.injectedPostStart = request.injectedPostStart.trim()
        cfg.injectedPostEnd = request.injectedPostEnd.trim()
        config.save()
        sessions.setMessage(session = session, value = "notice_injected_updated")
        return "redirect:/admin/appearances"
    }

    @GetMapping(value = ["/posts"])
    fun listPosts(servletRequest: HttpServletRequest, session: HttpSession, model: Model,
        @RequestParam(required = false) page: Int?,
        @RequestParam(required = false, defaultValue = "") title: String,
        @RequestParam(required = false, defaultValue = "") authorId: String,
        @RequestParam(required = false, defaultValue = "") visibility: String,
        @RequestParam(required = false, defaultValue = "") publishedDate: String,
    ): String {
        val self: User = requireLoggedIn(session = session)
        val currentPage: Int = page(page)
        val perPage = 30
        val query = ListPostsQuery(
            offset = (currentPage - 1) * perPage,
            limit = perPage,
            title = title,
            authorId = authorId,
            visibilities = listOf(Visibility.PUBLIC, Visibility.PASSWORD, Visibility.PRIVATE, Visibility.DRAFT),
            isTrashed = false,
            publishedDate = publishedDate,
        )
        val currentVisibility: Visibility = Visibility.from(value = visibility)
        if (currentVisibility != Visibility.UNKNOWN && currentVisibility != Visibility.TRASH) {
            query.visibilities = listOf(currentVisibility)
            query.isTrashed = false
        }
        if (currentVisibility == Visibility.TRASH) {
            query.isTrashed = true
        }
        val total: Int = posts.count(query = query)
        model.addAttribute("active", "post")
        model.addAttribute("self", self)
        model.addAttribute("posts", posts.list(query))
        model.addAttribute("counts", posts.countByType())
        model.addAttribute("pagination", pagination(servletRequest, currentPage, total, perPage))
        model.addAttribute("currentVisibility", visibility)
        model.addAttribute("message", sessions.message(session))
        return "admin_posts"
    }

    @GetMapping(value = ["/post/create"])
    fun createPostModel(session: HttpSession, model: Model): String {
        val self: User = requireLoggedIn(session = session)
        model.addAttribute("active", "post")
        model.addAttribute("self", self)
        model.addAttribute("post", null)
        model.addAttribute("users", users.list())
        model.addAttribute("allTags", tags.list(0, 999, ""))
        model.addAttribute("message", sessions.message(session))
        return "admin_post_form"
    }

    @PostMapping(value = ["/post/create"], consumes = ["multipart/form-data"])
    fun createPostForm(
        @Valid @ModelAttribute request: PostCreateRequest,
        @RequestParam(value = "cover_file", required = false) coverFile: MultipartFile?,
        session: HttpSession,
    ): String {
        requireLoggedIn(session = session)
        users.findById(id = request.authorId) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        val id: String = UUID.randomUUID().toString()
        files.saveCover(file = coverFile, postId = id)
        val now: Long = Instant.now().epochSecond
        val publishedAt: Long = if (request.publishedAt == 0L) now else request.publishedAt
        posts.create(post =
            PostWrite(
                id = id,
                title = request.title.trim(),
                slug = blog.toSlug(value = request.slug),
                excerpt = request.excerpt.trim(),
                authorId = request.authorId,
                password = if (request.visibility == Visibility.PASSWORD) request.password.trim() else "",
                visibility = request.visibility,
                content = request.content.trim(),
                pinnedAt = if (request.isPinned) now else 0,
                publishedAt = publishedAt,
                createdAt = now,
                updatedAt = now,
                tagIds = blog.createTags(tagNames = request.tags),
            ),
        )
        sessions.setMessage(session = session, value = "notice_post_created")
        return "redirect:/admin/post/$id"
    }

    @GetMapping(value = ["/post/{id}"])
    fun post(@PathVariable id: String, session: HttpSession, model: Model): String {
        val self: User = requireLoggedIn(session = session)
        val post: Post = posts.findById(id = id) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        model.addAttribute("active", "post")
        model.addAttribute("self", self)
        model.addAttribute("post", post)
        model.addAttribute("users", users.list())
        model.addAttribute("allTags", tags.list(0, 999, ""))
        model.addAttribute("message", sessions.message(session))
        return "admin_post_form"
    }

    @PostMapping(value = ["/post/{id}"], consumes = ["multipart/form-data"])
    fun updatePostForm(session: HttpSession,
        @PathVariable id: String,
        @Valid @ModelAttribute request: PostEditRequest,
        @RequestParam(value = "cover_file", required = false) coverFile: MultipartFile?
    ): String {
        requireLoggedIn(session = session)
        users.findById(id = request.authorId) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        val existing: Post = posts.findById(id = id) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        if (request.isClearCover) {
            Files.deleteIfExists(Path.of("data/uploads/covers/$id.jpg"))
        } else {
            files.saveCover(coverFile, id)
        }
        val now: Long = Instant.now().epochSecond
        val password: String = when {
            request.visibility != Visibility.PASSWORD -> ""
            request.password.isBlank() -> existing.password
            else -> request.password.trim()
        }
        posts.update(post =
            PostWrite(
                id = id,
                title = request.title.trim(),
                slug = blog.toSlug(value = request.slug),
                excerpt = request.excerpt.trim(),
                authorId = request.authorId,
                password = password,
                visibility = request.visibility,
                content = request.content.trim(),
                pinnedAt = if (request.isPinned) now else 0,
                publishedAt = request.publishedAt,
                createdAt = existing.createdAt,
                updatedAt = now,
                tagIds = blog.createTags(tagNames = request.tags),
            ),
        )
        sessions.setMessage(session = session, value = "notice_post_updated")
        return "redirect:/admin/post/$id"
    }

    @PostMapping(value = ["/post/{id}/trash"])
    fun trashPost(@PathVariable id: String, session: HttpSession): String {
        requireLoggedIn(session = session)
        posts.trash(id = id)
        sessions.setMessage(session = session, value = "notice_post_trashed")
        return "redirect:/admin/posts"
    }

    @PostMapping(value = ["/post/{id}/untrash"])
    fun untrashPost(@PathVariable id: String, session: HttpSession): String {
        requireLoggedIn(session = session)
        posts.untrash(id = id)
        sessions.setMessage(session = session, value = "notice_post_untrashed")
        return "redirect:/admin/posts"
    }

    @PostMapping(value = ["/post/{id}/delete"])
    fun deletePost(@PathVariable id: String, session: HttpSession): String {
        requireLoggedIn(session = session)
        posts.delete(id = id)
        sessions.setMessage(session = session, value = "notice_post_deleted")
        return "redirect:/admin/posts"
    }

    @PostMapping(value = ["/trashes/clear"])
    fun clearTrash(session: HttpSession): String {
        requireLoggedIn(session = session)
        posts.clearTrashPosts()
        sessions.setMessage(session = session, value = "notice_post_clear")
        return "redirect:/admin/posts"
    }

    @GetMapping(value = ["/photos"])
    fun photos(session: HttpSession, model: Model, @RequestParam(required = false) page: Int?): String {
        val self: User = requireLoggedIn(session = session)
        val currentPage: Int = page(requestedPage = page)
        val perPage = 100
        val allFiles: List<PhotoFile> = imageFiles()
        val paged: List<PhotoFile> = allFiles.drop(n = (currentPage - 1) * perPage).take(n = perPage)
        val groups: List<Map<String, Any>> = paged.groupBy { it.year to it.month }.map { (key: Pair<String, String>, files: List<PhotoFile>) ->
            mapOf("year" to key.first, "month" to key.second, "filenames" to files.map { it.filename })
        }
        model.addAttribute("active", "media")
        model.addAttribute("self", self)
        model.addAttribute("groups", groups)
        model.addAttribute("pagination", Pagination(currentPage = currentPage, totalCount = allFiles.size,
            totalPages = ceil(x = allFiles.size / perPage.toDouble()).toInt(), query = ""))
        model.addAttribute("message", sessions.message(session = session))
        return "admin_photos"
    }

    @PostMapping(value = ["/photos/api"])
    @ResponseBody
    fun photoApi(@RequestParam(value = "photo_file") file: MultipartFile, session: HttpSession): PhotoCreateResponse {
        requireLoggedIn(session = session)
        return PhotoCreateResponse(path = files.savePhoto(file = file))
    }

    @PostMapping(value = ["/photos"], consumes = ["multipart/form-data"])
    fun uploadPhotos(@RequestParam(value = "photo_file") uploaded: List<MultipartFile>, session: HttpSession): String {
        requireLoggedIn(session = session)
        uploaded.forEach { files.savePhoto(file = it) }
        sessions.setMessage(session = session, value = "notice_photo_uploaded")
        return "redirect:/admin/photos"
    }

    @PostMapping(value = ["/photo/delete"], consumes = ["application/x-www-form-urlencoded"])
    fun deletePhoto(@ModelAttribute request: PhotoDeleteRequest, session: HttpSession): String {
        requireLoggedIn(session = session)
        files.deleteImage(relativePath = request.path)
        sessions.setMessage(session = session, value = "notice_photo_deleted")
        return "redirect:/admin/photos"
    }

    private fun requireLoggedIn(session: HttpSession): User =
        sessions.currentUser(session = session) ?: throw UnauthorizedException(message = "UnauthorizedException")

    private fun requireConfig(): AnalogConfig =
        config.current() ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)

    private fun createUser(request: UserCreateRequest, session: HttpSession) {
        requireLoggedIn(session = session)
        var nickname: String = request.email.substringBefore(delimiter = "@").trim()
        if (users.nicknameExists(nickname))
            nickname = "$nickname-${Instant.now().epochSecond}"
        users.create(user =
            User(
                id = UUID.randomUUID().toString(),
                email = request.email.trim(),
                password = encoder.encode(request.password) ?: throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR),
                nickname = nickname,
                bio = "",
                createdAt = Instant.now().epochSecond,
            ),
        )
        sessions.setMessage(session = session, value = "notice_user_created")
    }

    private data class PhotoFile(val year: String, val month: String, val filename: String)

    private fun imageFiles(): List<PhotoFile> {
        val base: Path = Path.of("data/uploads/images")
        if (!Files.exists(base))
            return emptyList()
        val result: MutableList<PhotoFile> = mutableListOf()
        Files.walk(base).use { stream: Stream<Path> ->
            stream.filter { Files.isRegularFile(it) }.forEach { it: Path? ->
                val relative: Path = base.relativize(it)
                val parts: List<String> = relative.map { segment: Path -> segment.name }.toList()
                if (parts.size >= 3) {
                    result += PhotoFile(parts[0], parts[1], parts.last())
                }
            }
        }
        return result.sortedByDescending { it.filename }
    }
}
