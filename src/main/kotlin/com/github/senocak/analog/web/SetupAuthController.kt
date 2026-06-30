package com.github.senocak.analog.web

import com.github.senocak.analog.domain.PostWrite
import com.github.senocak.analog.domain.AnalogConfig
import com.github.senocak.analog.domain.User
import com.github.senocak.analog.domain.Visibility
import com.github.senocak.analog.repository.PostRepository
import com.github.senocak.analog.repository.UserRepository
import com.github.senocak.analog.service.ConfigService
import com.github.senocak.analog.service.HtmlRenderer
import com.github.senocak.analog.service.SessionService
import jakarta.servlet.http.HttpSession
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

@Controller
class SetupAuthController(
    private val configService: ConfigService,
    private val users: UserRepository,
    private val posts: PostRepository,
    private val sessions: SessionService,
    private val html: HtmlRenderer,
    private val encoder: BCryptPasswordEncoder,
) {
    @GetMapping("/wizard")
    fun wizard(session: HttpSession): ResponseEntity<String> {
        if (configService.current() != null) {
            return htmlResponse("""<meta http-equiv="refresh" content="0;url=/admin">""")
        }
        return htmlResponse(html.wizard(sessions.message(session)))
    }

    @PostMapping("/wizard", consumes = ["application/x-www-form-urlencoded"])
    fun wizardForm(@Valid @ModelAttribute request: WizardRequest, session: HttpSession): String =
        createSite(request, session)

    @PostMapping("/api/wizard")
    @ResponseBody
    fun wizardJson(@Valid @RequestBody request: WizardRequest, session: HttpSession): Map<String, Any> {
        createSite(request, session)
        return mapOf("ok" to true, "redirect" to "/admin")
    }

    @GetMapping("/login")
    fun login(session: HttpSession): ResponseEntity<String> {
        val self = sessions.currentUser(session)
        if (self != null) {
            return htmlResponse("""<meta http-equiv="refresh" content="0;url=/admin/posts">""")
        }
        val config = configService.current() ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        return htmlResponse(html.login(config, sessions.message(session)))
    }

    @PostMapping("/login", consumes = ["application/x-www-form-urlencoded"])
    fun loginForm(@Valid @ModelAttribute request: LoginRequest, session: HttpSession): String {
        authenticate(request, session)
        return "redirect:/admin/posts"
    }

    @PostMapping("/api/login")
    @ResponseBody
    fun loginJson(@Valid @RequestBody request: LoginRequest, session: HttpSession): Map<String, Any?> {
        val user = authenticate(request, session)
        return mapOf("ok" to true, "self" to user)
    }

    @PostMapping("/admin/logout", "/api/logout")
    fun logout(session: HttpSession): String {
        sessions.logout(session)
        sessions.setMessage(session, "notice_loggedout")
        return "redirect:/login"
    }

    private fun createSite(request: WizardRequest, session: HttpSession): String {
        if (configService.current() != null) return "redirect:/admin"
        val now = Instant.now().epochSecond
        val user = User(
            id = UUID.randomUUID().toString(),
            email = request.email.trim(),
            password = encoder.encode(request.password),
            nickname = request.nickname.trim(),
            bio = "",
            createdAt = now,
        )
        users.create(user)
        configService.set(
            AnalogConfig(
                name = request.name.trim(),
                description = request.description.trim(),
                isPublic = true,
                dateFormat = "2006-01-02",
                timeFormat = "15:04",
                timezone = request.timezone,
                locale = request.locale,
            ),
        )
        configService.save()
        posts.create(
            PostWrite(
                id = UUID.randomUUID().toString(),
                title = "Hello World",
                slug = "hello-world",
                excerpt = "",
                authorId = user.id,
                password = "",
                visibility = Visibility.PUBLIC,
                content = "Welcome to Analog.",
                publishedAt = now - 60,
                createdAt = now - 60,
                updatedAt = now - 60,
            ),
        )
        sessions.login(session, user.id)
        return "redirect:/admin"
    }

    private fun authenticate(request: LoginRequest, session: HttpSession): User {
        val user = users.findByEmail(request.email.trim())
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Incorrect email or password")
        if (!encoder.matches(request.password, user.password)) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Incorrect email or password")
        }
        sessions.login(session, user.id)
        return user
    }

    private fun htmlResponse(body: String): ResponseEntity<String> =
        ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(body)
}
