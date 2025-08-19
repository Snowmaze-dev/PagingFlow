package ru.snowmaze.pagingflow.presenters

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import ru.snowmaze.pagingflow.diff.AwaitDataSetEvent
import ru.snowmaze.pagingflow.diff.PagingEvent
import ru.snowmaze.pagingflow.diff.InvalidateEvent
import ru.snowmaze.pagingflow.presenters.InvalidateBehavior.*
import ru.snowmaze.pagingflow.presenters.list.ListBuildStrategy
import ru.snowmaze.pagingflow.utils.fastForEach
import ru.snowmaze.pagingflow.utils.limitedParallelismCompat
import kotlin.concurrent.Volatile
import kotlin.coroutines.CoroutineContext

inline fun <Data : Any> defaultPresenterFlowCreator(): () -> MutableSharedFlow<LatestData<Data>> = {
    MutableSharedFlow(1)
}

/**
 * Base class for list building presenters.
 * It manages state and invalidate behavior of presenter
 */
abstract class BuildListPagingPresenter<Key : Any, Data : Any>(
    private val listBuildStrategy: ListBuildStrategy<Key, Data>,
    protected val invalidateBehavior: InvalidateBehavior,
    protected val coroutineScope: CoroutineScope,
    processingContext: CoroutineContext,
    presenterFlow: () -> MutableSharedFlow<LatestData<Data>> = defaultPresenterFlowCreator()
) : PagingDataPresenter<Key, Data> {

    protected val _dataFlow = presenterFlow()

    override val latestDataFlow = _dataFlow.asSharedFlow()

    protected val processingContext = processingContext.limitedParallelismCompat(1)

    protected var lastInvalidateBehavior: InvalidateBehavior? = null

    @Volatile
    protected var _startIndex = 0
    override val startIndex get() = _startIndex

    init {
        coroutineScope.launch {
            _dataFlow.emit(LatestData(emptyList()))
        }
    }

    protected fun onInvalidateInternal(
        specifiedInvalidateBehavior: InvalidateBehavior? = null,
    ) {
        val invalidateBehavior = specifiedInvalidateBehavior ?: invalidateBehavior
        listBuildStrategy.invalidate()
        onInvalidateAdditionalAction()
        if (invalidateBehavior != INVALIDATE_IMMEDIATELY) {
            lastInvalidateBehavior = invalidateBehavior
        }
        afterInvalidatedAction(invalidateBehavior)
    }

    protected open fun onInvalidateAdditionalAction() {}

    protected open fun afterInvalidatedAction(
        invalidateBehavior: InvalidateBehavior
    ) {
    }

    private val onInvalidateAction = ::onInvalidateInternal

    protected suspend fun buildList(events: List<PagingEvent<Key, Data>>) {
        val invalidateEvent = events.lastOrNull() as? InvalidateEvent
        if (invalidateEvent != null) {
            val selectedBehavior = invalidateEvent.invalidateBehavior ?: invalidateBehavior
            if (selectedBehavior != INVALIDATE_IMMEDIATELY) {
                onInvalidateInternal(selectedBehavior)
                events.fastForEach { if (it is AwaitDataSetEvent) it.callback() }
                return
            }
        }
        val newList = listBuildStrategy.buildList(events, onInvalidateAction)
        _startIndex = listBuildStrategy.startPageIndex
        if (lastInvalidateBehavior == SEND_EMPTY_LIST_BEFORE_NEXT_VALUE_SET &&
            newList.isNotEmpty()
        ) {
            _dataFlow.emit(LatestData(emptyList()))
        }
        this.lastInvalidateBehavior = null
        val latestData = LatestData(
            data = newList,
            loadData = listBuildStrategy.recentLoadData
        )
        _dataFlow.emit(latestData)
        onItemsSet(events, latestData)
        events.fastForEach { if (it is AwaitDataSetEvent) it.callback() }
    }

    protected open suspend fun onItemsSet(
        events: List<PagingEvent<Key, Data>>,
        currentData: LatestData<Data>
    ) {

    }

    /**
     * Rebuilds list
     */
    fun forceRebuildList() {
        coroutineScope.launch(processingContext) {
            buildList(emptyList())
        }
    }
}