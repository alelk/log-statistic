package io.github.alelk.logstatistic

import io.github.alelk.logstatistic.flow.logRecordFlow
import java.io.File
import java.util.concurrent.Callable

fun main(args: Array<String>) {

    val files =
            if (args.isNotEmpty()) args.toList().map(::File)
            else (1..10).map { File("data/log-$it.txt") }

    val readers = files.map { it.bufferedReader() }

    try {
        val flow = readers.logRecordFlow()



        flow.blockingForEach { println(it) }
    } finally {
        readers.forEach { it.close() }
    }
}