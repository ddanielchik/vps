package ru.severnoyesiyanieBack.integration

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import ru.severnoyesiyanieBack.exception.ResourceInUseException
import ru.severnoyesiyanieBack.service.ReferenceDeletionService
import ru.severnoyesiyanieBack.service.ReferenceLock
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ReferenceDeletionIT : ApiIntegrationSupport() {
    @Autowired private lateinit var transactionManager: PlatformTransactionManager
    @Autowired private lateinit var referenceLock: ReferenceLock
    @Autowired private lateinit var deletions: ReferenceDeletionService

    @Test
    fun `unused user and menu item are deleted through their owners`() {
        val user = createClient()
        val item = createMenuItem()
        mockMvc.perform(delete("/api/users/$user")).andExpect(status().isNoContent)
        mockMvc.perform(delete("/api/menu/$item")).andExpect(status().isNoContent)
        assertFalse(remote.users.containsKey(user))
        assertFalse(remote.menu.containsKey(item))
        mockMvc.perform(delete("/api/users/$user")).andExpect(status().isNotFound)
        mockMvc.perform(delete("/api/menu/$item")).andExpect(status().isNotFound)
    }

    @Test
    fun `users and menu items referenced by visits or orders cannot be deleted`() {
        val client = createClient()
        val visit = createVisit(client)
        val waiter = createStaff("WAITER")
        val worker = createStaff("WORKER")
        val item = createMenuItem()
        mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(
            """{"visitId":$visit,"waiterId":$waiter,"workerId":$worker,"items":[{"menuItemId":$item,"quantity":1}]}"""
        )).andExpect(status().isCreated)
        listOf(client, waiter, worker).forEach { id ->
            mockMvc.perform(delete("/api/users/$id")).andExpect(status().isConflict)
            assertTrue(remote.users.containsKey(id))
        }
        mockMvc.perform(delete("/api/menu/$item")).andExpect(status().isConflict)
        assertTrue(remote.menu.containsKey(item))
    }

    @Test
    fun `delete waits for reference transaction then sees its committed visit`() {
        val user = createClient()
        val locked = CountDownLatch(1)
        val release = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val creation = executor.submit {
                TransactionTemplate(transactionManager).executeWithoutResult {
                    referenceLock.acquire(ReferenceLock.Kind.USER, listOf(user), shared = true)
                    locked.countDown()
                    check(release.await(10, TimeUnit.SECONDS))
                    jdbc.update("INSERT INTO visits (id_user, table_number, status) VALUES (?, 1, 'OPEN')", user)
                }
            }
            assertTrue(locked.await(5, TimeUnit.SECONDS))
            val deletion = executor.submit { deletions.deleteUser(user) }
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            var waiting = false
            while (System.nanoTime() < deadline && !waiting) {
                waiting = jdbc.queryForObject(
                    "SELECT COUNT(*) > 0 FROM pg_locks WHERE locktype = 'advisory' AND NOT granted AND classid::bigint = 1 AND objid::bigint = ?",
                    Boolean::class.java, user
                ) == true
                if (!waiting) Thread.sleep(20)
            }
            assertTrue(waiting, "Deletion must wait on the reference transaction's lock")
            release.countDown()
            creation.get(5, TimeUnit.SECONDS)
            val exception = assertThrows(ExecutionException::class.java) { deletion.get(5, TimeUnit.SECONDS) }
            assertInstanceOf(ResourceInUseException::class.java, exception.cause)
            assertTrue(remote.users.containsKey(user))
            assertEquals(1, count("visits"))
        } finally {
            release.countDown()
            executor.shutdownNow()
        }
    }
}
