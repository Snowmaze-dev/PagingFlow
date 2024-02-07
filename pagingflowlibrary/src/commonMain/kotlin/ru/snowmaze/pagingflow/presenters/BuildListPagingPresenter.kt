package ru.snowmaze.pagingflow.presenters

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.snowmaze.pagingflow.diff.DataChangedEvent
import ru.snowmaze.pagingflow.diff.InvalidateEvent
import ru.snowmaze.pagingflow.utils.limitedParallelismCompat

abstract class BuildListPagingPresenter<Key : Any, Data : Any>(
    protected val invalidateBehavior: InvalidateBehavior,
    protected val coroutineScope: CoroutineScope,
    processingDispatcher: CoroutineDispatcher
) : PagingDataPresenter<Key, Data> {

    private val _dataFlow = MutableStateFlow<List<Data?>>(emptyList())
    override val dataFlow: StateFlow<List<Data?>> = _dataFlow.asStateFlow()
    protected val processingDispatcher = processingDispatcher.limitedParallelismCompat(1)

    protected var isInvalidated = false

    protected fun onInvalidateInternal() {
        onInvalidateAdditionalAction()
        if (invalidateBehavior == InvalidateBehavior.INVALIDATE_IMMEDIATELY) buildList(
            listOf(InvalidateEvent())
        )
        else isInvalidated = true
    }

    protected open fun onInvalidateAdditionalAction() {}

    protected fun buildList(events: List<DataChangedEvent<Key, Data>>) {
        val result = buildListInternal()
        if (isInvalidated && invalidateBehavior ==
            InvalidateBehavior.INVALIDATE_AND_SEND_EMPTY_LIST_BEFORE_NEXT_VALUE
        ) _dataFlow.value = emptyList()
        this.isInvalidated = false
        _dataFlow.value = result
        onItemsSet(events)
    }

    protected open fun onItemsSet(events: List<DataChangedEvent<Key, Data>>) {

    }

    protected abstract fun buildListInternal(): List<Data?>
}