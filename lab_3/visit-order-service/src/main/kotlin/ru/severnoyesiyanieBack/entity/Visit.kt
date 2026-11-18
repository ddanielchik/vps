package ru.severnoyesiyanieBack.entity

import jakarta.persistence.*
import jakarta.validation.constraints.Positive
import java.time.LocalDateTime

@Entity
@Table(name = "visits")
class Visit(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_visit")
    var idVisit: Int? = null,

    @field:Positive
    @Column(name = "id_user", nullable = false)
    var clientId: Int,

    @field:Positive
    @Column(name = "table_number")
    var tableNumber: Int? = null,

    @Column(name = "opened_at", nullable = false)
    var openedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "closed_at")
    var closedAt: LocalDateTime? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    var status: VisitStatus = VisitStatus.OPEN,

    @ElementCollection
    @CollectionTable(name = "visit_taste_preferences", joinColumns = [JoinColumn(name = "id_visit")])
    var tastePreferences: MutableSet<TastePreference> = mutableSetOf()
)
