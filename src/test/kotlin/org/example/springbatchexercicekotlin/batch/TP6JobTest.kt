package org.example.springbatchexercicekotlin.batch

import org.example.springbatchexercicekotlin.batch.config.TP6_JobConfig
import org.example.springbatchexercicekotlin.batch.config.cheminRapportTp6
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.JobExecution
import org.springframework.batch.core.job.parameters.JobParametersBuilder
import org.springframework.batch.core.launch.JobOperator
import org.springframework.batch.infrastructure.item.ExecutionContext
import org.springframework.batch.infrastructure.item.ItemStreamReader
import org.springframework.batch.test.MetaDataInstanceFactory
import org.springframework.batch.test.StepScopeTestUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import java.io.File
import java.util.*
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Suite de validation du TP6 (passage de parametres / late binding).
 *
 * Les tests sont numerotes et executes dans l'ordre du COMPOSANT concerne :
 *   01-02  READER     : le parametre `fichierSource` choisit le fichier lu
 *   03-06  PROCESSOR  : `montantMini` filtre, `totalTtc` convertit
 *   07-10  WRITER     : `format` choisit le writer, l'extension et l'en-tete
 *   11-12  JOB        : le JobParametersValidator refuse les parametres invalides
 *   13     CHAINE     : les 4 parametres combines sur un meme lancement
 *
 * Tant que les beans ne sont pas @StepScope (avec @Value("#{jobParameters[...]}")),
 * ces tests echouent : c'est exactement le retour attendu cote projet apprenant.
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.MethodName::class)
class TP6JobTest {

    @Autowired
    lateinit var jobOperator: JobOperator

    @Autowired
    @Qualifier("tp6Job")
    lateinit var tp6Job: Job

    // Le PROXY @StepScope, injectable normalement comme n'importe quel bean
    @Autowired
    lateinit var tp6Reader: ItemStreamReader<TP6_JobConfig.TP6VenteDTO>

    /* ========================================================================= */
    /* READER — le parametre `fichierSource` decide du fichier lu                */
    /* ========================================================================= */

    /**
     * Les deux tests suivants sont volontairement symetriques : un reader qui aurait
     * le fichier EN DUR passerait l'un des deux mais echouerait sur l'autre.
     */
    @Test
    fun `01 reader - le parametre fichierSource choisit ventes_csv`() {
        val execution = lancer(VENTES_CSV, "CSV", totalTtc = false, montantMini = 0.0)

        val attendues = lignesSource(VENTES_CSV)          // 25 lignes
        val obtenues = lignesData(cheminRapportTp6("CSV"))

        assertEquals(
            attendues.size, obtenues.size,
            "La sortie doit contenir autant de lignes que ventes.csv"
        )
        assertEquals(
            attendues.map { produit(it) }, obtenues.map { produit(it) },
            "Les produits exportes doivent etre ceux de ventes.csv"
        )
        assertEquals(attendues.size.toLong(), stepChunk(execution).readCount)
    }

    @Test
    fun `02 reader - le parametre fichierSource choisit tp6_ventes_csv`() {
        val execution = lancer(TP6_VENTES_CSV, "CSV", totalTtc = false, montantMini = 0.0)

        val attendues = lignesSource(TP6_VENTES_CSV)      // 12 lignes
        val obtenues = lignesData(cheminRapportTp6("CSV"))

        assertEquals(
            attendues.size, obtenues.size,
            "La sortie doit contenir autant de lignes que tp6_ventes.csv"
        )
        assertEquals(
            attendues.map { produit(it) }, obtenues.map { produit(it) },
            "Les produits exportes doivent etre ceux de tp6_ventes.csv"
        )
        assertEquals(attendues.size.toLong(), stepChunk(execution).readCount)
    }

