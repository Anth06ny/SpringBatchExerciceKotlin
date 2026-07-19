package org.example.springbatchexercicekotlin.batch

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.parameters.JobParameters
import org.springframework.batch.core.job.parameters.JobParametersBuilder
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException
import org.springframework.batch.core.launch.JobOperator
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.util.UUID
import kotlin.test.assertEquals

/**
 * Suite de validation du TP 3 : tous les tests doivent passer au vert.
 *
 * On n'inspecte pas la console : tout est verifie via les metadonnees
 * que Spring Batch persiste dans le JobRepository (statuts, StepExecutions).
 */
@SpringBootTest
class TP3JobTest {

    @Autowired
    lateinit var jobOperator: JobOperator

    // Injection par nom : il faut un @Bean nomme tp3Job
    @Autowired
    lateinit var tp3Job: Job

    /** Chaque test utilise un runId unique pour partir d'une JobInstance vierge. */
    private fun params(runId: String, fail: Boolean): JobParameters =
        JobParametersBuilder()
            .addString("runId", runId)
            .addString("fail", fail.toString(), false)
            .toJobParameters()

    private fun newRunId() = UUID.randomUUID().toString()

    @Test
    fun `Enchaine les 4 steps dans l'ordre avec succes`() {
        val execution = jobOperator.start(tp3Job, params(newRunId(), fail = false))

        assertEquals(BatchStatus.COMPLETED, execution.status)
        assertEquals(
            listOf("tp3Step1", "tp3Step2", "tp3Step3", "tp3Step4"),
            execution.stepExecutions.sortedBy { it.id }.map { it.stepName },
            "Le job doit executer les 4 steps, dans l'ordre"
        )
    }

    @Test
    fun `Step 3 echoue et step 4 non execute`() {
        val execution = jobOperator.start(tp3Job, params(newRunId(), fail = true))

        assertEquals(BatchStatus.FAILED, execution.status)

        val steps = execution.stepExecutions.sortedBy { it.id }
        assertEquals(
            listOf("tp3Step1", "tp3Step2", "tp3Step3"),
            steps.map { it.stepName },
            "Le step 4 ne doit jamais demarrer apres l'echec du step 3"
        )
        assertEquals(BatchStatus.COMPLETED, steps[0].status)
        assertEquals(BatchStatus.COMPLETED, steps[1].status)
        assertEquals(BatchStatus.FAILED, steps[2].status)
    }

    @Test
    fun `Restart,  step 1 est saute, step 2 est rejoue`() {
        val runId = newRunId()

        // 1ere tentative : echec au step 3
        val execution1 = jobOperator.start(tp3Job, params(runId, fail = true))
        assertEquals(BatchStatus.FAILED, execution1.status)

        // 2eme tentative, MEMES parametres identifiants : restart de la meme instance
        val execution2 = jobOperator.start(tp3Job, params(runId, fail = false))
        assertEquals(BatchStatus.COMPLETED, execution2.status)
        assertEquals(
            execution1.jobInstance.id, execution2.jobInstance.id,
            "Les 2 tentatives doivent appartenir a la meme JobInstance"
        )

        assertEquals(
            listOf("tp3Step2", "tp3Step3", "tp3Step4"),
            execution2.stepExecutions.sortedBy { it.id }.map { it.stepName },
            "Au restart : step1 saute (deja COMPLETED), step2 rejoue (allowStartIfComplete), " +
                "step3 repart, step4 enfin execute"
        )
    }

    @Test
    fun `Non rejeu d'une instance terminee avec succes`() {
        val runId = newRunId()

        jobOperator.start(tp3Job, params(runId, fail = false))

        assertThrows<JobInstanceAlreadyCompleteException> {
            jobOperator.start(tp3Job, params(runId, fail = false))
        }
    }
}
