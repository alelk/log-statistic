package io.github.alelk.logstatistic

import io.github.alelk.logstatistic.flow.logRecordFlow
import io.github.alelk.logstatistic.statistic.logStatistic
import io.github.alelk.logstatistic.statistic.prettyString
import io.reactivex.schedulers.Schedulers
import java.io.File
import java.util.concurrent.TimeUnit

fun main(args: Array<String>) {

    val files =
            if (args.isNotEmpty()) args.toList().map(::File)
            else (1..10).map { File("data/log-$it.txt") }

    val readers = files.map { it.bufferedReader() }

    try {
        val logRecordFlow = readers.logRecordFlow()
                .observeOn(Schedulers.io(), false, 1)

        // Тест потока записей из всех лог-файлов - записи поступают отсортированными по временной метке
        //logRecordFlow.blockingForEach { println(it) }

        // статистика по минутам
        //logRecordFlow.logStatistic().blockingGet().forEach { println(it.prettyString()) }

        // статистика по часам
        logRecordFlow.logStatistic(1, TimeUnit.HOURS).blockingGet().forEach { println(it.prettyString()) }


    } finally {
        readers.forEach { it.close() }
    }
}