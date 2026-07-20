package org.example.springbatchexercicekotlin.batch

import java.time.format.DateTimeFormatter


const val TVA = 1.20
const val CHUNK_SIZE = 10
val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

const val VENTES_CSV = "data/ventes.csv"
const val TP5_OUTPUT = "data/tp5_ventes.csv"
const val TP6_VENTES_CSV = "data/tp6_ventes.csv"