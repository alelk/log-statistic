package io.github.alelk.logstatistic.flow

import io.github.alelk.logstatistic.model.LogLevel
import io.github.alelk.logstatistic.model.LogRecord
import io.reactivex.Emitter
import io.reactivex.Flowable
import io.reactivex.functions.BiFunction
import java.io.BufferedReader
import java.time.LocalDateTime
import java.util.concurrent.Callable


/** Создает реактивный поток записей лог-файл из заданного [BufferedReader]
 *
 * Строки из [BufferedReader] читаются по одной только по мере истощения потока, а не все сразу,
 * что позволит обрабатывать лог-файлы произвольного размера
 */
fun BufferedReader.logRecordFlow() =
        Flowable.generate(
                        Callable {},
                        BiFunction { _, emitter: Emitter<LogRecord> ->
                            try {
                                val line = this.readLine()
                                if (line != null)
                                    emitter.onNext(line.parseLogRecord())
                                else emitter.onComplete()
                            } catch (e: Exception) {
                                emitter.onError(e)
                            }
                        })

/** Создает реактивный поток упорядоченных по времени записей лога из списка других упорядоченных реактивных потоков */
fun List<BufferedReader>.logRecordFlow(): Flowable<LogRecord> =
        when {
            this.isEmpty() -> Flowable.empty()
            this.size == 1 -> this.first().logRecordFlow()
            else -> this
                    .map { it.logRecordFlow() }
                    .reduce { acc: Flowable<LogRecord>, f: Flowable<LogRecord> ->
                        acc.compose(mergeLogRecordFlows(f))
                    }
        }

/** Регулярное выражение записи лога */
val logRecordRegex = """^([\d-T:.]+)\s+(\w+)\s+(.*)$""".toRegex()

/** Парсит запись лога из строки */
fun String.parseLogRecord() =
        try {
            val (ts, level, msg) = logRecordRegex
                    .find(this)
                    ?.destructured
                    ?: throw IllegalArgumentException("Log record doesn't match regular expression $logRecordRegex")
            LogRecord(
                    timestamp = LocalDateTime.parse(ts),
                    level = LogLevel.forSignature(level) ?: throw IllegalArgumentException("Unknown log level: $level"),
                    message = msg)
        } catch (e: Exception) {
            throw UnsupportedOperationException("Unable to parse log record from string '$this': ${e.message}", e)
        }