package org.example.springbatchexercicekotlin.batch.config

import org.example.springbatchexercicekotlin.batch.CHUNK_SIZE
import org.example.springbatchexercicekotlin.batch.TP12_ENTETE
import org.example.springbatchexercicekotlin.batch.TP12_FUSION_OUTPUT
import org.example.springbatchexercicekotlin.batch.TP12_VENTES_A
import org.example.springbatchexercicekotlin.batch.TP12_VENTES_B
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.Step
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.batch.infrastructure.item.ItemProcessor
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader
import org.springframework.batch.infrastructure.item.file.FlatFileItemWriter
import org.springframework.batch.infrastructure.item.file.MultiResourceItemReader
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemReaderBuilder
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemWriterBuilder
import org.springframework.batch.infrastructure.item.file.builder.MultiResourceItemReaderBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.FileSystemResource
import org.springframework.transaction.PlatformTransactionManager

/**
 * TP12 — Tester un batch (le TP est INVERSE : ce JobConfig est fourni COMPLET,
 * c'est TP12JobTest que tu remplis).
 *
 * Le job fusionne deux CSV (`ventes_A.csv` + `ventes_B.csv`) en un troisieme
 * (`tp12_fusion.csv`) en supprimant les DOUBLONS (lignes identiques). Deux angles
 * de test a couvrir :
 *
 *   - le READER en ISOLATION : il VALIDE l'en-tete (skippedLinesCallback). Un CSV
 *     dont les colonnes sont dans le mauvais ordre, ou une ligne au mauvais nombre
 *     de colonnes, doit etre REFUSE.
 *   - le JOB COMPLET : lance de bout en bout (jobOperator.start), on verifie le
 *     statut et le contenu du fichier de sortie (fusion sans doublon).
 *
 * ⚠️ Piege pedagogique du TP : un FlatFileItemReader mappe les colonnes PAR POSITION
 * et SAUTE l'en-tete sans la lire. Donc, seul, il ne detecte PAS un mauvais ORDRE de
 * colonnes (il mapperait "produit" sur le champ "date" sans broncher). C'est le
 * skippedLinesCallback ci-dessous qui compare la ligne d'en-tete a TP12_ENTETE et
 * leve une exception si elle differe. Le mauvais NOMBRE de colonnes, lui, est detecte
 * nativement (tokenizer `strict` par defaut -> FlatFileParseException a la lecture).
 */
@Configuration
class TP12_JobConfig {

    /**
     * Reader d'UN fichier de ventes, avec VALIDATION de l'en-tete.
     * Pas de `.resource(...)` ici : la ressource est fixee soit par le
     * MultiResourceItemReader (dans le job), soit par le test (en isolation).
     * Type de retour CONCRET (FlatFileItemReader) pour que le test puisse appeler
     * `setResource(...)` puis `open()/read()`.
     */
    @Bean
    fun tp12Reader(): FlatFileItemReader<TP12VenteDTO> =
        FlatFileItemReaderBuilder<TP12VenteDTO>()
            .name("tp12Reader")
            .linesToSkip(1) // on saute l'en-tete...
            .skippedLinesCallback { entete -> // ...mais on la VALIDE au passage
                require(entete.trim() == TP12_ENTETE) {
                    "En-tete CSV invalide : \"$entete\" (attendu \"$TP12_ENTETE\")"
                }
            }
            .delimited()
            .delimiter(";")
            .names("date", "idBoutique", "produit", "montantHt")
            .targetType(TP12VenteDTO::class.java)
            .build()

    /**
     * Lit les DEUX fichiers a la suite via le meme delegate (tp12Reader).
     * MultiResourceItemReader fixe la ressource du delegate fichier par fichier.
     */
    @Bean
    fun tp12MultiReader(
        tp12Reader: FlatFileItemReader<TP12VenteDTO>
    ): MultiResourceItemReader<TP12VenteDTO> =
        MultiResourceItemReaderBuilder<TP12VenteDTO>()
            .name("tp12MultiReader")
            .delegate(tp12Reader)
            .resources(FileSystemResource(TP12_VENTES_A), FileSystemResource(TP12_VENTES_B))
            .build()

    /**
     * Deduplication : on garde une ligne la PREMIERE fois qu'on la voit, on renvoie
     * `null` (= filtre) ensuite. La cle est la ligne entiere (les 4 champs).
     * @StepScope => le Set est NEUF a chaque execution (sinon il garderait les cles
     * du run precedent). Step mono-thread ici : un HashSet simple suffit.
     */
    @Bean
    @StepScope
    fun tp12Processor(): ItemProcessor<TP12VenteDTO, TP12VenteDTO> {
        val dejaVues = HashSet<String>()
        return ItemProcessor { dto ->
            val cle = "${dto.date};${dto.idBoutique};${dto.produit};${dto.montantHt}"
            if (dejaVues.add(cle)) dto else null // add() == false -> doublon -> filtre
        }
    }

    /** Ecrit le CSV fusionne (memes colonnes, meme en-tete). */
    @Bean
    fun tp12Writer(): FlatFileItemWriter<TP12VenteDTO> =
        FlatFileItemWriterBuilder<TP12VenteDTO>()
            .name("tp12Writer")
            .resource(FileSystemResource(TP12_FUSION_OUTPUT))
            .headerCallback { it.write(TP12_ENTETE) }
            .delimited()
            .delimiter(";")
            .names("date", "idBoutique", "produit", "montantHt")
            .build()

    @Bean
    fun tp12Step(
        jobRepository: JobRepository,
        transactionManager: PlatformTransactionManager,
        tp12MultiReader: MultiResourceItemReader<TP12VenteDTO>,
        tp12Processor: ItemProcessor<TP12VenteDTO, TP12VenteDTO>,
        tp12Writer: FlatFileItemWriter<TP12VenteDTO>
    ): Step =
        StepBuilder("tp12Step", jobRepository)
            .chunk<TP12VenteDTO, TP12VenteDTO>(CHUNK_SIZE)
            .reader(tp12MultiReader)
            .processor(tp12Processor)
            .writer(tp12Writer)
            .transactionManager(transactionManager)
            .build()

    @Bean
    fun tp12Job(jobRepository: JobRepository, tp12Step: Step): Job =
        JobBuilder("tp12Job", jobRepository)
            .start(tp12Step)
            .build()

    /** DTO du CSV, propre au TP12 (no-arg + var, requis par BeanWrapperFieldSetMapper). */
    class TP12VenteDTO {
        var date: String = ""
        var idBoutique: String = ""
        var produit: String = ""
        var montantHt: Double = 0.0
    }
}
