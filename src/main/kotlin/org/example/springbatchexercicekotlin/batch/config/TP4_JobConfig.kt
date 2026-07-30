package org.example.springbatchexercicekotlin.batch.config

import jakarta.persistence.EntityManagerFactory
import org.example.springbatchexercicekotlin.batch.VENTES_CSV
import org.example.springbatchexercicekotlin.batch.model.VenteCsvDTO
import org.example.springbatchexercicekotlin.batch.model.VenteEntity
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.Step
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.infrastructure.item.ItemProcessor
import org.springframework.batch.infrastructure.item.ItemWriter
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemReaderBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.FileSystemResource
import org.springframework.transaction.PlatformTransactionManager


@Configuration
class TP4_JobConfig {

    /**
     * Reader : lit ventes.csv, saute l'en-tete, decoupe sur ';' et mappe
     * chaque ligne vers un VenteCsv (donnee brute, non calculee).
     */
    @Bean
    fun venteReader(): FlatFileItemReader<VenteCsvDTO> =
        FlatFileItemReaderBuilder<VenteCsvDTO>()
            .name("venteReader")
            .resource(FileSystemResource(VENTES_CSV))
            .delimited()
            .names("TODO")
            .targetType(VenteCsvDTO::class.java)
            .build()

    /**
     * Processor : transforme la ligne brute en entite persistable et calcule
     * le TTC. Ne retourne JAMAIS null (aucun filtrage).
     */
    @Bean
    fun venteProcessor() = ItemProcessor<VenteCsvDTO, VenteEntity> { null!! }

    /**
     * Writer : persiste le lot en base H2 via JpaItemWriter.
     * On enveloppe le writer JPA pour tracer la TAILLE du lot recu : c'est ce
     * log qui rend visible l'effet de la taille de chunk aux stagiaires.
     */
    @Bean
    fun venteWriter(entityManagerFactory: EntityManagerFactory): ItemWriter<VenteEntity> {
        return ItemWriter { chunk ->
            println("Writer: écriture d'un lot de ${chunk.size()} ventes")
        }
    }

    // Step chunk : lit/traite/ecrit par lots de 10, chaque lot = 1 transaction/commit
    @Bean
    fun tp4Step(
        jobRepository: JobRepository,
        transactionManager: PlatformTransactionManager,
        helloTask : Tasklet
    ): Step =
        StepBuilder("tp4Step", jobRepository)
            .tasklet(helloTask, transactionManager)
            .build()

    @Bean
    fun tp4Job(jobRepository: JobRepository, tp4Step : Step): Job =
        JobBuilder("tp4Job", jobRepository)
            .start( tp4Step)
            .build()
}


