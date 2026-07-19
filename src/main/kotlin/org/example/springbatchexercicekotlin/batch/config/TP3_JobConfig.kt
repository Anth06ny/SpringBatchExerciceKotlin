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
class TP3_JobConfig {

    // Un Tasklet qui ne fait qu'afficher un message
    private fun logTasklet(message: String) = Tasklet { contribution, chunkContext ->
        println(message)
        RepeatStatus.FINISHED
    }

    @Bean
    fun tp3Step1(
        jobRepository: JobRepository,
        transactionManager: PlatformTransactionManager
    ): Step = StepBuilder("tp3Step1", jobRepository)
        .tasklet(logTasklet("TP3 - Step 1"), transactionManager)
        .build()

    @Bean
    fun tp3Job(
        jobRepository: JobRepository,
        tp3Step1: Step
    ): Job = JobBuilder("tp3Job", jobRepository)
        .start(tp3Step1)
        .build()

}
