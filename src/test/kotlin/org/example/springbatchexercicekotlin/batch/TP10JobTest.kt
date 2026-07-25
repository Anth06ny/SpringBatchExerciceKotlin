package org.example.springbatchexercicekotlin.batch

import org.example.springbatchexercicekotlin.batch.config.TP10_JobConfig
import org.example.springbatchexercicekotlin.batch.repository.VenteRepository
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.JobExecution
import org.springframework.batch.core.job.parameters.JobParametersBuilder
import org.springframework.batch.core.launch.JobOperator
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import java.io.File
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest
@TestMethodOrder(MethodOrderer.MethodName::class)
class TP10JobTest {

    @Autowired
    lateinit var jobOperator: JobOperator

    @Autowired
    @Qualifier("tp10Job")
    lateinit var tp10Job: Job

    @Autowired
    lateinit var venteRepository: VenteRepository

    @Autowired
    lateinit var tp10Tracker: TP10_JobConfig.Tp10Tracker

    @Test
    fun `01 etat partage - numeros de traitement tous uniques`() {
        tp10Tracker.reset()
        venteRepository.deleteAll()

        val execution = lancer()
        assertEquals(BatchStatus.COMPLETED, execution.status)

        val attendues = lignesData(TP10_VENTES_CSV)
        val step = execution.stepExecutions.first { it.stepName == "tp10Step" }

        // Les items ne sont JAMAIS perdus : reader et writer sont mono-thread en Batch 6.
        assertEquals(attendues, step.writeCount, "Tous les items sont ecrits")
        assertEquals(attendues, venteRepository.count(), "Toutes les ventes sont en base")

        // LE point : le compteur PARTAGE du processor doit rester correct en parallele.
        // Des numeros dupliques = lost updates = compteur non thread-safe.
        assertEquals(attendues.toInt(), tp10Tracker.totalNumeros(),
            "Chaque vente recoit un numero")
        assertEquals(attendues.toInt(), tp10Tracker.numerosDistincts(),
            "Les numeros doivent etre TOUS uniques : des doublons = course sur l'etat partage")
    }

    @Test
    fun `02 parallelisme reel - plusieurs threads utilises`() {
        tp10Tracker.reset()
        venteRepository.deleteAll()

        val execution = lancer()
        assertEquals(BatchStatus.COMPLETED, execution.status)

        val threads = tp10Tracker.threads()
        assertTrue(threads.size > 1,
            "Le processing doit s'executer sur plusieurs threads (.taskExecutor). Threads vus : $threads")
    }

    @Test
    fun `03 gain de temps - le parallelisme passe sous le temps mono-thread`() {
        tp10Tracker.reset()
        venteRepository.deleteAll()

        val execution = lancer()
        assertEquals(BatchStatus.COMPLETED, execution.status)

        // Temps PLANCHER en mono-thread : la somme des sleeps du processor (items traites
        // l'un apres l'autre). Un run parallelise passe largement dessous. Seuil robuste :
        // Thread.sleep libere le CPU, les sleeps se recouvrent meme avec peu de coeurs.
        val attendues = lignesData(TP10_VENTES_CSV)
        val plancherMono = attendues * TP10_SLEEP_MS

        val duree = Duration.between(execution.startTime, execution.endTime).toMillis()

        assertTrue(duree < plancherMono,
            "Duree=${duree}ms >= plancher mono-thread ${plancherMono}ms : le step n'est PAS parallelise")
    }

    @Test
    fun `04 numero dans le libelle - le dernier produit porte le numero 500`() {
        tp10Tracker.reset()
        venteRepository.deleteAll()

        val execution = lancer()
        assertEquals(BatchStatus.COMPLETED, execution.status)

        val attendues = lignesData(TP10_VENTES_CSV).toInt()

        // Chaque libelleProduit persiste se termine par "_<numero>" (les noms de produits
        // ne contiennent pas de "_"). Avec un compteur correct, les numeros sont exactement
        // 1..500 -> le plus grand (le "dernier" attribue) vaut 500.
        val numeros = venteRepository.findAll()
            .map { it.libelleProduit.substringAfterLast("_").toInt() }

        assertEquals(attendues, numeros.max(),
            "Le dernier numero attribue (le plus grand) doit valoir $attendues")
        assertEquals(attendues, numeros.toSet().size,
            "Les numeros portes par les libelles doivent etre tous uniques (1..$attendues)")
    }

    private fun lancer(): JobExecution {
        val params = JobParametersBuilder()
            .addLong("run", System.nanoTime())
            .toJobParameters()
        return jobOperator.start(tp10Job, params)
    }

    /** Nombre de lignes de donnees du CSV (hors en-tete, hors lignes vides). */
    private fun lignesData(chemin: String): Long =
        File(chemin).readLines().drop(1).count { it.isNotBlank() }.toLong()
}
