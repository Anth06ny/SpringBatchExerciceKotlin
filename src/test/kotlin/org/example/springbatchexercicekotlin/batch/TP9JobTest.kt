package org.example.springbatchexercicekotlin.batch

import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.JobExecution
import org.springframework.batch.core.job.parameters.JobParametersBuilder
import org.springframework.batch.core.launch.JobOperator
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.Step
import org.springframework.batch.infrastructure.item.ExecutionContext
import org.springframework.batch.test.JobOperatorTestUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Suite de validation du TP9 (conditionnement du flow).
 *
 * 1 test = 1 exercice. Chaque test lance le job de l'exercice avec les parametres
 * qui pilotent la branche (scenario / montant / echouer) et verifie :
 *   - le BatchStatus final (COMPLETED / FAILED / STOPPED) ;
 *   - le CHEMIN reellement parcouru = la liste des steps executes.
 *
 * Les briques (steps) sont dans TP9_Steps ; les jobs a reproduire dans TP9_JobConfig.
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.MethodName::class)
class TP9JobTest {

    @Autowired
    lateinit var jobOperator: JobOperator

    @Autowired
    lateinit var applicationContext: ApplicationContext

    @Autowired
    lateinit var jobRepository: JobRepository

    // Le step a isoler : injecte directement, pas besoin de passer par un Job complet
    @Autowired
    lateinit var controleStep: Step



    /* ===== EX 1 — Sequentiel ================================================= */

    @Test
    fun `01 sequentiel - preparation puis expedition puis archivage`() {
        val execution = lancer(1)

        assertEquals(BatchStatus.COMPLETED, execution.status)
        assertEquals(listOf("preparationStep", "expeditionStep", "archivageStep"), steps(execution))
    }

    /* ===== EX 2 — Deux branches sur ExitStatus =============================== */

    @Test
    fun `02 deux branches - PREMIUM vers expedition sinon preparation`() {
        val premium = lancer(2, scenario = "PREMIUM")
        assertEquals(BatchStatus.COMPLETED, premium.status)
        assertEquals(listOf("controleStep", "expeditionStep"), steps(premium))

        val standard = lancer(2, scenario = "STANDARD")
        assertEquals(listOf("controleStep", "preparationStep"), steps(standard))
    }

    /**
     * Meme point que le test 02 (le ScenarioListener doit transformer `scenario` en
     * ExitStatus), mais isole : on ne passe par aucun job/flow, donc aucune dependance
     * au routage .on(...).to(...). Si ce test casse, le probleme vient du listener ;
     * s'il passe mais que 02 echoue, le probleme est dans le flow (routage).
     */
    @Test
    fun `02b controleStep isole - exitStatus suit le parametre scenario`() {
        val params = JobParametersBuilder()
            .addString("scenario", "PREMIUM", false)
            .addLong("run", System.nanoTime())
            .toJobParameters()

        // Construit a la main (pas @SpringBatchTest : son JobScopeTestExecutionListener
        val jobOperatorTestUtils = JobOperatorTestUtils(jobOperator, jobRepository)

        val execution = jobOperatorTestUtils.startStep(controleStep, params, ExecutionContext())

        assertEquals(BatchStatus.COMPLETED, execution.status)
        assertEquals("PREMIUM", execution.stepExecutions.first().exitStatus.exitCode)
    }

    /* ===== EX 3 — Fin anticipee .end() ====================================== */

    @Test
    fun `03 fin anticipee - VIDE termine le job sans archivage`() {
        val vide = lancer(3, scenario = "VIDE")
        assertEquals(BatchStatus.COMPLETED, vide.status)
        assertEquals(listOf("controleStep"), steps(vide),
            "Sur VIDE, le job se termine des le controle : pas d'archivage")

        val plein = lancer(3, scenario = "PLEIN")
        assertTrue(steps(plein).contains("archivageStep"))
    }

    /* ===== EX 4 — Echec explicite .fail() =================================== */

