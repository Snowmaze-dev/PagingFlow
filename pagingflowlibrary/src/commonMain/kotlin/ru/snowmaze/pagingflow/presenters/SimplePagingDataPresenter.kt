package ru.snowmaze.pagingflow.presenters

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import ru.snowmaze.pagingflow.PagingFlow
import ru.snowmaze.pagingflow.diff.DataChangedCallback
import ru.snowmaze.pagingflow.diff.DataChangesProvider
import ru.snowmaze.pagingflow.utils.limitedParallelismCompat

class SimplePagingDataPresenter<Key : Any, Data : Any>(
    dataChangesProvider: DataChangesProvider<Key, Data>,
    invalidateBehavior: InvalidateBehavior,
    throttleDurationMs: Long,
    override val coroutineScope: CoroutineScope,
    processingDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ThrottleEventsPagingPresenter<Key, Data>(invalidateBehavior, throttleDurationMs) {

    override val processingDispatcher = processingDispatcher.limitedParallelismCompat(1)

    init {
        dataChangesProvider.addDataChangedCallback(createDefaultDataChangedCallback())
        dataChangesProvider.addDataChangedCallback(object : DataChangedCallback<Key, Data> {

            override fun onPageAdded(
                key: Key?,
                pageIndex: Int,
                sourceIndex: Int,
                items: List<Data>
            ) = updateData { this[pageIndex] = items }

            override fun onPageChanged(
                key: Key?,
                pageIndex: Int,
                sourceIndex: Int,
                items: List<Data?>
            ) = updateData { this[pageIndex] = items }

            override fun onPageRemoved(key: Key?, pageIndex: Int, sourceIndex: Int) {
                updateData { remove(pageIndex) }
            }

            override fun onInvalidate() = onInvalidateInternal()
        })
    }
}

/**
 * Creates simple presenter, which builds list from pages and have throttling mechanism
 * @param invalidateBehavior behavior of invalidate, by default it clears list after new value received, it means that list wouldn't blink when invalidate happens
 * @param throttleDurationMs duration of throttle window
 * @see InvalidateBehavior
 */
fun <Key : Any, Data : Any> DataChangesProvider<Key, Data>.pagingDataPresenter(
    invalidateBehavior: InvalidateBehavior =
        InvalidateBehavior.INVALIDATE_AND_CLEAR_LIST_BEFORE_NEXT_VALUE_RECEIVED,
    throttleDurationMs: Long = 120L,
    coroutineScope: CoroutineScope,
    processingDispatcher: CoroutineDispatcher = Dispatchers.Default
) = SimplePagingDataPresenter(
    dataChangesProvider = this,
    invalidateBehavior = invalidateBehavior,
    throttleDurationMs = throttleDurationMs,
    coroutineScope = coroutineScope,
    processingDispatcher = processingDispatcher
)

fun <Key : Any, Data : Any> PagingFlow<Key, Data, *>.pagingDataPresenter(
    invalidateBehavior: InvalidateBehavior =
        InvalidateBehavior.INVALIDATE_AND_CLEAR_LIST_BEFORE_NEXT_VALUE_RECEIVED,
    throttleDurationMs: Long = 120L
) = pagingDataPresenter(
    invalidateBehavior = invalidateBehavior,
    throttleDurationMs = throttleDurationMs,
    coroutineScope = pagingFlowConfiguration.coroutineScope,
    processingDispatcher = pagingFlowConfiguration.processingDispatcher
)