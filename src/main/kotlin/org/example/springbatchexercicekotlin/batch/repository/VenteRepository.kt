package org.example.springbatchexercicekotlin.batch.repository

import org.example.springbatchexercicekotlin.batch.model.VenteEntity
import org.springframework.data.jpa.repository.JpaRepository

interface VenteRepository : JpaRepository<VenteEntity, Long>