    @Test
    fun `04 echec explicite - CORROMPU passe par alerte puis fait echouer le job`() {
        val corrompu = lancer(4, scenario = "CORROMPU")
        assertEquals(BatchStatus.FAILED, corrompu.status)
        assertEquals(listOf("controleStep", "alerteStep"), steps(corrompu))

        val ok = lancer(4, scenario = "OK")
        assertEquals(BatchStatus.COMPLETED, ok.status)
        assertTrue(steps(ok).contains("archivageStep"))
    }

    /* ===== EX 5 — Reagir a l'echec (.on("FAILED")) ========================== */

    @Test
    fun `05 reagir a l'echec - traitement KO route vers notification, job COMPLETED`() {
        val ko = lancer(5, echouer = true)
        assertEquals(BatchStatus.COMPLETED, ko.status,
            "L'echec du traitement est ABSORBE par la branche .on(FAILED) : le job reussit")
        assertEquals(listOf("traitementStep", "notificationStep"), steps(ko))

        val ok = lancer(5, echouer = false)
        assertEquals(BatchStatus.COMPLETED, ok.status)
        assertEquals(listOf("traitementStep", "archivageStep"), steps(ok))
    }

    /* ===== EX 6 — Wildcards ? et * ========================================== */

    @Test
    fun `06 wildcards - A suivi d'un caractere vers preparation, sinon archivage`() {
        assertTrue(steps(lancer(6, scenario = "A1")).contains("preparationStep"),
            "A1 correspond au motif A?")
        assertTrue(steps(lancer(6, scenario = "B1")).contains("archivageStep"),
            "B1 ne correspond pas a A?, il tombe dans *")
        assertTrue(steps(lancer(6, scenario = "A")).contains("archivageStep"),
            "A seul (1 caractere) ne correspond PAS a A? (qui exige A + 1 caractere)")
    }

    /* ===== EX 7 — Pause .stopAndRestart() =================================== */

    @Test
    fun `07 pause - ATTENTE met le job en STOPPED`() {
        val attente = lancer(7, scenario = "ATTENTE")
        assertEquals(BatchStatus.STOPPED, attente.status)
        assertFalse(steps(attente).contains("archivageStep"),
            "En pause, l'archivage n'est pas atteint")

        val ok = lancer(7, scenario = "OK")
        assertEquals(BatchStatus.COMPLETED, ok.status)
        assertTrue(steps(ok).contains("archivageStep"))
    }

    @Test
    fun `07b reprise - relancer la meme instance repart de controleStep`() {
        // runId FIXE => les deux lancements ciblent la MEME JobInstance.
        val runId = "tp9ex7-restart-${System.nanoTime()}"

        // 1er lancement : en attente de validation -> STOPPED.
        val premier = lancerAvecRunId(7, runId, scenario = "ATTENTE")
        assertEquals(BatchStatus.STOPPED, premier.status)

        // 2e lancement, MEME instance : la validation est arrivee (scenario != ATTENTE).
        // Le job ne repart PAS tout seul : c'est ce relancement manuel qui le reprend.
        val second = lancerAvecRunId(7, runId, scenario = "OK")
        assertEquals(BatchStatus.COMPLETED, second.status,
            "La reprise doit mener le job jusqu'au bout")
        assertTrue(steps(second).contains("controleStep"),
            "La reprise repart bien de controleStep (re-execute au restart)")
        assertTrue(steps(second).contains("archivageStep"),
            "controleStep re-evalue sort desormais vers archivageStep")
    }

    /* ===== EX 8 — Publication catalogue : flow imbrique ==================== */

