package ru.severnoyesiyanieback.menu.repository

import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.jpa.repository.JpaRepository
import ru.severnoyesiyanieback.menu.entity.MenuItem

interface MenuItemRepository : JpaRepository<MenuItem, Int> {
    fun findDistinctByTasteTags_NameIn(tastes: Set<String>, pageable: Pageable): Slice<MenuItem>

    fun findAllBy(pageable: Pageable): Slice<MenuItem>
}
