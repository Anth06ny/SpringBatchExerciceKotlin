package org.example.springbatchexercicekotlin.batch.model

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "COMMANDE")
class CommandeEntity(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    var city: String = "",

    var shop: String = "",

    var nb: Int = 0
)
