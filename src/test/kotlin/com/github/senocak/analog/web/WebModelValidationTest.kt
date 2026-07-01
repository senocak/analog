package com.github.senocak.analog.web

import com.github.senocak.analog.domain.Visibility
import jakarta.validation.Validation
import jakarta.validation.Validator
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebModelValidationTest {

    private lateinit var validator: Validator

    @BeforeTest
    fun setup() {
        val factory = Validation.buildDefaultValidatorFactory()
        validator = factory.validator
    }

    @Test
    fun `WizardRequest valid with all required fields`() {
        val req = WizardRequest(
            name = "My Blog",
            description = "A test blog",
            email = "admin@example.com",
            password = "secret123",
            nickname = "Admin",
        )
        val violations = validator.validate(req)
        assertTrue(violations.isEmpty())
    }

    @Test
    fun `WizardRequest fails when name is blank`() {
        val req = WizardRequest(name = "", email = "a@b.com", password = "x", nickname = "Admin")
        val violations = validator.validate(req)
        assertFalse(violations.isEmpty())
        assertTrue(violations.any { it.propertyPath.toString() == "name" })
    }

    @Test
    fun `WizardRequest fails when email is invalid`() {
        val req = WizardRequest(name = "Blog", email = "not-an-email", password = "x", nickname = "Admin")
        val violations = validator.validate(req)
        assertFalse(violations.isEmpty())
        assertTrue(violations.any { it.propertyPath.toString() == "email" })
    }

    @Test
    fun `WizardRequest fails when name exceeds max size`() {
        val req = WizardRequest(name = "a".repeat(49), email = "a@b.com", password = "x", nickname = "Admin")
        val violations = validator.validate(req)
        assertFalse(violations.isEmpty())
        assertTrue(violations.any { it.propertyPath.toString() == "name" })
    }

    @Test
    fun `LoginRequest valid with correct fields`() {
        val req = LoginRequest(email = "user@test.com", password = "secret")
        val violations = validator.validate(req)
        assertTrue(violations.isEmpty())
    }

    @Test
    fun `LoginRequest fails when email is blank`() {
        val req = LoginRequest(email = "", password = "secret")
        val violations = validator.validate(req)
        assertFalse(violations.isEmpty())
    }

    @Test
    fun `UserCreateRequest valid with correct fields`() {
        val req = UserCreateRequest(email = "user@test.com", password = "password123")
        val violations = validator.validate(req)
        assertTrue(violations.isEmpty())
    }

    @Test
    fun `UserCreateRequest fails when password is blank`() {
        val req = UserCreateRequest(email = "user@test.com", password = "")
        val violations = validator.validate(req)
        assertFalse(violations.isEmpty())
    }

    @Test
    fun `TagCreateRequest valid with name`() {
        val req = TagCreateRequest(name = "Kotlin", description = "A language")
        val violations = validator.validate(req)
        assertTrue(violations.isEmpty())
    }

    @Test
    fun `TagCreateRequest fails when name is blank`() {
        val req = TagCreateRequest(name = "")
        val violations = validator.validate(req)
        assertFalse(violations.isEmpty())
    }

    @Test
    fun `PostCreateRequest fails when title is blank`() {
        val req = PostCreateRequest(title = "", slug = "post", authorId = "u1")
        val violations = validator.validate(req)
        assertFalse(violations.isEmpty())
        assertTrue(violations.any { it.propertyPath.toString() == "title" })
    }

    @Test
    fun `PostCreateRequest fails when authorId is blank`() {
        val req = PostCreateRequest(title = "My Post", slug = "my-post", authorId = "")
        val violations = validator.validate(req)
        assertFalse(violations.isEmpty())
        assertTrue(violations.any { it.propertyPath.toString() == "authorId" })
    }

    @Test
    fun `PostCreateRequest valid with all required fields`() {
        val req = PostCreateRequest(
            title = "My Post",
            slug = "my-post",
            authorId = "u1",
            visibility = Visibility.PUBLIC,
        )
        val violations = validator.validate(req)
        assertTrue(violations.isEmpty())
    }

    @Test
    fun `SettingsEditRequest fails when name is blank`() {
        val req = SettingsEditRequest(
            name = "",
            description = "desc",
            isPublic = true,
            timezone = 0,
            dateFormat = "yyyy-MM-dd",
            timeFormat = "HH:mm",
            locale = "en-us",
        )
        val violations = validator.validate(req)
        assertFalse(violations.isEmpty())
    }

    @Test
    fun `AppearancesEditRequest fails when theme is blank`() {
        val req = AppearancesEditRequest(theme = "")
        val violations = validator.validate(req)
        assertFalse(violations.isEmpty())
    }
}
