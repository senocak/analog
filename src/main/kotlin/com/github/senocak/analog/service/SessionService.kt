package com.github.senocak.analog.service

import com.github.senocak.analog.domain.User
import com.github.senocak.analog.repository.UserRepository
import jakarta.servlet.http.HttpSession
import org.springframework.stereotype.Service

@Service
class SessionService(private val users: UserRepository) {
    fun currentUser(session: HttpSession): User? =
        (session.getAttribute(KEY_USER_ID) as? String)?.let { users.findById(it) }

    fun login(session: HttpSession, userId: String) {
        session.setAttribute(KEY_USER_ID, userId)
    }

    fun logout(session: HttpSession) {
        session.removeAttribute(KEY_USER_ID)
    }

    fun message(session: HttpSession): String {
        val value = session.getAttribute(KEY_MESSAGE) as? String ?: return ""
        session.removeAttribute(KEY_MESSAGE)
        return value
    }

    fun setMessage(session: HttpSession, value: String) {
        session.setAttribute(KEY_MESSAGE, value)
    }

    companion object {
        private const val KEY_USER_ID = "user_id"
        private const val KEY_MESSAGE = "message"
    }
}
