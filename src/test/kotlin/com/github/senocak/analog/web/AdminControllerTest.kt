package com.github.senocak.analog.web

import com.github.senocak.analog.domain.AnalogConfig
import com.github.senocak.analog.domain.Post
import com.github.senocak.analog.domain.PostCount
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.mockito.Mockito.`when`
import java.time.Instant

@WebMvcTest(AdminController::class)
class AdminControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var config: ConfigService

    @MockitoBean
    private lateinit var sessions: SessionService

    @MockitoBean
    private lateinit var users: UserRepository

    @MockitoBean
    private lateinit var posts: PostRepository

    @MockitoBean
    private lateinit var tags: TagRepository

    @MockitoBean
    private lateinit var navigations: NavigationRepository

    @MockitoBean
    private lateinit var files: FileStorageService

    @MockitoBean
    private lateinit var utils: TemplateUtils

    @MockitoBean
    private lateinit var blog: BlogService

    private val testUser = com.github.senocak.analog.domain.User(
        id = "admin-1",
        email = "admin@test.com",
        nickname = "Admin",
        password = "hashed",
        bio = "",
        createdAt = 1L,
    )

    @BeforeEach
    fun setup() {
        `when`(config.current()).thenReturn(
            AnalogConfig(name = "TestBlog", description = "", isPublic = true),
        )
        `when`(config.currentOrDefault()).thenReturn(
            AnalogConfig(name = "TestBlog", description = "", isPublic = true),
        )
        `when`(config.themes()).thenReturn(listOf("default"))
    }

    @Test
    fun `GET admin root redirects to posts`() {
        `when`(sessions.currentUser(org.mockito.kotlin.any())).thenReturn(testUser)

        mockMvc.get("/admin")
            .andExpect { status { is3xxRedirection() } }
            .andExpect { redirectedUrl("/admin/posts") }
    }

    @Test
    fun `GET admin posts requires authentication`() {
        `when`(sessions.currentUser(org.mockito.kotlin.any())).thenReturn(null)

        mockMvc.get("/admin/posts")
            .andExpect { status { is3xxRedirection() } }
    }

    @Test
    fun `GET admin posts returns posts page when authenticated`() {
        `when`(sessions.currentUser(org.mockito.kotlin.any())).thenReturn(testUser)
        `when`(posts.list(org.mockito.kotlin.any())).thenReturn(emptyList())
        `when`(posts.count(org.mockito.kotlin.any())).thenReturn(0)
        `when`(posts.countByType()).thenReturn(PostCount())
        `when`(blog.totalPages(org.mockito.kotlin.any(), org.mockito.kotlin.any())).thenReturn(0)

        mockMvc.get("/admin/posts")
            .andExpect { status { isOk() } }
            .andExpect { view { name("admin_posts") } }
    }

    @Test
    fun `GET admin users returns users page when authenticated`() {
        `when`(sessions.currentUser(org.mockito.kotlin.any())).thenReturn(testUser)
        `when`(users.list()).thenReturn(emptyList())

        mockMvc.get("/admin/users")
            .andExpect { status { isOk() } }
            .andExpect { view { name("admin_users") } }
    }

    @Test
    fun `GET admin tags returns tags page when authenticated`() {
        `when`(sessions.currentUser(org.mockito.kotlin.any())).thenReturn(testUser)
        `when`(tags.list(org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any())).thenReturn(emptyList())
        `when`(tags.count(org.mockito.kotlin.any())).thenReturn(0)
        `when`(blog.totalPages(org.mockito.kotlin.any(), org.mockito.kotlin.any())).thenReturn(0)

        mockMvc.get("/admin/tags")
            .andExpect { status { isOk() } }
            .andExpect { view { name("admin_tags") } }
    }

    @Test
    fun `GET admin settings returns settings page when authenticated`() {
        `when`(sessions.currentUser(org.mockito.kotlin.any())).thenReturn(testUser)

        mockMvc.get("/admin/settings")
            .andExpect { status { isOk() } }
            .andExpect { view { name("admin_settings") } }
    }

    @Test
    fun `GET admin appearances returns appearances page when authenticated`() {
        `when`(sessions.currentUser(org.mockito.kotlin.any())).thenReturn(testUser)

        mockMvc.get("/admin/appearances")
            .andExpect { status { isOk() } }
            .andExpect { view { name("admin_appearances") } }
    }

    @Test
    fun `GET admin navigations returns navigations page when authenticated`() {
        `when`(sessions.currentUser(org.mockito.kotlin.any())).thenReturn(testUser)
        `when`(navigations.list()).thenReturn(emptyList())

        mockMvc.get("/admin/navigations")
            .andExpect { status { isOk() } }
            .andExpect { view { name("admin_navigations") } }
    }

    @Test
    fun `GET admin photos returns photos page when authenticated`() {
        `when`(sessions.currentUser(org.mockito.kotlin.any())).thenReturn(testUser)

        mockMvc.get("/admin/photos")
            .andExpect { status { isOk() } }
            .andExpect { view { name("admin_photos") } }
    }

    @Test
    fun `GET admin post create returns post form when authenticated`() {
        `when`(sessions.currentUser(org.mockito.kotlin.any())).thenReturn(testUser)
        `when`(users.list()).thenReturn(listOf(testUser))
        `when`(tags.list(org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any())).thenReturn(emptyList())

        mockMvc.get("/admin/post/create")
            .andExpect { status { isOk() } }
            .andExpect { view { name("admin_post_form") } }
    }

    @Test
    fun `POST trash post requires authentication`() {
        `when`(sessions.currentUser(org.mockito.kotlin.any())).thenReturn(null)

        mockMvc.post("/admin/post/some-id/trash")
            .andExpect { status { is3xxRedirection() } }
    }
}
