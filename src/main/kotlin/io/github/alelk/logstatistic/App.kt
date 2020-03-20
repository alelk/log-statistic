package io.github.alelk.logstatistic

import io.github.alelk.logstatistic.flow.LogRecordFlow
import java.nio.file.Paths

fun main(args: Array<String>) {

    Paths.get("data", "log-1.txt").toFile().bufferedReader().use { reader ->
        val flow = LogRecordFlow.fromReader(reader)

        flow.blockingForEach { println(it) }
    }
}