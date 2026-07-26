package org.example.springbatchexercicekotlin.batch

import org.example.springbatchexercicekotlin.batch.config.TP12_JobConfig.TP12VenteDTO
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.launch.JobOperator
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.fail

@SpringBootTest
@TestMethodOrder(MethodOrderer.MethodName::class)
class TP12JobTest {

    @Autowired
    lateinit var jobOperator: JobOperator

    @Autowired
    lateinit var tp12Reader: FlatFileItemReader<TP12VenteDTO>

    @Autowired
    @Qualifier("tp12Job")
    lateinit var tp12Job: Job

    @Test
    fun `01 reader - entete valide lit toutes les lignes`() {
        // TODO 01 : lire "data/tp12/ventes_A.csv" via tp12Reader EN ISOLATION, puis verifier :
        //   - 5 lignes lues ; le 1er produit == "Clavier" ; la 1re boutique == "B01".
        fail("TODO 01 : tester le reader sur un CSV a l'en-tete valide")
    }

    @Test
    fun `02 reader - entete dans le mauvais ordre est rejetee`() {
        // TODO 02 : lire "data/tp12/entete_desordre.csv" doit ECHOUER des l'open()
        //   (le reader valide l'en-tete). Utiliser assertFailsWith<...> { ... }.
        fail("TODO 02 : l'en-tete en desordre doit etre refusee")
    }

    @Test
    fun `03 reader - nombre de colonnes invalide est rejete`() {
        // TODO 03 : lire "data/tp12/colonnes_manquantes.csv" doit ECHOUER a la LECTURE
        //   de la ligne fautive (3 colonnes). Utiliser assertFailsWith<...> { ... }.
        fail("TODO 03 : une ligne au mauvais nombre de colonnes doit etre refusee")
    }

    @Test
    fun `04 job - fusionne les deux csv sans doublon`() {
        // TODO 04 : lancer tp12Job (jobOperator.start avec un parametre unique), verifier
        //   BatchStatus.COMPLETED, puis relire TP12_FUSION_OUTPUT : 8 lignes de donnees
        //   (5 + 5 - 2 doublons), aucune ligne en double.
        fail("TODO 04 : lancer le job et verifier la fusion sans doublon")
    }
}