    /**
     * Meme point que les tests 01/02 (le SpEL `#{jobParameters['fichierSource']}` doit se
     * resoudre), mais isole : pas de job, pas de processor, pas de writer, pas de fichier de
     * sortie. Si ce test casse, le probleme vient du reader ; s'il passe mais que 01/02
     * echouent, le probleme est ailleurs dans la chaine (processor, writer...).
     */
    @Test
    fun `02b reader isole - StepScope resout fichierSource sans lancer tout le job`() {
        val params = JobParametersBuilder()
            .addString("fichierSource", TP6_VENTES_CSV)
            .toJobParameters()

        // Fabrique une StepExecution portant CES JobParameters, sans jamais lancer tp6Job.
        val stepExecution = MetaDataInstanceFactory.createStepExecution(params)

        // StepScopeTestUtils.doInStepScop enregistre ce StepExecution le temps du bloc.
        val premiereLigne = StepScopeTestUtils.doInStepScope<TP6_JobConfig.TP6VenteDTO>(stepExecution) {
            tp6Reader.open(ExecutionContext())
            tp6Reader.read() ?: error("tp6Reader n'a renvoye aucune ligne : fichierSource mal resolu ?")
        }

        // tp6_ventes.csv (boutiques B05-B07) : premiere ligne de donnees = B05
        assertEquals("B05", premiereLigne.idBoutique)
    }



    /* ========================================================================= */
    /* PROCESSOR — `montantMini` filtre, `totalTtc` convertit                    */
    /* ========================================================================= */

    @Test
    fun `03 processor - montantMini filtre les lignes sous le seuil`() {
        val seuil = 50.0
        val execution = lancer(VENTES_CSV, "CSV", totalTtc = false, montantMini = seuil)

        val source = lignesSource(VENTES_CSV)
        val gardees = source.filter { montant(it) >= seuil }
        val obtenues = lignesData(cheminRapportTp6("CSV"))

        assertTrue(
            gardees.size < source.size,
            "Le seuil de test doit ecarter des lignes, sinon le test ne prouve rien"
        )
        assertEquals(
            gardees.size, obtenues.size,
            "Seules les ventes dont le montant HT atteint $seuil doivent etre exportees"
        )
        assertTrue(
            obtenues.all { montant(it) >= seuil },
            "Aucune ligne sous le seuil ne doit se retrouver dans la sortie"
        )

        // Un processor qui renvoie null n'ecrit pas : la ligne compte dans filterCount.
        val step = stepChunk(execution)
        assertEquals(source.size.toLong(), step.readCount, "Toutes les lignes doivent etre LUES")
        assertEquals(gardees.size.toLong(), step.writeCount, "writeCount = lignes gardees")
        assertEquals(
            (source.size - gardees.size).toLong(), step.filterCount,
            "Les lignes ecartees par le processor doivent apparaitre en filterCount"
        )
    }

    @Test
    fun `04 processor - montantMini a zero ne filtre aucune ligne`() {
        val execution = lancer(TP6_VENTES_CSV, "CSV", totalTtc = false, montantMini = 0.0)

        val step = stepChunk(execution)
        assertEquals(0L, step.filterCount, "Avec un seuil a 0, aucune ligne ne doit etre filtree")
        assertEquals(step.readCount, step.writeCount, "Toutes les lignes lues doivent etre ecrites")
    }

    @Test
    fun `05 processor - totalTtc coche convertit les montants en TTC`() {
        lancer(TP6_VENTES_CSV, "CSV", totalTtc = true, montantMini = 0.0)

        val attendus = lignesSource(TP6_VENTES_CSV).map { montant(it) * TVA }
        val obtenus = lignesData(cheminRapportTp6("CSV")).map { montant(it) }

        assertEquals(
            attendus.map { format(it) }, obtenus.map { format(it) },
            "Case cochee : les montants doivent valoir HT x 1,20"
        )
    }

    @Test
    fun `06 processor - totalTtc decoche laisse les montants en HT`() {
        lancer(TP6_VENTES_CSV, "CSV", totalTtc = false, montantMini = 0.0)

        val attendus = lignesSource(TP6_VENTES_CSV).map { montant(it) }
        val obtenus = lignesData(cheminRapportTp6("CSV")).map { montant(it) }

        assertEquals(
            attendus.map { format(it) }, obtenus.map { format(it) },
            "Case decochee : les montants doivent rester ceux du fichier source"
        )
    }

    /* ========================================================================= */
    /* WRITER — `format` choisit le writer, l'extension et l'en-tete             */
    /* ========================================================================= */

