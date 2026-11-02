package ru.severnoyesiyanieBack.repository

import org.springframework.data.jpa.repository.JpaRepository
import ru.severnoyesiyanieBack.entity.Order

interface OrderRepository : JpaRepository<Order, Int>