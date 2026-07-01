package com.github.senocak.analog.domain

import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler

class UnauthorizedException(message: String) : RuntimeException(message)

@ControllerAdvice
class GlobalExceptionHandler {
    @ExceptionHandler(value = [UnauthorizedException::class])
    fun handleValidation(ex: UnauthorizedException): String {
        return "redirect:/login"
    }
}
