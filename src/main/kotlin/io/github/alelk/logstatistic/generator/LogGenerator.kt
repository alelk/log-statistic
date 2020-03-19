package io.github.alelk.logstatistic.generator

import com.github.javafaker.Faker
import io.github.alelk.logstatistic.model.LogLevel
import io.github.alelk.logstatistic.model.LogRecord
import java.time.LocalDateTime
import kotlin.random.Random

object LogGenerator {

    private val faker by lazy { Faker() }

    fun next() =
            LogRecord(
                    LocalDateTime.now(),
                    Random.nextInt(3).let { LogLevel.values()[it] },
                    faker.lorem().characters(10, 100))
}

fun main() {
    for (i in 0..100) println(LogGenerator.next())
}