    @Test
    fun `08 publication - licence, deja publie, validation absorbee`() {
        val sansLicence = lancer(8, scenario = "SANS_LICENCE")
        assertEquals(BatchStatus.FAILED, sansLicence.status)
        assertEquals(listOf("controleStep", "alerteStep"), steps(sansLicence))

        val dejaPublie = lancer(8, scenario = "DEJA_PUBLIE")
        assertEquals(BatchStatus.COMPLETED, dejaPublie.status)
        assertEquals(listOf("controleStep"), steps(dejaPublie))

        val brouillon = lancer(8, scenario = "BROUILLON")
        assertEquals(BatchStatus.STOPPED, brouillon.status)
        assertEquals(listOf("controleStep"), steps(brouillon),
            "En brouillon, le job est mis en pause des le controle (stopAndRestart)")

        val normal = lancer(8, scenario = "NOUVEAU", echouer = false)
        assertEquals(BatchStatus.COMPLETED, normal.status)
        assertEquals(
            listOf("controleStep", "preparationStep", "traitementStep", "expeditionStep", "archivageStep"),
            steps(normal)
        )

        // Validation KO : ICI l'echec est ABSORBE (notification -> end) -> job COMPLETED.
        val validationKo = lancer(8, scenario = "NOUVEAU", echouer = true)
        assertEquals(BatchStatus.COMPLETED, validationKo.status,
            "L'echec de validation est absorbe (notification -> end), le job reussit")
        assertEquals(listOf("controleStep", "preparationStep", "traitementStep", "notificationStep"),
            steps(validationKo))
    }

    /* ===== EX 9 — Commande : flow riche imbrique ============================ */

    @Test
    fun `09 commande - rupture, stock partiel, controle qualite`() {
        val rupture = lancer(9, scenario = "RUPTURE")
        assertEquals(BatchStatus.FAILED, rupture.status)
        assertEquals(listOf("controleStep", "alerteStep"), steps(rupture))

        val partiel = lancer(9, scenario = "STOCK_PARTIEL")
        assertEquals(BatchStatus.COMPLETED, partiel.status)
        assertEquals(listOf("controleStep", "notificationStep"), steps(partiel))

        val ok = lancer(9, scenario = "STOCK_OK", echouer = false)
        assertEquals(BatchStatus.COMPLETED, ok.status)
        assertEquals(
            listOf("controleStep", "preparationStep", "traitementStep", "expeditionStep", "archivageStep"),
            steps(ok)
        )

        val qualiteKo = lancer(9, scenario = "STOCK_OK", echouer = true)
        assertEquals(BatchStatus.FAILED, qualiteKo.status)
        assertEquals(
            listOf("controleStep", "preparationStep", "traitementStep", "rapportStep"),
            steps(qualiteKo),
            "Le controle qualite en echec route vers rapportStep avant .fail()"
        )
    }

    /* ===== EX 10 — Paiement (depuis une histoire) ========================== */

    @Test
    fun `10 paiement - carte refusee, fraude, capture`() {
        val refusee = lancer(10, scenario = "CARTE_REFUSEE")
        assertEquals(BatchStatus.COMPLETED, refusee.status,
            "Carte refusee : on notifie le client puis le job se termine normalement")
        assertEquals(listOf("controleStep", "notificationStep"), steps(refusee))

        val fraude = lancer(10, scenario = "FRAUDE")
        assertEquals(BatchStatus.FAILED, fraude.status)
        assertEquals(listOf("controleStep", "alerteStep"), steps(fraude))

        val paye = lancer(10, scenario = "PAYE", echouer = false)
        assertEquals(BatchStatus.COMPLETED, paye.status)
        assertEquals(
            listOf("controleStep", "traitementStep", "expeditionStep", "archivageStep"),
            steps(paye)
        )

        val captureKo = lancer(10, scenario = "PAYE", echouer = true)
        assertEquals(BatchStatus.FAILED, captureKo.status)
        assertTrue(steps(captureKo).containsAll(listOf("traitementStep", "rapportStep")),
            "Echec de capture : rapport d'incident puis .fail()")
    }

    /* ===== EX 11 — JobExecutionDecider ====================================== */

    @Test
    fun `11 decider - gros montant vers notification, petit vers archivage`() {
        val gros = lancer(11, montant = 5000.0)
        assertEquals(BatchStatus.COMPLETED, gros.status)
        assertEquals(listOf("importStep", "notificationStep"), steps(gros))

        val petit = lancer(11, montant = 50.0)
        assertEquals(listOf("importStep", "archivageStep"), steps(petit))
    }

