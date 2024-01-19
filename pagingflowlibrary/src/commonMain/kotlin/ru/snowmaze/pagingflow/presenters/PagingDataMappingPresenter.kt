package ru.snowmaze.pagingflow.presenters

import ru.snowmaze.pagingflow.PagingFlow
import ru.snowmaze.pagingflow.diff.DataChangedCallback
import ru.snowmaze.pagingflow.utils.limitedParallelismCompat

class PagingDataMappingPresenter<Key : Any, Data : Any, NewData : Any>(
    pagingFlow: PagingFlow<Key, Data, *>,
    invalidateBehavior: InvalidateBehavior,
    throttleDurationMs: Long,
    private val transform: (List<Data?>) -> List<NewData?>,
) : ThrottleEventsPagingPresenter<Key, NewData>(invalidateBehavior, throttleDurationMs) {

    override val coroutineScope = pagingFlow.pagingFlowConfiguration.coroutineScope
    override val processingDispatcher = pagingFlow.pagingFlowConfiguration.processingDispatcher
        .limitedParallelismCompat(1)

    init {
        pagingFlow.addDataChangedCallback(object : DataChangedCallback<Key, Data> {
            override fun onPageAdded(key: Key?, pageIndex: Int, items: List<Data>) {
                updateData { this[pageIndex] = transform(items) }
            }

            override fun onPageChanged(key: Key?, pageIndex: Int, items: List<Data?>) {
                updateData { this[pageIndex] = transform(items) }
            }

            override fun onPageRemoved(key: Key?, pageIndex: Int) {
                updateData { remove(pageIndex) }
            }

            override fun onInvalidate() = onInvalidateInternal()
        })
    }
}

/**
 * Creates mapping presenter, which maps only changed pages and have throttling mechanism
 * @param invalidateBehavior behavior of invalidate, by default it clears list after new value received
 * it means that list wouldn't blink when invalidate happens
 */
fun <Key : Any, Data : Any, NewData : Any> PagingFlow<Key, Data, *>.mappingPresenter(
    invalidateBehavior: InvalidateBehavior =
        InvalidateBehavior.INVALIDATE_AND_CLEAR_LIST_BEFORE_NEXT_VALUE_RECEIVED,
    throttleDurationMs: Long = 120L,
    transform: (List<Data?>) -> List<NewData?>
): PagingDataPresenter<Key, NewData> = PagingDataMappingPresenter(
    pagingFlow = this,
    invalidateBehavior = invalidateBehavior,
    throttleDurationMs = throttleDurationMs,
    transform = transform
)