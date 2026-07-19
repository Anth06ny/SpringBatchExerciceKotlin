package org.example.springbatchexercicekotlin.batch.config

import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.Step
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.PlatformTransactionManager
import java.util.*

@Configuration
class TP5_JobConfig {


    // Step chunk : lit la base -> ecrit le CSV, par lots de 10.
    @Bean
    fun tp5Step(
        jobRepository: JobRepository,
        transactionManager: PlatformTransactionManager,
        helloTask: Tasklet
    ): Step =
        StepBuilder("tp5Step", jobRepository)
            .tasklet({ _, _ ->
                TODO("A remplacer par un chunk step")
            }, transactionManager)
            .build()


    @Bean
    fun tp5Job(jobRepository: JobRepository, tp5Step: Step): Job =
        JobBuilder("tp5Job", jobRepository)
            .start(tp5Step)
            .build()

    /** Montant a 2 decimales avec un point (comme dans ventes.csv). */
    private fun formatMontant(montant: Double): String =
        String.format(Locale.US, "%.2f", montant)
}
