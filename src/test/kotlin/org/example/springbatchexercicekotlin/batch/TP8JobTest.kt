package org.example.springbatchexercicekotlin.batch

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
import kotlin.test.assertEquals

/**
 * Suite de validation du TP8 partie 1 (ExitStatus personnalise).
 *
 * Le meme step reussit toujours (BatchStatus COMPLETED) mais renvoie un exitCode
 * different selon le chiffre d'affaires du jour (JobParameter `caJour`), decide
 * dans Tp8BilanListener.afterStep. Objectif : OBJECTIF_CA = 1000.
 *
 *   01  CA >= objectif        -> "OBJECTIF_ATTEINT"
 *   02  CA >= objectif / 2    -> "A_SURVEILLER"
 *   03  CA < objectif / 2     -> "ALERTE"
 *   04  le BatchStatus reste COMPLETED quel que soit l'exitCode
 *   05  l'exitCode du job est celui du dernier step (base de l'aiguillage TP8.2)
 *
 * A CODER pour faire passer 01-03 et 05 : le corps de Tp8BilanListener.afterStep.
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.MethodName::class)
class TP8JobTest {

    @Autowired
    lateinit var jobOperator: JobOperator

    @Autowired
    @Qualifier("tp8Job")
    lateinit var tp8Job: Job

    @Test
    fun `01 CA au dessus de l'objectif - exitCode OBJECTIF_ATTEINT`() {
        val execution = lancer(1200.0)

        assertEquals(BatchStatus.COMPLETED, execution.status)
        assertEquals("OBJECTIF_ATTEINT", execution.exitStatus.exitCode,
            "CA (1200) >= objectif (1000) : l'exitCode doit etre OBJECTIF_ATTEINT")
    }

    @Test
    fun `02 CA a la moitie de l'objectif - exitCode A_SURVEILLER`() {
        val execution = lancer(600.0)

        assertEquals(BatchStatus.COMPLETED, execution.status)
        assertEquals("A_SURVEILLER", execution.exitStatus.exitCode,
            "500 <= CA (600) < 1000 : l'exitCode doit etre A_SURVEILLER")
    }

    @Test
    fun `03 CA faible - exitCode ALERTE`() {
        val execution = lancer(200.0)

        assertEquals(BatchStatus.COMPLETED, execution.status)
        assertEquals("ALERTE", execution.exitStatus.exitCode,
            "CA (200) < moitie de l'objectif (500) : l'exitCode doit etre ALERTE")
    }

    @Test
    fun `04 le BatchStatus reste COMPLETED quel que soit l'exitCode`() {
        assertEquals(BatchStatus.COMPLETED, lancer(0.0).status,
            "Un exitCode metier (ALERTE) ne fait PAS echouer le job : c'est un succes nuance")
    }

    @Test
    fun `05 l'exitCode du job reprend celui du dernier step`() {
        val execution = lancer(1200.0)

        val step = execution.stepExecutions.first { it.stepName == "tp8Step" }
        assertEquals(step.exitStatus.exitCode, execution.exitStatus.exitCode,
            "L'ExitStatus du job est propage depuis le step : base de l'aiguillage des flows")
    }

    /* ========================================================================= */
    /* Aides                                                                     */
    /* ========================================================================= */

    /** Lance tp8Job avec le CA voulu ; timestamp unique = nouvelle JobInstance a chaque appel. */
    private fun lancer(caJour: Double): JobExecution {
        val params = JobParametersBuilder()
            .addDouble("caJour", caJour)
            .addLong("timestamp", System.nanoTime())
            .toJobParameters()
        return jobOperator.start(tp8Job, params)
    }
}
