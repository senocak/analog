package com.github.senocak.analog.service

import java.awt.Graphics2D
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.imageio.ImageIO
import kotlin.io.path.exists

@Service
class FileStorageService {
    fun saveCover(file: MultipartFile?, postId: String): String {
        if (file == null || file.isEmpty)
            return ""
        val target: Path = Path.of("data/uploads/covers/$postId.jpg")
        Files.createDirectories(target.parent)
        writeJpeg(file = file, target = target, maxWidth = 1024)
        return "uploads/covers/$postId.jpg"
    }

    fun savePhoto(file: MultipartFile): String {
        val now: LocalDate = LocalDate.now()
        val filename = "${Instant.now().epochSecond}_${UUID.randomUUID()}.jpg"
        val target: Path = Path.of("data/uploads/images/${now.year}/${"%02d".format(now.monthValue)}/$filename")
        Files.createDirectories(target.parent)
        writeJpeg(file = file, target = target, maxWidth = 2000)
        return "uploads/images/${now.year}/${"%02d".format(now.monthValue)}/$filename"
    }

    fun resourceUnder(base: Path, relativePath: String): Resource {
        val normalizedBase: Path = base.toAbsolutePath().normalize()
        val target: Path = normalizedBase.resolve(relativePath.removePrefix(prefix = "/")).normalize()
        if (!target.startsWith(normalizedBase) || !target.exists()) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND)
        }
        return FileSystemResource(target)
    }

    fun deleteImage(relativePath: String) {
        val base = Path.of("data/uploads/images").toAbsolutePath().normalize()
        val target = base.resolve(relativePath.removePrefix("/")).normalize()
        if (!target.startsWith(base)) throw ResponseStatusException(HttpStatus.FORBIDDEN)
        Files.delete(target)
    }

    private fun writeJpeg(file: MultipartFile, target: Path, maxWidth: Int) {
        val original: BufferedImage = ImageIO.read(file.inputStream)
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported image file")
        val width: Int = minOf(a = original.width, b = maxWidth)
        val height: Int = (original.height.toDouble() * width.toDouble() / original.width.toDouble()).toInt().coerceAtLeast(minimumValue = 1)
        val output = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val graphics: Graphics2D = output.createGraphics()
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            graphics.drawImage(original, 0, 0, width, height, null)
        } finally {
            graphics.dispose()
        }
        ImageIO.write(output, "jpg", target.toFile())
    }
}
