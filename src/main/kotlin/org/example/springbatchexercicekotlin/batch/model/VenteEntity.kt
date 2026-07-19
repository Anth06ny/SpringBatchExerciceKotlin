package org.example.springbatchexercicekotlin.batch.model

import jakarta.persistence.*
import java.time.LocalDate


@Entity
@Table(name = "VENTE")
class VenteEntity(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    var dateVente: LocalDate? = null,

    var boutique: String = "",

    var libelleProduit: String = "",

    var prixHt : Double = 0.0,

    var prixTtc: Double = 0.0
)

