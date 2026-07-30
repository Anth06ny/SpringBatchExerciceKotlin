package org.example.springbatchexercicekotlin.batch

import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.JobExecution
import org.springframework.batch.core.job.parameters.JobParameters
import org.springframework.batch.core.job.parameters.JobParametersBuilder
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException
import org.springframework.batch.core.launch.JobOperator
import org.springframework.batch.core.launch.JobRestartException
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.Step
import org.springframework.batch.core.step.StepExecution
import org.springframework.batch.infrastructure.item.ExecutionContext
import org.springframework.batch.test.JobOperatorTestUtils
import org.springframework.beans.BeansException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import kotlin.test.fail

/**
 * Suite de validation du TP9 (conditionnement du flow).
 *
 * 1 test = 1 exercice. Chaque test lance le job de l'exercice avec les parametres
 * qui pilotent la branche (scenario / montant / echouer) et verifie :
 *   - le BatchStatus final (COMPLETED / FAILED / STOPPED) ;
 *   - le CHEMIN reellement parcouru = la liste des steps executes.
 *
 * Les briques (steps) sont dans TP9_Steps ; les jobs a reproduire dans TP9_JobConfig.
 *
 * DIAGNOSTIC : les assertions passent par [verifier]. En cas d'echec, le rapport affiche le
 * scenario en cause, le chemin attendu vs parcouru avec la position exacte de la divergence,
 * le statut ET l'exitStatus de chaque step (c'est l'exitStatus qui pilote le routage .on(...)),
 * les exceptions, puis des pistes de correction. Voir la section "Diagnostic" en bas de fichier.
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
        verifier("EX1 sequentiel", lancer(1))
            .regle("Les 3 steps s'enchainent : preparation -> expedition -> archivage")
            .statut(BatchStatus.COMPLETED)
            .chemin("preparationStep", "expeditionStep", "archivageStep")
    }

    /* ===== EX 2 — Deux branches sur ExitStatus =============================== */

    @Test
    fun `02 deux branches - PREMIUM vers expedition sinon preparation`() {
        verifier("EX2 scenario=PREMIUM", lancer(2, scenario = "PREMIUM"))
            .regle("controleStep sort en PREMIUM -> expedition")
            .statut(BatchStatus.COMPLETED)
            .chemin("controleStep", "expeditionStep")

        verifier("EX2 scenario=STANDARD", lancer(2, scenario = "STANDARD"))
            .regle("Tout ce qui n'est pas PREMIUM -> preparation")
            .chemin("controleStep", "preparationStep")
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

        verifier("EX2b controleStep isole (scenario=PREMIUM)", execution)
            .regle("Le ScenarioListener doit renvoyer ExitStatus(scenario)")
            .statut(BatchStatus.COMPLETED)
            .exitStatus("controleStep", "PREMIUM")
    }

    /* ===== EX 3 — Fin anticipee .end() ====================================== */

    @Test
    fun `03 fin anticipee - VIDE termine le job sans archivage`() {
        verifier("EX3 scenario=VIDE", lancer(3, scenario = "VIDE"))
            .regle("Sur VIDE, le job se termine des le controle : pas d'archivage")
            .statut(BatchStatus.COMPLETED)
            .chemin("controleStep")

        verifier("EX3 scenario=PLEIN", lancer(3, scenario = "PLEIN"))
            .regle("Hors VIDE, le flow continue jusqu'a l'archivage")
            .contient("archivageStep")
    }

    /* ===== EX 4 — Echec explicite .fail() =================================== */

    @Test
    fun `04 echec explicite - CORROMPU passe par alerte puis fait echouer le job`() {
        verifier("EX4 scenario=CORROMPU", lancer(4, scenario = "CORROMPU"))
            .regle("CORROMPU -> alerte puis .fail() : le job doit finir FAILED")
            .statut(BatchStatus.FAILED)
            .chemin("controleStep", "alerteStep")

        verifier("EX4 scenario=OK", lancer(4, scenario = "OK"))
            .statut(BatchStatus.COMPLETED)
            .contient("archivageStep")
    }

    /* ===== EX 5 — Reagir a l'echec (.on("FAILED")) ========================== */

    @Test
    fun `05 reagir a l'echec - traitement KO route vers notification, job COMPLETED`() {
        verifier("EX5 traitement en echec (echouer=true)", lancer(5, echouer = true))
            .regle("L'echec du traitement est ABSORBE par la branche .on(FAILED) : le job reussit")
            .statut(BatchStatus.COMPLETED)
            .chemin("traitementStep", "notificationStep")

        verifier("EX5 traitement OK (echouer=false)", lancer(5, echouer = false))
            .statut(BatchStatus.COMPLETED)
            .chemin("traitementStep", "archivageStep")
    }

    /* ===== EX 6 — Wildcards ? et * ========================================== */

    @Test
    fun `06 wildcards - A suivi d'un caractere vers preparation, sinon archivage`() {
        verifier("EX6 scenario=A1", lancer(6, scenario = "A1"))
            .regle("A1 correspond au motif A?")
            .contient("preparationStep")

        verifier("EX6 scenario=B1", lancer(6, scenario = "B1"))
            .regle("B1 ne correspond pas a A?, il tombe dans *")
            .contient("archivageStep")

        verifier("EX6 scenario=A", lancer(6, scenario = "A"))
            .regle("A seul (1 caractere) ne correspond PAS a A? (qui exige A + 1 caractere)")
            .contient("archivageStep")
    }

    /* ===== EX 7 — Pause .stopAndRestart() =================================== */

    @Test
    fun `07 pause - ATTENTE met le job en STOPPED`() {
        verifier("EX7 scenario=ATTENTE", lancer(7, scenario = "ATTENTE"))
            .regle("En pause, l'archivage n'est pas atteint")
            .statut(BatchStatus.STOPPED)
            .neContientPas("archivageStep")

        verifier("EX7 scenario=OK", lancer(7, scenario = "OK"))
            .statut(BatchStatus.COMPLETED)
            .contient("archivageStep")
    }

    @Test
    fun `07b reprise - relancer la meme instance repart de controleStep`() {
        // runId FIXE => les deux lancements ciblent la MEME JobInstance.
        val runId = "tp9ex7-restart-${System.nanoTime()}"

        // 1er lancement : en attente de validation -> STOPPED.
        verifier("EX7b 1er lancement (scenario=ATTENTE)", lancerAvecRunId(7, runId, scenario = "ATTENTE"))
            .regle("Le 1er lancement doit finir STOPPED, sinon l'instance n'est pas reprenable")
            .statut(BatchStatus.STOPPED)

        // 2e lancement, MEME instance : la validation est arrivee (scenario != ATTENTE).
        // Le job ne repart PAS tout seul : c'est ce relancement manuel qui le reprend.
        verifier("EX7b reprise de la meme instance (scenario=OK)", lancerAvecRunId(7, runId, scenario = "OK"))
            .regle("La reprise doit mener le job jusqu'au bout")
            .statut(BatchStatus.COMPLETED)
            .contient("controleStep", "archivageStep")
    }

    /* ===== EX 8 — Publication catalogue : flow imbrique ==================== */

    @Test
    fun `08 publication - licence, deja publie, validation absorbee`() {
        verifier("EX8 scenario=SANS_LICENCE", lancer(8, scenario = "SANS_LICENCE"))
            .regle("Sans licence : alerte puis .fail()")
            .statut(BatchStatus.FAILED)
            .chemin("controleStep", "alerteStep")

        verifier("EX8 scenario=DEJA_PUBLIE", lancer(8, scenario = "DEJA_PUBLIE"))
            .regle("Deja publie : le job se termine des le controle (.end())")
            .statut(BatchStatus.COMPLETED)
            .chemin("controleStep")

        verifier("EX8 scenario=BROUILLON", lancer(8, scenario = "BROUILLON"))
            .regle("En brouillon, le job est mis en pause des le controle (stopAndRestart)")
            .statut(BatchStatus.STOPPED)
            .chemin("controleStep")

        verifier("EX8 scenario=NOUVEAU", lancer(8, scenario = "NOUVEAU", echouer = false))
            .regle("Cas nominal : le flow complet est parcouru")
            .statut(BatchStatus.COMPLETED)
            .chemin("controleStep", "preparationStep", "traitementStep", "expeditionStep", "archivageStep")

        // Validation KO : ICI l'echec est ABSORBE (notification -> end) -> job COMPLETED.
        verifier("EX8 scenario=NOUVEAU + validation KO (echouer=true)", lancer(8, scenario = "NOUVEAU", echouer = true))
            .regle("L'echec de validation est absorbe (notification -> end), le job reussit")
            .statut(BatchStatus.COMPLETED)
            .chemin("controleStep", "preparationStep", "traitementStep", "notificationStep")
    }

    /* ===== EX 9 — Commande : flow riche imbrique ============================ */

    @Test
    fun `09 commande - rupture, stock partiel, controle qualite`() {
        verifier("EX9 scenario=RUPTURE", lancer(9, scenario = "RUPTURE"))
            .regle("Rupture : alerte puis .fail()")
            .statut(BatchStatus.FAILED)
            .chemin("controleStep", "alerteStep")

        verifier("EX9 scenario=STOCK_PARTIEL", lancer(9, scenario = "STOCK_PARTIEL"))
            .regle("Stock partiel : on notifie le client et le job se termine normalement")
            .statut(BatchStatus.COMPLETED)
            .chemin("controleStep", "notificationStep")

        verifier("EX9 scenario=STOCK_OK", lancer(9, scenario = "STOCK_OK", echouer = false))
            .regle("Cas nominal : le flow complet est parcouru")
            .statut(BatchStatus.COMPLETED)
            .chemin("controleStep", "preparationStep", "traitementStep", "expeditionStep", "archivageStep")

        verifier("EX9 scenario=STOCK_OK + qualite KO (echouer=true)", lancer(9, scenario = "STOCK_OK", echouer = true))
            .regle("Le controle qualite en echec route vers rapportStep avant .fail()")
            .statut(BatchStatus.FAILED)
            .chemin("controleStep", "preparationStep", "traitementStep", "rapportStep")
    }

    /* ===== EX 10 — Paiement (depuis une histoire) ========================== */

    @Test
    fun `10 paiement - carte refusee, fraude, capture`() {
        verifier("EX10 scenario=CARTE_REFUSEE", lancer(10, scenario = "CARTE_REFUSEE"))
            .regle("Carte refusee : on notifie le client puis le job se termine normalement")
            .statut(BatchStatus.COMPLETED)
            .chemin("controleStep", "notificationStep")

        verifier("EX10 scenario=FRAUDE", lancer(10, scenario = "FRAUDE"))
            .regle("Fraude : alerte puis .fail()")
            .statut(BatchStatus.FAILED)
            .chemin("controleStep", "alerteStep")

        verifier("EX10 scenario=PAYE", lancer(10, scenario = "PAYE", echouer = false))
            .regle("Paiement accepte : capture, expedition puis archivage")
            .statut(BatchStatus.COMPLETED)
            .chemin("controleStep", "traitementStep", "expeditionStep", "archivageStep")

        verifier("EX10 scenario=PAYE + capture KO (echouer=true)", lancer(10, scenario = "PAYE", echouer = true))
            .regle("Echec de capture : rapport d'incident puis .fail()")
            .statut(BatchStatus.FAILED)
            .contient("traitementStep", "rapportStep")
    }

    /* ===== EX 11 — JobExecutionDecider ====================================== */

    @Test
    fun `11 decider - gros montant vers notification, petit vers archivage`() {
        verifier("EX11 montant=5000.0", lancer(11, montant = 5000.0))
            .regle("Le decider doit renvoyer GROS au-dela du seuil -> notification")
            .statut(BatchStatus.COMPLETED)
            .chemin("importStep", "notificationStep")

        verifier("EX11 montant=50.0", lancer(11, montant = 50.0))
            .regle("Le decider doit renvoyer PETIT sous le seuil -> archivage")
            .chemin("importStep", "archivageStep")
    }

    /* ===== EX 12 — split() parallele ======================================== */

    @Test
    fun `12 split - rapport et archivage en parallele puis notification`() {
        verifier("EX12 split", lancer(12))
            .regle("import, PUIS rapport et archivage en parallele, PUIS notification")
            .statut(BatchStatus.COMPLETED)
            .contient("importStep", "rapportStep", "archivageStep", "notificationStep")
            .regle("Le split ne demarre qu'une fois l'import termine")
            .premier("importStep")
            .regle("La notification vient APRES le split (les 2 flux paralleles rejoignent avant)")
            .dernier("notificationStep")
    }

    /* ===== EX 13 — Deux split() enchaines =================================== */

    @Test
    fun `13 cloture nuit - deux splits enchaines`() {
        verifier("EX13 cloture de nuit", lancer(13))
            .statut(BatchStatus.COMPLETED)
            // Tous les steps du flow ont tourne.
            .contient(
                "importStep", "preparationStep", "expeditionStep", "rapportStep",
                "traitementStep", "notificationStep", "archivageStep"
            )
            // archivageStep est REUTILISE dans les deux splits -> deux StepExecutions.
            .regle("archivageStep apparait dans le split 1 (compta) ET dans le split 2 (final)")
            .compte("archivageStep", 2)
            // Ordre : import en premier, puis split1, puis traitement (jointure), puis split2.
            .premier("importStep")
            .regle("Le split 1 (logistique) est termine avant la consolidation")
            .avant("expeditionStep", "traitementStep")
            .regle("Le split 1 (compta) est termine avant la consolidation")
            .avant("rapportStep", "traitementStep")
            .regle("Le split 2 demarre APRES la consolidation")
            .avant("traitementStep", "notificationStep")
            // Ordre INTRA-branche : chaque branche est une sequence, son ordre interne est garanti.
            .regle("Branche logistique : la preparation precede l'expedition")
            .avant("preparationStep", "expeditionStep")
            .regle("Branche compta : le rapport precede l'archivage")
            .avant("rapportStep", "archivageStep")
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
        val params = JobParametersBuilder()
            .addString("scenario", scenario, false)
            .addDouble("montant", montant, false)
            .addString("echouer", echouer.toString(), false)
            .addLong("run", System.nanoTime())
            .toJobParameters()
        return demarrer(exercice, params)
    }

    /**
     * Lance avec un `runId` IDENTIFIANT fixe (et scenario NON identifiant) : deux appels
     * avec le meme runId ciblent la meme JobInstance -> le 2e est un RESTART, pas une
     * nouvelle instance. Sert a tester la reprise apres un STOPPED.
     */
    private fun lancerAvecRunId(exercice: Int, runId: String, scenario: String): JobExecution {
        val params = JobParametersBuilder()
            .addString("runId", runId)
            .addString("scenario", scenario, false)
            .addString("echouer", "false", false)
            .toJobParameters()
        return demarrer(exercice, params)
    }

    /** Les steps reellement executes, dans l'ordre (par id d'execution). */
    private fun steps(execution: JobExecution): List<String> =
        executions(execution).map { it.stepName }

    private fun executions(execution: JobExecution): List<StepExecution> =
        execution.stepExecutions.sortedBy { it.id }

    /* ========================================================================= */
    /* Diagnostic : un echec doit dire CE QUI a ete parcouru, et POURQUOI         */
    /* ========================================================================= */

    /** Recupere le job de l'exercice, avec un message clair s'il n'est pas encore ecrit. */
    private fun job(exercice: Int): Job {
        val nom = "tp9ex${exercice}Job"
        return try {
            applicationContext.getBean(nom, Job::class.java)
        }
        catch (e: BeansException) {
            fail(
                "\n$SEP\nECHEC : le bean '$nom' est introuvable dans le contexte Spring." +
                    "\nL'exercice $exercice n'est pas (encore) declare dans TP9_JobConfig," +
                    "\nou la fonction @Bean ne s'appelle pas exactement '$nom'.\n$SEP\n${e.message}"
            )
        }
    }

    /** Lance le job en transformant les exceptions de demarrage en message exploitable. */
    private fun demarrer(exercice: Int, params: JobParameters): JobExecution {
        val job = job(exercice)
        return try {
            jobOperator.start(job, params)
        }
        catch (e: Exception) {
            val sb = StringBuilder("\n$SEP\n")
            sb.append("ECHEC : EX$exercice - le lancement du job a lance une exception\n")
            sb.append(SEP).append("\n")
            sb.append("Job        : tp9ex${exercice}Job\n")
            sb.append("Parametres : ").append(parametres(params)).append("\n")
            sb.append("Exception  : ").append(decrire(e)).append("\n")
            if (e is JobInstanceAlreadyCompleteException || e is JobRestartException) {
                sb.append("\nPistes :\n")
                sb.append("  - Cette JobInstance (memes parametres IDENTIFIANTS) est deja terminee : elle ne peut pas\n")
                sb.append("    etre relancee. Pour tester une reprise, le 1er lancement doit finir en STOPPED\n")
                sb.append("    (branche .stopAndRestart(...)), pas en COMPLETED.\n")
            }
            sb.append(SEP).append("\n")
            fail(sb.toString())
        }
    }

    private fun verifier(contexte: String, execution: JobExecution) = Verif(contexte, execution)

    /**
     * Verificateur maison : chaque assertion qui echoue produit un rapport complet
     * (chemin attendu vs parcouru + position de la divergence, statut ET exitStatus de
     * chaque step, exceptions, pistes de correction) au lieu du seul
     * "expected: <[a, b]> but was: <[a]>" qui ne dit pas ou le flow a devie.
     */
    private inner class Verif(private val contexte: String, private val execution: JobExecution) {

        private val execs = executions(execution)
        private val parcours = execs.map { it.stepName }
        private var regle: String? = null

        /** Rappelle la regle metier attendue ; elle s'affiche en tete du rapport d'echec. */
        fun regle(regle: String) = apply { this.regle = regle }

        /* --- assertions ---------------------------------------------------- */

        fun statut(attendu: BatchStatus): Verif {
            val obtenu = execution.status
            if (obtenu == attendu) return this
            echec(
                "statut final du job incorrect",
                listOf(
                    "Statut attendu  : $attendu",
                    "Statut obtenu   : $obtenu",
                    "Chemin parcouru : ${fleches(parcours)}"
                ),
                indicesStatut(attendu, obtenu)
            )
        }

        /** Chemin exact : memes steps, meme ordre, ni plus ni moins. */
        fun chemin(vararg attendus: String): Verif {
            val attendu = attendus.toList()
            if (attendu == parcours) return this
            echec(
                "le chemin parcouru n'est pas celui attendu",
                listOf(
                    "Chemin attendu  : ${fleches(attendu)}",
                    "Chemin parcouru : ${fleches(parcours)}",
                    "Divergence      : ${divergence(attendu, parcours)}"
                ),
                indicesChemin(attendu, parcours)
            )
        }

        fun contient(vararg noms: String): Verif {
            val manquants = noms.filterNot { parcours.contains(it) }
            if (manquants.isEmpty()) return this
            echec(
                "des steps attendus n'ont pas ete executes",
                listOf(
                    "Steps attendus  : ${noms.joinToString(", ")}",
                    "Manquants       : ${manquants.joinToString(", ")}",
                    "Chemin parcouru : ${fleches(parcours)}"
                ),
                indicesArret(manquants.first())
            )
        }

        fun neContientPas(vararg noms: String): Verif {
            val presents = noms.filter { parcours.contains(it) }
            if (presents.isEmpty()) return this
            echec(
                "des steps ont ete executes alors qu'ils ne devaient pas l'etre",
                listOf(
                    "Steps interdits ici : ${presents.joinToString(", ")}",
                    "Chemin parcouru     : ${fleches(parcours)}"
                ),
                listOf(
                    "Le flow atteint '${presents.first()}' : la branche empruntee devrait se terminer" +
                        " avant (.end() / .fail() / .stopAndRestart(...))."
                )
            )
        }

        fun premier(nom: String): Verif {
            if (parcours.firstOrNull() == nom) return this
            echec(
                "le job ne demarre pas sur le bon step",
                listOf(
                    "Premier step attendu : $nom",
                    "Premier step obtenu  : ${parcours.firstOrNull() ?: "(aucun)"}",
                    "Chemin parcouru      : ${fleches(parcours)}"
                ),
                listOf("Verifie le .start($nom) du job.")
            )
        }

        fun dernier(nom: String): Verif {
            if (parcours.lastOrNull() == nom) return this
            echec(
                "le dernier step execute n'est pas celui attendu",
                listOf(
                    "Dernier step attendu : $nom",
                    "Dernier step obtenu  : ${parcours.lastOrNull() ?: "(aucun)"}",
                    "Chemin parcouru      : ${fleches(parcours)}"
                ),
                listOf(
                    "'$nom' doit venir APRES la jointure du split : place-le en .next(...) du flow issu" +
                        " du split, pas a l'interieur d'une branche parallele."
                )
            )
        }

        fun compte(nom: String, attendu: Int): Verif {
            val obtenu = parcours.count { it == nom }
            if (obtenu == attendu) return this
            echec(
                "'$nom' n'a pas ete execute le bon nombre de fois",
                listOf(
                    "Executions attendues : $attendu",
                    "Executions obtenues  : $obtenu",
                    "Chemin parcouru      : ${fleches(parcours)}"
                ),
                listOf(
                    "Un meme Step peut etre reutilise dans plusieurs flows : chaque passage doit produire" +
                        " une StepExecution. S'il n'y en a qu'une, le step n'est reference que dans un seul flow."
                )
            )
        }

        /** [a] doit apparaitre avant [b] dans le parcours. */
        fun avant(a: String, b: String): Verif {
            val ia = parcours.indexOf(a)
            val ib = parcours.indexOf(b)
            if (ia >= 0 && ib >= 0 && ia < ib) return this

            val bloc = mutableListOf(
                "Ordre attendu   : $a avant $b",
                "Chemin parcouru : ${fleches(parcours)}"
            )
            if (ia < 0 || ib < 0) {
                val manquant = if (ia < 0) a else b
                bloc += "Probleme        : $manquant n'a pas ete execute"
                echec("ordre d'execution non verifiable (step manquant)", bloc, indicesArret(manquant))
            }
            bloc += "Probleme        : '$a' (position ${ia + 1}) arrive apres '$b' (position ${ib + 1})"
            echec(
                "ordre d'execution incorrect", bloc,
                listOf(
                    "Dans un split, l'ordre ENTRE branches n'est pas garanti, mais la jointure l'est :" +
                        " tout ce qui suit le split ne demarre qu'une fois les branches terminees.",
                    "Verifie que '$b' est bien place APRES le split contenant '$a'" +
                        " (ou dans la meme branche, plus loin dans la sequence)."
                )
            )
        }

        /** ExitStatus d'un step : c'est LUI qui pilote le routage .on(...). */
        fun exitStatus(nomStep: String, codeAttendu: String): Verif {
            val step = execs.lastOrNull { it.stepName == nomStep }
                ?: echec(
                    "le step '$nomStep' n'a pas ete execute",
                    listOf("Chemin parcouru : ${fleches(parcours)}"),
                    indicesArret(nomStep)
                )
            val obtenu = step.exitStatus.exitCode
            if (obtenu == codeAttendu) return this
            echec(
                "l'exitStatus de '$nomStep' n'est pas celui attendu",
                listOf(
                    "ExitStatus attendu : $codeAttendu",
                    "ExitStatus obtenu  : $obtenu"
                ),
                listOf(
                    "L'exitStatus est ce sur quoi porte le routage .on(\"...\") : s'il vaut COMPLETED" +
                        " au lieu de '$codeAttendu', aucune branche specifique ne peut matcher.",
                    "Ici il est produit par un @AfterStep qui doit RENVOYER un ExitStatus (fonction non Unit)" +
                        " et etre enregistre via .listener(...) sur le step."
                )
            )
        }

        /* --- pistes de correction ------------------------------------------ */

        private fun indicesStatut(attendu: BatchStatus, obtenu: BatchStatus): List<String> {
            val pistes = mutableListOf<String>()
            if (attendu == BatchStatus.FAILED && obtenu == BatchStatus.COMPLETED) {
                pistes += "Le job devait echouer : cette branche doit se terminer par .fail() et non .end()" +
                    " (.end() force COMPLETED meme apres une erreur)."
            }
            if (attendu == BatchStatus.COMPLETED && obtenu == BatchStatus.FAILED && !transitionManquante()) {
                val enEchec = execs.filter { it.status == BatchStatus.FAILED }.joinToString(", ") { it.stepName }
                pistes += if (enEchec.isNotEmpty()) {
                    "Le(s) step(s) [$enEchec] ont echoue et leur echec n'est pas absorbe :" +
                        " il faut .from(<step>).on(\"FAILED\").to(<step de secours>) puis .end() pour que" +
                        " le job se termine en COMPLETED."
                }
                else {
                    "Aucun step n'est en echec : le FAILED vient donc du flow lui-meme" +
                        " (.fail() atteint, ou transition introuvable). Voir les exceptions ci-dessus."
                }
            }
            if (attendu == BatchStatus.STOPPED && obtenu != BatchStatus.STOPPED) {
                pistes += "Le job devait etre mis en pause : cette branche doit se terminer par" +
                    " .stopAndRestart(<step de reprise>)."
            }
            if (obtenu == BatchStatus.STOPPED && attendu != BatchStatus.STOPPED) {
                pistes += "Une branche appelle .stopAndRestart(...) alors que ce scenario ne doit pas mettre" +
                    " le job en pause : verifie le motif .on(\"...\") qui declenche la pause."
            }
            if (pistes.isEmpty() && !transitionManquante()) {
                pistes += "Compare l'exitStatus de chaque step ci-dessus avec les motifs .on(\"...\") du job."
            }
            return pistes
        }

        private fun indicesChemin(attendu: List<String>, obtenu: List<String>): List<String> {
            val pistes = mutableListOf<String>()
            val i = premierEcart(attendu, obtenu)

            when {
                obtenu.isEmpty() ->
                    pistes += "Aucun step n'a ete execute : le job est probablement encore vide" +
                        " (seul le .start(...) est ecrit ?) ou il a echoue avant le 1er step."

                i == 0 ->
                    pistes += "Le job ne demarre pas sur le bon step : attendu .start(${attendu[0]})," +
                        " mais c'est '${obtenu[0]}' qui s'execute en premier."

                i >= obtenu.size -> {
                    // Le flow s'est arrete trop tot.
                    val dernier = obtenu.size - 1
                    pistes += "Le flow s'arrete apres '${obtenu[dernier]}' (exitStatus=${exitCode(dernier)})" +
                        " alors qu'il devait continuer vers '${attendu[i]}'."
                    pistes += transitionASuggerer(exitCode(dernier), obtenu[dernier], attendu[i])
                    pistes += PISTE_DECIDER
                    if (execution.status == BatchStatus.COMPLETED) {
                        pistes += "Le job est COMPLETED : une branche l'a termine ici (.end() de trop, ou motif" +
                            " .on(\"*\") qui capture le cas avant la bonne branche)."
                    }
                }

                i >= attendu.size -> {
                    // Le flow a continue trop loin.
                    pistes += "Le flow continue au-dela de ce qui est attendu : apres '${attendu.last()}'" +
                        " il enchaine sur '${obtenu[i]}' alors qu'il devait s'arreter."
                    pistes += "Termine cette branche par .end() (COMPLETED), .fail() (FAILED) ou" +
                        " .stopAndRestart(...) (STOPPED) selon le comportement demande."
                }

                else -> {
                    // Divergence : mauvaise branche prise.
                    val precedent = obtenu[i - 1]
                    pistes += "Depuis '$precedent' (exitStatus=${exitCode(i - 1)}) le flow part vers" +
                        " '${obtenu[i]}' au lieu de '${attendu[i]}'."
                    pistes += "Verifie .from($precedent).on(\"${exitCode(i - 1)}\").to(${attendu[i]})" +
                        " — et l'ordre des branches : un motif large (\"*\") declare avant un motif precis" +
                        " peut le court-circuiter."
                }
            }
            return pistes
        }

        /** Pistes quand un step attendu n'a jamais ete atteint. */
        private fun indicesArret(manquant: String): List<String> {
            if (parcours.isEmpty()) {
                return listOf("Aucun step execute : le job est probablement encore vide (.start(...) seul).")
            }
            val dernier = parcours.size - 1
            return listOf(
                "Le flow s'arrete apres '${parcours[dernier]}' (exitStatus=${exitCode(dernier)})" +
                    " sans jamais atteindre '$manquant'.",
                transitionASuggerer(exitCode(dernier), parcours[dernier], manquant),
                PISTE_DECIDER
            )
        }

        /* --- rapport -------------------------------------------------------- */

        private fun echec(probleme: String, bloc: List<String>, pistes: List<String>): Nothing {
            val sb = StringBuilder("\n").append(SEP).append("\n")
            sb.append("ECHEC : ").append(contexte).append("\n")
            sb.append("        ").append(probleme).append("\n")
            sb.append(SEP).append("\n")
            regle?.let { sb.append("Regle attendue : ").append(it).append("\n\n") }
            sb.append("Job        : ").append(execution.jobInstance.jobName)
                .append("   (jobExecution #").append(execution.id).append(")\n")
            sb.append("Parametres : ").append(parametres(execution.jobParameters)).append("\n")
            sb.append("Resultat   : BatchStatus=").append(execution.status)
                .append("   ExitStatus=").append(execution.exitStatus.exitCode).append("\n\n")

            bloc.forEach { sb.append(it).append("\n") }

            sb.append("\nSteps executes (l'exitStatus est ce que matchent les .on(\"...\")) :\n")
            sb.append(tableauSteps())

            val exceptions = exceptions()
            if (exceptions.isNotEmpty()) {
                sb.append("\nExceptions :\n").append(exceptions)
            }

            val toutesPistes = pistes.toMutableList()
            if (transitionManquante()) {
                // Cas classique et tres mal signale par Spring Batch : la vraie cause est enfouie
                // derriere un "Flow execution ended unexpectedly".
                toutesPistes += "Spring Batch n'a trouve AUCUNE transition pour l'exitStatus sorti d'un step" +
                    " (voir les exceptions ci-dessus) : ajoute la branche .on(\"...\") correspondante," +
                    " ou un .on(\"*\") de secours. Un flow doit couvrir TOUS les exitStatus possibles."
            }
            if (toutesPistes.isNotEmpty()) {
                sb.append("\nPistes :\n")
                toutesPistes.forEach { sb.append("  - ").append(it).append("\n") }
            }
            sb.append(SEP).append("\n")
            fail(sb.toString())
        }

        private fun tableauSteps(): String {
            if (execs.isEmpty()) return "  (aucun step execute)\n"
            val largeur = execs.maxOf { it.stepName.length }
            val sb = StringBuilder()
            execs.forEachIndexed { i, step ->
                sb.append(
                    "  #%d  %-${largeur}s  status=%-9s exitStatus=%s%n".format(
                        i + 1, step.stepName, step.status, step.exitStatus.exitCode
                    )
                )
                val description = step.exitStatus.exitDescription
                if (!description.isNullOrBlank()) {
                    sb.append("      -> ").append(resumer(description)).append("\n")
                }
            }
            return sb.toString()
        }

        private fun exceptions(): String =
            execution.allFailureExceptions.joinToString("") { "  - ${decrire(it)}\n" }

        private fun transitionManquante(): Boolean =
            execution.allFailureExceptions
                .flatMap { causes(it) }
                .any { it.message?.contains("Next state not found") == true }

        private fun exitCode(index: Int): String = execs[index].exitStatus.exitCode
    }

    private companion object {

        private val SEP = "=".repeat(90)

        private const val PISTE_DECIDER =
            "Si le routage passe par un JobExecutionDecider, c'est le status RENVOYE PAR LE DECIDER" +
                " qui est matche, pas celui du step : .next(<decider>).on(\"...\").to(...)."

        /**
         * Transition a ajouter pour aller de [depuis] vers [vers]. Sur un exitStatus "metier"
         * (ni COMPLETED/FAILED/STOPPED), on rappelle qu'un motif peut aussi le couvrir.
         */
        private fun transitionASuggerer(exitCode: String, depuis: String, vers: String): String {
            val base = "Ajoute/corrige la transition : .from($depuis).on(\"$exitCode\").to($vers)"
            val statutTechnique = exitCode in listOf("COMPLETED", "FAILED", "STOPPED", "UNKNOWN", "NOOP")
            return if (statutTechnique) {
                "$base — .on(\"...\") matche l'EXIT STATUS du step, pas le nom du scenario."
            }
            else {
                "$base — ou un motif qui couvre cet exitStatus (\"*\", \"${exitCode.first()}?\"...)." +
                    " Rappel : .on(\"...\") matche l'EXIT STATUS, ici produit par le listener du step."
            }
        }

        private fun fleches(steps: List<String>): String =
            if (steps.isEmpty()) "(aucun step execute)" else steps.joinToString(" -> ")

        /** Index du premier ecart entre les deux listes (= taille de la plus courte si prefixe). */
        private fun premierEcart(attendu: List<String>, obtenu: List<String>): Int {
            val commun = minOf(attendu.size, obtenu.size)
            for (i in 0 until commun) {
                if (attendu[i] != obtenu[i]) return i
            }
            return commun
        }

        private fun divergence(attendu: List<String>, obtenu: List<String>): String {
            val i = premierEcart(attendu, obtenu)
            return when {
                i >= obtenu.size ->
                    "position ${i + 1} : attendu '${attendu[i]}', mais le job s'est arrete avant"
                i >= attendu.size ->
                    "position ${i + 1} : rien n'etait attendu apres, mais '${obtenu[i]}' a ete execute"
                else ->
                    "position ${i + 1} : attendu '${attendu[i]}', obtenu '${obtenu[i]}'"
            }
        }

        /** Parametres du job, sans le `run` technique qui ne sert qu'a rendre l'instance unique. */
        private fun parametres(parameters: JobParameters): String =
            parameters.filterNot { it.name() == "run" }
                .sortedBy { it.name() }
                .joinToString(", ") { "${it.name()}=${it.value()}" }
                .ifEmpty { "(aucun)" }

        /**
         * Exception + chaine de causes : Spring Batch enveloppe souvent la vraie explication
         * ("Next state not found in flow...") derriere un "Flow execution ended unexpectedly".
         */
        private fun decrire(e: Throwable): String {
            val chaine = causes(e)
            val sb = StringBuilder("${chaine[0].javaClass.simpleName} : ${resumer(chaine[0].message)}")
            for (i in 1 until chaine.size) {
                sb.append("\n      cause : ${chaine[i].javaClass.simpleName} : ${resumer(chaine[i].message)}")
            }
            return sb.toString()
        }

        /** L'exception puis ses causes successives (5 niveaux max, protege des cycles). */
        private fun causes(e: Throwable): List<Throwable> {
            val chaine = mutableListOf<Throwable>()
            var courant: Throwable? = e
            while (courant != null && chaine.size < 5) {
                chaine += courant
                if (courant.cause === courant) break
                courant = courant.cause
            }
            return chaine
        }

        /** Premiere ligne du message, tronquee : assez pour situer, sans noyer le rapport. */
        private fun resumer(message: String?): String {
            if (message == null) return "(pas de message)"
            val premiere = message.split(Regex("\\R"), limit = 2)[0].trim()
            return if (premiere.length > 200) premiere.take(200) + "..." else premiere
        }
    }
}
