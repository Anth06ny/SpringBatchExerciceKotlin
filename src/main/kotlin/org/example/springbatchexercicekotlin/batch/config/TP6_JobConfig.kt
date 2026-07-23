package org.example.springbatchexercicekotlin.batch.config

import org.example.springbatchexercicekotlin.batch.CHUNK_SIZE
import org.example.springbatchexercicekotlin.batch.TP6_VENTES_CSV
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.Step
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.batch.infrastructure.item.ItemProcessor
import org.springframework.batch.infrastructure.item.ItemStreamReader
import org.springframework.batch.infrastructure.item.ItemStreamWriter
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemReaderBuilder
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemWriterBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.FileSystemResource
import org.springframework.transaction.PlatformTransactionManager
import java.util.*

@Configuration
class TP6_JobConfig {

    /* ------------------------------------------------------------------ */
    /* Reader : le fichier lu depend du parametre `fichierSource`          */
    /* ------------------------------------------------------------------ */

    @Bean
    fun tp6Reader(): ItemStreamReader<TP6VenteDTO> =
        FlatFileItemReaderBuilder<TP6VenteDTO>()
            .name("tp6Reader")
            // La ressource n'est plus figee : elle vient du menu deroulant de l'IHM.
            .resource(FileSystemResource(TP6_VENTES_CSV))
            .linesToSkip(1) // ligne d'en-tete
            .delimited()
            .delimiter(";")
            // La 4e colonne du CSV s'appelle "montantHt", mais on la mappe sur la propriete `montant`
            .names("date", "idBoutique", "produit", "montant")
            .targetType(TP6VenteDTO::class.java)
            .build()

    @Bean
    fun tp6Processor(): ItemProcessor<TP6VenteDTO, TP6VenteDTO> =
        object : ItemProcessor<TP6VenteDTO, TP6VenteDTO> {
            override fun process(item: TP6VenteDTO): TP6VenteDTO {
                //TODO
                return item
            }
        }

    @Bean
    fun tp6Writer(): ItemStreamWriter<TP6VenteDTO> {
        val sortie = FileSystemResource(cheminRapportTp6("csv"))
        return FlatFileItemWriterBuilder<TP6VenteDTO>()
            .name("tp6WriterCsv")
            .resource(sortie)
            .lineAggregator { v ->
                "${v.date};${v.idBoutique};${v.produit};${formatMontant(v.montant)}"
            }
            .headerCallback { writer -> writer.write("date;idBoutique;produit;montantHT") }
            .build()
    }

    /* ------------------------------------------------------------------ */
    /* Steps                                                               */
    /* ------------------------------------------------------------------ */

    @Bean
    fun tp6Step(
        jobRepository: JobRepository,
        transactionManager: PlatformTransactionManager,
        tp6Reader: ItemStreamReader<TP6VenteDTO>,
        tp6Processor: ItemProcessor<TP6VenteDTO, TP6VenteDTO>,
        tp6Writer: ItemStreamWriter<TP6VenteDTO>
    ): Step =
        StepBuilder("tp6Step", jobRepository)
            .chunk<TP6VenteDTO, TP6VenteDTO>(CHUNK_SIZE)
            .reader(tp6Reader)
            .processor(tp6Processor)
            .writer(tp6Writer)
            .transactionManager(transactionManager)
            .build()

    @Bean
    fun tp6Job(jobRepository: JobRepository, tp6Step: Step): Job =
        JobBuilder("tp6Job", jobRepository)
            .start(tp6Step)
            .build()

    /** Montant a 2 decimales avec un point (comme dans ventes.csv). */
    private fun formatMontant(montant: Double): String =
        String.format(Locale.US, "%.2f", montant)

    class TP6VenteDTO {
        var date: String = ""
        var idBoutique: String = ""
        var produit: String = ""
        var montant: Double = 0.0
    }
}


fun cheminRapportTp6(format: String): String = "data/out/tp6_ventes_sortie.${format.lowercase()}"
