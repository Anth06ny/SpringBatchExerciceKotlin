package org.example.springbatchexercicekotlin.batch.config

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

@Configuration
class TP1_2_JobConfig {

    // Batch rappelle execute() jusqu'à RepeatStatus.FINISHED.
    @Bean
    fun helloTask(): Tasklet = Tasklet { contribution, chunkContext ->

        // Recuperation des parametres du job (Map<String, Object>)
        val message = chunkContext.stepContext.jobParameters["message"] as? String ?: "-"

        println("Bonjour depuis un Tasklet ! : $message")
        RepeatStatus.FINISHED
    }

    // Une etape de type Tasklet
    @Bean
    fun helloStep(
        jobRepository: JobRepository,
        transactionManager: PlatformTransactionManager
    ): Step =
        StepBuilder("helloStep", jobRepository)
            .tasklet(helloTask(), transactionManager)
            .build()

    // Le Job : un enchainement d'etapes (ici une seule)
    @Bean
    fun helloJob(jobRepository: JobRepository, helloStep: Step): Job =
        JobBuilder("helloJob", jobRepository)
            .start(helloStep)
            .build()

    /* -------------------------------- */
    // 2eme tâche
    /* -------------------------------- */


}