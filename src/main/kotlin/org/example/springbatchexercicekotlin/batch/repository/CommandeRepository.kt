package org.example.springbatchexercicekotlin.batch.repository

import org.example.springbatchexercicekotlin.batch.model.CommandeEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface CommandeRepository : JpaRepository<CommandeEntity, Long> {

    /** Les villes presentes en base (une par entree du camions.txt), triees. */
    @Query("SELECT DISTINCT c.city FROM CommandeEntity c ORDER BY c.city")
    fun findCity(): List<String>

    /** Toutes les commandes d'une ville, triees par magasin (pour le fichier chauffeur). */
    fun findByCityOrderByShop(ville: String): List<CommandeEntity>
}
