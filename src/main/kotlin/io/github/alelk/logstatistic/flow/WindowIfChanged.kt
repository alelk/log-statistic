package io.github.alelk.logstatistic.flow

import io.reactivex.Emitter
import io.reactivex.Flowable
import io.reactivex.FlowableTransformer
import io.reactivex.functions.BiFunction
import java.util.concurrent.Callable

/** Трансформер, работающий наподобие groupBy, но не пробегающий по всему потоку целиком.
 *
 * @param keySelector признак окна - когда пизнак меняется, начинается новое окно
 */
// todo: было бы хорошо отладить этот трансформер - разбивать входящий поток на окна по условию, затем для каждого
//  окна считать статистику. Это позвлолило бы обрабатывать бесконечные потоки логов.
//  Но за нехваткой времени, решил использовать reduce для сбора статистики - см файл LogStatistic - данный способ
//  сворачивает поток в единственное значение. Но в этом есть и преимущества - мы не храним все объекты за последний
//  промежуток времени, а храним только статистический результат
@Deprecated("нужно отладить")
fun <A, B> windowIfChanged(keySelector: (A) -> B) = FlowableTransformer<A, List<A>> { f ->
    val iterator = f.blockingIterable(1).iterator()
    Flowable.generate(
            Callable { ((null as B?) to emptyList<A>()) },
            BiFunction { (currentKey: B?, currentWindow: List<A>), emitter: Emitter<List<A>> ->
                when {
                    iterator.hasNext() -> {
                        val next = iterator.next()
                        val nextKey = keySelector(next)
                        if (currentKey == nextKey) {
                            currentKey to (currentWindow + next)
                        } else {
                            if (currentWindow.isNotEmpty()) emitter.onNext(currentWindow)
                            nextKey to listOf(next)
                        }
                    }
                    else -> {
                        if (currentWindow.isNotEmpty()) emitter.onNext(currentWindow)
                        emitter.onComplete()
                        null to emptyList()
                    }
                }
            }
    )
}

fun main() {
    val v: Flowable<Int> = Flowable.fromIterable((1..20).toList())
    v.compose(windowIfChanged<Int, Int> { it / 3 }).blockingForEach(::println) // test
}