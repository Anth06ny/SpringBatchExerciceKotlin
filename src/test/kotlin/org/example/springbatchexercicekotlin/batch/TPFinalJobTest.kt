package org.example.springbatchexercicekotlin.batch

import org.example.springbatchexercicekotlin.batch.config.TP_FinalJobConfig
import org.example.springbatchexercicekotlin.batch.model.CommandeEntity
import org.example.springbatchexercicekotlin.batch.repository.CommandeRepository
import org.example.springbatchexercicekotlin.batch.service.MeteoService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.JobExecution
import org.springframework.batch.core.job.parameters.JobParametersBuilder
import org.springframework.batch.core.launch.JobOperator
import org.springframework.batch.core.step.StepExecution
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest
@Import(TPFinalJobTest.FakeMeteoConfig::class)
@TestMethodOrder(MethodOrderer.MethodName::class)
class TPFinalJobTest {

    @Autowired
    lateinit var jobOperator: JobOperator

    @Autowired
    @Qualifier("tpFinalJob")
    lateinit var tpFinalJob: Job

    @Autowired
    lateinit var commandeRepository: CommandeRepository

    @Autowired
    lateinit var fakeMeteo: FakeMeteoService

    @Autowired
    lateinit var meteoTracker: TP_FinalJobConfig.TpFinalMeteoTracker

    @BeforeEach
    fun setup() {
        commandeRepository.deleteAll()
        fakeMeteo.reset()    // par defaut : 20° partout = aucune hausse
        meteoTracker.reset()
    }

    /* ===================================================================== */
    /* 1 : le nettoyage remet TOUT a zero (dossier de sortie + table)        */
    /* ===================================================================== */

    /**
     * Sans purge, un plan obsolete survivrait : une ville disparue du CSV garderait son
     * fichier chauffeur, et les commandes de la veille s'ajouteraient a celles du jour
     * (les totaux seraient doubles). On simule ces deux residus, puis on verifie qu'ils
     * ont disparu.
     */
    @Test
    fun `01 nettoyage - les sorties et la table du run precedent sont videes`() {
        // Un vestige de fichier...
        File(TPFINAL_CHAUFFEURS_DIR).mkdirs()
        val vestige = File(TPFINAL_CHAUFFEURS_DIR, "VilleDisparue.txt")
        vestige.writeText("Fantome : 999\n")
        // ...et un vestige en base.
        commandeRepository.save(CommandeEntity(city = "VilleDisparue", shop = "Fantome", nb = 999))

        val execution = lancer(TPFINAL_COMMANDES_CSV)

        assertEquals(BatchStatus.COMPLETED, execution.status)
        assertTrue(!vestige.exists(),
            "Le fichier chauffeur d'une ville disparue doit etre supprime par le nettoyage")
        assertEquals(8, fichiersChauffeurs().size,
            "Il ne doit rester QUE les 8 villes du CSV du jour")
        assertEquals(500, commandeRepository.count(),
            "La table doit avoir ete videe avant l'import : 500 commandes, pas 501")
        assertTrue(camions().keys.none { it == "VilleDisparue" },
            "camions.txt ne doit plus contenir la ville de la veille")
    }

    /* ===================================================================== */
    /* 2 : chemin heureux — 500 commandes, camions.txt + chauffeurs generes  */
    /* ===================================================================== */

    @Test
    fun `02 fichier valide - COMPLETED, camions et chauffeurs coherents`() {
        val execution = lancer(TPFINAL_COMMANDES_CSV)

        assertEquals(BatchStatus.COMPLETED, execution.status)

        val camions = camions()
        assertEquals(8, camions.size, "camions.txt = une ligne par ville (8 villes)")
        assertEquals(56072, camions.values.sum(),
            "Total des bouteilles (aucune hausse meteo : le mock renvoie 20°)")
        assertEquals(camions.values.sum(), totalChauffeurs(),
            "Le total des fichiers chauffeurs doit egaler celui de camions.txt")
        assertEquals(8, fichiersChauffeurs().size, "Un fichier chauffeur par ville")
        assertEquals(0, rejets().size, "Aucun rejet sur un fichier sain")
        assertEquals(500, commandeRepository.count(), "Les 500 commandes sont en base")
    }

