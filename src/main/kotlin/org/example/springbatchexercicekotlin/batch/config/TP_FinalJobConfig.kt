package org.example.springbatchexercicekotlin.batch.config

import org.example.springbatchexercicekotlin.batch.TPFINAL_CAMIONS_TXT
import org.example.springbatchexercicekotlin.batch.TPFINAL_CHAUFFEURS_DIR
import org.example.springbatchexercicekotlin.batch.TPFINAL_OUT_DIR
import org.example.springbatchexercicekotlin.batch.model.CommandeEntity
import org.example.springbatchexercicekotlin.batch.repository.CommandeRepository
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.Step
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.batch.infrastructure.item.ItemProcessor
import org.springframework.batch.infrastructure.repeat.RepeatStatus
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.PlatformTransactionManager
import java.io.File
import java.util.concurrent.ConcurrentHashMap

@Configuration
class TP_FinalJobConfig {

    /* =================================================================== */
    /* 1. Nettoyage : on repart d'une ardoise vierge                       */
    /* =================================================================== */

    /**
     * Vide le dossier de sortie ET la table. C'est le 1er step : tout ce que le job
     * produit ensuite (rejets, camions.txt, fichiers chauffeurs) est donc forcement
     * le resultat du run courant, jamais un residu de la veille.
     */
    @Bean
    fun tpFinalNettoyageStep(
        jobRepository: JobRepository,
        transactionManager: PlatformTransactionManager,
        commandeRepository: CommandeRepository
    ): Step =
        StepBuilder("tpFinalNettoyageStep", jobRepository)
            .tasklet({ _, _ ->
                // deleteRecursively sur le CONTENU : on garde le dossier racine.
                File(TPFINAL_OUT_DIR).listFiles()?.forEach { it.deleteRecursively() }

                // On recree les 2 dossiers tout de suite : les steps du split ecriront
                // dedans sans avoir a se soucier de leur existence.
                File(TPFINAL_CAMIONS_TXT).parentFile.mkdirs()
                File(TPFINAL_CHAUFFEURS_DIR).mkdirs()

                commandeRepository.deleteAll()

                println("TP final — sorties et table COMMANDE remises à zéro")
                RepeatStatus.FINISHED
            }, transactionManager)
            .build()


    /* =================================================================== */
    /* 2. Mise en base (chunk tolerant aux fautes, comme le TP7)           */
    /* =================================================================== */

    //Step qui doit importer en base de donnée le csv (Reader / Processor /Writer)
    @Bean
    fun tpFinalImportStep(
        jobRepository: JobRepository,
        transactionManager: PlatformTransactionManager
    ): Step =
        StepBuilder("tpFinalImportStep", jobRepository)
            .tasklet({ _, _ ->
                println("tpFinalImportStep")
                RepeatStatus.FINISHED
            }, transactionManager)
            .build()


    /* =================================================================== */
    /* 3. Hausse meteo — CHUNK PARALLELISE (cf. TP10)                      */
    /* =================================================================== */
    @Bean
    @StepScope
    fun tpFinalMeteoProcessor(
        tpFinalMeteoTracker: TpFinalMeteoTracker
    ): ItemProcessor<CommandeEntity, CommandeEntity> {

        return ItemProcessor { commande ->
            //pour les tests
            tpFinalMeteoTracker.enregistrerThread(Thread.currentThread().name)

            commande
        }
    }


    //Appel parallélisé pour récupérer la météo d'une ville et augmenter les commandes en fonction
    @Bean
    fun tpFinalMeteoStep(
        jobRepository: JobRepository,
        transactionManager: PlatformTransactionManager,
    ): Step =
        StepBuilder("tpFinalMeteoStep", jobRepository)
            .tasklet({ _, _ ->
                println("tpFinalMeteoStep")
                RepeatStatus.FINISHED
            }, transactionManager)
            .build()

    /** Collecteur thread-safe des threads utilises (preuve du parallelisme pour les tests). */
    class TpFinalMeteoTracker {
        private val threads = ConcurrentHashMap.newKeySet<String>()
        fun enregistrerThread(nom: String) { threads.add(nom) }
        fun threads(): Set<String> = threads.toSet()
        fun reset() = threads.clear()
    }

    @Bean
    fun tpFinalMeteoTracker(): TpFinalMeteoTracker = TpFinalMeteoTracker()

    /* =================================================================== */
    /* 4. Generation des fichiers, les deux branches du split              */
    /* =================================================================== */

    /** Branche 1 du split : entrepot/camions.txt = total de bouteilles PAR VILLE. */
    @Bean
    fun tpFinalCamionsStep(
        jobRepository: JobRepository,
        transactionManager: PlatformTransactionManager
    ): Step =
        StepBuilder("tpFinalCamionsStep", jobRepository)
            .tasklet({ _, chunkContext ->
                // Trace du thread : le test verifie que les deux branches du split ne
                // tournent PAS sur le meme thread (un .next() sequentiel les mettrait
                // toutes les deux sur le thread principal du job).
                chunkContext.stepContext.stepExecution.executionContext
                    .putString("thread", Thread.currentThread().name)

                println("TP final — camions.txt à généré [${Thread.currentThread().name}]")
                RepeatStatus.FINISHED
            }, transactionManager)
            .build()

    /** Branche 2 du split : chauffeurs/{Ville}.txt = total PAR MAGASIN de la ville. */
    @Bean
    fun tpFinalChauffeursStep(
        jobRepository: JobRepository,
        transactionManager: PlatformTransactionManager,
        commandeRepository: CommandeRepository
    ): Step =
        StepBuilder("tpFinalChauffeursStep", jobRepository)
            .tasklet({ _, chunkContext ->
                chunkContext.stepContext.stepExecution.executionContext
                    .putString("thread", Thread.currentThread().name) // cf. tpFinalCamionsStep

                println("TP final — fichiers chauffeurs à générés [${Thread.currentThread().name}]")
                RepeatStatus.FINISHED
            }, transactionManager)
            .build()

    /* =================================================================== */
    /* Le job : nettoyage -> import -> meteo -> split -> end               */
    /* =================================================================== */

    @Bean
    fun tpFinalJob(
        jobRepository: JobRepository,
        tpFinalNettoyageStep: Step
    ): Job {

        return JobBuilder("tpFinalJob", jobRepository)
            .start(tpFinalNettoyageStep)
            .build()
    }
}
