package org.example.springbatchexercicekotlin.batch.config

import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.Step
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration


@Configuration
class TP9_JobConfig {

    @Bean
    fun tp9ex1Job(
        jobRepository: JobRepository,
        preparationStep: Step,
        expeditionStep: Step,
        archivageStep: Step
    ): Job =
        // @formatter:off
        JobBuilder("tp9ex1Job", jobRepository)
            .start(preparationStep)
            .build()
        // @formatter:on

    @Bean
    fun tp9ex2Job(
        jobRepository: JobRepository,
        controleStep: Step,
        expeditionStep: Step,
        preparationStep: Step
    ): Job =
        // @formatter:off
        JobBuilder("tp9ex2Job", jobRepository)
            .start(controleStep)
            .build()
        // @formatter:on

    @Bean
    fun tp9ex3Job(
        jobRepository: JobRepository,
        controleStep: Step,
        archivageStep: Step
    ): Job =
        // @formatter:off
        JobBuilder("tp9ex3Job", jobRepository)
            .start(controleStep)
            .build()
        // @formatter:on

    @Bean
    fun tp9ex4Job(
        jobRepository: JobRepository,
        controleStep: Step,
        alerteStep: Step,
        archivageStep: Step
    ): Job =
        // @formatter:off
        JobBuilder("tp9ex4Job", jobRepository)
            .start(controleStep)
            .build()
        // @formatter:on

    @Bean
    fun tp9ex5Job(
        jobRepository: JobRepository,
        traitementStep: Step,
        notificationStep: Step,
        archivageStep: Step
    ): Job =
        // @formatter:off
        JobBuilder("tp9ex5Job", jobRepository)
            .start(traitementStep)
            .build()
        // @formatter:on

    @Bean
    fun tp9ex6Job(
        jobRepository: JobRepository,
        controleStep: Step,
        preparationStep: Step,
        archivageStep: Step
    ): Job =
        // @formatter:off
        JobBuilder("tp9ex6Job", jobRepository)
            .start(controleStep)
            .build()
        // @formatter:on

    @Bean
    fun tp9ex7Job(
        jobRepository: JobRepository,
        controleStep: Step,
        archivageStep: Step
    ): Job =
        // @formatter:off
        JobBuilder("tp9ex7Job", jobRepository)
            .start(controleStep)
            .build()
        // @formatter:on

    @Bean
    fun tp9ex8Job(
        jobRepository: JobRepository,
        controleStep: Step,
        alerteStep: Step,
        preparationStep: Step,
        traitementStep: Step,
        notificationStep: Step,
        expeditionStep: Step,
        archivageStep: Step
    ): Job =
        // @formatter:off
        JobBuilder("tp9ex8Job", jobRepository)
            .start(controleStep)
            .build()
        // @formatter:on

    @Bean
    fun tp9ex9Job(
        jobRepository: JobRepository,
        controleStep: Step,
        alerteStep: Step,
        notificationStep: Step,
        preparationStep: Step,
        traitementStep: Step,
        rapportStep: Step,
        expeditionStep: Step,
        archivageStep: Step
    ): Job =
        // @formatter:off
        JobBuilder("tp9ex9Job", jobRepository)
            .start(controleStep)
            .build()
        // @formatter:on

    @Bean
    fun tp9ex10Job(
        jobRepository: JobRepository,
        controleStep: Step,
        notificationStep: Step,
        alerteStep: Step,
        traitementStep: Step,
        rapportStep: Step,
        expeditionStep: Step,
        archivageStep: Step
    ): Job =
        // @formatter:off
        JobBuilder("tp9ex10Job", jobRepository)
            .start(controleStep)
            .build()
        // @formatter:on



    @Bean
    fun tp9ex11Job(
        jobRepository: JobRepository,
        importStep: Step,
        notificationStep: Step,
        archivageStep: Step,
        //montantDecider: MontantDecider
    ): Job =
        // @formatter:off
        JobBuilder("tp9ex11Job", jobRepository)
            .start(importStep)
            //.next(montantDecider).on("GROS").to(notificationStep)
            //.from(montantDecider).on("PETIT").to(archivageStep)
            //.end()
            .build()
        // @formatter:on


    @Bean
    fun tp9ex12Job(
        jobRepository: JobRepository,
        importStep: Step,
        rapportStep: Step,
        archivageStep: Step,
        notificationStep: Step
    ): Job =
        // @formatter:off
        JobBuilder("tp9ex12Job", jobRepository)
            .start(importStep)
            .build()
        // @formatter:on

    // EX 13 — Cloture de nuit : DEUX split() enchaines (voir l'histoire + le schema).
    //   import -> [prepa->expedition || rapport->archivage] -> traitement -> [notification || archivage]
    @Bean
    fun tp9ex13Job(
        jobRepository: JobRepository,
        importStep: Step,
        preparationStep: Step,
        expeditionStep: Step,
        rapportStep: Step,
        traitementStep: Step,
        notificationStep: Step,
        archivageStep: Step
    ): Job =
        // @formatter:off
        JobBuilder("tp9ex13Job", jobRepository)
            .start(importStep)
            .build()
        // @formatter:on
}
