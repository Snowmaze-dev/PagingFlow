package ru.snowmaze.pagingflow.utils

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job

private val job: Job = Job()

internal fun CoroutineDispatcher.limitedParallelismCompat(
    parallelism: Int
) = if (isDispatchNeeded(job)) limitedParallelism(parallelism)
else this