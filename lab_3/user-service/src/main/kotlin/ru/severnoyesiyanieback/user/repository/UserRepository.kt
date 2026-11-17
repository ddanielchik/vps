package ru.severnoyesiyanieback.user.repository

import org.springframework.data.repository.reactive.ReactiveCrudRepository
import ru.severnoyesiyanieback.user.entity.User

interface UserRepository : ReactiveCrudRepository<User, Int>
