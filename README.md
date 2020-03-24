
Задача
======

**Статистика логов:**

Зачастую различные компоненты одной системы записывают логи одновременно в несколько разных файлов. Требуется собрать
некоторую статистическую информацию об этих логах за определенный промежуток времени (например, количество ошибок за час)

Решение
=======

**Выбор способа решения задачи:**

- Лог-файлы могут быть больших размеров (может до нескольких ГБ - кто знает). 
  Лучше не читать весь лог файл в ОЗУ сразу, а читать построчно. Для этого подойдет реактивные потоки (RxJava, например)
- Логи в каждом лог-файле отсортированы по времени. Лог-файлов может быть сколько угодно много. А для статистики нужно 
  знать все записи из всех лог-файлов за заданный промежуток времени. Напрашивается решение - mergeSort. Объединение 
  отсортированных потоков в общий отсортированный поток.
  > В RxJava не оказалось метода mergeSort, пришлось реализовать самостоятельно - из каждого потока по одному забираем 
  самый меньший элемент. Таким образом достаточно буфера размером 1 для каждого лог-файла. 
  Из файлов можно читать по 1 строке по мере истощения потока.
- Было бы здорово заложить возможность сбора статистики по бесконечным потокам логов в режиме реального времени.
  Т.е. из входных потоков формировать выходной реактивный поток со статистикой за заданный промежуток времени.
  Чтоб решить эту задачу, можно разбить исходный поток на окна по условию (условие принадлежности временной метки лога к
  заданному временному промежутку), на выходе получить поток из окон и для каждого окна вычислить статистику.
  Я начал реализовывать этот [функционал (с окнами)](src/main/kotlin/io/github/alelk/logstatistic/flow/WindowIfChanged.kt),
  но не хватило времени, чтоб все отладить. Пришлось выбрать способ 
  попроще - reduce. В этом способе сворачиваем весь поток поэлементно, вычисляя некое состояние.

Генерация лог-файлов для тестов ([LogGenerator.kt](src/main/kotlin/io/github/alelk/logstatistic/LogGenerator.kt))
-----------------------------------------------------------------------------------------------------------------

#### [LogRecord](src/main/kotlin/io/github/alelk/logstatistic/model/LogRecord.kt):

```kotlin
/** Запись лога
 *
 * @param timestamp временная метка
 * @param level уровень
 * @param message текст сообщения
 */
data class LogRecord(val timestamp: LocalDateTime, val level: LogLevel, val message: String)
```

#### [Генерация случайного LogRecord](src/main/kotlin/io/github/alelk/logstatistic/LogGenerator.kt):

```kotlin
fun nextForTimestamp(ts: LocalDateTime) =
            LogRecord(
                    ts,
                    Random.nextInt(4).let { LogLevel.values()[it] },
                    faker.lorem().sentence(Random.nextInt(5, 20)))
```

#### [Генерация N лог-файлов](src/main/kotlin/io/github/alelk/logstatistic/LogGenerator.kt)

```kotlin
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

            // следующий лог через случайный промежуток времени [0 секунд - 1 минута]
            startTs.plus(Random.nextLong(1 * 60 * 1000), ChronoUnit.MILLIS)
        }
    } finally {
        fileWriters.forEach { it.second.close() } // освободить ресурсы
    }
}
```

#### [Объединение двух упорядоченных потоков в один упорядоченный поток](src/main/kotlin/io/github/alelk/logstatistic/flow/MergeLogRecordFlows.kt)

```kotlin
/** Объединяет два потока [LogRecord] (упорядоченных по временной метке) в один общий упорядоченный поток.
 *
 * @param f2 - поток записей лога, упорядоченных по временной метке
 */
fun mergeLogRecordFlows(f2: Flowable<LogRecord>) = FlowableTransformer<LogRecord, LogRecord> { f1 ->
    // по потокам будем бежать один раз с помощью итераторов
    val f1Iterator = f1.blockingIterable(1).iterator()
    val f2Iterator = f2.blockingIterable(1).iterator()
    Flowable.generate(
            Callable { null as LogRecord? to null as LogRecord? },

            // между итерациями будем хранить один последний элемент - либо из потока 1, либо из потока 2
            BiFunction { (lastF1: LogRecord?, lastF2: LogRecord?), emitter: Emitter<LogRecord> ->
                when {
                    // если последний элемент был из первого итератора, то извлекаем элемент из второго итератора, сравниваем по дате
                    lastF1 != null && f2Iterator.hasNext() -> {
                        val nextF2 = f2Iterator.next()
                        // первый (по дате) элемент отправляем в результирующий поток, другой сохраняем до следующей итерации
                        if (lastF1.timestamp <= nextF2.timestamp) {
                            emitter.onNext(lastF1)
                            null to nextF2
                        } else {
                            emitter.onNext(nextF2)
                            lastF1 to null
                        }
                    }

                    // второй итератор пуст, поэтому просто читаем элементы из первого итератора
                    lastF1 != null -> {
                        // больше одного элемента, к сожалению, поместить в выходной поток за одну итерацию нельзя,
                        // поэтому перекладываем элементы по одному, пока поток f1 не опустеет
                        emitter.onNext(lastF1)
                        null to null
                    }

                    lastF2 != null && f1Iterator.hasNext() -> {
                        val nextF1 = f1Iterator.next()
                        if (nextF1.timestamp <= lastF2.timestamp) {
                            emitter.onNext(nextF1)
                            null to lastF2
                        } else {
                            emitter.onNext(lastF2)
                            nextF1 to null
                        }
                    }

                    lastF2 != null -> {
                        emitter.onNext(lastF2)
                        null to null
                    }

                    // первая итерация (нет сохраненного элемента и оба потока не пусты)
                    f1Iterator.hasNext() && f2Iterator.hasNext() -> {
                        val nextF1 = f1Iterator.next()
                        val nextF2 = f2Iterator.next()
                        if (nextF1.timestamp <= nextF2.timestamp) {
                            emitter.onNext(nextF1)
                            null to nextF2
                        } else {
                            emitter.onNext(nextF2)
                            nextF1 to null
                        }
                    }

                    // только в первом потоке остались элементы - перекладываем их по одному за итерацию в выходной поток
                    f1Iterator.hasNext() -> {
                        val nextF1 = f1Iterator.next()
                        emitter.onNext(nextF1)
                        null to null
                    }

                    // только во втором потоке остались элементы
                    f2Iterator.hasNext() -> {
                        val nextF2 = f2Iterator.next()
                        emitter.onNext(nextF2)
                        null to null
                    }

                    // оба потока пусты
                    else -> {
                        emitter.onComplete()
                        null to null
                    }
                }
            })
}
```

#### [Вычисление статистики логов за определенный промежуток времени](src/main/kotlin/io/github/alelk/logstatistic/statistic/LogStatistic.kt)

```kotlin
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
```