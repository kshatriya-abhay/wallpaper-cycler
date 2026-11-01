package dev.abhay.wallcycler

import kotlin.random.Random

/** A random image other than [current], or null when there's no other image. */
fun <T> pickNext(images: List<T>, current: T?, random: Random = Random.Default): T? =
    images.filter { it != current }.randomOrNull(random)
