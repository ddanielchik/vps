package ru.severnoyesiyanieBack.integration

import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class ReferenceDeletionIT : ApiIntegrationSupport() {
    @Test
    fun `unused records have no references`() {
        val user = createClient()
        val item = createMenuItem()
        mockMvc.perform(get("/api/v1/orders/references/users/$user"))
            .andExpect(status().isOk).andExpect(content().string("false"))
        mockMvc.perform(get("/api/v1/orders/references/menu/$item"))
            .andExpect(status().isOk).andExpect(content().string("false"))
    }

    @Test
    fun `checks protect users but allow deleting menu with complete snapshots`() {
        val client = createClient()
        val visit = createVisit(client)
        val waiter = createStaff("WAITER")
        val worker = createStaff("WORKER")
        val item = createMenuItem()
        mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content(
            """{"visitId":$visit,"waiterId":$waiter,"workerId":$worker,"items":[{"menuItemId":$item,"quantity":1}]}"""
        )).andExpect(status().isCreated)
        listOf(client, waiter, worker).forEach { id ->
            mockMvc.perform(get("/api/v1/orders/references/users/$id"))
                .andExpect(status().isOk).andExpect(content().string("true"))
        }
        mockMvc.perform(get("/api/v1/orders/references/menu/$item"))
            .andExpect(status().isOk).andExpect(content().string("false"))
    }
}
