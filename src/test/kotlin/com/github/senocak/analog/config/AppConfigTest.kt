package com.github.senocak.analog.config

import com.github.senocak.analog.domain.AuthorBlock
import com.github.senocak.analog.domain.ColorScheme
import com.github.senocak.analog.domain.FontFamily
import com.github.senocak.analog.domain.Visibility
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.convert.support.DefaultConversionService

@SpringBootTest
class AppConfigTest {

    private val conversionService = DefaultConversionService()

    init {
        conversionService.addConverter(object : org.springframework.core.convert.converter.Converter<String, Visibility> {
            override fun convert(source: String): Visibility = Visibility.from(source)
        })
        conversionService.addConverter(object : org.springframework.core.convert.converter.Converter<String, ColorScheme> {
            override fun convert(source: String): ColorScheme = ColorScheme.from(source)
        })
        conversionService.addConverter(object : org.springframework.core.convert.converter.Converter<String, FontFamily> {
            override fun convert(source: String): FontFamily = FontFamily.from(source)
        })
        conversionService.addConverter(object : org.springframework.core.convert.converter.Converter<String, AuthorBlock> {
            override fun convert(source: String): AuthorBlock = AuthorBlock.from(source)
        })
    }

    @Test
    fun `conversion service converts Visibility from string`() {
        val result = conversionService.convert("public", Visibility::class.java)
        assert(result == Visibility.PUBLIC)
    }

    @Test
    fun `conversion service converts Visibility password from string`() {
        val result = conversionService.convert("password", Visibility::class.java)
        assert(result == Visibility.PASSWORD)
    }

    @Test
    fun `conversion service converts Visibility with unknown value to UNKNOWN`() {
        val result = conversionService.convert("invalid", Visibility::class.java)
        assert(result == Visibility.UNKNOWN)
    }

    @Test
    fun `conversion service converts ColorScheme from string`() {
        assert(conversionService.convert("light", ColorScheme::class.java) == ColorScheme.LIGHT)
        assert(conversionService.convert("dark", ColorScheme::class.java) == ColorScheme.DARK)
        assert(conversionService.convert("", ColorScheme::class.java) == ColorScheme.AUTO)
    }

    @Test
    fun `conversion service converts FontFamily from string`() {
        assert(conversionService.convert("sans", FontFamily::class.java) == FontFamily.SANS)
        assert(conversionService.convert("serif", FontFamily::class.java) == FontFamily.SERIF)
    }

    @Test
    fun `conversion service converts AuthorBlock from string`() {
        assert(conversionService.convert("start", AuthorBlock::class.java) == AuthorBlock.START)
        assert(conversionService.convert("end", AuthorBlock::class.java) == AuthorBlock.END)
        assert(conversionService.convert("none", AuthorBlock::class.java) == AuthorBlock.NONE)
    }
}