    /* ===================================================================== */
    /* 3-4 : tolerance aux fautes — les invalides sont ecartees et tracees   */
    /* ===================================================================== */

    @Test
    fun `03 quinze corrompues - ecartees et tracees dans rejets csv`() {
        val execution = lancer(TPFINAL_COMMANDES_15L_CSV)

        assertEquals(BatchStatus.COMPLETED, execution.status,
            "Les commandes invalides sont ecartees, elles ne font pas echouer le job")
        assertEquals(15L, step(execution, "tpFinalImportStep").skipCount,
            "Les 15 commandes invalides doivent etre skippees")
        assertEquals(15, rejets().size, "Chaque commande rejetee est tracee dans rejets.csv")
        assertEquals(485, commandeRepository.count(), "Seules les 485 valides sont en base")
        assertEquals(54142, camions().values.sum(),
            "Seules les commandes valides sont livrees (total des 485 restantes)")

        // Les DEUX familles d'erreurs doivent etre tolerees, et le listener doit savoir
        // les distinguer : 8 "abc" (illisibles -> skip a la LECTURE) et 7 quantites
        // negatives (lisibles mais invalides -> skip au TRAITEMENT). Un apprenant qui ne
        // declare qu'un seul .skip(...) fait echouer le job ; un listener qui n'implemente
        // qu'un seul callback perd la moitie des lignes de rejets.csv.
        assertEquals(8, rejets().count { it.startsWith("LECTURE;") },
            "Les lignes illisibles sont rejetees a la LECTURE (FlatFileParseException)")
        assertEquals(7, rejets().count { it.startsWith("TRAITEMENT;") },
            "Les quantites negatives sont rejetees au TRAITEMENT (CommandeInvalideException)")
    }

    /**
     * AUCUN seuil de refus : meme 25 rejets passent.
     * ⚠️ Piege : le skipLimit par DEFAUT de Spring Batch vaut 10. Un apprenant qui ecrit
     * `.faultTolerant().skip(...)` sans `.skipLimit(...)` verra le job echouer au 11e rejet.
     */
    @Test
    fun `04 vingt-cinq corrompues - toujours accepte, aucun seuil de refus`() {
        val execution = lancer(TPFINAL_COMMANDES_25L_CSV)

        assertEquals(BatchStatus.COMPLETED, execution.status,
            "Aucun seuil : toutes les invalides sont ecartees, le job reussit " +
                    "(il faut lever le skipLimit par defaut, qui vaut 10)")
        assertEquals(25L, step(execution, "tpFinalImportStep").skipCount)
        assertEquals(25, rejets().size)
        assertEquals(475, commandeRepository.count())
    }

    /* ===================================================================== */
    /* 5-6 : la hausse meteo (mockee)                                        */
    /* ===================================================================== */

    @Test
    fun `05 meteo - une ville en canicule voit ses commandes augmentees de 20 pourcent`() {
        // Mock : 40° a Toulouse (> 35 -> +20%), 20° ailleurs (aucune hausse).
        fakeMeteo.parVille = { ville -> if (ville == "Toulouse") 40.0 else 20.0 }

        val execution = lancer(TPFINAL_COMMANDES_CSV)

        assertEquals(BatchStatus.COMPLETED, execution.status)
        val camions = camions()
        assertEquals(8537, camions["Toulouse"],
            "Toulouse +20% commande par commande (arrondi) : 7115 brut -> 8537")
        assertEquals(7014, camions["Paris"],
            "Paris a 20° : aucune hausse")
    }

    /**
     * L'autre branche de la hausse (+10 %) et les BORNES. Deux erreurs classiques :
     *   - inverser l'ordre du `when` (tester > 25 avant > 35) -> une canicule ne donne
     *     plus que +10 %, ce que le test 05 attrape deja ;
     *   - utiliser >= au lieu de > -> une ville pile a 25° prendrait +10 % a tort.
     */
    @Test
    fun `06 meteo - palier a plus dix pourcent et bornes strictes`() {
        fakeMeteo.parVille = { ville ->
            when (ville) {
                "Lyon" -> 30.0        // entre 25 et 35 -> +10 %
                "Paris" -> 25.0       // PILE au seuil -> aucune hausse (comparaison stricte)
                "Nice" -> 35.0        // PILE au seuil canicule -> +10 %, pas +20 %
                else -> 20.0
            }
        }

        val execution = lancer(TPFINAL_COMMANDES_CSV)

        assertEquals(BatchStatus.COMPLETED, execution.status)
        val camions = camions()
        assertEquals(7767, camions["Lyon"], "Lyon a 30° -> +10 % (7059 brut)")
        assertEquals(7014, camions["Paris"], "Paris a 25° PILE -> aucune hausse (seuil strict)")
        assertEquals(7789, camions["Nice"], "Nice a 35° PILE -> +10 % et non +20 % (7077 brut)")
        assertEquals(6612, camions["Strasbourg"], "Strasbourg a 20° -> inchange")
    }

