package ru.snowmaze.pagingflow.presenters

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import ru.snowmaze.pagingflow.PagingFlow
import ru.snowmaze.pagingflow.diff.DataChangedCallback
import ru.snowmaze.pagingflow.utils.limitedParallelismCompat

class PagingDataMappingPresenter<Key : Any, Data : Any, NewData : Any>(
    pagingDataPresenter: PagingDataPresenter<Key, Data>,
    invalidateBehavior: InvalidateBehavior,
    throttleDurationMs: Long,
    private val transform: (List<Data?>) -> List<NewData?>,
    override val coroutineScope: CoroutineScope,
    processingDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ThrottleEventsPagingPresenter<Key, NewData>(invalidateBehavior, throttleDurationMs) {

    override val processingDispatcher = processingDispatcher.limitedParallelismCompat(1)

    init {
        pagingDataPresenter.addDataChangedCallback(object : DataChangedCallback<Key, Data> {
            override fun onPageAdded(
                key: Key?,
                pageIndex: Int,
                sourceIndex: Int,
                items: List<Data>
            ) = updateData {
                val transformResult = transform(items) as List<NewData>
                this[pageIndex] = transformResult
                callDataChangedCallbacks {
                    onPageAdded(key, pageIndex, sourceIndex, transformResult)
                }
            }

            override fun onPageChanged(
                key: Key?,
                pageIndex: Int,
                sourceIndex: Int,
                items: List<Data?>
            ) = updateData {
                val transformResult = transform(items)
                this[pageIndex] = transformResult
                callDataChangedCallbacks {
                    onPageChanged(key, pageIndex, sourceIndex, transformResult)
                }
            }

            override fun onPageRemoved(key: Key?, pageIndex: Int, sourceIndex: Int) {
                updateData {
                    remove(pageIndex)
                    callDataChangedCallbacks {
                        onPageRemoved(key, pageIndex, sourceIndex)
                    }
                }
            }

            override fun onInvalidate() = onInvalidateInternal()
        })
    }
}

/**
 * Creates mapping presenter, which maps only changed pages and have throttling mechanism
 * @see pagingDataPresenter for arguments docs
 */
fun <Key : Any, Data : Any, NewData : Any> PagingDataPresenter<Key, Data>.mappingDataPresenter(
    invalidateBehavior: InvalidateBehavior =
        InvalidateBehavior.INVALIDATE_AND_CLEAR_LIST_BEFORE_NEXT_VALUE_RECEIVED,
    throttleDurationMs: Long = 120L,
    coroutineScope: CoroutineScope,
    processingDispatcher: CoroutineDispatcher = Dispatchers.Default,
    transform: (List<Data?>) -> List<NewData?>
) = PagingDataMappingPresenter(
    pagingDataPresenter = this,
    invalidateBehavior = invalidateBehavior,
    throttleDurationMs = throttleDurationMs,
    coroutineScope = coroutineScope,
    processingDispatcher = processingDispatcher,
    transform = transform
)

fun <Key : Any, Data : Any, NewData : Any> PagingFlow<Key, Data, *>.mappingDataPresenter(
    invalidateBehavior: InvalidateBehavior =
        InvalidateBehavior.INVALIDATE_AND_CLEAR_LIST_BEFORE_NEXT_VALUE_RECEIVED,
    throttleDurationMs: Long = 120L,
    transform: (List<Data?>) -> List<NewData?>
) = pagingFlowWrapperPresenter().mappingDataPresenter(
    invalidateBehavior = invalidateBehavior,
    throttleDurationMs = throttleDurationMs,
    coroutineScope = pagingFlowConfiguration.coroutineScope,
    processingDispatcher = pagingFlowConfiguration.processingDispatcher,
    transform = transform
)