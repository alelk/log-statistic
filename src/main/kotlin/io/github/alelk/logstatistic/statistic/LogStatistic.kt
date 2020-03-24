package io.github.alelk.logstatistic.statistic

import io.github.alelk.logstatistic.model.LogLevel
import io.github.alelk.logstatistic.model.LogRecord
import io.reactivex.Flowable
import io.reactivex.Single
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/** Статистика логов
 *
 * @param startTs начальная временная метка
 * @param endTs конечная временная метка
 * @param countDebug количество логов уровня DEBUG
 * @param countInfo количество логов уровня INFO
 * @param countWarn количество логов уровня WARN
 * @param countErr количество логов уровня ERR
 */
data class LogStatistic(
        val startTs: LocalDateTime,
        val endTs: LocalDateTime,
        val countDebug: Long = 0,
        val countInfo: Long = 0,
        val countWarn: Long = 0,
        val countErr: Long = 0) {

    fun addDebug() = copy(countDebug = countDebug + 1)
    fun addInfo() = copy(countInfo = countInfo + 1)
    fun addWarn() = copy(countWarn = countWarn + 1)
    fun addErr() = copy(countErr = countErr + 1)
}

private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm:ss")

fun LogStatistic.prettyString() =
        "%s - %s\tDEBUG: %6d\tINFO: %6d\tWARN: %6d\tERR: %6d"
                .format(
                        this.startTs.format(dateFormat), this.endTs.format(dateFormat),
                        this.countDebug, this.countInfo, this.countWarn, this.countErr)

/** Считает статистику логов
 *
 * @param period временной интервал, за который считается статистика
 * @param timeUnit единица измерения времени
 */
// todo: сначала хотел решить задачу с помощью разбиения потока логов на окна во временному условию,
//  а затем считать статистику для каждого окна (см файл WindowIfChanged). Но за нехваткой времени выбрал более простой вариант
fun Flowable<LogRecord>.logStatistic(period: Long = 1, timeUnit: TimeUnit = TimeUnit.MINUTES): Single<List<LogStatistic>> {
    val periodMillis = timeUnit.toMillis(period)
    return this.reduce(emptyList(), { statistics, log ->
        val lastTsKey = statistics.lastOrNull()?.startTs?.toInstant(ZoneOffset.UTC)?.toEpochMilli()?.let { it / periodMillis }
        val currentTsKey = log.timestamp.toInstant(ZoneOffset.UTC).toEpochMilli() / periodMillis
        if (lastTsKey == currentTsKey) {
            val nextStatistic = updateLogStatistic(log, statistics.last())
            statistics.dropLast(1) + nextStatistic
        } else {
            val nextStatistic = updateLogStatistic(
                    log,
                    LogStatistic(
                            LocalDateTime.ofInstant(Instant.ofEpochMilli(currentTsKey * periodMillis), ZoneId.of("UTC")),
                            LocalDateTime.ofInstant(Instant.ofEpochMilli(currentTsKey * periodMillis + periodMillis), ZoneId.of("UTC"))))
            statistics + nextStatistic
        }
    })
}

private fun updateLogStatistic(log: LogRecord, prevStatistic: LogStatistic) =
        when (log.level) {
            LogLevel.DEBUG -> prevStatistic.addDebug()
            LogLevel.INFO -> prevStatistic.addInfo()
            LogLevel.WARN -> prevStatistic.addWarn()
            LogLevel.ERROR -> prevStatistic.addErr()
        }