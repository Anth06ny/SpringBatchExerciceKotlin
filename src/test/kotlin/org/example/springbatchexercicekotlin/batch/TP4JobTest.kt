package org.example.springbatchexercicekotlin.batch

import jakarta.persistence.EntityManagerFactory
import org.example.springbatchexercicekotlin.batch.config.TP4_JobConfig
import org.example.springbatchexercicekotlin.batch.model.VenteCsvDTO
import org.example.springbatchexercicekotlin.batch.model.VenteEntity
import org.example.springbatchexercicekotlin.batch.repository.VenteRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.parameters.JobParametersBuilder
import org.springframework.batch.core.launch.JobOperator
import org.springframework.batch.infrastructure.item.Chunk
import org.springframework.batch.infrastructure.item.ExecutionContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.io.File
import java.time.LocalDate
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.fail

@SpringBootTest
class TP4JobTest {

    @Autowired
    lateinit var jobOperator: JobOperator

    // Injection par nom : il faut un @Bean nomme tp4Job
    @Autowired
    lateinit var tp4Job: Job

    @Autowired
    lateinit var venteRepository: VenteRepository

    // Le writer JPA a besoin de l'EntityManagerFactory et d'un gestionnaire de
    // transaction : contrairement au reader/processor, on ne peut pas le tester
    // sans le contexte Spring (mais on l'isole quand meme du job et du step).
    @Autowired
    lateinit var entityManagerFactory: EntityManagerFactory

    @Autowired
    lateinit var transactionManager: PlatformTransactionManager

    /**
     * Test ISOLE du reader : ni job, ni processor, ni writer, ni contexte Spring.
     * On instancie directement la config et on ouvre le reader a la main.
     *
     * Les proprietes du DTO sont lues par INTROSPECTION (voir `lire`) : le test
     * compile donc meme si VenteCsvDTO n'a encore aucun attribut, et echoue avec
     * un message parlant tant que l'apprenant n'a pas ajoute les champs attendus.
     */
    @Test
    fun venteReaderTest() {
        val attendu = nbLignesCsv()
        val reader = TP4_JobConfig().venteReader()

        // open() est OBLIGATOIRE avant read() (sinon ReaderNotOpenException).
        reader.open(ExecutionContext())
        try {
            val lignes = generateSequence { reader.read() }.toList()

            // Toutes les lignes de donnees sont lues, l'en-tete est saute.
            assertEquals(
                attendu, lignes.size,
                "Nombre de lignes lues incorrect : le reader doit sauter l'en-tete (.linesToSkip(1)) et lire les $attendu lignes de data/ventes.csv"
            )
            assertNull(
                reader.read(),
                "Le reader doit renvoyer null en fin de flux (une fois toutes les lignes lues)"
            )

            // Le decoupage ';' et le mapping vers le DTO sont corrects (1re ligne).
            val premiere = lignes.first()
            assertEquals(
                "2026-01-05", lire(premiere, "date"),
                "Colonne 'date' mal mappee : verifie l'ordre des .names(...) du reader et le champ 'date' de VenteCsvDTO"
            )
            assertEquals(
                "B01", lire(premiere, "idBoutique"),
                "Colonne 'idBoutique' mal mappee : verifie les .names(...) du reader et le champ 'idBoutique' de VenteCsvDTO"
            )
            assertEquals(
                "Clavier mecanique", lire(premiere, "produit"),
                "Colonne 'produit' mal mappee : verifie les .names(...) du reader et le champ 'produit' de VenteCsvDTO"
            )
            assertEquals(
                79.90, lire(premiere, "montantHt") as Double, 0.001,
                "Colonne 'montantHt' mal mappee : verifie le delimiteur ';', les .names(...) et le type Double du champ 'montantHt'"
            )
        }
        finally {
            reader.close()
        }
    }

    /**
     * Test ISOLE du processor : simple appel de fonction sur un DTO construit a la main.
     * -> valide le mapping DTO->Entity et le calcul du TTC sans base ni contexte Spring.
     *
     * Le DTO est alimente par INTROSPECTION (`ecrire`) car VenteCsvDTO est a coder :
     * le test compile meme quand le DTO est vide. En revanche VenteEntity est FOURNIE,
     * donc ses champs sont lus directement (pas d'introspection cote entite).
     */
    @Test
    fun venteProcessorTest() {
        val csv = VenteCsvDTO()
        ecrire(csv, "date", "2026-01-05")
        ecrire(csv, "idBoutique", "B01")
        ecrire(csv, "produit", "Clavier mecanique")
        ecrire(csv, "montantHt", 100.0)

        // process() renvoie null si le processor n'est pas encore code
        val entity = TP4_JobConfig().venteProcessor().process(csv)
            ?: fail("venteProcessor().process(...) a renvoye null : le processor doit toujours retourner une VenteEntity (aucun filtrage dans ce TP)")

        // Noms cote entite = noms METIER (differents du DTO) : c'est ce mapping qu'on verifie.
        assertEquals(
            LocalDate.parse("2026-01-05"), entity.dateVente,
            "Le processor doit mapper csv.date (String) -> VenteEntity.dateVente (LocalDate.parse)"
        )
        assertEquals(
            "B01", entity.boutique,
            "Le processor doit mapper csv.idBoutique -> VenteEntity.boutique"
        )
        assertEquals(
            "Clavier mecanique", entity.libelleProduit,
            "Le processor doit mapper csv.produit -> VenteEntity.libelleProduit"
        )
        assertEquals(
            100.0, entity.prixHt, 0.001,
            "Le processor doit mapper csv.montantHt -> VenteEntity.prixHt"
        )
        assertEquals(
            100.0 * TVA, entity.prixTtc, 0.001,
            "prixTtc mal calcule : il doit valoir prixHt * TVA (${TVA}), calcule dans le processor"
        )
    }

