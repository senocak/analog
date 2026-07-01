package com.github.senocak.analog.web

import com.github.senocak.analog.domain.AnalogConfig
import com.github.senocak.analog.domain.User
import com.github.senocak.analog.repository.PostRepository
import com.github.senocak.analog.repository.UserRepository
import com.github.senocak.analog.service.ConfigService
import com.github.senocak.analog.service.SessionService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.mockito.Mockito.`when`

@WebMvcTest(SetupAuthController::class)
class SetupAuthControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var configService: ConfigService

    @MockitoBean
    private lateinit var users: UserRepository

    @MockitoBean
    private lateinit var posts: PostRepository

    @MockitoBean
    private lateinit var sessions: SessionService

    @MockitoBean
    private lateinit var encoder: BCryptPasswordEncoder

    private val testUser = User(
        id = "admin-1",
        email = "admin@test.com",
        nickname = "Admin",
        password = "hashed",
        bio = "",
        createdAt = 1L,
    )

    private val testConfig = AnalogConfig(
        name = "TestBlog",
        description = "",
        isPublic = true,
    )

    @BeforeEach
    fun setup() {
        `when`(configService.current()).thenReturn(testConfig)
    }

    @Test
    fun `GET wizard redirects to admin when config exists`() {
        // config already set in @BeforeEach
        mockMvc.get("/wizard")
            .andExpect { status { is3xxRedirection() } }
            .andExpect { redirectedUrl("/admin") }
    }

    @Test
    fun `GET wizard shows wizard page when no config exists`() {
        `when`(configService.current()).thenReturn(null)

        mockMvc.get("/wizard")
            .andExpect { status { isOk() } }
            .andExpect { view { name("wizard") } }
    }

    @Test
    fun `GET login redirects to admin posts when already logged in`() {
        `when`(sessions.currentUser(org.mockito.kotlin.any())).thenReturn(testUser)

        mockMvc.get("/login")
            .andExpect { status { is3xxRedirection() } }
            .andExpect { redirectedUrl("/admin/posts") }
    }

    @Test
    fun `GET login shows login page when not logged in`() {
        `when`(sessions.currentUser(org.mockito.kotlin.any())).thenReturn(null)

        mockMvc.get("/login")
            .andExpect { status { isOk() } }
            .andExpect { view { name("login") } }
    }
}