    @Test
    fun `07 writer - format CSV produit un fichier csv delimite`() {
        lancer(TP6_VENTES_CSV, "CSV", totalTtc = false, montantMini = 0.0)

        val lignes = lignesFichier(cheminRapportTp6("CSV"))
        val donnees = lignes.drop(1) // on saute l'en-tete

        assertTrue(donnees.isNotEmpty(), "Le fichier CSV doit contenir des lignes de donnees")
        assertTrue(
            donnees.all { it.split(";").size == 4 },
            "Chaque ligne CSV doit avoir 4 colonnes separees par ';'"
        )
    }

    @Test
    fun `08 writer - format JSON produit un tableau json`() {
        lancer(TP6_VENTES_CSV, "JSON", totalTtc = false, montantMini = 0.0)

        val contenu = File(cheminRapportTp6("JSON")).readText().trim()

        assertTrue(contenu.startsWith("["), "Le rapport JSON doit etre un tableau")
        assertTrue(contenu.endsWith("]"), "Le rapport JSON doit etre un tableau")
        assertContains(contenu, "idBoutique", message = "Le DTO du TP6 doit etre serialise tel quel")

        val montantsJson = montantsDuJson(contenu)
        val attendus = lignesSource(TP6_VENTES_CSV).map { montant(it) }

        assertEquals(
            attendus.map { format(it) }, montantsJson.map { format(it) },
            "Le JSON doit contenir les memes ventes que le CSV source"
        )
    }

    @Test
    fun `09 writer - l extension du fichier de sortie suit le format`() {
        assertEquals("data/out/tp6_ventes_sortie.csv", cheminRapportTp6("CSV"))
        assertEquals("data/out/tp6_ventes_sortie.json", cheminRapportTp6("JSON"))

        File(cheminRapportTp6("CSV")).delete()
        File(cheminRapportTp6("JSON")).delete()

        lancer(TP6_VENTES_CSV, "CSV", totalTtc = false, montantMini = 0.0)
        assertTrue(File(cheminRapportTp6("CSV")).exists(), "format=CSV doit ecrire le fichier .csv")

        lancer(TP6_VENTES_CSV, "JSON", totalTtc = false, montantMini = 0.0)
        assertTrue(File(cheminRapportTp6("JSON")).exists(), "format=JSON doit ecrire le fichier .json")
    }

    @Test
    fun `10 writer - l en-tete CSV indique montantHT ou montantTTC`() {
        lancer(TP6_VENTES_CSV, "CSV", totalTtc = false, montantMini = 0.0)
        assertEquals(
            "date;idBoutique;produit;montantHT", lignesFichier(cheminRapportTp6("CSV")).first(),
            "Case decochee : la 4e colonne doit s'appeler montantHT"
        )

        lancer(TP6_VENTES_CSV, "CSV", totalTtc = true, montantMini = 0.0)
        assertEquals(
            "date;idBoutique;produit;montantTTC", lignesFichier(cheminRapportTp6("CSV")).first(),
            "Case cochee : la 4e colonne doit s'appeler montantTTC"
        )
    }

    /* ========================================================================= */
    /* JOB — le JobParametersValidator                                           */
    /* ========================================================================= */

    @Test
    fun `11 job - le validator refuse un parametre manquant`() {
        // `format`, `totalTtc` et `montantMini` sont absents : le validator doit refuser
        // le lancement AVANT que le reader ne tente d'ouvrir un fichier.
        val incomplets = JobParametersBuilder()
            .addString("fichierSource", VENTES_CSV)
            .addLong("timestamp", System.nanoTime())
            .toJobParameters()

        assertFailsWith<Exception>("Un parametre requis manquant doit empecher le demarrage") {
            jobOperator.start(tp6Job, incomplets)
        }
    }