    /**
     * Test ISOLE du writer : hors job et hors step, mais AVEC le contexte Spring.
     * Le JpaItemWriter exige un EntityManagerFactory et une transaction active pour
     * persister/flusher — impossible de s'en passer, d'ou l'usage d'un TransactionTemplate.
     *
     * VenteEntity etant fournie, on construit les entites directement (pas d'introspection).
     */
    @Test
    fun venteWriterTest() {
        val writer = TP4_JobConfig().venteWriter(entityManagerFactory)

        val lot = Chunk(
            listOf(
                VenteEntity(
                    dateVente = LocalDate.parse("2026-01-05"),
                    boutique = "B01",
                    libelleProduit = "Clavier mecanique",
                    prixHt = 100.0,
                    prixTtc = 120.0
                ),
                VenteEntity(
                    dateVente = LocalDate.parse("2026-01-06"),
                    boutique = "B02",
                    libelleProduit = "Souris sans fil",
                    prixHt = 50.0,
                    prixTtc = 60.0
                )
            )
        )

        // JpaItemWriter persiste dans l'EntityManager lie a la transaction courante :
        // sans transaction active, rien n'est ecrit. On enveloppe donc l'appel.
        TransactionTemplate(transactionManager).executeWithoutResult {
            writer.write(lot)
        }

        // Tout le lot est en base.
        assertEquals(
            2L, venteRepository.count(),
            "Le writer doit persister toutes les ventes du lot recu"
        )

        // Les valeurs sont bien celles ecrites (verifie sur la ligne B01).
        val persistee = venteRepository.findAll().first { it.boutique == "B01" }
        assertEquals(
            "Clavier mecanique",
            persistee.libelleProduit,
            "Le writer ne doit pas alterer les donnees"
        )
        assertEquals(100.0, persistee.prixHt, 0.001)
        assertEquals(120.0, persistee.prixTtc, 0.001)
    }

    @Test
    fun testCompletTP4Job() {
        val attendu = nbLignesCsv()

        val params = JobParametersBuilder()
            .addLong("timestamp", System.currentTimeMillis())
            .toJobParameters()

        val execution = jobOperator.start(tp4Job, params)

        // 1) Le job se termine avec succes
        assertEquals(BatchStatus.COMPLETED, execution.status)

        // 2) Toutes les lignes du fichier sont en base
        val ventes = venteRepository.findAll()
        assertEquals(
            attendu.toLong(), venteRepository.count(),
            "La table VENTE doit contenir exactement le nombre de lignes du CSV"
        )

        // 3) Le TTC est bien calcule (HT * 1,20) — verifie sur au moins une ligne
        val vente = ventes.first()
        val ttcAttendu = vente.prixHt * TVA
        assertEquals(
            0, vente.prixTtc.compareTo(ttcAttendu),
            "prixTtc doit valoir prixHt * 1,20"
        )

        // 4) Metriques du step : tout lu = tout ecrit, et 1 commit par chunk
        val step = execution.stepExecutions.first()
        assertEquals(attendu.toLong(), step.readCount, "readCount = nb de lignes")
        assertEquals(attendu.toLong(), step.writeCount, "writeCount = nb de lignes")
        assertEquals(
            ((attendu + CHUNK_SIZE - 1) / CHUNK_SIZE).toLong(), step.commitCount,
            "commitCount = ceil(nbLignes / tailleChunk)"
        )
    }


    /**
     * Lit une propriete par INTROSPECTION. Si la classe ne l'expose pas encore
     * (attribut a coder dans le TP), on echoue avec un message explicite plutot
     * que de casser la COMPILATION du test.
     */
    private fun lire(cible: Any, nom: String): Any? {
        val prop = cible::class.memberProperties.firstOrNull { it.name == nom }
            ?: fail("La classe ${cible::class.simpleName} doit exposer une propriete '$nom' (a coder dans le TP)")
        prop.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return (prop as KProperty1<Any, *>).get(cible)
    }

    /** Ecrit une propriete par INTROSPECTION (meme logique que `lire`, cote setter). */
    private fun ecrire(cible: Any, nom: String, valeur: Any?) {
        val prop = cible::class.memberProperties.firstOrNull { it.name == nom }
            ?: fail("La classe ${cible::class.simpleName} doit exposer une propriete '$nom' (a coder dans le TP)")
        if (prop !is KMutableProperty1<*, *>)
            fail("La propriete '$nom' de ${cible::class.simpleName} doit etre 'var' (modifiable)")
        prop.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        (prop as KMutableProperty1<Any, Any?>).set(cible, valeur)
    }

    /** Nombre de lignes de donnees du CSV (hors en-tete) : la reference attendue en base. */
    private fun nbLignesCsv(): Int =
        File(VENTES_CSV)
            .bufferedReader()
            .useLines { lines -> lines.count { it.isNotBlank() } } - 1

    @BeforeEach
    fun clean() {
        venteRepository.deleteAll()
    }
}
