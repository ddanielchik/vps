package ru.severnoyesiyanieBack.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.severnoyesiyanieBack.client.MenuClient
import ru.severnoyesiyanieBack.client.UserClient
import ru.severnoyesiyanieBack.exception.ResourceInUseException
import ru.severnoyesiyanieBack.repository.OrderRepository
import ru.severnoyesiyanieBack.repository.VisitRepository

@Service
class ReferenceDeletionService(
    private val visits: VisitRepository,
    private val orders: OrderRepository,
    private val users: UserClient,
    private val menu: MenuClient,
    private val referenceLock: ReferenceLock
) {
    @Transactional
    fun deleteUser(id: Int) {
        referenceLock.acquire(ReferenceLock.Kind.USER, listOf(id), shared = false)
        users.getUser(id)
        if (visits.existsByClientId(id) || orders.existsByWaiterIdOrWorkerId(id, id)) {
            throw ResourceInUseException("User $id is referenced by a visit or order")
        }
        users.deleteUser(id)
    }

    @Transactional
    fun deleteMenuItem(id: Int) {
        referenceLock.acquire(ReferenceLock.Kind.MENU_ITEM, listOf(id), shared = false)
        menu.getMenuItem(id)
        if (orders.existsByItemsMenuItemId(id)) {
            throw ResourceInUseException("Menu item $id is referenced by an order")
        }
        menu.deleteMenuItem(id)
    }
}
