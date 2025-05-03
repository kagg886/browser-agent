package top.kagg886.browser.browseragent.util

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import reactor.core.publisher.Flux

fun <T> Flux<T>.asFlow(): Flow<T> = channelFlow {
    val disposable = this@asFlow.subscribe(
        { value -> trySend(value).onFailure { close(it) } },  // onNext
        { error -> close(error) },                           // onError
        { close() }                                         // onComplete
    )
    // 确保 Flow 取消时取消订阅
    awaitClose { disposable.dispose() }
}
