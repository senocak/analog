package com.github.senocak.analog.service

import com.github.senocak.analog.domain.Visibility
import org.springframework.stereotype.Component
import java.net.URLEncoder
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Component("utils")
class TemplateUtils {

    fun date(epoch: Long): String =
        DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC).format(Instant.ofEpochSecond(epoch))

    fun u(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8).replace(oldValue = "+", newValue = "%20")

    fun visibilityPrefix(visibility: Visibility): String =
        when (visibility) {
            Visibility.PRIVATE -> "[Private] "
            Visibility.PASSWORD -> "[Password] "
            Visibility.DRAFT -> "[Draft] "
            else -> ""
        }

    fun markdown(value: String): String {
        val escaped: String = value
            .replace(oldValue = "&", newValue = "&amp;")
            .replace(oldValue = "<", newValue = "&lt;")
            .replace(oldValue = ">", newValue = "&gt;")
            .replace(oldValue = "\"", newValue = "&quot;")
        return escaped
            .replace(oldValue = "\n\n", newValue = "</p><p>")
            .replace(oldValue = "\n", newValue = "<br>")
            .let { "<p>$it</p>" }
    }
}
