package ru.severnoyesiyanieBack.integration

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class UserAndMenuApiIT : ApiIntegrationSupport() {
    @Test
    fun `user can be created read updated and deleted`() {
        val id = createClient()

        mockMvc.perform(get("/api/users/$id"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("Guest"))
            .andExpect(jsonPath("$.email").value("client@example.com"))
            .andExpect(jsonPath("$.role").value("CLIENT"))
        assertEquals("CLIENT", jdbc.queryForObject("SELECT role FROM users WHERE id_user = ?", String::class.java, id))

        mockMvc.perform(
            put("/api/users/$id").contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Renamed guest","email":"new@example.com"}""")
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("Renamed guest"))
            .andExpect(jsonPath("$.email").value("new@example.com"))
        assertEquals("new@example.com", jdbc.queryForObject("SELECT email FROM users WHERE id_user = ?", String::class.java, id))

        mockMvc.perform(delete("/api/users/$id"))
            .andExpect(status().isNoContent)
            .andExpect(content().string(""))
        mockMvc.perform(get("/api/users/$id"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.message").isNotEmpty)
        assertEquals(0, count("users"))
    }

    @ParameterizedTest
    @ValueSource(strings = [
        "{\"name\":\"\",\"email\":\"guest@example.com\"}",
        "{\"name\":\"Guest\",\"email\":\"\"}",
        "{\"name\":\"Guest\",\"email\":\"invalid-email\"}"
    ])
    fun `invalid user is rejected at controller and is not persisted`(body: String) {
        mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").isNotEmpty)
        assertEquals(0, count("users"))
    }

    @Test
    fun `duplicate emails return conflict without overwriting users`() {
        val firstId = createClient()
        val secondId = createClient("second@example.com")

        mockMvc.perform(
            post("/api/users").contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Duplicate","email":"client@example.com"}""")
        ).andExpect(status().isConflict).andExpect(jsonPath("$.message").isNotEmpty)
        mockMvc.perform(
            put("/api/users/$secondId").contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Duplicate","email":"client@example.com"}""")
        ).andExpect(status().isConflict)

        mockMvc.perform(get("/api/users/$secondId"))
            .andExpect(jsonPath("$.name").value("Guest"))
            .andExpect(jsonPath("$.email").value("second@example.com"))
        // An unchanged email must not be mistaken for a duplicate of the same user.
        mockMvc.perform(
            put("/api/users/$firstId").contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"New name","email":"client@example.com"}""")
        ).andExpect(status().isOk)
        assertEquals(2, count("users"))
    }

    @Test
    fun `unknown users return not found for all available operations`() {
        mockMvc.perform(get("/api/users/999"))
            .andExpect(status().isNotFound).andExpect(jsonPath("$.message").isNotEmpty)
        mockMvc.perform(delete("/api/users/999")).andExpect(status().isNotFound)
        mockMvc.perform(
            put("/api/users/999").contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Guest","email":"guest@example.com"}""")
        ).andExpect(status().isNotFound)
    }

    @Test
    fun `menu item CRUD persists many to many tags`() {
        val id = createMenuItem()
        mockMvc.perform(get("/api/menu/$id"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("Northern Lights"))
            .andExpect(jsonPath("$.price").value(125.5))
            .andExpect(jsonPath("$.ingredients").value("Gin, lime"))
            .andExpect(jsonPath("$.tasteTags[0]").value("SOUR"))
            .andExpect(jsonPath("$.tasteTags[1]").value("SWEET"))
        assertEquals(2, count("menu_taste_tags"))

        mockMvc.perform(
            put("/api/menu/$id").contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Bitter Lights","price":150.25,"ingredients":"Gin, bitters","tasteTagIds":[3]}""")
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("Bitter Lights"))
            .andExpect(jsonPath("$.price").value(150.25))
            .andExpect(jsonPath("$.tasteTags.length()").value(1))
            .andExpect(jsonPath("$.tasteTags[0]").value("BITTER"))
        assertEquals(1, count("menu_taste_tags"))
        assertEquals(3, jdbc.queryForObject("SELECT id_tag FROM menu_taste_tags WHERE id_position = ?", Int::class.java, id))

        mockMvc.perform(delete("/api/menu/$id"))
            .andExpect(status().isNoContent).andExpect(content().string(""))
        mockMvc.perform(get("/api/menu/$id"))
            .andExpect(status().isNotFound).andExpect(jsonPath("$.message").isNotEmpty)
        assertEquals(0, count("menu_taste_tags"))
        assertEquals(0, count("menu"))
    }

    @ParameterizedTest
    @ValueSource(strings = [
        "{\"name\":\"\",\"price\":10,\"ingredients\":\"Gin\",\"tasteTagIds\":[1]}",
        "{\"name\":\"Drink\",\"price\":-0.01,\"ingredients\":\"Gin\",\"tasteTagIds\":[1]}",
        "{\"name\":\"Drink\",\"price\":10,\"ingredients\":\"\",\"tasteTagIds\":[1]}",
        "{\"name\":\"Drink\",\"price\":10,\"ingredients\":\"Gin\",\"tasteTagIds\":[]}"
    ])
    fun `invalid menu fields return readable bad request`(body: String) {
        mockMvc.perform(post("/api/menu").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest).andExpect(jsonPath("$.message").isNotEmpty)
        assertEquals(0, count("menu"))
    }

    @ParameterizedTest
    @ValueSource(ints = [51, 500])
    fun `ingredients up to DTO limit can be persisted`(length: Int) {
        val ingredients = "a".repeat(length)
        mockMvc.perform(
            post("/api/menu").contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Long recipe","price":0,"ingredients":"$ingredients","tasteTagIds":[1]}""")
        ).andExpect(status().isCreated).andExpect(jsonPath("$.ingredients").value(ingredients))
        assertEquals(ingredients, jdbc.queryForObject("SELECT ingredients FROM menu", String::class.java))
    }

    @Test
    fun `ingredients beyond limit are rejected before persistence`() {
        val ingredients = "a".repeat(501)
        mockMvc.perform(
            post("/api/menu").contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Too long","price":10,"ingredients":"$ingredients","tasteTagIds":[1]}""")
        ).andExpect(status().isBadRequest)
        assertEquals(0, count("menu"))
    }

    @Test
    fun `unknown tags cannot create or partially update a cocktail`() {
        val id = createMenuItem()
        val body = """{"name":"Invalid update","price":99,"ingredients":"Gin","tasteTagIds":[1,999]}"""
        mockMvc.perform(post("/api/menu").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest)
        mockMvc.perform(put("/api/menu/$id").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest)
        mockMvc.perform(get("/api/menu/$id"))
            .andExpect(jsonPath("$.name").value("Northern Lights"))
            .andExpect(jsonPath("$.price").value(125.5))
            .andExpect(jsonPath("$.tasteTags.length()").value(2))
        assertEquals(1, count("menu"))
    }

    @Test
    fun `unknown menu items return not found for update and delete`() {
        mockMvc.perform(delete("/api/menu/999")).andExpect(status().isNotFound)
        mockMvc.perform(
            put("/api/menu/999").contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Drink","price":10,"ingredients":"Gin","tasteTagIds":[1]}""")
        ).andExpect(status().isNotFound)
    }

    @Test
    fun `menu uses slices with a next page and without total count`() {
        jdbc.update(
            """
            INSERT INTO menu (name_product, price, ingredients)
            SELECT 'Drink ' || n, 10, 'Gin' FROM generate_series(1, 12) AS n
            """.trimIndent()
        )
        mockMvc.perform(get("/api/menu"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(10))
            .andExpect(jsonPath("$.content[0].idPosition").value(1))
            .andExpect(jsonPath("$.first").value(true))
            .andExpect(jsonPath("$.last").value(false))
            .andExpect(jsonPath("$.totalElements").doesNotExist())
            .andExpect(jsonPath("$.totalPages").doesNotExist())
            .andExpect(header().doesNotExist("X-Total-Count"))
        mockMvc.perform(get("/api/menu").param("page", "1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(2))
            .andExpect(jsonPath("$.content[0].idPosition").value(11))
            .andExpect(jsonPath("$.last").value(true))
        mockMvc.perform(get("/api/menu").param("page", "2"))
            .andExpect(status().isOk).andExpect(jsonPath("$.content").isEmpty)
        mockMvc.perform(get("/api/menu").param("page", "-1")).andExpect(status().isBadRequest)
    }
}
