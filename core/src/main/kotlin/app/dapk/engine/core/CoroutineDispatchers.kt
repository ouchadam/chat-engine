package app.dapk.engine.core

import kotlinx.coroutines.*

data class CoroutineDispatchers(
    val io: CoroutineDispatcher = Dispatchers.IO,
    val main: CoroutineDispatcher = Dispatchers.Main,
    val global: CoroutineScope = GlobalScope,
)

suspend fun <T> CoroutineDispatchers.withIoContext(
    block: suspend CoroutineScope.() -> T
) = withContext(this.io, block)

suspend fun <T> CoroutineDispatchers.withIoContextAsync(
    block: suspend CoroutineScope.() -> T
): Deferred<T> = withContext(this.io) {
    async { block() }
}
