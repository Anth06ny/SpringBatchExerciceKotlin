package org.example.springbatchexercicekotlin.web

import org.example.springbatchexercicekotlin.batch.*
import org.example.springbatchexercicekotlin.batch.config.TP10_JobConfig
import org.example.springbatchexercicekotlin.batch.config.cheminRapportTp6
import org.example.springbatchexercicekotlin.batch.repository.VenteRepository
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.JobExecutionException
import org.springframework.batch.core.job.parameters.JobParametersBuilder
import org.springframework.batch.core.launch.JobOperator
import org.springframework.batch.core.repository.JobRepository
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


@Controller
class WebController(
    private val jobOperator: JobOperator,
    private val jobRepository: JobRepository,
    private val venteRepository: VenteRepository,
    private val helloJob: Job,
    private val tp5Job: Job,
    private val tp6Job: Job,
    private val tp7Job: Job,
    private val tp8Job: Job,
    private val tp10Job: Job,
    private val tp10Tracker: TP10_JobConfig.Tp10Tracker,
    private val applicationContext: ApplicationContext

) {


    @GetMapping("/")
    fun index(model: Model): String {
        model.addAttribute("executions", recentExecutions())
        return "index"
    }

    @PostMapping("/jobs/hello")
    fun tp1(redirect: RedirectAttributes): String {
        // Parametre unique : sinon Spring Batch refuse de relancer un job deja
        // termine avec les memes parametres.
        val params = JobParametersBuilder()
            //.addLong("timestamp", System.currentTimeMillis())
            .toJobParameters()

        val execution = jobOperator.start(helloJob, params)

        redirect.addFlashAttribute(
            "message",
            "helloJob → ${execution.status} (exécution #${execution.id})"
        )
        return "redirect:/"
    }

    @PostMapping("/jobs/second")
    fun tp1_2(
        redirect: RedirectAttributes
    ): String {


        redirect.addFlashAttribute(
            "errorMessage",
            "secondJob → TODO)"
        )
        return "redirect:/"
    }

    @PostMapping("/jobs/tp2")
    fun tp2(
        @RequestParam(defaultValue = "") message: String,
        redirect: RedirectAttributes
    ): String {

        redirect.addFlashAttribute(
            "errorMessage",
            "tp2Job → TODO)"
        )
        return "redirect:/"
    }


    @PostMapping("/jobs/tp3")
    fun tp3(
        @RequestParam(defaultValue = "1") runId: String,
        @RequestParam(defaultValue = "false") fail: Boolean,
        redirect: RedirectAttributes
    ): String {


        redirect.addFlashAttribute(
            "errorMessage",
            "tp3Job (runId=$runId) → TODO)"
        )
        return "redirect:/"
    }

    @PostMapping("/jobs/tp4")
    fun tp4(redirect: RedirectAttributes): String {

        // On vide la table VENTE avant l'import : chaque clic reflete donc le seul
        // contenu du CSV (sinon les lignes s'accumuleraient à chaque relance).
        venteRepository.deleteAll()

        redirect.addFlashAttribute(
            "errorMessage",
            "tp4Job → TODO)"
        )

        return "redirect:/"
    }

    @PostMapping("/jobs/tp5")
    fun tp5(redirect: RedirectAttributes): String {

        // TP5 exporte le contenu actuel de la table VENTE vers ventes_tp5.csv.
        // (Pense a lancer le TP4 avant, pour avoir des donnees a exporter.)
        val params = JobParametersBuilder()
            .addLong("timestamp", System.currentTimeMillis())
            .toJobParameters()

        val execution = jobOperator.start(tp5Job, params)

        redirect.addFlashAttribute(
            "message",
            "tp5Job → ${execution.status} (exécution #${execution.id}) → ventes_tp5.csv"
        )
        return "redirect:/"
    }

    @PostMapping("/jobs/tp6")
    fun tp6(
        @RequestParam(defaultValue = VENTES_CSV) fichierSource: String,
        @RequestParam(defaultValue = "CSV") format: String,
        // ATTENTION : une case NON cochee n'envoie RIEN dans un POST HTML.
        // Sans defaultValue, le parametre serait absent et la requete echouerait.
        @RequestParam(defaultValue = "false") totalTtc: Boolean,
        @RequestParam(defaultValue = "0") montantMini: Double,
        redirect: RedirectAttributes
    ): String {

        // JobParameters n'accepte que String / Long / Double / Date & co.
        val params = JobParametersBuilder()
            .addLong("timestamp", System.currentTimeMillis())
            .toJobParameters()

        val execution = jobOperator.start(tp6Job, params)

        // Bilan pour l'IHM : les compteurs du step suffisent, pas besoin d'un step dedie.
        // filterCount = lignes ecartees par le processor (celles sous montantMini).
        val step = execution.stepExecutions.first { it.stepName == "tp6Step" }
        val bilan = "${step.writeCount} ligne(s) exportée(s) sur ${step.readCount} lue(s)" +
                " (${step.filterCount} filtrée(s)) → ${cheminRapportTp6(format)}"

        redirect.addFlashAttribute(
            "message",
            "tp6Job → ${execution.status} (exécution #${execution.id}) — $bilan"
        )
        return "redirect:/"
    }

    @PostMapping("/jobs/tp7")
    fun tp7(
        @RequestParam(defaultValue = TP7_VENTES_5L_CSV) fichierSource: String,
        @RequestParam(defaultValue = "") runId: String,
        @RequestParam(defaultValue = "false") risque: Boolean,
        redirect: RedirectAttributes
    ): String {

        // chaque clic repart d'une table VENTE vide, le contenu
        venteRepository.deleteAll()

        // runId vide -> on en genere un depuis la date : chaque clic est alors une
        // nouvelle JobInstance. Saisir un runId a la main permet de REJOUER la meme
        // instance
        val id = runId.ifBlank {
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        }

        val params = JobParametersBuilder()
            .addString("runId", id)
            // Non-identifiants : on peut changer de fichier ou cocher le risque
            // entre deux tentatives sans changer d'instance.
            .addString("fichierSource", fichierSource, false)
            .addString("risque", risque.toString(), false)
            .toJobParameters()

        val execution = jobOperator.start(tp7Job, params)

        val step = execution.stepExecutions.first { it.stepName == "tp7Step" }
        val bilan = "lues=${step.readCount}, écrites=${step.writeCount}, " +
                "rejets lecture=${step.readSkipCount}, rejets traitement=${step.processSkipCount}, " +
                "rollbacks=${step.rollbackCount} → rejets dans $TP7_REJETS_CSV"

        val texte = "tp7Job (runId=$id) → ${execution.status} (exécution #${execution.id}) — $bilan"
        if (execution.status == BatchStatus.COMPLETED) {
            redirect.addFlashAttribute("message", texte)
        }
        else {
            redirect.addFlashAttribute("errorMessage", texte)
        }
        return "redirect:/"
    }

    @PostMapping("/jobs/tp8")
    fun tp8(
        @RequestParam(defaultValue = "0") caJour: Double,
        redirect: RedirectAttributes
    ): String {

        val params = JobParametersBuilder()
            .addDouble("caJour", caJour)
            .addLong("timestamp", System.currentTimeMillis())
            .toJobParameters()

        val execution = jobOperator.start(tp8Job, params)

        // Le job REUSSIT toujours (BatchStatus COMPLETED) : c'est l'exitCode (metier)
        // qui change selon le CA. Il apparait dans la colonne "exit" du panneau.
        redirect.addFlashAttribute(
            "message",
            """
                tp8Job → statut=${execution.status} 
                (CA=$caJour €, objectif=$OBJECTIF_CA €)"
            """.trimIndent()
        )
        return "redirect:/"
    }

    @PostMapping("/jobs/tp9")
    fun tp9(
        @RequestParam(defaultValue = "1") exercice: Int,
        @RequestParam(defaultValue = "") scenario: String,
        @RequestParam(defaultValue = "0") montant: Double,
        @RequestParam(defaultValue = "false") echouer: Boolean,
        redirect: RedirectAttributes
    ): String {

        // Un seul controleur pour les 10 exercices : on recupere le job par son nom.
        val job = applicationContext.getBean("tp9ex${exercice}Job", Job::class.java)

        val params = JobParametersBuilder()
            .addString("scenario", scenario, false)
            .addDouble("montant", montant, false)
            .addString("echouer", echouer.toString(), false)
            .addLong("timestamp", System.currentTimeMillis())
            .toJobParameters()

        val execution = jobOperator.start(job, params)

        // Le panneau de droite montre les steps reellement executes (le chemin pris).
        val chemin = execution.stepExecutions
            .sortedBy { it.id }
            .joinToString(" → ") { it.stepName }
        val texte = "tp9ex${exercice}Job → ${execution.status} " +
                "(exitCode=${execution.exitStatus.exitCode}) — chemin : $chemin"

        if (execution.status == BatchStatus.COMPLETED) {
            redirect.addFlashAttribute("message", texte)
        } else {
            redirect.addFlashAttribute("errorMessage", texte)
        }
        return "redirect:/"
    }

    @PostMapping("/jobs/tp10")
    fun tp10(redirect: RedirectAttributes): String {

        // On vide la table VENTE avant l'import (comme au TP4) : chaque clic reflete
        // le seul contenu du CSV. On remet aussi le tracker a zero pour que le bilan
        // affiche les numeros de CE lancement uniquement.
        venteRepository.deleteAll()
        tp10Tracker.reset()

        val params = JobParametersBuilder()
            .addLong("timestamp", System.currentTimeMillis())
            .toJobParameters()

        val execution = jobOperator.start(tp10Job, params)

        val step = execution.stepExecutions.first { it.stepName == "tp10Step" }
        val start = execution.startTime
        val end = execution.endTime
        val duree = if (start != null && end != null) Duration.between(start, end).toMillis() else 0

        // LE point du TP : le compteur PARTAGE du processor doit rester correct en parallele.
        // distincts < total => des numeros ont ete dupliques (course sur l'etat partage).
        val total = tp10Tracker.totalNumeros()
        val distincts = tp10Tracker.numerosDistincts()
        val threads = tp10Tracker.threads().size
        val etatCompteur =
            if (distincts == total) "$distincts/$total uniques ✅"
            else "$distincts/$total uniques ⚠️ DOUBLONS, Le compteur n'est pas unique par vente !"

        // Le produit portant le PLUS GRAND numero : doit finir par "_500" si le compteur
        // est correct, un numero plus petit s'il a ete corrompu par la course.
        val dernierLibelle = venteRepository.findAll()
            .maxByOrNull { it.libelleProduit.substringAfterLast("_").toIntOrNull() ?: 0 }
            ?.libelleProduit ?: "-"

        val bilan = "lues=${step.readCount}, écrites=${step.writeCount}, " +
                "numéros=$etatCompteur, threads=$threads en $duree ms\nLibelle le plus élevé=$dernierLibelle"

        val texte = "tp10Job → ${execution.status} (exécution #${execution.id}) — $bilan"
        if (execution.status == BatchStatus.COMPLETED) {
            redirect.addFlashAttribute("message", texte)
        } else {
            redirect.addFlashAttribute("errorMessage", texte)
        }
        return "redirect:/"
    }

    /* -------------------------------- */
    // Pour l'UI
    /* -------------------------------- */

    @ExceptionHandler(Exception::class)
    fun onJobError(e: Exception, redirect: RedirectAttributes): String {
        e.printStackTrace()
        if (e is JobExecutionException) {
            redirect.addFlashAttribute("errorMessage", "Impossible de lancer le job : ${e.message}")
        }
        else {
            redirect.addFlashAttribute("errorMessage", "Une erreur est survenue : ${e.message}")
        }
        return "redirect:/"
    }

    /** Les dernieres executions, tous jobs confondus, de la plus recente a la plus ancienne. */
    private fun recentExecutions(limit: Int = 12): List<ExecView> =
        jobRepository.jobNames
            .flatMap { name -> jobRepository.getJobInstances(name, 0, 50) }
            .flatMap { instance -> jobRepository.getJobExecutions(instance) }
            .sortedByDescending { it.id }
            .take(limit)
            .map { e ->
                val start = e.startTime
                val end = e.endTime
                ExecView(
                    jobName = e.jobInstance.jobName,
                    id = e.id,
                    status = e.status.name,
                    ok = e.status == BatchStatus.COMPLETED,
                    failed = e.status == BatchStatus.FAILED,
                    start = start?.format(TIME_FORMAT) ?: "—",
                    duration = if (start != null && end != null)
                        "${Duration.between(start, end).toMillis()} ms"
                    else "—",
                    exitCode = e.exitStatus.exitCode,
                    params = e.jobParameters.joinToString(", ") { "${it.name()}=${it.value()}" },
                    exitMessage = e.exitStatus.exitDescription.take(400),
                    steps = e.stepExecutions.map { s ->
                        StepView(
                            name = s.stepName,
                            status = s.status.name,
                            read = s.readCount,
                            write = s.writeCount,
                            commit = s.commitCount,
                            rollback = s.rollbackCount,
                            skip = s.skipCount
                        )
                    }
                )
            }
}


/** Vue simplifiee d'une execution de job pour l'affichage (evite d'exposer les blobs). */
data class ExecView(
    val jobName: String,
    val id: Long,
    val status: String,
    val ok: Boolean,
    val failed: Boolean,
    val start: String,
    val duration: String,
    val exitCode: String,
    val params: String,
    val exitMessage: String,
    val steps: List<StepView>
)

data class StepView(
    val name: String,
    val status: String,
    val read: Long,
    val write: Long,
    val commit: Long,
    val rollback: Long,
    // skips = lecture + traitement + ecriture (TP7)
    val skip: Long
)
