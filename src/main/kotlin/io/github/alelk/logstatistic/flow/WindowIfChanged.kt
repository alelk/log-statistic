package io.github.alelk.logstatistic.flow

import io.reactivex.Emitter
import io.reactivex.Flowable
import io.reactivex.FlowableTransformer
import io.reactivex.functions.BiFunction
import java.util.concurrent.Callable

fun <A, B> windowIfChanged(keySelector: (A) -> B): FlowableTransformer<A, List<A>> = FlowableTransformer<A, List<A>> { f ->
    val iterator = f.blockingIterable(1).iterator()
    if (iterator.hasNext()) {
        var firstInWindow = iterator.next()
        Flowable.generate(
                Callable {Unit},
                BiFunction { _, emitter: Emitter<List<A>> ->
                    when {
                        iterator.hasNext() -> {
                            val key = keySelector(firstInWindow)
                            val items = mutableListOf(firstInWindow)
                            while (iterator.hasNext()) {
                                val next = iterator.next()
                                val nextKey = keySelector(next)
                                if (key == nextKey) items.add(next)
                                else {
                                    firstInWindow = next
                                    break
                                }
                            }
                            emitter.onNext(items)
                        }
                        else -> {
                            emitter.onNext(listOf(firstInWindow))
                            emitter.onComplete()
                        }
                    }
                }
        )
    } else Flowable.empty<List<A>>()
}

fun main() {
    val v: Flowable<Int> = Flowable.fromIterable((1..20).toList())
    v.compose(windowIfChanged<Int, Int> { it / 3 }).blockingForEach(::println)
}