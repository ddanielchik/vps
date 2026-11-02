package ru.severnoyesiyanieBack.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.severnoyesiyanieBack.entity.TasteTag
import ru.severnoyesiyanieBack.exception.TasteTagsNotFoundException
import ru.severnoyesiyanieBack.repository.TasteTagRepository

@Service
class TasteTagService(
    private val tasteTagRepository: TasteTagRepository
) {

    @Transactional(readOnly = true)
    fun getTagsByIds(ids: Set<Int>): Set<TasteTag> {
        if (ids.isEmpty()) {
            return emptySet()
        }

        val tags = tasteTagRepository.findAllById(ids)

        if (tags.size != ids.size) {
            throw TasteTagsNotFoundException()
        }

        return tags.toSet()
    }
}