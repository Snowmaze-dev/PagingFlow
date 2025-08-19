package ru.snowmaze.samples.pagingflow

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

suspend inline fun <T> Flow<T>.firstEqualsWithTimeout(
    value: T,
    timeout: Long = 1000
) = firstWithTimeout(timeout, {
    """expected $value
        |actual $it
    """.trimMargin()
}) {
    value == it
}

suspend fun <T> Flow<T>.firstWithTimeout(
    timeout: Long = 1000,
    message: ((T?) -> String)? = null,
    predicate: suspend (T) -> Boolean
): T {
    val cause = Exception()
    return if (message == null) withTimeout(timeout) { first(predicate) }
    else {
        var lastValue: T? = null
        val result = withTimeoutOrNull(timeout) {
            first {
                lastValue = it
                predicate(it)
            }
        }
        if (result == null) throw AssertionError(message(lastValue), cause)
        result
    }
}