    /* ===================================================================== */
    /* 7-8 : le step meteo est un CHUNK PARALLELISE avec cache partage       */
    /* ===================================================================== */

    /**
     * LA regle de l'enonce : "attention a ne faire qu'un appel d'API par ville".
     * Il y a 500 commandes pour 8 villes -> sans cache, 500 appels.
     * ⚠️ Le compteur du faux service est thread-safe : le processor tourne sur 4 threads.
     */
    @Test
    fun `07 meteo - exactement un appel API par ville malgre les 500 commandes`() {
        val execution = lancer(TPFINAL_COMMANDES_CSV)

        assertEquals(BatchStatus.COMPLETED, execution.status)
        assertEquals(8, fakeMeteo.nbAppelsTotal(),
            "8 villes = 8 appels. Sans cache partage il y en aurait 500 ; avec un cache " +
                    "non atomique (HashMap + if/put), il y en aurait quelques-uns en trop")
        listOf("Paris", "Lyon", "Marseille", "Toulouse", "Nice", "Nantes", "Strasbourg", "Bordeaux")
            .forEach { ville ->
                assertEquals(1, fakeMeteo.nbAppels(ville), "Une seule interrogation pour $ville")
            }
    }

    @Test
    fun `08 meteo - le traitement est reparti sur plusieurs threads`() {
        val execution = lancer(TPFINAL_COMMANDES_CSV)

        assertEquals(BatchStatus.COMPLETED, execution.status)
        assertTrue(meteoTracker.threads().size > 1,
            "Le processor doit tourner sur le pool du taskExecutor, pas sur le seul thread " +
                    "du step (vu : ${meteoTracker.threads()})")
        assertEquals(500L, step(execution, "tpFinalMeteoStep").writeCount,
            "Le parallelisme ne doit perdre AUCUN item : lecture et ecriture restent " +
                    "mono-thread en Batch 6")
    }

    /* ===================================================================== */
    /* 9 : camions et chauffeurs sont generes par un SPLIT (2 branches)      */
    /* ===================================================================== */

    @Test
    fun `09 generation - camions et chauffeurs en split parallele`() {
        val execution = lancer(TPFINAL_COMMANDES_CSV)

        assertEquals(BatchStatus.COMPLETED, execution.status)

        val camionsStep = step(execution, "tpFinalCamionsStep")
        val chauffeursStep = step(execution, "tpFinalChauffeursStep")
        assertEquals(BatchStatus.COMPLETED, camionsStep.status)
        assertEquals(BatchStatus.COMPLETED, chauffeursStep.status)

        // Les deux branches ne demarrent qu'apres la meteo (jointure amont).
        assertTrue(camionsStep.startTime!! >= step(execution, "tpFinalMeteoStep").endTime!!,
            "La generation doit attendre que la hausse meteo soit appliquee")

        // PREUVE du split : chaque branche tourne sur son propre thread. Un `.next()`
        // sequentiel (l'erreur classique) les enchainerait sur le thread principal du job
        // et ferait echouer cette seule assertion — tout le reste passerait.
        val threadCamions = camionsStep.executionContext.getString("thread")
        val threadChauffeurs = chauffeursStep.executionContext.getString("thread")
        assertTrue(threadCamions != threadChauffeurs,
            "Les deux branches doivent etre lancees en parallele par le taskExecutor du split " +
                    "(vu : $threadCamions et $threadChauffeurs)")
    }

    /* ===================================================================== */
    /* 10 : rejets.csv reparti de zero d'une execution a l'autre             */
    /* ===================================================================== */

