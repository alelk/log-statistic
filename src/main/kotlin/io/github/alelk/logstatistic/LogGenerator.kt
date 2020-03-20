package io.github.alelk.logstatistic

import com.github.javafaker.Faker
import io.github.alelk.logstatistic.model.LogLevel
import io.github.alelk.logstatistic.model.LogRecord
import java.io.FileWriter
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.random.Random

/** Генератор записей логирования (для тестов) */
object LogGenerator {
    private val faker by lazy { Faker() }

    fun nextNow() = nextForTimestamp(LocalDateTime.now())

    fun nextForTimestamp(ts: LocalDateTime) =
            LogRecord(
                    ts,
                    Random.nextInt(3).let { LogLevel.values()[it] },
                    faker.lorem().sentence(Random.nextInt(5, 20)))
}

/** Преобразовать LogRecord в строку лог-файла */
fun LogRecord.toFileStr() = "${this.timestamp} ${this.level.signature} ${this.message}"

fun main(args: Array<String>) {

    // директория, в которой сгенерировать лог-файлы
    val logBasePath = args.elementAtOrNull(0) ?: "data"

    // количество лог-файлов
    val countLogFiles = args.elementAtOrNull(1)?.toInt() ?: 10

    // общее количество записей логирования
    val countRecords = args.elementAtOrNull(2)?.toInt() ?: 1_000

    val fileWriters = (1..countLogFiles)
            .map { Paths.get(logBasePath, "log-$it.txt").toFile() }
            .map { it to FileWriter(it) }

    // случайный лог-файл
    fun randomFileWriter() = Random.nextInt(fileWriters.size - 1).let { fileWriters[it] }

    try {
        (0..countRecords).fold(LocalDateTime.now()) { startTs, _ ->
            val nextLogRecord = LogGenerator.nextForTimestamp(startTs).toFileStr()
            val (file, fileWriter) = randomFileWriter()
            println("[${file.name}]: $nextLogRecord")
            fileWriter.write("$nextLogRecord\n")

            // следующий лог через случайный промежуток времени [0 секунд - 5 минут]
            startTs.plus(Random.nextLong(5 * 60 * 1000), ChronoUnit.MICROS)
        }
    } finally {
        fileWriters.forEach { it.second.close() } // освободить ресурсы
    }
}

