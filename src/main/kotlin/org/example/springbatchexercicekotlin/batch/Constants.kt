package org.example.springbatchexercicekotlin.batch

import java.time.format.DateTimeFormatter


const val TVA = 1.20
const val CHUNK_SIZE = 10
val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

const val VENTES_CSV = "data/ventes.csv"
const val TP5_OUTPUT = "data/tp5_ventes.csv"
const val TP6_VENTES_CSV = "data/tp6_ventes.csv"

// TP7 : 80 ventes dont 5 corrompues (3 illisibles + 2 montants negatifs).
const val TP7_VENTES_5L_CSV = "data/tp7_ventes_5lcorrompues.csv"

// TP7 : 80 ventes dont 10 corrompues (7 illisibles + 3 montants negatifs).
const val TP7_VENTES_10L_CSV = "data/tp7_ventes_10lcorrompues.csv"

// TP7 : les lignes rejetees et leur cause, ECRASE a chaque execution du step.
const val TP7_REJETS_CSV = "data/tp7_rejets.csv"