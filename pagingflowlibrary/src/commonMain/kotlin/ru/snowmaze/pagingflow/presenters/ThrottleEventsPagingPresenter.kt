package ru.snowmaze.pagingflow.presenters

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.snowmaze.pagingflow.diff.mediums.DataChangesMedium

/**
 * Emits new list data when data changed last time in [throttleDurationMs] time window.
 * @see [InvalidateBehavior]
 */
class ThrottleEventsPagingPresenter<Key : Any, Data : Any>(
    dataChangesMedium: DataChangesMedium<Key, Data>,
    invalidateBehavior: InvalidateBehavior,
    private val throttleDurationMs: Long,
    coroutineScope: CoroutineScope,
    processingDispatcher: CoroutineDispatcher
) : BuildListPagingPresenter<Key, Data>(
    dataChangesMedium = dataChangesMedium,
    invalidateBehavior = invalidateBehavior,
    coroutineScope = coroutineScope,
    processingDispatcher = processingDispatcher
) {

    private var job: Job? = null

    override fun updateData(update: MutableMap<Int, List<Data?>>.() -> Unit) {
        job?.cancel()
        job = coroutineScope.launch(processingDispatcher + changeDataJob) {
            pageIndexes.update()
            delay(throttleDurationMs)
            pageIndexesKeys = pageIndexes.keys.sorted()
            buildList()
        }
    }
}