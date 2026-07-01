package com.github.senocak.analog.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.senocak.analog.domain.AnalogConfig
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Stream
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener

@Service
class ConfigService(private val mapper: ObjectMapper) {
    private val configFile: Path = Path.of("config.json")
    private val dataDir: Path = Path.of("data")
    @Volatile
    private var config: AnalogConfig? = null

    @EventListener(value = [ApplicationReadyEvent::class])
    fun initialize() {
        Files.createDirectories(dataDir.resolve("uploads/images"))
        Files.createDirectories(dataDir.resolve("uploads/covers"))
        Files.createDirectories(dataDir.resolve("themes"))
        ensureDefaultTheme()
        if (configFile.exists()) {
            config = mapper.readValue(configFile.toFile(), AnalogConfig::class.java)
            save()
        }
    }

    fun current(): AnalogConfig? = config

    fun currentOrDefault(): AnalogConfig = config ?: AnalogConfig()

    fun set(newConfig: AnalogConfig) {
        config = newConfig
    }

    fun save() {
        val current: AnalogConfig = config ?: return
        mapper.writerWithDefaultPrettyPrinter().writeValue(configFile.toFile(), current)
    }

    fun themes(): List<String> {
        val themesDir: Path = dataDir.resolve("themes")
        if (!themesDir.exists())
            return emptyList()
        return Files.list(themesDir).use { paths: Stream<Path> ->
            paths.filter { it.isDirectory() }.map { it.name }.sorted().toList()
        }
    }

    fun themeExists(theme: String): Boolean = theme in themes()

    private fun ensureDefaultTheme() {
        val defaultTheme: Path = dataDir.resolve("themes/default")
        if (defaultTheme.exists())
            return
        val source: Path = Path.of("system/themes/default")
        if (!source.exists()) {
            Files.createDirectories(defaultTheme.resolve("assets"))
            Files.writeString(
                defaultTheme.resolve("index.html"),
                "<!doctype html><html><body><h1>Analog</h1><div id=\"root\"></div></body></html>",
            )
            Files.writeString(defaultTheme.resolve("singular.html"), "<!doctype html><html><body><div id=\"root\"></div></body></html>")
            return
        }
        Files.walk(source).use { paths ->
            paths.forEach { path: Path ->
                val target: Path = defaultTheme.resolve(source.relativize(path).toString())
                if (Files.isDirectory(path)) {
                    Files.createDirectories(target)
                } else {
                    Files.createDirectories(target.parent)
                    Files.copy(path, target)
                }
            }
        }
    }
}
