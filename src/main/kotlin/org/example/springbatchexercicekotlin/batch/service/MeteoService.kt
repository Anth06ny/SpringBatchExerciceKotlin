package org.example.springbatchexercicekotlin.batch.service

import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient

/**
 * TP final, etape 2 — recuperation de la temperature d'une ville.
 *
 * On isole l'appel externe derriere une INTERFACE : le job depend de `MeteoService`,
 * pas d'OpenWeatherMap. Dans les tests on remplace donc l'implementation reelle par un
 * FAUX service (mock/stub) qui renvoie la temperature qu'on veut, sans reseau et de
 * maniere deterministe (c'est ce qui rend l'etape meteo testable).
 */
interface MeteoService {
    /** Temperature actuelle en °C pour la ville. UN seul appel par ville cote job. */
    fun temperature(ville: String): Double
}

/**
 * Implementation reelle : appelle l'API OpenWeatherMap (utilisee quand on lance le job
 * depuis l'IHM). En cas de souci reseau/parsing on renvoie 0° = aucune hausse, pour ne
 * jamais faire echouer le batch a cause de la meteo.
 */
@Service
class OpenWeatherMeteoService : MeteoService {

    private val restClient = RestClient.create()
    private val cle = "b80967f0a6bd10d23e44848547b26550"

    override fun temperature(ville: String): Double =
        try {
            val reponse = restClient.get()
                .uri(
                    "https://api.openweathermap.org/data/2.5/find?q={ville}&appid={cle}&units=metric&lang=fr",
                    ville, cle
                )
                .retrieve()
                .body(Map::class.java)

            // { "list": [ { "main": { "temp": 21.3 } } ] }
            val liste = reponse?.get("list") as? List<*>
            val premier = liste?.firstOrNull() as? Map<*, *>
            val main = premier?.get("main") as? Map<*, *>
            (main?.get("temp") as? Number)?.toDouble() ?: 0.0
        } catch (e: Exception) {
            println("TP final — meteo indisponible pour $ville (${e.message}), on n'applique aucune hausse")
            0.0
        }
}