    /* ===== EX 12 — split() parallele ======================================== */

    @Test
    fun `12 split - rapport et archivage en parallele puis notification`() {
        val execution = lancer(12)

        assertEquals(BatchStatus.COMPLETED, execution.status)
        val noms = steps(execution)
        assertTrue(noms.containsAll(listOf("rapportStep", "archivageStep", "notificationStep")),
            "Les 3 steps doivent s'executer (rapport et archivage en parallele)")
        assertEquals("notificationStep", noms.last(),
            "La notification vient APRES le split (les 2 flux paralleles rejoignent avant)")
    }

    /* ===== EX 13 — Deux split() enchaines =================================== */

    @Test
    fun `13 cloture nuit - deux splits enchaines`() {
        val execution = lancer(13)

        assertEquals(BatchStatus.COMPLETED, execution.status)
        val noms = steps(execution)

        // Tous les steps du flow ont tourne.
        assertTrue(noms.containsAll(listOf(
            "importStep", "preparationStep", "expeditionStep", "rapportStep",
            "traitementStep", "notificationStep", "archivageStep"
        )))
        // archivageStep est REUTILISE dans les deux splits -> deux StepExecutions.
        assertEquals(2, noms.count { it == "archivageStep" },
            "archivageStep apparait dans le split 1 (compta) ET dans le split 2 (final)")

        // Ordre : import en premier, puis split1, puis traitement (jointure), puis split2.
        assertEquals("importStep", noms.first())
        val idxTraitement = noms.indexOf("traitementStep")
        assertTrue(noms.indexOf("expeditionStep") < idxTraitement,
            "Le split 1 (logistique) est termine avant la consolidation")
        assertTrue(noms.indexOf("rapportStep") < idxTraitement,
            "Le split 1 (compta) est termine avant la consolidation")
        assertTrue(idxTraitement < noms.indexOf("notificationStep"),
            "Le split 2 demarre APRES la consolidation")

        // Ordre INTRA-branche : chaque branche est une sequence, son ordre interne est garanti.
        assertTrue(noms.indexOf("preparationStep") < noms.indexOf("expeditionStep"),
            "Branche logistique : la preparation precede l'expedition")
        assertTrue(noms.indexOf("rapportStep") < noms.indexOf("archivageStep"),
            "Branche compta : le rapport precede l'archivage")
    }

    /* ========================================================================= */
    /* Aides                                                                     */
    /* ========================================================================= */

    private fun lancer(
        exercice: Int,
        scenario: String = "",
        montant: Double = 0.0,
        echouer: Boolean = false
    ): JobExecution {
        val job = applicationContext.getBean("tp9ex${exercice}Job", Job::class.java)
        val params = JobParametersBuilder()
            .addString("scenario", scenario, false)
            .addDouble("montant", montant, false)
            .addString("echouer", echouer.toString(), false)
            .addLong("run", System.nanoTime())
            .toJobParameters()
        return jobOperator.start(job, params)
    }

    /**
     * Lance avec un `runId` IDENTIFIANT fixe (et scenario NON identifiant) : deux appels
     * avec le meme runId ciblent la meme JobInstance -> le 2e est un RESTART, pas une
     * nouvelle instance. Sert a tester la reprise apres un STOPPED.
     */
    private fun lancerAvecRunId(exercice: Int, runId: String, scenario: String): JobExecution {
        val job = applicationContext.getBean("tp9ex${exercice}Job", Job::class.java)
        val params = JobParametersBuilder()
            .addString("runId", runId)
            .addString("scenario", scenario, false)
            .addString("echouer", "false", false)
            .toJobParameters()
        return jobOperator.start(job, params)
    }

    /** Les steps reellement executes, dans l'ordre (par id d'execution). */
    private fun steps(execution: JobExecution): List<String> =
        execution.stepExecutions.sortedBy { it.id }.map { it.stepName }
}
