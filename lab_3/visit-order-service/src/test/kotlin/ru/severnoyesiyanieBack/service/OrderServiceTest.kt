package ru.severnoyesiyanieBack.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Answers
import org.mockito.Mockito.*
import reactor.core.publisher.Mono
import ru.severnoyesiyanieBack.dto.CreateOrderItemRequest
import ru.severnoyesiyanieBack.dto.CreateOrderRequest
import ru.severnoyesiyanieBack.dto.MenuItemResponse
import ru.severnoyesiyanieBack.entity.Order
import ru.severnoyesiyanieBack.entity.OrderItem
import ru.severnoyesiyanieBack.dto.UserResponse
import ru.severnoyesiyanieBack.client.UserClient
import ru.severnoyesiyanieBack.client.MenuClient
import ru.severnoyesiyanieBack.client.RemoteBusinessException
import ru.severnoyesiyanieBack.entity.UserRole
import ru.severnoyesiyanieBack.entity.Visit
import ru.severnoyesiyanieBack.entity.VisitStatus
import ru.severnoyesiyanieBack.exception.*
import ru.severnoyesiyanieBack.repository.OrderRepository
import ru.severnoyesiyanieBack.repository.VisitRepository
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.Optional

class OrderServiceTest {

    private lateinit var orderRepository: OrderRepository
    private lateinit var visitRepository: VisitRepository
    private lateinit var userClient: UserClient
    private lateinit var menuClient: MenuClient
    private lateinit var service: OrderService

    private var savedOrder: Order? = null

    private lateinit var client: UserResponse
    private lateinit var waiter: UserResponse
    private lateinit var worker: UserResponse
    private lateinit var visit: Visit
    private lateinit var cocktail: MenuItemResponse
    private lateinit var lemonade: MenuItemResponse

    private val request = CreateOrderRequest(
        visitId = 4,
        items = listOf(
            CreateOrderItemRequest(10, 2),
            CreateOrderItemRequest(11, 3)
        )
    )

    @BeforeEach
    fun setUp() {

        savedOrder = null

        client = UserResponse(
            1, UserRole.CLIENT
        )

        waiter = UserResponse(
            2, UserRole.WAITER
        )

        worker = UserResponse(
            3, UserRole.WORKER
        )

        visit = Visit(
            idVisit = 4,
            clientId = client.idUser,
            tableNumber = 6
        )

        cocktail = MenuItemResponse(
            idPosition = 10,
            name = "Aurora",
            price = BigDecimal("125.50"), ingredients = "Rum", tasteTags = listOf("SWEET")
        )

        lemonade = MenuItemResponse(
            idPosition = 11,
            name = "Lemonade",
            price = BigDecimal("45.25"), ingredients = "Lemon", tasteTags = listOf("SOUR")
        )

        orderRepository = mock(OrderRepository::class.java) { invocation ->
            if (invocation.method.name == "save") {
                invocation.getArgument<Order>(0).also {
                    it.idOrder = 20
                    savedOrder = it
                }
            } else {
                Answers.RETURNS_DEFAULTS.answer(invocation)
            }
        }

        visitRepository = mock(VisitRepository::class.java)
        userClient = mock(UserClient::class.java)
        menuClient = mock(MenuClient::class.java)
        doReturn(Mono.just(waiter)).`when`(userClient).getUser(waiter.idUser)

        service = OrderService(
            orderRepository,
            visitRepository,
            userClient,
            menuClient
        )
    }

