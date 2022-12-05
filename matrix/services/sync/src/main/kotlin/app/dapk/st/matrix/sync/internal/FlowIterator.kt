package app.dapk.st.matrix.sync.internal

import app.dapk.engine.core.extensions.ErrorTracker
import app.dapk.st.matrix.common.MatrixLogTag.SYNC
import app.dapk.st.matrix.common.MatrixLogger
import app.dapk.st.matrix.common.matrixLog
import kotlinx.coroutines.*

internal class SideEffectFlowIterator(private val logger: MatrixLogger, private val errorTracker: ErrorTracker) {
    suspend fun <T> loop(initial: T?, onPost: suspend () -> Unit, onIteration: suspend (T?) -> T?) {
        var previousState = initial

        while (currentCoroutineContext().isActive) {
            logger.matrixLog(SYNC, "loop iteration")
            try {
                previousState = withContext(NonCancellable) {
                    onIteration(previousState)
                }
                onPost()
            } catch (error: Throwable) {
                logger.matrixLog(SYNC, "on loop error: ${error.message}")
                errorTracker.track(error, "sync loop error")
                delay(10000L)
            }
        }
        logger.matrixLog(SYNC, "isActive: ${currentCoroutineContext().isActive}")
    }
}