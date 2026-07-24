package org.example.springbatchexercicekotlin.batch.config

import org.example.springbatchexercicekotlin.batch.OBJECTIF_CA
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.Step
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.infrastructure.repeat.RepeatStatus
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.PlatformTransactionManager

/**
 * TP8 (partie 1) — ExitStatus personnalise.
 *
 * Objectif : un step qui REUSSIT toujours (BatchStatus = COMPLETED), mais dont
 * l'exitCode varie selon le chiffre d'affaires du jour (JobParameter `caJour`) :
 *
 *   caJour >= OBJECTIF_CA        -> "OBJECTIF_ATTEINT"
 *   caJour >= OBJECTIF_CA / 2    -> "A_SURVEILLER"
 *   sinon                        -> "ALERTE"
 *
 * A distinguer :
 *   - BatchStatus : enum interne (COMPLETED / FAILED...) — reste COMPLETED ici.
 *   - ExitStatus  : exitCode + description, sert au reporting et a l'aiguillage
 *                   (.on("ALERTE").to(...)) — c'est lui qu'on module.
 *
 * A CODER : le corps de Tp8BilanListener.afterStep (voir le TODO).
 */
@Configuration
class TP8_JobConfig {

    /* ------------------------------------------------------------------ */
    /* Step : un Tasklet minimal — tout l'interet est dans le LISTENER.    */
    /* ------------------------------------------------------------------ */

    @Bean
    fun tp8Step(
        jobRepository: JobRepository,
        transactionManager: PlatformTransactionManager,
    ): Step {

        // Le step ne "fait" presque rien : il lit juste le CA pour la console
        // (lecture d'un parametre depuis un Tasklet, comme au TP2).
        val bilanTask = Tasklet { _, chunkContext ->
            val caJour = chunkContext.stepContext.jobParameters["caJour"] as? Double ?: 0.0
            println("TP8 : CA du jour = $caJour € (objectif = $OBJECTIF_CA €)")
            RepeatStatus.FINISHED
        }

        return StepBuilder("tp8Step", jobRepository)
            .tasklet(bilanTask, transactionManager)
            .build()
    }

    @Bean
    fun tp8Job(jobRepository: JobRepository, tp8Step: Step): Job =
        JobBuilder("tp8Job", jobRepository)
            .start(tp8Step)
            .build()
}