    @Test
    fun `creating an order calculates exact totals and connects every item to its order`() {

        stubOpenVisit()

        doReturn(Mono.just(cocktail))
            .`when`(menuClient).getMenuItem(10)

        doReturn(Mono.just(lemonade))
            .`when`(menuClient).getMenuItem(11)

        val response = service.createOrder(request, waiter.idUser)
        val persisted = requireNotNull(savedOrder)

        assertEquals(20, response.idOrder)
        assertEquals(4, response.visitId)
        assertEquals(2, response.waiterId)
        assertNull(response.workerId)

        assertEquals(
            BigDecimal("386.75"),
            response.totalAmount
        )

        assertEquals(
            listOf(10, 11),
            response.items.map { it.menuItemId }
        )

        assertEquals(
            listOf("Aurora", "Lemonade"),
            response.items.map { it.name }
        )

        assertEquals(
            listOf(2, 3),
            response.items.map { it.quantity }
        )

        assertEquals(
            listOf(
                BigDecimal("125.50"),
                BigDecimal("45.25")
            ),
            response.items.map { it.unitPrice }
        )

        assertEquals(
            listOf(
                BigDecimal("251.00"),
                BigDecimal("135.75")
            ),
            response.items.map { it.subtotal }
        )

        assertSame(visit, persisted.visit)
        assertEquals(waiter.idUser, persisted.waiterId)
        assertNull(persisted.workerId)

        assertEquals(cocktail.idPosition, persisted.items[0].menuItemId)
        assertEquals(lemonade.idPosition, persisted.items[1].menuItemId)

        persisted.items.forEach {
            assertSame(persisted, it.order)
        }

        assertEquals(response.createdAt, persisted.createdAt)
        assertEquals(response.totalAmount, persisted.totalAmount)

        verify(visitRepository).findById(4)
        verify(menuClient).getMenuItem(10)
        verify(menuClient).getMenuItem(11)
        verify(orderRepository).save(persisted)
    }

    @Test
    fun `legacy order saves recovered name for subsequent reads`() {
        val order = Order(visit = visit, waiterId = 2, workerId = 3, totalAmount = BigDecimal("125.50"))
        order.idOrder = 20
        order.items.add(OrderItem(order = order, menuItemId = 10, unitPrice = BigDecimal("125.50"), quantity = 1))
        doReturn(Optional.of(order)).`when`(orderRepository).findById(20)
        doReturn(Mono.just(cocktail)).`when`(menuClient).getMenuItem(10)
        assertEquals("Aurora", service.getOrderById(20, waiter.idUser).items.single().name)
        assertEquals("Aurora", order.items.single().menuItemName)
        clearInvocations(menuClient)
        assertEquals("Aurora", service.getOrderById(20, waiter.idUser).items.single().name)
        verifyNoInteractions(menuClient)
    }

    @Test
    fun `blocked employee cannot create an order`() {
        stubOpenVisit()
        doReturn(Mono.just(waiter.copy(status = ru.severnoyesiyanieBack.entity.UserStatus.BLOCKED)))
            .`when`(userClient).getUser(waiter.idUser)
        val error = assertThrows(org.springframework.web.server.ResponseStatusException::class.java) {
            service.createOrder(request, waiter.idUser)
        }
        assertEquals(org.springframework.http.HttpStatus.FORBIDDEN, error.statusCode)
        verifyNoInteractions(menuClient, orderRepository)
    }

    @Test
    fun `existing order keeps name and prices without contacting deleted or unavailable menu`() {

        val createdAt = LocalDateTime.of(2025, 4, 7, 20, 30)

        val order = Order(
            20,
            visit,
            waiter.idUser,
            worker.idUser,
            BigDecimal("251.00"),
            createdAt
        )

        order.items.add(
            OrderItem(
                order = order,
                menuItemId = cocktail.idPosition,
                unitPrice = BigDecimal("125.50"),
                quantity = 2,
                menuItemName = "Aurora"
            )
        )

        cocktail = cocktail.copy(price = BigDecimal("999.00"), name = "New name")
        doReturn(Mono.just(cocktail)).`when`(menuClient).getMenuItem(10)

        `when`(orderRepository.findById(20))
            .thenReturn(Optional.of(order))

        val response = service.getOrderById(20, waiter.idUser)

        assertEquals(BigDecimal("251.00"), response.totalAmount)
        assertEquals(
            BigDecimal("125.50"),
            response.items.single().unitPrice
        )
        assertEquals(
            BigDecimal("251.00"),
            response.items.single().subtotal
        )
        assertEquals(createdAt, response.createdAt)

        assertEquals("Aurora", response.items.single().name)
        verifyNoInteractions(menuClient)
        verifyNoInteractions(visitRepository)
    }

