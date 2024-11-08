package ru.snowmaze.pagingflow.presenters

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import ru.snowmaze.pagingflow.diff.DataChangedEvent
import ru.snowmaze.pagingflow.diff.InvalidateEvent
import ru.snowmaze.pagingflow.diff.PageChangedEvent
import ru.snowmaze.pagingflow.utils.fastSumOf
import ru.snowmaze.pagingflow.utils.limitedParallelismCompat
import kotlin.concurrent.Volatile

inline fun <Data : Any> defaultPresenterFlowCreator(): () -> MutableSharedFlow<LatestData<Data>> = {
    MutableSharedFlow(1)
}

/**
 * Base class for list building presenters.
 * It manages state and invalidate behavior of presenter
 */
abstract class BuildListPagingPresenter<Key : Any, Data : Any>(
    protected val invalidateBehavior: InvalidateBehavior,
    protected val coroutineScope: CoroutineScope,
    processingDispatcher: CoroutineDispatcher,
    presenterFlow: () -> MutableSharedFlow<LatestData<Data>> = defaultPresenterFlowCreator()
) : PagingDataPresenter<Key, Data> {

    protected val _dataFlow = presenterFlow()

    @Volatile
    private var _latestData = LatestData<Data>(emptyList())
    override val latestDataFlow = _dataFlow.asSharedFlow()

    override val latestData get() = _latestData
    protected val processingDispatcher = processingDispatcher.limitedParallelismCompat(1)

    protected var lastInvalidateBehavior: InvalidateBehavior? = null

    @Volatile
    protected var _startIndex = 0
    override val startIndex get() = _startIndex

    init {
        coroutineScope.launch {
            _dataFlow.emit(latestData)
        }
    }

    protected suspend fun onInvalidateInternal(
        specifiedInvalidateBehavior: InvalidateBehavior? = null,
    ) {
        if (latestData.data.isEmpty()) return
        val invalidateBehavior = specifiedInvalidateBehavior ?: invalidateBehavior
        onInvalidateAdditionalAction()
        val previousData = latestData
        if (invalidateBehavior == InvalidateBehavior.INVALIDATE_IMMEDIATELY) {
            buildList(listOf(InvalidateEvent(invalidateBehavior)))
        } else lastInvalidateBehavior = invalidateBehavior
        afterInvalidatedAction(invalidateBehavior, previousData)
    }

    protected open fun onInvalidateAdditionalAction() {}

    protected open fun afterInvalidatedAction(
        invalidateBehavior: InvalidateBehavior,
        previousData: LatestData<Data>
    ) {
    }

    protected suspend fun buildList(events: List<DataChangedEvent<Key, Data>>) {
        val previousList = latestData
        val result = buildListInternal()
        if (lastInvalidateBehavior == InvalidateBehavior.SEND_EMPTY_LIST_BEFORE_NEXT_VALUE_SET) {
            _dataFlow.emit(LatestData(emptyList()))
        }
        this.lastInvalidateBehavior = null
        _latestData = LatestData(result, events.mapNotNullTo(
            ArrayList(events.fastSumOf { if (it is PageChangedEvent) 1 else 0 })
        ) {
            if (it is PageChangedEvent) it.params else null
        })
        _dataFlow.emit(latestData)
        onItemsSet(events, previousList)
    }

    protected open suspend fun onItemsSet(
        events: List<DataChangedEvent<Key, Data>>,
        previousData: LatestData<Data>
    ) {

    }

    /**
     * Rebuilds list
     */
    fun forceRebuildList() {
        coroutineScope.launch(processingDispatcher) {
            buildList(emptyList())
        }
    }

    protected abstract suspend fun buildListInternal(): List<Data?>
}