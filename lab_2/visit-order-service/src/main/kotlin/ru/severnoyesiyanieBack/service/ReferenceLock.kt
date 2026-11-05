package ru.severnoyesiyanieBack.service

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowCallbackHandler
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/** Coordinates reference creation and deletion across replicas using the visits database. */
@Component
class ReferenceLock(private val jdbc: JdbcTemplate) {
    enum class Kind(val key: Int) { USER(1), MENU_ITEM(2) }

    @Transactional(propagation = Propagation.MANDATORY)
    fun acquire(kind: Kind, ids: Collection<Int>, shared: Boolean) {
        val function = if (shared) "pg_advisory_xact_lock_shared" else "pg_advisory_xact_lock"
        ids.distinct().sorted().forEach { id ->
            jdbc.query("SELECT $function(?, ?)", RowCallbackHandler { }, kind.key, id)
        }
    }
}