    @Test
    fun `order response permits missing staff references`() {

        val order = Order(
            idOrder = 20,
            visit = visit,
            totalAmount = BigDecimal.ZERO
        )

        `when`(orderRepository.findById(20))
            .thenReturn(Optional.of(order))

        val response = service.getOrderById(20, waiter.idUser)

        assertNull(response.waiterId)
        assertNull(response.workerId)
        assertTrue(response.items.isEmpty())
    }

    @Test
    fun `missing order is reported`() {

        `when`(orderRepository.findById(99))
            .thenReturn(Optional.empty())

        assertThrows(OrderNotFoundException::class.java) {
            service.getOrderById(99, waiter.idUser)
        }
    }

    @Test
    fun `missing visit prevents creation`() {

        `when`(visitRepository.findById(4))
            .thenReturn(Optional.empty())

        assertThrows(VisitNotFoundException::class.java) {
            service.createOrder(request, waiter.idUser)
        }

        verifyNoInteractions(
            menuClient,
            orderRepository
        )
    }

    @Test
    fun `closed visit rejects a new order`() {

        visit.status = VisitStatus.CLOSED

        `when`(visitRepository.findById(4))
            .thenReturn(Optional.of(visit))

        assertThrows(VisitAlreadyClosedException::class.java) {
            service.createOrder(request, waiter.idUser)
        }

        verifyNoInteractions(
            menuClient,
            orderRepository
        )
    }

    @Test
    fun `missing waiter prevents order persistence`() {

        `when`(visitRepository.findById(4))
            .thenReturn(Optional.of(visit))

        `when`(userClient.getUser(2))
            .thenReturn(Mono.error(RemoteBusinessException(404, "User 2 not found")))

        assertThrows(RemoteBusinessException::class.java) {
            service.createOrder(request, waiter.idUser)
        }

        verifyNoInteractions(
            menuClient,
            orderRepository
        )
    }

    @Test
    fun `a client cannot order for a different visit owner`() {
        stubOpenVisit()
        val otherClient = client.copy(idUser = 55)
        doReturn(Mono.just(otherClient)).`when`(userClient).getUser(otherClient.idUser)

        val failure = assertThrows(org.springframework.web.server.ResponseStatusException::class.java) {
            service.createOrder(request, otherClient.idUser)
        }

        assertEquals(org.springframework.http.HttpStatus.FORBIDDEN, failure.statusCode)
        verifyNoInteractions(menuClient, orderRepository)
    }

    @Test
    fun `a worker cannot place an order`() {
        doReturn(Mono.just(worker)).`when`(userClient).getUser(worker.idUser)

        val failure = assertThrows(org.springframework.web.server.ResponseStatusException::class.java) {
            service.createOrder(request, worker.idUser)
        }

        assertEquals(org.springframework.http.HttpStatus.FORBIDDEN, failure.statusCode)
        verifyNoInteractions(visitRepository, menuClient, orderRepository)
    }

    @Test
    fun `missing menu item prevents saving a partial order`() {

        stubOpenVisit()

        doReturn(Mono.just(cocktail))
            .`when`(menuClient).getMenuItem(10)

        doReturn(Mono.error<MenuItemResponse>(RemoteBusinessException(404, "Menu item 11 not found")))
            .`when`(menuClient).getMenuItem(11)

        assertThrows(RemoteBusinessException::class.java) {
            service.createOrder(request, waiter.idUser)
        }

        verify(menuClient).getMenuItem(10)
        verify(menuClient).getMenuItem(11)

        verifyNoInteractions(orderRepository)
    }

    private fun stubOpenVisit() {
        doReturn(Optional.of(visit)).`when`(visitRepository).findById(4)
    }
}
