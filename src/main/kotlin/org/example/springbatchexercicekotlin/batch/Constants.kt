package org.example.springbatchexercicekotlin.batch

import java.time.format.DateTimeFormatter


const val TVA = 1.20
const val CHUNK_SIZE = 10
val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

const val VENTES_CSV = "data/ventes.csv"
// Les SORTIES generees vont dans data/out/.
const val TP5_OUTPUT = "data/out/tp5_ventes.csv"

// TP6 : 2e fichier source propose dans le menu deroulant de l'IHM (12 lignes).
const val TP6_VENTES_CSV = "data/tp6_ventes.csv"

// TP7 : 80 ventes dont 5 corrompues (3 illisibles + 2 montants negatifs).
const val TP7_VENTES_5L_CSV = "data/tp7_ventes_5lcorrompues.csv"

// TP7 : 80 ventes dont 10 corrompues (7 illisibles + 3 montants negatifs).
const val TP7_VENTES_10L_CSV = "data/tp7_ventes_10lcorrompues.csv"

// TP7 : les lignes rejetees et leur cause, ECRASE a chaque execution du step (sortie -> data/out/).
const val TP7_REJETS_CSV = "data/out/tp7_rejets.csv"

// TP7 : 4 lignes valides + 1 ligne PARFAITEMENT lisible mais a date invalide
// ("pas-une-date"). Elle passe le reader et la regle metier, mais fait echouer
// LocalDate.parse -> DateTimeParseException, une exception NI skippee NI retry.
const val TP7_VENTES_ERREUR_INATTENDUE_CSV = "data/tp7_ventes_erreur_inattendue.csv"

// TP8 : objectif de chiffre d'affaires du jour. Le bilan (afterStep) en deduit
// l'ExitStatus metier : >= objectif -> OBJECTIF_ATTEINT, >= moitie -> A_SURVEILLER,
// sinon -> ALERTE. Ces exitCodes aiguilleront les flows au TP8 partie 2.
const val OBJECTIF_CA = 1000.0

// TP10 : 500 ventes a importer. Le processor simule une action LENTE (Thread.sleep)
// pour rendre le step long en mono-thread -> on le parallelise avec un TaskExecutor.
const val TP10_VENTES_CSV = "data/tp10_ventes.csv"

// Duree du "gros traitement" simule par item (ms). En mono-thread : 500 x cette duree.
const val TP10_SLEEP_MS = 8L

// Meme "gros traitement" simule qu'au TP10, pour rendre le parallelisme observable.
const val TP11_SLEEP_MS = 8L

// TP12 : tests de batch. Le job fusionne deux CSV en un 3e, en supprimant les
// doublons (lignes identiques). L'en-tete attendue est VALIDEE par le reader
// (skippedLinesCallback) : ordre/nom des colonnes faux -> refus.
const val TP12_ENTETE = "date;idBoutique;produit;montantHt"
const val TP12_VENTES_A = "data/tp12/ventes_A.csv"
const val TP12_VENTES_B = "data/tp12/ventes_B.csv"
const val TP12_FUSION_OUTPUT = "data/out/tp12_fusion.csv"

// TP final : planification de camions. Un CSV "ville;magasin;nb" (500 commandes,
// 8 grosses villes) -> mise en base -> hausse meteo -> camions.txt + 1 fichier par
// chauffeur (ville) -> archivage.
// ENTREES  : data/tpfinal/          (versionnees)
// SORTIES  : data/out/tpfinal/      (generees, ignorees par git comme tout data/out/)
// data/tpfinal/out/ n'est PLUS ecrit par le job : c'est l'EXEMPLE de sortie attendue,
// versionne pour que l'apprenant compare son resultat.
const val TPFINAL_COMMANDES_CSV = "data/tpfinal/commandes.csv"                 // 500 valides, total 56072
const val TPFINAL_COMMANDES_15L_CSV = "data/tpfinal/commandes15lcorrompus.csv" // 15 invalides (8 illisibles / 7 negatives)
const val TPFINAL_COMMANDES_25L_CSV = "data/tpfinal/commande25corrompu.csv"    // 25 invalides : passe aussi (aucun seuil)

// Racine des sorties generees par le job.
const val TPFINAL_OUT_DIR = "data/out/tpfinal"

// Plan de livraison du jour : purge par tpFinalNettoyageStep, regenere par le split
// camions/chauffeurs. Ces deux dossiers sont ECRASES a chaque execution.
const val TPFINAL_CAMIONS_TXT = "$TPFINAL_OUT_DIR/entrepot/camions.txt"
const val TPFINAL_CHAUFFEURS_DIR = "$TPFINAL_OUT_DIR/chauffeurs"

// Les rejets sont ranges dans un dossier DATE (data/out/tpfinal/2026-07-29/rejets.csv).
// Ce sont des FONCTIONS et pas des const : la date n'est connue qu'au lancement du job.
fun dossierDuJourTpFinal(): String = "$TPFINAL_OUT_DIR/${java.time.LocalDate.now()}"

/** Les commandes rejetees et leur cause (ecrase a chaque execution du step d'import). */
fun cheminRejetsTpFinal(): String = "${dossierDuJourTpFinal()}/rejets.csv"

// Hausse liee a la chaleur : > 25° -> +10%, > 35° -> +20%.
const val TPFINAL_SEUIL_CHAUD = 25.0
const val TPFINAL_SEUIL_CANICULE = 35.0



