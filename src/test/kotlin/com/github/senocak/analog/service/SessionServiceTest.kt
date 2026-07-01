package com.github.senocak.analog.service

import com.github.senocak.analog.domain.User
import com.github.senocak.analog.repository.UserRepository
import jakarta.servlet.http.HttpSession
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import kotlin.test.assertEquals
import kotlin.test.assertNull

@ExtendWith(MockitoExtension::class)
class SessionServiceTest {

    @Mock
    private lateinit var session: HttpSession

    @Mock
    private lateinit var users: UserRepository

    @InjectMocks
    private lateinit var sessionService: SessionService

    private val testUser = User(
        id = "user-123",
        email = "test@example.com",
        nickname = "TestUser",
        password = "hashed",
        bio = "",
        createdAt = 1L,
    )

    @Test
    fun `currentUser returns user when session has user_id`() {
        `when`(session.getAttribute("user_id")).thenReturn("user-123")
        `when`(users.findById("user-123")).thenReturn(testUser)

        val result = sessionService.currentUser(session)
        assertEquals(testUser, result)
    }

    @Test
    fun `currentUser returns null when session has no user_id`() {
        `when`(session.getAttribute("user_id")).thenReturn(null)

        val result = sessionService.currentUser(session)
        assertNull(result)
    }

    @Test
    fun `login stores userId in session`() {
        sessionService.login(session, "user-123")
        verify(session).setAttribute("user_id", "user-123")
    }

    @Test
    fun `logout removes user_id from session`() {
        sessionService.logout(session)
        verify(session).removeAttribute("user_id")
    }

    @Test
    fun `setMessage stores message in session`() {
        sessionService.setMessage(session, "notice_saved")
        verify(session).setAttribute("message", "notice_saved")
    }

    @Test
    fun `message returns empty string when no message exists`() {
        `when`(session.getAttribute("message")).thenReturn(null)

        val result = sessionService.message(session)
        assertEquals("", result)
        verify(session, never()).removeAttribute("message")
    }

    @Test
    fun `message returns value and removes it`() {
        `when`(session.getAttribute("message")).thenReturn("notice_saved")

        val result = sessionService.message(session)
        assertEquals("notice_saved", result)
        verify(session).removeAttribute("message")
    }
}
