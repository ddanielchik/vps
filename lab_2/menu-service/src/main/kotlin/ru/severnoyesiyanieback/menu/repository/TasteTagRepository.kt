package ru.severnoyesiyanieback.menu.repository

import org.springframework.data.jpa.repository.JpaRepository
import ru.severnoyesiyanieback.menu.entity.TasteTag

interface TasteTagRepository : JpaRepository<TasteTag, Int> {
}
