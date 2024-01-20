package ru.snowmaze.pagingflow.presenters

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import ru.snowmaze.pagingflow.PagingFlow
import ru.snowmaze.pagingflow.diff.DataChangedCallback
import ru.snowmaze.pagingflow.utils.limitedParallelismCompat

class SimplePagingDataPresenter<Key : Any, Data : Any>(
    pagingFlowWrapperPresenter: PagingDataPresenter<Key, Data>,
    invalidateBehavior: InvalidateBehavior,
    throttleDurationMs: Long,
    override val coroutineScope: CoroutineScope,
    processingDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ThrottleEventsPagingPresenter<Key, Data>(invalidateBehavior, throttleDurationMs) {

    override val processingDispatcher = processingDispatcher.limitedParallelismCompat(1)

    init {
        pagingFlowWrapperPresenter.addDataChangedCallback(object : DataChangedCallback<Key, Data> {
            override fun onPageAdded(key: Key?, pageIndex: Int, items: List<Data>) {
                updateData { this[pageIndex] = items }
            }

            override fun onPageChanged(key: Key?, pageIndex: Int, items: List<Data?>) {
                updateData { this[pageIndex] = items }
            }

            override fun onPageRemoved(key: Key?, pageIndex: Int) {
                updateData { remove(pageIndex) }
            }

            override fun onInvalidate() = onInvalidateInternal()
        })
    }
}

fun <Key : Any, Data : Any> PagingDataPresenter<Key, Data>.pagingDataPresenter(
    invalidateBehavior: InvalidateBehavior =
        InvalidateBehavior.INVALIDATE_AND_CLEAR_LIST_BEFORE_NEXT_VALUE_RECEIVED,
    throttleDurationMs: Long = 120L,
    coroutineScope: CoroutineScope,
    processingDispatcher: CoroutineDispatcher = Dispatchers.Default
) = SimplePagingDataPresenter(
    pagingFlowWrapperPresenter = this,
    invalidateBehavior = invalidateBehavior,
    throttleDurationMs = throttleDurationMs,
    coroutineScope = coroutineScope,
    processingDispatcher = processingDispatcher
)

fun <Key : Any, Data : Any> PagingFlow<Key, Data, *>.pagingDataPresenter(
    invalidateBehavior: InvalidateBehavior =
        InvalidateBehavior.INVALIDATE_AND_CLEAR_LIST_BEFORE_NEXT_VALUE_RECEIVED,
    throttleDurationMs: Long = 120L
) = pagingFlowWrapperPresenter().pagingDataPresenter(
    invalidateBehavior = invalidateBehavior,
    throttleDurationMs = throttleDurationMs,
    coroutineScope = pagingFlowConfiguration.coroutineScope,
    processingDispatcher = pagingFlowConfiguration.processingDispatcher
)