    @Test
    fun `12 job - le validator tolere un parametre inconnu et se contente d un warning`() {
        // Tous les parametres requis sont la, mais `couleur` n'est ni requis ni optionnel.
        // ATTENTION : DefaultJobParametersValidator ne LEVE PAS pour une cle inconnue,
        // il se contente d'un logger.warn(). Seule une cle REQUISE manquante fait echouer
        // le demarrage (cf. test 11). A savoir si on attend une validation stricte.
        val inconnu = JobParametersBuilder()
            .addString("fichierSource", VENTES_CSV)
            .addString("format", "CSV")
            .addString("totalTtc", "false")
            .addDouble("montantMini", 0.0)
            .addString("couleur", "rouge")
            .addLong("timestamp", System.nanoTime())
            .toJobParameters()

        val execution = jobOperator.start(tp6Job, inconnu)

        assertEquals(
            BatchStatus.COMPLETED, execution.status,
            "Une cle inconnue ne doit pas empecher le job de tourner (simple warning)"
        )
    }

    /* ========================================================================= */
    /* CHAINE COMPLETE — les 4 parametres sur un meme lancement                  */
    /* ========================================================================= */

    @Test
    fun `13 chaine complete - les quatre parametres se combinent`() {
        val seuil = 50.0
        // ventes.csv + JSON + TTC + seuil : chaque parametre doit jouer son role.
        val execution = lancer(VENTES_CSV, "JSON", totalTtc = true, montantMini = seuil)

        val source = lignesSource(VENTES_CSV)
        val gardees = source.filter { montant(it) >= seuil }   // filtre sur le HT
        val attendus = gardees.map { montant(it) * TVA }       // puis conversion TTC

        val contenu = File(cheminRapportTp6("JSON")).readText().trim()
        val obtenus = montantsDuJson(contenu)

        assertEquals(
            attendus.map { format(it) }, obtenus.map { format(it) },
            "Le filtre porte sur le HT, la conversion TTC s'applique ensuite, le tout en JSON"
        )

        val step = stepChunk(execution)
        assertEquals(source.size.toLong(), step.readCount)
        assertEquals(gardees.size.toLong(), step.writeCount)
        assertEquals((source.size - gardees.size).toLong(), step.filterCount)
    }

    /* --------------------------------- Helpers --------------------------------- */

    private fun lancer(
        fichierSource: String,
        format: String,
        totalTtc: Boolean,
        montantMini: Double
    ): JobExecution {
        val params = JobParametersBuilder()
            .addString("fichierSource", fichierSource)
            .addString("format", format)
            .addString("totalTtc", totalTtc.toString())
            .addDouble("montantMini", montantMini)
            .addLong("timestamp", System.nanoTime())
            .toJobParameters()

        val execution = jobOperator.start(tp6Job, params)
        assertEquals(
            BatchStatus.COMPLETED, execution.status,
            "tp6Job doit se terminer avec succes (${execution.allFailureExceptions})"
        )
        return execution
    }

    private fun stepChunk(execution: JobExecution) =
        execution.stepExecutions.first { it.stepName == "tp6Step" }

    /** Lignes non vides du fichier genere (racine du projet = repertoire de travail). */
    private fun lignesFichier(chemin: String): List<String> =
        File(chemin).readLines().filter { it.isNotBlank() }

    /** Lignes de donnees de la sortie CSV (en-tete exclu). */
    private fun lignesData(chemin: String): List<String> =
        lignesFichier(chemin).filter { ligne ->
            val cols = ligne.split(";")
            cols.size == 4 && cols[3].toDoubleOrNull() != null
        }

    /** Lignes de donnees d'un CSV source (en-tete saute). */
    private fun lignesSource(chemin: String): List<String> =
        File(chemin).bufferedReader()
            .useLines { lines -> lines.filter { it.isNotBlank() }.drop(1).toList() }

    /** Les valeurs du champ `montant` d'un rapport JSON, dans l'ordre du fichier. */
    private fun montantsDuJson(contenu: String): List<Double> =
        Regex("\"montant\"\\s*:\\s*([0-9.]+)")
            .findAll(contenu).map { it.groupValues[1].toDouble() }.toList()

    private fun montant(ligne: String): Double = ligne.split(";")[3].toDouble()

    private fun produit(ligne: String): String = ligne.split(";")[2]

    private fun format(montant: Double): String = String.format(Locale.US, "%.2f", montant)
}
