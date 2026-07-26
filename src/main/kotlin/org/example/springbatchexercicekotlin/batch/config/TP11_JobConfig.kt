package org.example.springbatchexercicekotlin.batch.config

import jakarta.persistence.EntityManagerFactory
import org.example.springbatchexercicekotlin.batch.CHUNK_SIZE
import org.example.springbatchexercicekotlin.batch.TP11_SLEEP_MS
import org.example.springbatchexercicekotlin.batch.TVA
import org.example.springbatchexercicekotlin.batch.model.VenteEntity
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.Step
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.batch.infrastructure.item.ItemProcessor
import org.springframework.batch.infrastructure.item.ItemStreamReader
import org.springframework.batch.infrastructure.item.ItemWriter
import org.springframework.batch.infrastructure.item.database.builder.JpaItemWriterBuilder
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemReaderBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.FileSystemResource
import org.springframework.transaction.PlatformTransactionManager
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap


@Configuration
class TP11_JobConfig {

    @Bean
    @StepScope
    fun tp11Reader(): ItemStreamReader<TP11VenteDTO> =
        FlatFileItemReaderBuilder<TP11VenteDTO>()
            .name("tp11Reader")
            .resource(FileSystemResource("data/tp11/ventes_B01.csv"))
            .linesToSkip(1) // ligne d'en-tete
            .delimited()
            .delimiter(";")
            .names("date", "idBoutique", "produit", "montantHt")
            .targetType(TP11VenteDTO::class.java)
            .build()


    @Bean
    @StepScope
    fun tp11Processor(
        @Value("#{jobParameters['casserB10'] ?: false}") casserB10: Boolean,
        tp11Tracker: Tp11Tracker
    ): ItemProcessor<TP11VenteDTO, VenteEntity> =
        ItemProcessor { dto ->
            tp11Tracker.enregistrerThread(Thread.currentThread().name)
            Thread.sleep(TP11_SLEEP_MS) // simule une tarification lente

            if (casserB10 && dto.idBoutique == "B10") {
                throw IllegalStateException("Panne simulée sur la boutique B10")
            }

            VenteEntity(
                dateVente = LocalDate.parse(dto.date),
                boutique = dto.idBoutique,
                libelleProduit = dto.produit,
                prixHt = dto.montantHt,
                prixTtc = dto.montantHt * TVA
            )
        }

    @Bean
    fun tp11Writer(entityManagerFactory: EntityManagerFactory): ItemWriter<VenteEntity> =
        JpaItemWriterBuilder<VenteEntity>()
            .entityManagerFactory(entityManagerFactory)
            .build()


    // Step chunk : lit la base -> ecrit le CSV, par lots de 10.
    @Bean
    fun tp11WorkerStep(
        jobRepository: JobRepository,
        transactionManager: PlatformTransactionManager,
        tp11Reader: ItemStreamReader<TP11VenteDTO>,
        tp11Processor: ItemProcessor<TP11VenteDTO, VenteEntity>,
        tp11Writer: ItemWriter<VenteEntity>
    ): Step =
        StepBuilder("tp11WorkerStep", jobRepository)
            .chunk<TP11VenteDTO, VenteEntity>(CHUNK_SIZE)
            .reader(tp11Reader)
            .processor(tp11Processor)
            .writer(tp11Writer)
            .transactionManager(transactionManager)
            .build()


    @Bean
    fun tp11Job(jobRepository: JobRepository, tp11WorkerStep: Step): Job =
        JobBuilder("tp11Job", jobRepository)
            .start(tp11WorkerStep)
            .build()

    /* ------------------------------------------------------------------ */
    /* Infra fournie (DTO + tracker)                                       */
    /* ------------------------------------------------------------------ */

    /** DTO du CSV, propre au TP11. */
    class TP11VenteDTO {
        var date: String = ""
        var idBoutique: String = ""
        var produit: String = ""
        var montantHt: Double = 0.0
    }

    /** Collecteur thread-safe des threads utilises (preuve du parallelisme, test 3). */
    class Tp11Tracker {
        private val threads = ConcurrentHashMap.newKeySet<String>()
        fun enregistrerThread(nom: String) {
            threads.add(nom)
        }

        fun threads(): Set<String> = threads.toSet()
        fun reset() = threads.clear()
    }

    @Bean
    fun tp11Tracker(): Tp11Tracker = Tp11Tracker()
}
