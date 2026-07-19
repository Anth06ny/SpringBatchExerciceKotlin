package org.example.springbatchexercicekotlin.web

import org.example.springbatchexercicekotlin.batch.TIME_FORMAT
import org.example.springbatchexercicekotlin.batch.repository.VenteRepository
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.parameters.JobParametersBuilder
import org.springframework.batch.core.launch.JobOperator
import org.springframework.batch.core.repository.JobRepository
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import java.time.Duration
import java.time.format.DateTimeFormatter


@Controller
class WebController(
    private val jobOperator: JobOperator,
    private val jobRepository: JobRepository,
    private val venteRepository: VenteRepository,
    private val helloJob: Job
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
        return "redirect:/"
    }

    @PostMapping("/jobs/tp2")
    fun tp2(
        @RequestParam(defaultValue = "") message: String,
        redirect: RedirectAttributes
    ): String {

        return "redirect:/"
    }


    @PostMapping("/jobs/tp3")
    fun tp3(
        @RequestParam(defaultValue = "1") runId: String,
        @RequestParam(defaultValue = "false") fail: Boolean,
        redirect: RedirectAttributes
    ): String {

        return "redirect:/"
    }

    @PostMapping("/jobs/tp4")
    fun tp4(redirect: RedirectAttributes): String {

        // On vide la table VENTE avant l'import : chaque clic reflete donc le seul
        // contenu du CSV (sinon les lignes s'accumuleraient à chaque relance).
        venteRepository.deleteAll()

        return "redirect:/"
    }

    /* -------------------------------- */
    // Pour l'UI
    /* -------------------------------- */

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
                            rollback = s.rollbackCount
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
    val rollback: Long
)
