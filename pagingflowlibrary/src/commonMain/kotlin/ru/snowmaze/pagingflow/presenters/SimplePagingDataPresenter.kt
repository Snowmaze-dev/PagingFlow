package ru.snowmaze.pagingflow.presenters

import ru.snowmaze.pagingflow.PagingFlow
import ru.snowmaze.pagingflow.diff.DataChangedCallback
import ru.snowmaze.pagingflow.utils.limitedParallelismCompat

class SimplePagingDataPresenter<Key : Any, Data : Any>(
    pagingFlow: PagingFlow<Key, Data, *>,
    invalidateBehavior: InvalidateBehavior,
    throttleDurationMs: Long,
) : ThrottleEventsPagingPresenter<Key, Data>(invalidateBehavior, throttleDurationMs) {

    override val coroutineScope = pagingFlow.pagingFlowConfiguration.coroutineScope
    override val processingDispatcher = pagingFlow.pagingFlowConfiguration.processingDispatcher
        .limitedParallelismCompat(1)

    init {
        pagingFlow.addDataChangedCallback(object : DataChangedCallback<Key, Data> {
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

fun <Key : Any, Data : Any> PagingFlow<Key, Data, *>.pagingDataPresenter(
    invalidateBehavior: InvalidateBehavior =
        InvalidateBehavior.INVALIDATE_AND_CLEAR_LIST_BEFORE_NEXT_VALUE_RECEIVED,
    throttleDurationMs: Long = 120L
) = SimplePagingDataPresenter(
    pagingFlow = this,
    invalidateBehavior = invalidateBehavior,
    throttleDurationMs = throttleDurationMs
)