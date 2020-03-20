package io.github.alelk.logstatistic.flow

import io.github.alelk.logstatistic.model.LogRecord
import io.reactivex.Emitter
import io.reactivex.Flowable
import io.reactivex.FlowableTransformer
import io.reactivex.functions.BiFunction
import java.util.concurrent.Callable

//
// В библиотеке RxJava и в вспомогательных библиотеках не нашел метод для объединения отсортированных потоков
// в общий упорядоченный поток.
// Пришлось написать реализацию самостоятельно.
//

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