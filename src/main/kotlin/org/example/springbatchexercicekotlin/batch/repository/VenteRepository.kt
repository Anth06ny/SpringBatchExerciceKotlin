package org.example.springbatchexercicekotlin.batch.repository

import org.example.springbatchexercicekotlin.batch.model.VenteEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface VenteRepository : JpaRepository<VenteEntity, Long> {
    @Query("SELECT SUM(v.prixTtc) FROM VenteEntity v")
    fun sumPrixTtc(): Double?
}