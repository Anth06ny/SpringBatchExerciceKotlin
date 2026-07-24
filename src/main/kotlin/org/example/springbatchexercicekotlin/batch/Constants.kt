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