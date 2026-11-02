package ru.severnoyesiyanieBack.repository

import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.jpa.repository.JpaRepository
import ru.severnoyesiyanieBack.entity.MenuItem
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface MenuItemRepository : JpaRepository<MenuItem, Int> {
    @Query(
        """
    SELECT DISTINCT m
    FROM MenuItem m
    JOIN m.tasteTags t
    WHERE t.name IN :tastes
"""
    )
    fun findByTasteTags(
        @Param("tastes") tastes: Set<String>,
        pageable: Pageable
    ): Slice<MenuItem>

    fun findAllBy(pageable: Pageable): Slice<MenuItem>

}