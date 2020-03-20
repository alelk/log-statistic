package io.github.alelk.logstatistic.flow

import io.github.alelk.logstatistic.model.LogLevel
import io.github.alelk.logstatistic.model.LogRecord
import io.reactivex.Flowable
import io.reactivex.functions.BiFunction
import io.reactivex.schedulers.Schedulers
import java.io.BufferedReader
import java.time.LocalDateTime
import java.util.concurrent.Callable

object LogRecordFlow {

    /** Создает реактивный поток записей лог-файл из заданного [BufferedReader]
     *
     * Строки из [BufferedReader] читаются по одной только по мере истощения потока, а не все сразу,
     * что позволит обрабатывать лог-файлы произвольного размера
     *
     *  @param r входной поток из лог-файла
     */
    fun fromReader(r: BufferedReader) =
            Flowable.generate(
                            Callable {},
                            BiFunction { _, emitter: io.reactivex.Emitter<LogRecord> ->
                                try {
                                    val line = r.readLine()
                                    if (line != null)
                                        emitter.onNext(line.parseLogRecord())
                                    else emitter.onComplete()
                                } catch (e: Exception) {
                                    emitter.onError(e)
                                }
                            })
                    .observeOn(Schedulers.io(), false, 1)
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