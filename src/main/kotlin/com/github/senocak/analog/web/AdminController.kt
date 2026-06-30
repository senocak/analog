package com.github.senocak.analog.web

import com.github.senocak.analog.domain.ListPostsQuery
import com.github.senocak.analog.domain.Navigation
import com.github.senocak.analog.domain.Pagination
import com.github.senocak.analog.domain.PostWrite
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
import com.github.senocak.analog.service.HtmlRenderer
import com.github.senocak.analog.service.SessionService
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
import kotlin.io.path.name
import kotlin.math.ceil

@Controller
@RequestMapping("/admin")
class AdminController(
    blog: BlogService,
    private val config: ConfigService,
    private val sessions: SessionService,
    private val users: UserRepository,
    private val posts: PostRepository,
    private val tags: TagRepository,
    private val navigations: NavigationRepository,
    private val files: FileStorageService,
    private val html: HtmlRenderer,
    private val encoder: BCryptPasswordEncoder,
    @param:Value("\${analog.version}") private val version: String,
) : BaseController(blog) {

    @GetMapping("", "/")
    fun adminRoot(): String = "redirect:/admin/posts"

    @GetMapping("/users")
    fun users(session: HttpSession): ResponseEntity<String> {
        val self = requireLoggedIn(session)
        return htmlResponse(html.adminUsers(requireConfig(), self, sessions.message(session), users.list()))
    }

    @PostMapping("/users", consumes = ["application/x-www-form-urlencoded"])
    fun createUserForm(@Valid @ModelAttribute request: UserCreateRequest, session: HttpSession): String {
        createUser(request, session)
        return "redirect:/admin/users"
    }

    @PostMapping("/api/users")
    @ResponseBody
    fun createUserJson(@Valid @RequestBody request: UserCreateRequest, session: HttpSession): Map<String, Any> {
        createUser(request, session)
        return mapOf("ok" to true)
    }

    @GetMapping("/user/{id}")
    fun user(@PathVariable id: String, session: HttpSession): ResponseEntity<String> {
        val self = requireLoggedIn(session)
        val user = users.findById(id) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        val allUsers = users.list()
        return htmlResponse(html.adminUserEdit(requireConfig(), self, sessions.message(session), user, allUsers, posts.countByUser(id)))
    }

    @PostMapping("/user/{id}", consumes = ["application/x-www-form-urlencoded"])
    fun updateUserForm(@PathVariable id: String, @Valid @ModelAttribute request: UserEditRequest, session: HttpSession): String {
        updateUser(id, request, session)
        return "redirect:/admin/users"
    }

    @PostMapping("/user/{id}/delete", consumes = ["application/x-www-form-urlencoded"])
    fun deleteUserForm(@PathVariable id: String, @ModelAttribute request: UserDeleteRequest, session: HttpSession): String {
        deleteUser(id, request, session)
        return "redirect:/admin/users"
    }

    @GetMapping("/tags")
    fun listTags(
        request: HttpServletRequest,
        session: HttpSession,
        @RequestParam(required = false) page: Int?,
        @RequestParam(required = false, defaultValue = "") keyword: String,
    ): ResponseEntity<String> {
        val self = requireLoggedIn(session)
        val currentPage = page(page)
        val perPage = 50
        val total = tags.count(keyword)
        return htmlResponse(
            html.adminTags(
                requireConfig(),
                self,
                sessions.message(session),
                tags.list((currentPage - 1) * perPage, perPage, keyword),
                pagination(request, currentPage, total, perPage),
                keyword,
            ),
        )
    }

    @PostMapping("/tags", consumes = ["application/x-www-form-urlencoded"])
    fun createTagForm(@Valid @ModelAttribute request: TagCreateRequest, session: HttpSession): String {
        createTag(request, session)
        return "redirect:/admin/tags"
    }

    @GetMapping("/tag/{id}")
    fun tag(@PathVariable id: String, session: HttpSession): ResponseEntity<String> {
        val self = requireLoggedIn(session)
        val tag = tags.findById(id) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        return htmlResponse(html.adminTagEdit(requireConfig(), self, sessions.message(session), tag))
    }

    @PostMapping("/tag/{id}", consumes = ["application/x-www-form-urlencoded"])
    fun updateTagForm(@PathVariable id: String, @Valid @ModelAttribute request: TagEditRequest, session: HttpSession): String {
        updateTag(id, request, session)
        return "redirect:/admin/tags"
    }

    @PostMapping("/tag/{id}/delete")
    fun deleteTagForm(@PathVariable id: String, session: HttpSession): String {
        requireLoggedIn(session)
        tags.delete(id)
        sessions.setMessage(session, "notice_tag_deleted")
        return "redirect:/admin/tags"
    }

    @GetMapping("/navigations")
    fun listNavigations(session: HttpSession): ResponseEntity<String> {
        val self = requireLoggedIn(session)
        return htmlResponse(html.adminNavigations(requireConfig(), self, sessions.message(session), navigations.list()))
    }

    @PostMapping("/navigations", consumes = ["application/x-www-form-urlencoded"])
    fun createNavigationForm(@Valid @ModelAttribute request: NavigationCreateRequest, session: HttpSession): String {
        createNavigation(request, session)
        return "redirect:/admin/navigations"
    }

    @PostMapping("/navigations/edit", consumes = ["application/x-www-form-urlencoded"])
    fun editNavigationsForm(@ModelAttribute request: NavigationEditRequest, session: HttpSession): String {
        editNavigations(request, session)
        return "redirect:/admin/navigations"
    }

    @GetMapping("/settings")
    fun settings(session: HttpSession): ResponseEntity<String> {
        val self = requireLoggedIn(session)
        return htmlResponse(html.adminSettings(requireConfig(), self, sessions.message(session), version, System.getProperty("java.version")))
    }

    @PostMapping("/settings", consumes = ["application/x-www-form-urlencoded"])
    fun updateSettingsForm(@Valid @ModelAttribute request: SettingsEditRequest, session: HttpSession): String {
        updateSettings(request, session)
        return "redirect:/admin/settings"
    }

    @GetMapping("/appearances")
    fun appearances(session: HttpSession): ResponseEntity<String> {
        val self = requireLoggedIn(session)
        return htmlResponse(html.adminAppearances(requireConfig(), self, sessions.message(session), config.themes()))
    }

    @PostMapping("/appearances", consumes = ["application/x-www-form-urlencoded"])
    fun updateAppearancesForm(@Valid @ModelAttribute request: AppearancesEditRequest, session: HttpSession): String {
        updateAppearances(request, session)
        return "redirect:/admin/appearances"
    }

    @PostMapping("/appearances/injected", consumes = ["application/x-www-form-urlencoded"])
    fun updateInjectedForm(@ModelAttribute request: AppearancesInjectedRequest, session: HttpSession): String {
        updateInjected(request, session)
        return "redirect:/admin/appearances"
    }

    @GetMapping("/posts")
    fun listPosts(
        servletRequest: HttpServletRequest,
        session: HttpSession,
        @RequestParam(required = false) page: Int?,
        @RequestParam(required = false, defaultValue = "") title: String,
        @RequestParam(required = false, defaultValue = "") authorId: String,
        @RequestParam(required = false, defaultValue = "") visibility: String,
        @RequestParam(required = false, defaultValue = "") publishedDate: String,
    ): ResponseEntity<String> {
        val self = requireLoggedIn(session)
        val currentPage = page(page)
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
        val currentVisibility = Visibility.from(visibility)
        if (currentVisibility != Visibility.UNKNOWN && currentVisibility != Visibility.TRASH) {
            query.visibilities = listOf(currentVisibility)
            query.isTrashed = false
        }
        if (currentVisibility == Visibility.TRASH) {
            query.isTrashed = true
        }
        val total = posts.count(query)
        return htmlResponse(
            html.adminPosts(
                requireConfig(),
                self,
                sessions.message(session),
                posts.list(query),
                posts.countByType(),
                pagination(servletRequest, currentPage, total, perPage),
            ),
        )
    }

    @GetMapping("/post/create")
    fun createPostModel(session: HttpSession): ResponseEntity<String> {
        val self = requireLoggedIn(session)
        return htmlResponse(html.adminPostForm(requireConfig(), self, sessions.message(session), users.list(), tags.list(0, 999, "")))
    }

    @PostMapping("/post/create", consumes = ["multipart/form-data"])
    fun createPostForm(
        @Valid @ModelAttribute request: PostCreateRequest,
        @RequestParam("cover_file", required = false) coverFile: MultipartFile?,
        session: HttpSession,
    ): String {
        val id = createPost(request, coverFile, session)
        return "redirect:/admin/post/$id"
    }

    @GetMapping("/post/{id}")
    fun post(@PathVariable id: String, session: HttpSession): ResponseEntity<String> {
        val self = requireLoggedIn(session)
        val post = posts.findById(id) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        return htmlResponse(html.adminPostForm(requireConfig(), self, sessions.message(session), users.list(), tags.list(0, 999, ""), post))
    }

    @PostMapping("/post/{id}", consumes = ["multipart/form-data"])
    fun updatePostForm(
        @PathVariable id: String,
        @Valid @ModelAttribute request: PostEditRequest,
        @RequestParam("cover_file", required = false) coverFile: MultipartFile?,
        session: HttpSession,
    ): String {
        updatePost(id, request, coverFile, session)
        return "redirect:/admin/post/$id"
    }

    @PostMapping("/post/{id}/trash")
    fun trashPost(@PathVariable id: String, session: HttpSession): String {
        requireLoggedIn(session)
        posts.trash(id)
        sessions.setMessage(session, "notice_post_trashed")
        return "redirect:/admin/posts"
    }

    @PostMapping("/post/{id}/untrash")
    fun untrashPost(@PathVariable id: String, session: HttpSession): String {
        requireLoggedIn(session)
        posts.untrash(id)
        sessions.setMessage(session, "notice_post_untrashed")
        return "redirect:/admin/posts"
    }

    @PostMapping("/post/{id}/delete")
    fun deletePost(@PathVariable id: String, session: HttpSession): String {
        requireLoggedIn(session)
        posts.delete(id)
        sessions.setMessage(session, "notice_post_deleted")
        return "redirect:/admin/posts"
    }

    @PostMapping("/trashes/clear")
    fun clearTrash(session: HttpSession): String {
        requireLoggedIn(session)
        posts.clearTrashPosts()
        sessions.setMessage(session, "notice_post_clear")
        return "redirect:/admin/posts"
    }

    @GetMapping("/photos")
    fun photos(session: HttpSession, @RequestParam(required = false) page: Int?): ResponseEntity<String> {
        val self = requireLoggedIn(session)
        val currentPage = page(page)
        val perPage = 100
        val allFiles = imageFiles()
        val paged = allFiles.drop((currentPage - 1) * perPage).take(perPage)
        val groups = paged.groupBy { it.year to it.month }.map { (key, files) ->
                mapOf("year" to key.first, "month" to key.second, "filenames" to files.map { it.filename })
            }
        return htmlResponse(
            html.adminPhotos(
                requireConfig(),
                self,
                sessions.message(session),
                groups,
                Pagination(currentPage, allFiles.size, ceil(allFiles.size / perPage.toDouble()).toInt(), ""),
            ),
        )
    }

    @GetMapping("/assets/{*asset}")
    fun adminAsset(@PathVariable asset: String): ResponseEntity<Resource> =
        ResponseEntity.ok().contentType(mediaType(asset)).body(files.resourceUnder(Path.of("view/assets"), asset))

    @PostMapping("/photos/api")
    @ResponseBody
    fun photoApi(@RequestParam("photo_file") file: MultipartFile, session: HttpSession): PhotoCreateResponse {
        requireLoggedIn(session)
        return PhotoCreateResponse(files.savePhoto(file))
    }

    @PostMapping("/photos", consumes = ["multipart/form-data"])
    fun uploadPhotos(@RequestParam("photo_file") uploaded: List<MultipartFile>, session: HttpSession): String {
        requireLoggedIn(session)
        uploaded.forEach { files.savePhoto(it) }
        sessions.setMessage(session, "notice_photo_uploaded")
        return "redirect:/admin/photos"
    }

    @PostMapping("/photo/delete", consumes = ["application/x-www-form-urlencoded"])
    fun deletePhoto(@ModelAttribute request: PhotoDeleteRequest, session: HttpSession): String {
        requireLoggedIn(session)
        files.deleteImage(request.path)
        sessions.setMessage(session, "notice_photo_deleted")
        return "redirect:/admin/photos"
    }

    private fun requireLoggedIn(session: HttpSession): User =
        sessions.currentUser(session) ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED)

    private fun requireConfig() =
        config.current() ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)

    private fun htmlResponse(body: String): ResponseEntity<String> =
        ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(body)

    private fun createUser(request: UserCreateRequest, session: HttpSession) {
        requireLoggedIn(session)
        var nickname = request.email.substringBefore("@").trim()
        if (users.nicknameExists(nickname)) nickname = "$nickname-${Instant.now().epochSecond}"
        users.create(
            User(
                id = UUID.randomUUID().toString(),
                email = request.email.trim(),
                password = encoder.encode(request.password) ?: throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR),
                nickname = nickname,
                bio = "",
                createdAt = Instant.now().epochSecond,
            ),
        )
        sessions.setMessage(session, "notice_user_created")
    }

    private fun updateUser(id: String, request: UserEditRequest, session: HttpSession) {
        requireLoggedIn(session)
        users.findById(id) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        if (request.password.isNotBlank()) users.updatePassword(id, encoder.encode(request.password) ?: throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR))
        users.update(id, request.nickname.trim(), request.bio.trim(), request.email.trim())
        sessions.setMessage(session, "notice_user_updated")
    }

    @Transactional
    fun deleteUser(id: String, request: UserDeleteRequest, session: HttpSession) {
        requireLoggedIn(session)
        if (request.transferToId.isNotBlank()) {
            users.findById(request.transferToId) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
            posts.transferPosts(id, request.transferToId)
            sessions.setMessage(session, "notice_user_deletedwithposts")
        } else {
            posts.deleteByUser(id)
            sessions.setMessage(session, "notice_user_deleted")
        }
        users.delete(id)
    }

    private fun createTag(request: TagCreateRequest, session: HttpSession) {
        requireLoggedIn(session)
        tags.create(
            Tag(
                id = UUID.randomUUID().toString(),
                slug = blog.toSlug(request.name),
                name = request.name.replace(",", "").trim(),
                description = request.description.trim(),
                createdAt = Instant.now().epochSecond,
            ),
        )
        sessions.setMessage(session, "notice_tag_created")
    }

    private fun updateTag(id: String, request: TagEditRequest, session: HttpSession) {
        requireLoggedIn(session)
        val existing = tags.findById(id) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        tags.update(
            existing.copy(
                slug = blog.toSlug(request.slug),
                name = request.name.replace(",", "").trim(),
                description = request.description.trim(),
            ),
        )
        sessions.setMessage(session, "notice_tag_updated")
    }

    private fun createNavigation(request: NavigationCreateRequest, session: HttpSession) {
        requireLoggedIn(session)
        val existing = navigations.list()
        navigations.clear()
        existing.forEachIndexed { index, nav -> navigations.create(nav.copy(sequence = index + 1)) }
        navigations.create(Navigation(UUID.randomUUID().toString(), request.url.trim(), request.name.trim(), existing.size + 1))
        sessions.setMessage(session, "notice_nagivation_created")
    }

    private fun editNavigations(request: NavigationEditRequest, session: HttpSession) {
        requireLoggedIn(session)
        val items = request.name.indices.mapNotNull { index ->
            val deleted = request.isDeleted.getOrNull(index) ?: false
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
        items.forEachIndexed { index, nav -> navigations.create(nav.copy(sequence = index + 1)) }
        sessions.setMessage(session, "notice_nagivation_updated")
    }

    private fun updateSettings(request: SettingsEditRequest, session: HttpSession) {
        requireLoggedIn(session)
        val cfg = config.current() ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        cfg.name = request.name.trim()
        cfg.description = request.description.trim()
        cfg.isPublic = request.isPublic
        cfg.timezone = request.timezone
        cfg.locale = request.locale
        cfg.dateFormat = if (request.dateFormat == "custom") request.dateFormatCustom.trim() else request.dateFormat
        cfg.timeFormat = if (request.timeFormat == "custom") request.timeFormatCustom.trim() else request.timeFormat
        config.save()
        sessions.setMessage(session, "notice_settings_updated")
    }

    private fun updateAppearances(request: AppearancesEditRequest, session: HttpSession) {
        requireLoggedIn(session)
        if (!config.themeExists(request.theme)) throw ResponseStatusException(HttpStatus.BAD_REQUEST)
        val cfg = config.current() ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
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
        sessions.setMessage(session, "notice_appearances_updated")
    }

    private fun updateInjected(request: AppearancesInjectedRequest, session: HttpSession) {
        requireLoggedIn(session)
        val cfg = config.current() ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        cfg.injectedHead = request.injectedHead.trim()
        cfg.injectedFoot = request.injectedFoot.trim()
        cfg.injectedPostStart = request.injectedPostStart.trim()
        cfg.injectedPostEnd = request.injectedPostEnd.trim()
        config.save()
        sessions.setMessage(session, "notice_injected_updated")
    }

    private fun createPost(request: PostCreateRequest, cover: MultipartFile?, session: HttpSession): String {
        requireLoggedIn(session)
        users.findById(request.authorId) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        val id = UUID.randomUUID().toString()
        files.saveCover(cover, id)
        val now = Instant.now().epochSecond
        val publishedAt = if (request.publishedAt == 0L) now else request.publishedAt
        posts.create(
            PostWrite(
                id = id,
                title = request.title.trim(),
                slug = blog.toSlug(request.slug),
                excerpt = request.excerpt.trim(),
                authorId = request.authorId,
                password = if (request.visibility == Visibility.PASSWORD) request.password.trim() else "",
                visibility = request.visibility,
                content = request.content.trim(),
                pinnedAt = if (request.isPinned) now else 0,
                publishedAt = publishedAt,
                createdAt = now,
                updatedAt = now,
                tagIds = blog.createTags(request.tags),
            ),
        )
        sessions.setMessage(session, "notice_post_created")
        return id
    }

    private fun updatePost(id: String, request: PostEditRequest, cover: MultipartFile?, session: HttpSession) {
        requireLoggedIn(session)
        users.findById(request.authorId) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        val existing = posts.findById(id) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        if (request.isClearCover) {
            Files.deleteIfExists(Path.of("data/uploads/covers/$id.jpg"))
        } else {
            files.saveCover(cover, id)
        }
        val now = Instant.now().epochSecond
        val password = when {
            request.visibility != Visibility.PASSWORD -> ""
            request.password.isBlank() -> existing.password
            else -> request.password.trim()
        }
        posts.update(
            PostWrite(
                id = id,
                title = request.title.trim(),
                slug = blog.toSlug(request.slug),
                excerpt = request.excerpt.trim(),
                authorId = request.authorId,
                password = password,
                visibility = request.visibility,
                content = request.content.trim(),
                pinnedAt = if (request.isPinned) now else 0,
                publishedAt = request.publishedAt,
                createdAt = existing.createdAt,
                updatedAt = now,
                tagIds = blog.createTags(request.tags),
            ),
        )
        sessions.setMessage(session, "notice_post_updated")
    }

    private data class PhotoFile(val year: String, val month: String, val filename: String)

    private fun imageFiles(): List<PhotoFile> {
        val base = Path.of("data/uploads/images")
        if (!Files.exists(base)) return emptyList()
        val result = mutableListOf<PhotoFile>()
        Files.walk(base).use { stream ->
            stream.filter { Files.isRegularFile(it) }.forEach {
                val relative = base.relativize(it)
                val parts = relative.map { segment -> segment.name }.toList()
                if (parts.size >= 3) {
                    result += PhotoFile(parts[0], parts[1], parts.last())
                }
            }
        }
        return result.sortedByDescending { it.filename }
    }
}
