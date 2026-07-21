package org.example.springbatchexercicekotlin.batch

import org.example.springbatchexercicekotlin.batch.repository.VenteRepository
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
import java.io.File
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Suite de validation du TP7 (tolerance aux fautes : skip, retry, tracage des rejets).
 *
 * Les tests sont numerotes et executes dans l'ordre du cours :
 *   01-02  SKIP LECTURE    : FlatFileParseException skippee, skipLimit(6)
 *   03-05  SKIP TRAITEMENT : VenteInvalideException (montant <= 0) skippee
 *   06-08  LISTENER        : les rejets sont traces dans data/tp7_rejets.csv
 *   09-11  RETRY           : VenteInstableException (panne simulee) rejouee
 *   12     CHAINE          : skip et retry combines sur le fichier 10L
 *
 * Rappel des jeux de donnees (80 ventes chacun, memes lignes valides) :
 *   - tp7_ventes_5lcorrompues.csv  : 3 illisibles + 2 montants negatifs -> 75 valides
 *   - tp7_ventes_10lcorrompues.csv : 7 illisibles + 3 montants negatifs -> 70 valides
 *
 * MOTEUR BATCH 6 : le skip (lecture comme traitement) et le retry se font SUR PLACE,
 * sans rollback du chunk — les tests 05 et 11 verrouillent ce comportement.
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.MethodName::class)
class TP7JobTest {

    @Autowired
    lateinit var jobOperator: JobOperator

    @Autowired
    @Qualifier("tp7Job")
    lateinit var tp7Job: Job

    @Autowired
    lateinit var venteRepository: VenteRepository

    /* ========================================================================= */
    /* SKIP LECTURE — les lignes illisibles sont ecartees, dans la limite de 6   */
    /* ========================================================================= */

    @Test
    fun `01 skip lecture - tp7_ventes_5l_corrompues passe sous la limite`() {
        val execution = lancer(TP7_VENTES_5L_CSV)

        assertEquals(BatchStatus.COMPLETED, execution.status,
            "3 lignes illisibles < skipLimit(6) : le job doit reussir")
        assertEquals(3L, step(execution).readSkipCount,
            "Les 3 lignes illisibles doivent etre skippees en LECTURE")
        assertEquals(77L, step(execution).readCount,
            "readCount ne compte pas les lignes skippees : 80 - 3 = 77")
    }

    @Test
    fun `02 skip lecture - tp7_ventes_10l_corrompues depasse la limite`() {
        val execution = lancer(TP7_VENTES_10L_CSV)

        assertEquals(BatchStatus.FAILED, execution.status,
            "10 lignes corrompues > skipLimit(6) : le job doit echouer")
        assertTrue(step(execution).exitStatus.exitDescription.contains("SkipLimitExceeded"),
            "L'echec doit venir du depassement de la limite de skip")
        assertEquals(6L, step(execution).skipCount,
            "La limite est un DEPASSEMENT : 6 rejets sont passes, le 7e a tout arrete")
    }

    /* ========================================================================= */
    /* SKIP TRAITEMENT — la regle metier (montant <= 0) rejette via une exception */
    /* ========================================================================= */

    @Test
    fun `03 skip traitement - les montants negatifs sont rejetes`() {
        val execution = lancer(TP7_VENTES_5L_CSV)

        assertEquals(2L, step(execution).processSkipCount,
            "Les 2 montants negatifs doivent etre skippes en TRAITEMENT (VenteInvalideException)")
        assertEquals(0L, step(execution).filterCount,
            "Un rejet par exception n'est PAS un filtrage (pas de return null ici)")
    }

    @Test
    fun `04 skip traitement - seules les lignes valides sont en base`() {
        lancer(TP7_VENTES_5L_CSV)

        val ventes = venteRepository.findAll()
        assertEquals(75, ventes.size,
            "80 lignes - 3 illisibles - 2 montants negatifs = 75 ventes en base")
        assertTrue(ventes.none { it.prixHt <= 0 },
            "Aucun montant negatif ne doit atteindre la base")
        // Somme calculee depuis les entites (sans dependre de sumPrixTtc, exercice du TP5).
        val sommeTtc = ventes.sumOf { it.prixTtc }
        assertTrue(abs(sommeTtc - 6167.35) < 0.1,
            "La somme TTC attendue est 6167.35 (75 lignes valides), obtenu : $sommeTtc")
    }

    /**
     * NOUVEAU MOTEUR BATCH 6 : le skip se fait SUR PLACE, item par item, sans
     * rollback du chunk (Batch 5 rollbackait puis rejouait le chunk entier).
     */
    @Test
    fun `05 skip - le rejet se fait sur place sans aucun rollback`() {
        val execution = lancer(TP7_VENTES_5L_CSV)

        assertEquals(0L, step(execution).rollbackCount,
            "En Batch 6, skipper (lecture ou traitement) ne coute plus un rollback")
        assertEquals(75L, step(execution).writeCount)
        // Un chunk = 10 TENTATIVES de lecture (pas 10 items) : les lignes skippees
        // "creusent" les chunks. 80 tentatives / 10 = 8 commits, les 3 derniers lots
        // ne font que 8, 8 et 9 ecritures (visible dans les logs du writer).
        assertEquals(8L, step(execution).commitCount,
            "80 tentatives de lecture par chunks de 10 = 8 commits")
    }

    /* ========================================================================= */
    /* LISTENER — chaque rejet est trace dans data/tp7_rejets.csv                */
    /* ========================================================================= */

