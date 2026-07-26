package org.example.springbatchexercicekotlin.batch

import org.example.springbatchexercicekotlin.batch.config.TP11_JobConfig
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
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Suite de validation du TP11 (partitionnement).
 *
 *   01  PARTITIONER   : le job lance 10 partitions (une par fichier/boutique).
 *   02  ISOLATION     : total importe == 500, chaque partition n'a lu QUE sa boutique
 *                       (50 ventes par boutique, 10 boutiques distinctes).
 *   03  PARALLELISME  : plusieurs threads utilises + duree < plancher sequentiel.
 *   04  REPRISE       : une partition en echec -> job FAILED ; au restart (meme instance),
 *                       SEULE la partition FAILED rejoue, le job finit COMPLETED.
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.MethodName::class)
class TP11JobTest {

    @Autowired
    lateinit var jobOperator: JobOperator

    @Autowired
    @Qualifier("tp11Job")
    lateinit var tp11Job: Job

    @Autowired
    lateinit var venteRepository: VenteRepository

    @Autowired
    lateinit var tp11Tracker: TP11_JobConfig.Tp11Tracker

    @Test
    fun `01 partitioner - dix partitions lancees`() {
        prepare()
        val execution = lancer()

        assertEquals(BatchStatus.COMPLETED, execution.status)
        val workers = execution.stepExecutions.filter { it.stepName.startsWith("tp11WorkerStep:") }
        assertEquals(10, workers.size, "Une partition (step esclave) par fichier")
    }

    @Test
    fun `02 isolation - chaque partition importe sa seule boutique`() {
        prepare()
        val execution = lancer()
        assertEquals(BatchStatus.COMPLETED, execution.status)

        // Tout est importe, sans perte ni doublon.
        assertEquals(500, venteRepository.count(), "500 ventes au total")

        // Chaque boutique est presente 50 fois : preuve que chaque partition n'a traite
        // QUE son fichier (aucun recouvrement).
        val parBoutique = venteRepository.findAll().groupingBy { it.boutique }.eachCount()
        assertEquals(10, parBoutique.size, "10 boutiques distinctes")
        assertTrue(
            parBoutique.values.all { it == 50 },
            "Chaque boutique importee exactement 50 fois : $parBoutique"
        )
    }

    @Test
    fun `03 parallelisme - plusieurs threads et gain de temps`() {
        prepare()
        val execution = lancer()
        assertEquals(BatchStatus.COMPLETED, execution.status)

        val threads = tp11Tracker.threads()
        assertTrue(
            threads.size > 1,
            "Les partitions doivent tourner sur plusieurs threads. Threads vus : $threads"
        )

        // Plancher sequentiel = 500 items x sleep. En parallele on passe dessous.
        val plancherSequentiel = 500 * TP11_SLEEP_MS
        val duree = Duration.between(execution.startTime, execution.endTime).toMillis()
        assertTrue(
            duree < plancherSequentiel,
            "Duree=${duree}ms >= plancher sequentiel ${plancherSequentiel}ms : pas parallelise"
        )
    }

    @Test
    fun `04 reprise - seule la partition en echec rejoue au restart`() {
        prepare()
        val runId = "tp11-restart-${System.nanoTime()}"

        // 1er lancement : B10 casse -> sa partition FAILED, les 9 autres COMPLETED.
        val premier = lancerRestart(runId, casserB10 = true)
        assertEquals(BatchStatus.FAILED, premier.status)
        val workers1 = premier.stepExecutions.filter { it.stepName.startsWith("tp11WorkerStep:") }
        assertEquals(1, workers1.count { it.status == BatchStatus.FAILED }, "1 partition en echec")
        assertEquals(9, workers1.count { it.status == BatchStatus.COMPLETED }, "9 partitions OK")

        // Restart (MEME instance) sans casser : seule la partition B10 doit rejouer.
        val second = lancerRestart(runId, casserB10 = false)
        assertEquals(BatchStatus.COMPLETED, second.status, "La reprise mene le job au bout")
        val workers2 = second.stepExecutions.filter { it.stepName.startsWith("tp11WorkerStep:") }
        assertEquals(
            1, workers2.size,
            "Au restart, SEULE la partition en echec rejoue (les 9 COMPLETED sont sautees)"
        )
        assertEquals(500, venteRepository.count(), "Les 500 ventes sont finalement en base")
    }

    /* ========================================================================= */
    /* Aides                                                                     */
    /* ========================================================================= */

    private fun prepare() {
        tp11Tracker.reset()
        venteRepository.deleteAll()
    }

    private fun lancer(): JobExecution {
        val params = JobParametersBuilder()
            .addLong("run", System.nanoTime())
            .toJobParameters()
        return jobOperator.start(tp11Job, params)
    }

    /** runId IDENTIFIANT fixe (restart) + casserB10 NON identifiant. */
    private fun lancerRestart(runId: String, casserB10: Boolean): JobExecution {
        val params = JobParametersBuilder()
            .addString("runId", runId)
            .addString("casserB10", casserB10.toString(), false)
            .toJobParameters()
        return jobOperator.start(tp11Job, params)
    }
}
