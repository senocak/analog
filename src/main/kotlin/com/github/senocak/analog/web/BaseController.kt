package com.github.senocak.analog.web

import com.github.senocak.analog.domain.Pagination
import com.github.senocak.analog.service.BlogService
import org.springframework.http.MediaType
import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.util.UriComponentsBuilder

abstract class BaseController(protected val blog: BlogService) {
    protected fun page(requestedPage: Int?): Int = (requestedPage ?: 1).coerceAtLeast(1)

    protected fun pagination(request: HttpServletRequest, page: Int, total: Int, perPage: Int): Pagination {
        val query = request.parameterMap
            .filterKeys { it != "page" }
            .flatMap { (key, values) -> values.take(1).map { key to it } }
            .joinToString("&") { (key, value) ->
                "${UriComponentsBuilder.fromPath("").queryParam(key, value).build().query?.removePrefix("?") ?: ""}"
            }
            .let { if (it.isBlank()) "" else "$it&" }
        return Pagination(
            currentPage = page,
            totalCount = total,
            totalPages = blog.totalPages(total, perPage),
            query = query,
        )
    }

    protected fun rootUrl(request: HttpServletRequest): String {
        val scheme = if (request.isSecure) "https" else "http"
        return "$scheme://${request.serverName}${if (request.serverPort in listOf(80, 443)) "" else ":${request.serverPort}"}"
    }

    protected fun mediaType(path: String): MediaType =
        when (path.substringAfterLast('.', "").lowercase()) {
            "css" -> MediaType("text", "css")
            "js" -> MediaType("application", "javascript")
            "svg" -> MediaType("image", "svg+xml")
            "png" -> MediaType.IMAGE_PNG
            "jpg", "jpeg" -> MediaType.IMAGE_JPEG
            "gif" -> MediaType.IMAGE_GIF
            else -> MediaType.APPLICATION_OCTET_STREAM
        }
}
