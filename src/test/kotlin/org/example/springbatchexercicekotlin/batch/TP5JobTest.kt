package org.example.springbatchexercicekotlin.batch

import org.example.springbatchexercicekotlin.batch.repository.VenteRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.parameters.JobParametersBuilder
import org.springframework.batch.core.launch.JobOperator
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import java.io.File
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Suite de validation du TP5 (export DB -> CSV).
 *
 * Mise en place : on importe d'abord ventes.csv en base (tp4Job) puis on genere
 * ventes_tp5.csv (tp5Job). Les assertions portent ensuite sur le fichier produit.
 */
@SpringBootTest
class TP5JobTest {

    @Autowired
    lateinit var jobOperator: JobOperator

    @Autowired
    @Qualifier("tp4Job")
    lateinit var tp4Job: Job

    @Autowired
    @Qualifier("tp5Job")
    lateinit var tp5Job: Job

    @Autowired
    lateinit var venteRepository: VenteRepository

    /** Table remise a zero, puis import (TP4) + export (TP5) : le fichier est pret pour les tests. */
    @BeforeEach
    fun genererLeCsv() {
        venteRepository.deleteAll()
        lancer(tp4Job)
        lancer(tp5Job)
    }

    @Test
    fun `ventes_tp5_csv triees par montant decroissant`() {
        // Reference : les lignes de ventes.csv, triees par montant DECROISSANT.
        val attendues = lignesDataSource().sortedByDescending { montant(it) }

        // Observe : uniquement les lignes de donnees du fichier genere (en-tete et
        // pied de page exclus). Ce test reste vrai meme SANS ligne d'en-tete, car on
        // ne garde que les lignes ayant un montant numerique en 4e colonne.
        val obtenues = lignesFichier().filter { estLigneData(it) }

        assertEquals(
            attendues, obtenues,
            "Les ventes exportees doivent etre celles de ventes.csv, triees par montant HT decroissant"
        )
    }

    @Test
    fun `ventes_tp5_csv avec header`() {
        assertEquals(
            "date;idBoutique;produit;montantHt", lignesFichier().first(),
            "La 1re ligne du fichier doit etre l'en-tete des colonnes"
        )
    }

    @Test
    fun `ventes_tp5_csv avec footer`() {
        val totalAttendu = lignesDataSource().sumOf { montant(it) } * TVA

        val derniere = lignesFichier().last()
        assertTrue(
            derniere.startsWith("Total : "),
            "La derniere ligne doit commencer par 'Total : ' (etait: \"$derniere\")"
        )
        assertEquals(
            "Total : ${format(totalAttendu)}", derniere,
            "Le total en pied de page doit valoir la somme des montants TTC"
        )
    }

    /* -------------------------------- Helpers -------------------------------- */

    private fun lancer(job: Job) {
        val params = JobParametersBuilder()
            .addLong("timestamp", System.nanoTime())
            .toJobParameters()
        val execution = jobOperator.start(job, params)
        assertEquals(
            BatchStatus.COMPLETED,
            execution.status,
            "${job.name} doit se terminer avec succes"
        )
    }

    /** Lignes non vides du fichier genere (racine du projet = repertoire de travail). */
    private fun lignesFichier(): List<String> =
        File(TP5_OUTPUT).readLines().filter { it.isNotBlank() }

    /** Lignes de donnees de ventes.csv (en-tete saute). */
    private fun lignesDataSource(): List<String> =
        File(VENTES_CSV).bufferedReader()
            .useLines { lines -> lines.filter { it.isNotBlank() }.drop(1).toList() }

    /** Une ligne de donnees = 4 colonnes dont la 4e est un montant numerique (exclut en-tete et total). */
    private fun estLigneData(ligne: String): Boolean {
        val cols = ligne.split(";")
        return cols.size == 4 && cols[3].toDoubleOrNull() != null
    }

    private fun montant(ligne: String): Double = ligne.split(";")[3].toDouble()

    private fun format(montant: Double): String = String.format(Locale.US, "%.2f", montant)
}