    /**
     * Le test 04 vient de laisser 25 rejets dans le fichier : un run sain doit repartir
     * d'une trace VIERGE. C'est le role du beforeStep du listener (writeText, pas append).
     */
    @Test
    fun `10 rejets - la trace est ecrasee et non completee a chaque execution`() {
        lancer(TPFINAL_COMMANDES_15L_CSV)
        assertEquals(15, rejets().size)

        lancer(TPFINAL_COMMANDES_CSV)
        assertEquals(0, rejets().size,
            "rejets.csv doit etre ecrase au demarrage du step, pas complete d'un run a l'autre")
    }

    /* ===================================================================== */
    /* Aides                                                                 */
    /* ===================================================================== */

    /** Pas de deleteAll ici : c'est le nettoyageStep du job qui doit vider la table. */
    private fun lancer(fichierSource: String): JobExecution {
        val params = JobParametersBuilder()
            .addString("fichierSource", fichierSource)
            .addLong("timestamp", System.nanoTime())
            .toJobParameters()
        return jobOperator.start(tpFinalJob, params)
    }

    private fun step(execution: JobExecution, nom: String): StepExecution =
        execution.stepExecutions.first { it.stepName == nom }

    /** camions.txt -> Map ville -> total (lignes "Ville : n"). */
    private fun camions(): Map<String, Int> =
        File(TPFINAL_CAMIONS_TXT).readLines().filter { it.isNotBlank() }
            .associate { ligne ->
                val (ville, total) = ligne.split(" : ")
                ville to total.trim().toInt()
            }

    private fun fichiersChauffeurs(): List<File> =
        File(TPFINAL_CHAUFFEURS_DIR).listFiles { f -> f.extension == "txt" }?.toList() ?: emptyList()

    /** Somme des quantites de tous les fichiers chauffeurs (lignes "Magasin : n"). */
    private fun totalChauffeurs(): Int =
        fichiersChauffeurs().sumOf { fichier ->
            fichier.readLines().filter { it.isNotBlank() }.sumOf { it.substringAfterLast(" : ").trim().toInt() }
        }

    private fun rejets(): List<String> =
        File(cheminRejetsTpFinal()).readLines().drop(1).filter { it.isNotBlank() }

    /* ===================================================================== */
    /* Faux service meteo (mock) : injecte a la place de l'appel OpenWeather */
    /* ===================================================================== */

    class FakeMeteoService : MeteoService {

        /** Temperature renvoyee par ville ; par defaut 20° (aucune hausse). */
        var parVille: (String) -> Double = { 20.0 }

        /**
         * Nombre d'appels PAR VILLE. Le processor etant parallelise, `temperature()` peut
         * etre appelee depuis 4 threads : ce compteur doit imperativement etre thread-safe.
         * Une HashMap avec un read-modify-write (lire la valeur, ajouter 1, la reecrire)
         * perdrait des increments — exactement le bug du TP10.
         */
        val appels = ConcurrentHashMap<String, AtomicInteger>()

        /**
         * Latence simulee : un appel HTTP reel coute des dizaines de millisecondes.
         * Sans elle, le mock repond instantanement et la fenetre de course est si etroite
         * qu'un cache NON atomique (lire / tester / ecrire) passerait le test 07 par
         * chance. Avec cette latence, plusieurs threads tombent sur la meme ville pendant
         * l'appel et le doublon apparait. Elle ne peut jamais faire echouer une
         * implementation correcte (computeIfAbsent bloque les autres threads sur la cle).
         */
        override fun temperature(ville: String): Double {
            appels.computeIfAbsent(ville) { AtomicInteger() }.incrementAndGet()
            Thread.sleep(30)
            return parVille(ville)
        }

        fun nbAppels(ville: String): Int = appels[ville]?.get() ?: 0
        fun nbAppelsTotal(): Int = appels.values.sumOf { it.get() }

        fun reset() {
            parVille = { 20.0 }
            appels.clear()
        }
    }

    @TestConfiguration
    class FakeMeteoConfig {
        // @Primary : ce faux service est choisi a la place d'OpenWeatherMeteoService
        // partout ou le job injecte un MeteoService.
        @Bean
        @Primary
        fun fakeMeteoService(): FakeMeteoService = FakeMeteoService()
    }
}
