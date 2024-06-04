package ru.snowmaze.pagingflow.presenters

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import ru.snowmaze.pagingflow.diff.DataChangedEvent
import ru.snowmaze.pagingflow.diff.InvalidateEvent
import ru.snowmaze.pagingflow.utils.limitedParallelismCompat
import kotlin.concurrent.Volatile

inline fun <Data : Any> defaultPresenterFlowCreator(): () -> MutableSharedFlow<List<Data?>> = {
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
    presenterFlow: () -> MutableSharedFlow<List<Data?>> = defaultPresenterFlowCreator()
) : PagingDataPresenter<Key, Data> {

    protected val _dataFlow = presenterFlow()
    override val dataFlow = _dataFlow.asSharedFlow()
    override var data: List<Data?> = emptyList()
    protected val processingDispatcher = processingDispatcher.limitedParallelismCompat(1)

    private var lastInvalidateBehavior: InvalidateBehavior? = null

    @Volatile
    protected var _startIndex = 0
    override val startIndex get() = _startIndex

    init {
        coroutineScope.launch {
            _dataFlow.emit(emptyList())
        }
    }

    protected suspend fun onInvalidateInternal(
        specifiedInvalidateBehavior: InvalidateBehavior? = null,
    ) {
        if (data.isEmpty()) return
        val invalidateBehavior = specifiedInvalidateBehavior ?: invalidateBehavior
        onInvalidateAdditionalAction()
        val previousList = data
        if (invalidateBehavior == InvalidateBehavior.INVALIDATE_IMMEDIATELY) {
            buildList(listOf(InvalidateEvent(invalidateBehavior)))
        } else lastInvalidateBehavior = invalidateBehavior
        afterInvalidatedAction(invalidateBehavior, previousList)
    }

    protected open fun onInvalidateAdditionalAction() {}

    protected open fun afterInvalidatedAction(
        invalidateBehavior: InvalidateBehavior,
        previousList: List<Data?>
    ) {
    }

    protected suspend fun buildList(events: List<DataChangedEvent<Key, Data>>) {
        val previousList = data
        val result = buildListInternal()
        if (lastInvalidateBehavior == InvalidateBehavior.SEND_EMPTY_LIST_BEFORE_NEXT_VALUE_SET) {
            _dataFlow.emit(emptyList())
        }
        this.lastInvalidateBehavior = null
        data = result
        _dataFlow.emit(result)
        onItemsSet(events, previousList)
    }

    protected open suspend fun onItemsSet(
        events: List<DataChangedEvent<Key, Data>>,
        previousList: List<Data?>
    ) {

    }

    protected abstract suspend fun buildListInternal(): List<Data?>
}