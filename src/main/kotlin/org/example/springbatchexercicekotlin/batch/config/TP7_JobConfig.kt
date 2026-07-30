package org.example.springbatchexercicekotlin.batch.config

import jakarta.persistence.EntityManagerFactory
import jakarta.persistence.QueryTimeoutException
import org.example.springbatchexercicekotlin.batch.CHUNK_SIZE
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
import org.springframework.batch.infrastructure.item.database.JpaItemWriter
import org.springframework.batch.infrastructure.item.database.builder.JpaItemWriterBuilder
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemReaderBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.FileSystemResource
import org.springframework.transaction.PlatformTransactionManager
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger

@Configuration
class TP7_JobConfig {

    @Bean
    @StepScope
    fun tp7Reader(
        @Value("#{jobParameters['fichierSource']}") fichierSource: String
    ): ItemStreamReader<TP7VenteDTO> =
        FlatFileItemReaderBuilder<TP7VenteDTO>()
            .name("tp7Reader")
            .resource(FileSystemResource(fichierSource))
            .linesToSkip(1) // ligne d'en-tete
            .delimited()
            .delimiter(";")
            .names("date", "idBoutique", "produit", "montantHt")
            .targetType(TP7VenteDTO::class.java)
            .build()

    /* ------------------------------------------------------------------ */
    /* Processor : mapping TP4 + regle metier + panne simulee              */
    /* ------------------------------------------------------------------ */

    @Bean
    @StepScope
    fun tp7Processor(
        @Value("#{jobParameters['risque']}") risque: Boolean
    ): ItemProcessor<TP7VenteDTO, VenteEntity> {
        val compteurAppels = AtomicInteger(0)
        return ItemProcessor { csv ->
            if (risque) {
                val appel = compteurAppels.incrementAndGet()
                if (appel % 10 == 0) {
                    throw QueryTimeoutException("Panne simulée (appel n°$appel)")
                }
            }

            VenteEntity(
                dateVente = LocalDate.parse(csv.date),
                boutique = csv.idBoutique,
                libelleProduit = csv.produit,
                prixHt = csv.montantHt,
                prixTtc = csv.montantHt * TVA
            )
        }
    }

    /* ------------------------------------------------------------------ */
    /* Writer : le meme JpaItemWriter que le TP4, avec le log de lot       */
    /* ------------------------------------------------------------------ */

    @Bean
    fun tp7Writer(entityManagerFactory: EntityManagerFactory): ItemWriter<VenteEntity> {
        val jpaWriter: JpaItemWriter<VenteEntity> = JpaItemWriterBuilder<VenteEntity>()
            .entityManagerFactory(entityManagerFactory)
            //.usePersist(true)
            .build()

        return ItemWriter { chunk ->
            println("TP7 Writer: écriture d'un lot de ${chunk.size()} ventes")
            jpaWriter.write(chunk)
        }
    }


    /* ------------------------------------------------------------------ */
    /* Step : chunk + faultTolerant + skip + retry                         */
    /* ------------------------------------------------------------------ */

    @Bean
    fun tp7Step(
        jobRepository: JobRepository,
        transactionManager: PlatformTransactionManager,
        tp7Reader: ItemStreamReader<TP7VenteDTO>,
        tp7Processor: ItemProcessor<TP7VenteDTO, VenteEntity>,
        tp7Writer: ItemWriter<VenteEntity>
    ): Step =
        StepBuilder("tp7Step", jobRepository)
            .chunk<TP7VenteDTO, VenteEntity>(CHUNK_SIZE)
            .reader(tp7Reader)
            .processor(tp7Processor)
            .writer(tp7Writer)
            .transactionManager(transactionManager)
            .build()

    @Bean
    fun tp7Job(jobRepository: JobRepository, tp7Step: Step): Job =
        JobBuilder("tp7Job", jobRepository)
            .start(tp7Step)
            .build()

    /** DTO du CSV, propre au TP7 (VenteCsvDTO est l'exercice du TP4, on n'en depend pas). */
    class TP7VenteDTO {
        var date: String = ""
        var idBoutique: String = ""
        var produit: String = ""
        var montantHt: Double = 0.0
    }
}
