package org.example.springbatchexercicekotlin.batch.config

import jakarta.persistence.EntityManagerFactory
import org.example.springbatchexercicekotlin.batch.CHUNK_SIZE
import org.example.springbatchexercicekotlin.batch.TP10_SLEEP_MS
import org.example.springbatchexercicekotlin.batch.TP10_VENTES_CSV
import org.example.springbatchexercicekotlin.batch.TVA
import org.example.springbatchexercicekotlin.batch.model.VenteEntity
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.Step
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.batch.infrastructure.item.ItemProcessor
import org.springframework.batch.infrastructure.item.ItemReader
import org.springframework.batch.infrastructure.item.ItemWriter
import org.springframework.batch.infrastructure.item.database.builder.JpaItemWriterBuilder
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemReaderBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.FileSystemResource
import org.springframework.transaction.PlatformTransactionManager
import java.time.LocalDate
import java.util.*
import java.util.concurrent.ConcurrentHashMap

@Configuration
class TP10_JobConfig {


    @Bean
    fun tp10Reader(): ItemReader<TP10VenteDTO> =
        FlatFileItemReaderBuilder<TP10VenteDTO>()
            .name("tp10Reader")
            .resource(FileSystemResource(TP10_VENTES_CSV))
            .linesToSkip(1) // ligne d'en-tete
            .delimited()
            .delimiter(";")
            .names("date", "idBoutique", "produit", "montantHt")
            .targetType(TP10VenteDTO::class.java)
            .build()

    @Bean
    @StepScope
    fun tp10Processor(tp10Tracker: Tp10Tracker): ItemProcessor<TP10VenteDTO, VenteEntity> {
        var compteur = 0
        return ItemProcessor { dto ->
            tp10Tracker.enregistrerThread(Thread.currentThread().name)
            val numero = compteur + 1
            Thread.sleep(TP10_SLEEP_MS)
            compteur = numero
            tp10Tracker.enregistrerNumero(numero)
            VenteEntity(
                dateVente = LocalDate.parse(dto.date),
                boutique = dto.idBoutique,
                libelleProduit = dto.produit + "_$numero",
                prixHt = dto.montantHt,
                prixTtc = dto.montantHt * TVA
            )
        }
    }


    @Bean
    fun tp10Writer(entityManagerFactory: EntityManagerFactory): ItemWriter<VenteEntity> =
        JpaItemWriterBuilder<VenteEntity>()
            .entityManagerFactory(entityManagerFactory)
            .build()

    @Bean
    fun tp10Step(
        jobRepository: JobRepository,
        transactionManager: PlatformTransactionManager,
        tp10Reader: ItemReader<TP10VenteDTO>,
        tp10Processor: ItemProcessor<TP10VenteDTO, VenteEntity>,
        tp10Writer: ItemWriter<VenteEntity>,
    ): Step =
        StepBuilder("tp10Step", jobRepository)
            .chunk<TP10VenteDTO, VenteEntity>(CHUNK_SIZE)
            .reader(tp10Reader)
            .processor(tp10Processor)
            .writer(tp10Writer)
            .transactionManager(transactionManager)
            .build()


    @Bean
    fun tp10Job(jobRepository: JobRepository, tp10Step: Step): Job =
        JobBuilder("tp10Job", jobRepository)
            .start(tp10Step)
            .build()

    /* ------------------------------------------------------------------ */
    /* Infra fournie (DTO + tracker) — ne pas modifier                     */
    /* ------------------------------------------------------------------ */

    class TP10VenteDTO {
        var date: String = ""
        var idBoutique: String = ""
        var produit: String = ""
        var montantHt: Double = 0.0
    }

    /**
     * Collecteur thread-safe pour la demo et les tests :
     *   - les threads reellement utilises par le processor (preuve du parallelisme) ;
     *   - tous les numeros de traitement attribues (des doublons = course sur le compteur).
     */
    class Tp10Tracker {
        private val threads = ConcurrentHashMap.newKeySet<String>()
        private val numeros = Collections.synchronizedList(ArrayList<Int>())

        fun enregistrerThread(nom: String) { threads.add(nom) }
        fun enregistrerNumero(n: Int) { numeros.add(n) }

        fun threads(): Set<String> = threads.toSet()
        fun totalNumeros(): Int = synchronized(numeros) { numeros.size }
        fun numerosDistincts(): Int = synchronized(numeros) { numeros.toSet().size }
        fun reset() {
            threads.clear()
            synchronized(numeros) { numeros.clear() }
        }
    }

    @Bean
    fun tp10Tracker(): Tp10Tracker = Tp10Tracker()
}
