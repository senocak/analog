package com.github.senocak.analog.service

import com.github.senocak.analog.repository.UserRepository
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Component
import kotlin.random.Random
import kotlin.system.exitProcess

@Component
class PasswordResetRunner(
    private val users: UserRepository,
    private val encoder: BCryptPasswordEncoder,
) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        if (!args.sourceArgs.firstOrNull().equals("reset-password")) return
        val email = args.nonOptionArgs.drop(1).firstOrNull()
            ?: error("Email is required: reset-password user@example.com")
        val user = users.findByEmail(email) ?: error("User not found: $email")
        val password = randomPassword()
        users.updatePassword(user.id, encoder.encode(password))
        println("""Password for user ${user.email} has been reset to: "$password"""")
        exitProcess(0)
    }

    private fun randomPassword(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        return (1..16).map { chars[Random.nextInt(chars.length)] }.joinToString("")
    }
}
