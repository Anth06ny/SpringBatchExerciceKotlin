package org.example.springbatchexercicekotlin.batch.config

import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.annotation.AfterStep
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.Step
import org.springframework.batch.core.step.StepExecution
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.infrastructure.repeat.RepeatStatus
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.PlatformTransactionManager


@Configuration
class TP9_Steps {

    fun logStep(nom: String, jobRepository: JobRepository, tm: PlatformTransactionManager): Step {
        val task = Tasklet { _, _ ->
            println("🧩 $nom")
            RepeatStatus.FINISHED
        }
        return StepBuilder(nom, jobRepository)
            .tasklet(task, tm)
            .build()
    }

    @Bean
    fun preparationStep(jobRepository: JobRepository, tm: PlatformTransactionManager): Step =
        logStep("preparationStep", jobRepository, tm)

    @Bean
    fun expeditionStep(jobRepository: JobRepository, tm: PlatformTransactionManager): Step =
        logStep("expeditionStep", jobRepository, tm)

    @Bean
    fun archivageStep(jobRepository: JobRepository, tm: PlatformTransactionManager): Step =
        logStep("archivageStep", jobRepository, tm)

    @Bean
    fun alerteStep(jobRepository: JobRepository, tm: PlatformTransactionManager): Step =
        logStep("alerteStep", jobRepository, tm)

    @Bean
    fun notificationStep(jobRepository: JobRepository, tm: PlatformTransactionManager): Step =
        logStep("notificationStep", jobRepository, tm)

    @Bean
    fun rapportStep(jobRepository: JobRepository, tm: PlatformTransactionManager): Step =
        logStep("rapportStep", jobRepository, tm)

    @Bean
    fun importStep(jobRepository: JobRepository, tm: PlatformTransactionManager): Step =
        logStep("importStep", jobRepository, tm)

    @Bean
    fun controleStep(jobRepository: JobRepository, tm: PlatformTransactionManager): Step {
        val task = Tasklet { _, chunkContext ->
            val scenario = chunkContext.stepContext.jobParameters["scenario"] ?: ""
            println("🧩 controleStep (scenario=$scenario)")
            RepeatStatus.FINISHED
        }
        return StepBuilder("controleStep", jobRepository)
            .tasklet(task, tm)
            .listener(ScenarioListener())
            // INDISPENSABLE pour le stopAndRestart(controleStep) de l'ex7 : sans ca, au
            // restart Spring saute controleStep (deja COMPLETED) et REUTILISE son ancien
            // ExitStatus (ATTENTE) -> il redonne STOPPED indefiniment. allowStartIfComplete
            // force le RE-jeu : controleStep relit le scenario (change) et repart.
            .allowStartIfComplete(true)
            .build()
    }

    class ScenarioListener {
        @AfterStep
        fun afterStep(stepExecution: StepExecution): ExitStatus {
            val scenario = stepExecution.jobExecution.jobParameters.getString("scenario")
            return if (scenario.isNullOrBlank()) ExitStatus.COMPLETED else ExitStatus(scenario)
        }
    }

    @Bean
    fun traitementStep(jobRepository: JobRepository, tm: PlatformTransactionManager): Step {
        val task = Tasklet { _, chunkContext ->
            val echouer = chunkContext.stepContext.jobParameters["echouer"] == "true"
            println("🧩 traitementStep (echouer=$echouer)")
            if (echouer) {
                throw IllegalStateException("Traitement en échec (simulation)")
            }
            RepeatStatus.FINISHED
        }
        return StepBuilder("traitementStep", jobRepository)
            .tasklet(task, tm)
            .build()
    }
}
