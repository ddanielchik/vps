package ru.severnoyesiyanieBack.repository

import org.springframework.data.jpa.repository.JpaRepository
import ru.severnoyesiyanieBack.entity.TasteTag

interface TasteTagRepository : JpaRepository<TasteTag, Int> {
}