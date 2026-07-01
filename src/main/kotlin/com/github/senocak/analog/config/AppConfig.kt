package com.github.senocak.analog.config

import com.github.senocak.analog.domain.AuthorBlock
import com.github.senocak.analog.domain.ColorScheme
import com.github.senocak.analog.domain.FontFamily
import com.github.senocak.analog.domain.Visibility
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.converter.Converter
import org.springframework.format.FormatterRegistry
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class AppConfig : WebMvcConfigurer {

    @Bean
    fun passwordEncoder(): BCryptPasswordEncoder = BCryptPasswordEncoder()

    override fun addFormatters(registry: FormatterRegistry) {
        registry.addConverter(object : Converter<String, Visibility> {
            override fun convert(source: String): Visibility = Visibility.from(source)
        })
        registry.addConverter(object : Converter<String, ColorScheme> {
            override fun convert(source: String): ColorScheme = ColorScheme.from(source)
        })
        registry.addConverter(object : Converter<String, FontFamily> {
            override fun convert(source: String): FontFamily = FontFamily.from(source)
        })
        registry.addConverter(object : Converter<String, AuthorBlock> {
            override fun convert(source: String): AuthorBlock = AuthorBlock.from(source)
        })
    }
}