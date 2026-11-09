package dev.twango.jetplay.editor

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration

/** Completes an optional media request with failure while preserving cancellation of its owning editor. */
internal suspend fun <T> mediaLoadOperation(
    timeout: Duration,
    onFailure: (Exception) -> Unit,
    operation: suspend () -> T,
): T? = try {
    withTimeout(timeout) { operation() }
} catch (e: TimeoutCancellationException) {
    // A timeout of this operation is a UI failure; cancellation of its parent must still propagate.
    currentCoroutineContext().ensureActive()
    onFailure(e)
    null
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    onFailure(e)
    null
}
