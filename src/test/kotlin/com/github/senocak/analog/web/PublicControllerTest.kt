package com.github.senocak.analog.web

import com.github.senocak.analog.domain.AnalogConfig
import com.github.senocak.analog.domain.Post
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.mockito.Mockito.`when`
import java.net.URLEncoder
import java.time.Instant

@WebMvcTest(PublicController::class)
class PublicControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var config: ConfigService

    @MockitoBean
    private lateinit var posts: PostRepository

    @MockitoBean
    private lateinit var tags: TagRepository

    @MockitoBean
    private lateinit var users: UserRepository

    @MockitoBean
    private lateinit var navigations: NavigationRepository

    @MockitoBean
    private lateinit var sessions: SessionService

    @MockitoBean
    private lateinit var files: FileStorageService

    @MockitoBean
    private lateinit var utils: TemplateUtils

    @MockitoBean
    private lateinit var blog: BlogService

    private val testConfig = AnalogConfig(
        name = "TestBlog",
        description = "Test Description",
        isPublic = true,
        postsPerPage = 10,
    )

    private fun testPost(): Post = Post(
        id = "p1",
        title = "Hello World",
        slug = "hello-world",
        originalExcerpt = "",
        authorId = "u1",
        password = "",
        visibility = Visibility.PUBLIC,
        content = "Content body",
        pinnedAt = 0,
        publishedAt = 1_700_000_000L,
        createdAt = 1_700_000_000L,
        updatedAt = 1_700_000_000L,
        trashedAt = 0,
        author = User(
            id = "u1",
            email = "a@b.com",
            nickname = "Author",
            password = "",
            bio = "",
            createdAt = 1L,
        ),
    )

    @BeforeEach
    fun setup() {
        `when`(config.current()).thenReturn(testConfig)
        `when`(config.currentOrDefault()).thenReturn(testConfig)
        `when`(sessions.currentUser(org.mockito.kotlin.any())).thenReturn(null)
        `when`(utils.date(org.mockito.kotlin.any())).thenReturn("2023-11-14")
        `when`(utils.visibilityPrefix(org.mockito.kotlin.any())).thenReturn("")
        `when`(utils.u(org.mockito.kotlin.any())).thenAnswer { URLEncoder.encode(it.getArgument(0) as String, "UTF-8").replace("+", "%20") }
        `when`(blog.totalPages(org.mockito.kotlin.any(), org.mockito.kotlin.any())).thenReturn(1)
    }

    @Test
    fun `GET root returns index page`() {
        `when`(posts.list(org.mockito.kotlin.any())).thenReturn(listOf(testPost()))
        `when`(posts.count(org.mockito.kotlin.any())).thenReturn(1)
        `when`(navigations.list()).thenReturn(emptyList())

        mockMvc.get("/")
            .andExpect { status { isOk() } }
            .andExpect { view { name("index") } }
    }

    @Test
    fun `GET post by slug returns post page`() {
        val post = testPost()
        `when`(posts.findBySlug("hello-world")).thenReturn(post)
        `when`(navigations.list()).thenReturn(emptyList())
        `when`(posts.previous("p1")).thenReturn(null)
        `when`(posts.next("p1")).thenReturn(null)

        mockMvc.get("/post/hello-world")
            .andExpect { status { isOk() } }
            .andExpect { view { name("post") } }
    }

    @Test
    fun `GET non-existent post returns 404`() {
        `when`(posts.findBySlug("missing")).thenReturn(null)

        mockMvc.get("/post/missing")
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `POST post with correct password redirects back to post`() {
        val post = testPost().copy(visibility = Visibility.PASSWORD, password = "secret")
        `when`(posts.findBySlug("secret-post")).thenReturn(post)
        `when`(navigations.list()).thenReturn(emptyList())
        `when`(posts.previous("p1")).thenReturn(null)
        `when`(posts.next("p1")).thenReturn(null)

        mockMvc.perform(
            post("/post/secret-post")
                .param("password", "secret"),
        )
            .andExpect(status().isOk)
    }

    @Test
    fun `GET rss returns xml`() {
        `when`(posts.list(org.mockito.kotlin.any())).thenReturn(listOf(testPost()))

        mockMvc.get("/rss.xml")
            .andExpect { status { isOk() } }
            .andExpect { content { contentTypeCompatibleWith(MediaType.APPLICATION_XML) } }
    }

    @Test
    fun `GET sitemap returns xml`() {
        `when`(posts.list(org.mockito.kotlin.any())).thenReturn(listOf(testPost()))

        mockMvc.get("/sitemap.xml")
            .andExpect { status { isOk() } }
            .andExpect { content { contentTypeCompatibleWith(MediaType.APPLICATION_XML) } }
    }
}
