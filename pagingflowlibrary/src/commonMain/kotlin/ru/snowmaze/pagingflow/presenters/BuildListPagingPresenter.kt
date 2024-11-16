package ru.snowmaze.pagingflow.presenters

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import ru.snowmaze.pagingflow.diff.AwaitDataSetEvent
import ru.snowmaze.pagingflow.diff.DataChangedEvent
import ru.snowmaze.pagingflow.presenters.list.ListBuildStrategy
import ru.snowmaze.pagingflow.utils.fastForEach
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
    private val listBuildStrategy: ListBuildStrategy<Key, Data>,
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

    protected fun onInvalidateInternal(
        specifiedInvalidateBehavior: InvalidateBehavior? = null,
    ) {
        if (latestData.data.isEmpty()) return
        val invalidateBehavior = specifiedInvalidateBehavior ?: invalidateBehavior
        onInvalidateAdditionalAction()
        val previousData = latestData
        if (invalidateBehavior != InvalidateBehavior.INVALIDATE_IMMEDIATELY) {
            lastInvalidateBehavior = invalidateBehavior
        }
        afterInvalidatedAction(invalidateBehavior, previousData)
    }

    protected open fun onInvalidateAdditionalAction() {}

    protected open fun afterInvalidatedAction(
        invalidateBehavior: InvalidateBehavior,
        previousData: LatestData<Data>
    ) {
    }

    private val onInvalidateAction = ::onInvalidateInternal

    protected suspend fun buildList(events: List<DataChangedEvent<Key, Data>>) {
        val previousList = latestData
        listBuildStrategy.buildList(events, onInvalidateAction)
        _startIndex = listBuildStrategy.startPageIndex
        if (lastInvalidateBehavior == InvalidateBehavior.SEND_EMPTY_LIST_BEFORE_NEXT_VALUE_SET &&
            listBuildStrategy.list.isNotEmpty()
        ) {
            _dataFlow.emit(LatestData(emptyList()))
        }
        this.lastInvalidateBehavior = null
        _latestData = LatestData(
            data = listBuildStrategy.list,
            recentLoadData = listBuildStrategy.recentLoadData
        )
        _dataFlow.emit(_latestData)
        onItemsSet(events, previousList)
        events.fastForEach { if (it is AwaitDataSetEvent) it.callback() }
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
}