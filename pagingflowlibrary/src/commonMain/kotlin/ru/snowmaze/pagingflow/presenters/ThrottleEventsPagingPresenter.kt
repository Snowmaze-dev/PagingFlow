package ru.snowmaze.pagingflow.presenters

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Emits new list data when data changed last time in [throttleDurationMs] time window.
 * @see [InvalidateBehavior]
 */
abstract class ThrottleEventsPagingPresenter<Key : Any, Data : Any>(
    private val invalidateBehavior: InvalidateBehavior,
    private val throttleDurationMs: Long
) : PagingDataPresenter<Key, Data>() {

    abstract val coroutineScope: CoroutineScope
    abstract val processingDispatcher: CoroutineDispatcher

    private val _dataFlow = MutableStateFlow<List<Data?>>(emptyList())
    override val dataFlow: StateFlow<List<Data?>> = _dataFlow.asStateFlow()

    private val pageIndexes = mutableMapOf<Int, List<Data?>>()
    private var pageIndexesKeys = emptyList<Int>()
    private var changeDataJob: Job = Job()
    private var isInvalidated = false

    protected fun onInvalidateInternal() {
        coroutineScope.launch(processingDispatcher) {
            pageIndexes.clear()
            pageIndexesKeys = emptyList()
            if (invalidateBehavior == InvalidateBehavior.INVALIDATE_IMMEDIATELY) buildList()
            else isInvalidated = true
            changeDataJob.cancel()
            changeDataJob = Job()
            callDataChangedCallbacks { onInvalidate() }
        }
    }

    protected fun updateData(update: MutableMap<Int, List<Data?>>.() -> Unit) {
        coroutineScope.launch(processingDispatcher + changeDataJob) {
            pageIndexes.update()
            delay(throttleDurationMs)
            pageIndexesKeys = pageIndexes.keys.sorted()
            buildList()
        }
    }

    private fun buildList() {
        val result = buildList(pageIndexesKeys.sumOf { pageIndexes.getValue(it).size }) {
            for (pageIndex in pageIndexesKeys) {
                addAll(pageIndexes.getValue(pageIndex))
            }
        }
        if (isInvalidated && invalidateBehavior ==
            InvalidateBehavior.INVALIDATE_AND_SEND_EMPTY_LIST_BEFORE_NEXT_VALUE
        ) _dataFlow.value = emptyList()
        this.isInvalidated = false
        _dataFlow.value = result
    }

    /**
     * Rebuilds list
     */
    fun forceRebuildList() {
        coroutineScope.launch(processingDispatcher) {
            buildList()
        }
    }
}