    @Test
    fun `06 listener - tp7_rejets_csv contient une ligne par rejet`() {
        lancer(TP7_VENTES_5L_CSV)

        assertEquals(5, rejets().size,
            "5 lignes corrompues = 5 rejets traces (3 lecture + 2 traitement)")
    }

    @Test
    fun `07 listener - tp7_rejets_csv precise la phase et la cause`() {
        lancer(TP7_VENTES_5L_CSV)

        val lignes = rejets()
        assertEquals(3, lignes.count { it.startsWith("LECTURE;") },
            "3 rejets en phase LECTURE (onSkipInRead)")
        assertEquals(2, lignes.count { it.startsWith("TRAITEMENT;") },
            "2 rejets en phase TRAITEMENT (onSkipInProcess)")
        assertTrue(lignes.any { it.startsWith("LECTURE;") && it.contains("quarante euros") },
            "La ligne brute illisible doit etre recopiee (FlatFileParseException.input)")
        assertTrue(lignes.any { it.startsWith("LECTURE;") && it.contains("illisible") },
            "La cause d'un rejet de lecture doit indiquer une ligne illisible")
        assertTrue(lignes.any { it.startsWith("TRAITEMENT;") && it.contains("Montant invalide") },
            "La cause d'un rejet de traitement doit venir de VenteInvalideException")
    }

    @Test
    fun `08 listener - tp7_rejets_csv est ecrase a chaque execution`() {
        lancer(TP7_VENTES_10L_CSV)   // laisse 6 rejets dans le fichier
        lancer(TP7_VENTES_5L_CSV)    // beforeStep doit REPARTIR d'un fichier vierge

        val lignes = rejets()
        assertEquals(5, lignes.size,
            "Les rejets du lancement precedent ne doivent pas s'accumuler")
        assertTrue(lignes.none { it.contains("abc") },
            "Aucune trace du fichier 10L (corruption 'abc') ne doit rester")
    }

    /* ========================================================================= */
    /* RETRY — la panne passagere est rejouee au lieu de faire echouer le step   */
    /* ========================================================================= */

    @Test
    fun `09 retry - risque desactive le job passe sans panne`() {
        val execution = lancer(TP7_VENTES_5L_CSV, risque = false)

        assertEquals(BatchStatus.COMPLETED, execution.status)
        assertEquals(75L, step(execution).writeCount)
    }

    @Test
    fun `10 retry - risque active le job passe grace au retry`() {
        val execution = lancer(TP7_VENTES_5L_CSV, risque = true)

        assertEquals(BatchStatus.COMPLETED, execution.status,
            "Chaque panne simulee (1 appel sur 10) doit etre rejouee avec succes")
        assertEquals(75L, step(execution).writeCount,
            "Le retry ne doit perdre AUCUNE ligne valide (contrairement a un skip)")
        assertEquals(75, venteRepository.count().toInt())
    }

    /**
     * NOUVEAU MOTEUR BATCH 6 : le retry rejoue l'ITEM sur place, sans rollback ni
     * rejeu du chunk. La re-tentative incremente le compteur du processor (11e appel,
     * 21e...), qui n'est plus un multiple de 10 : chaque panne guerit en un retry.
     */
    @Test
    fun `11 retry - la panne est rejouee sur place sans rollback`() {
        val execution = lancer(TP7_VENTES_5L_CSV, risque = true)

        assertEquals(0L, step(execution).rollbackCount,
            "Le retry Batch 6 se fait sur place : aucun rollback")
        assertEquals(3L, step(execution).readSkipCount,
            "Le retry ne doit pas perturber les skips de lecture")
        assertEquals(2L, step(execution).processSkipCount,
            "Le retry ne doit pas perturber les skips de traitement")
    }

    /* ========================================================================= */
    /* CHAINE COMPLETE — skip et retry se combinent                              */
    /* ========================================================================= */

    @Test
    fun `12 chaine complete - skip et retry se combinent`() {
        val execution = lancer(TP7_VENTES_10L_CSV, risque = true)

        assertEquals(BatchStatus.FAILED, execution.status,
            "Le retry guerit les pannes, mais les 10 lignes corrompues depassent toujours skipLimit(6)")
        assertTrue(step(execution).exitStatus.exitDescription.contains("SkipLimitExceeded"),
            "L'echec doit venir du skip, pas de la panne simulee (guerie par le retry)")
    }

    /* ========================================================================= */
    /* Aides                                                                     */
    /* ========================================================================= */

    /**
     * Lance tp7Job comme le ferait l'IHM : table VENTE videe avant, runId unique
     * (nouvelle JobInstance a chaque appel), fichier et risque non-identifiants.
     */
    private fun lancer(fichierSource: String, risque: Boolean = false): JobExecution {
        venteRepository.deleteAll()
        val params = JobParametersBuilder()
            .addString("runId", "test-${System.nanoTime()}")
            .addString("fichierSource", fichierSource, false)
            .addString("risque", risque.toString(), false)
            .toJobParameters()
        return jobOperator.start(tp7Job, params)
    }

    private fun step(execution: JobExecution): StepExecution =
        execution.stepExecutions.first { it.stepName == "tp7Step" }

    /** Les lignes de rejet (sans la ligne d'en-tete `phase;donnee;cause`). */
    private fun rejets(): List<String> =
        File(TP7_REJETS_CSV).readLines().drop(1).filter { it.isNotBlank() }
}
