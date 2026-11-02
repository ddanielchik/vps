package ru.severnoyesiyanieBack.entity
import jakarta.persistence.*
import jakarta.validation.constraints.Positive
import java.time.LocalDateTime


@Entity
@Table(name = "visits")

class Visit(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_visit")
    var idVisit: Int? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_user", nullable = false)
    var user: User,

    @field: Positive
    @Column(name = "table_number", nullable = true)
    var tableNumber: Int? = null,

    @Column(name = "opened_at", nullable = false)
    var openedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "closed_at")
    var closedAt: LocalDateTime? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    var status: VisitStatus = VisitStatus.OPEN,

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "visit_taste_preferences",
        joinColumns = [JoinColumn(name = "id_visit")],
        inverseJoinColumns = [JoinColumn(name = "id_tag")]
    )
    var tastePreferences: MutableSet<TasteTag> = mutableSetOf()

)