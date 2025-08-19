package ru.snowmaze.pagingflow.utils

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext

private val job: Job = Job()

internal fun CoroutineContext.limitedParallelismCompat(
    parallelism: Int
): CoroutineContext {
    val dispatcher = this[ContinuationInterceptor] as? CoroutineDispatcher
    return if (dispatcher == null) this
    else if (dispatcher.isDispatchNeeded(job)) {
        this + dispatcher.limitedParallelism(parallelism)
    } else this
}