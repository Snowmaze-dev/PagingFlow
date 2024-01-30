package ru.snowmaze.pagingflow.presenters

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.snowmaze.pagingflow.diff.DataChangedCallback
import ru.snowmaze.pagingflow.diff.mediums.DataChangesMedium
import ru.snowmaze.pagingflow.utils.limitedParallelismCompat

open class BuildListPagingPresenter<Key : Any, Data : Any>(
    dataChangesMedium: DataChangesMedium<Key, Data>,
    private val invalidateBehavior: InvalidateBehavior,
    val coroutineScope: CoroutineScope,
    processingDispatcher: CoroutineDispatcher
) : PagingDataPresenter<Key, Data>() {

    private val _dataFlow = MutableStateFlow<List<Data?>>(emptyList())
    override val dataFlow: StateFlow<List<Data?>> = _dataFlow.asStateFlow()

    protected val pageIndexes = mutableMapOf<Int, List<Data?>>()
    protected var pageIndexesKeys = emptyList<Int>()
    protected var changeDataJob: Job = Job()
    protected var isInvalidated = false
    protected val processingDispatcher = processingDispatcher.limitedParallelismCompat(1)

    init {
        dataChangesMedium.addDataChangedCallback(object : DataChangedCallback<Key, Data> {

            override fun onPageAdded(
                key: Key?,
                pageIndex: Int,
                sourceIndex: Int,
                items: List<Data>
            ) = updateData { this[pageIndex] = items }

            override fun onPageChanged(
                key: Key?,
                pageIndex: Int,
                sourceIndex: Int,
                items: List<Data?>
            ) = updateData { this[pageIndex] = items }

            override fun onPageRemoved(key: Key?, pageIndex: Int, sourceIndex: Int) {
                updateData { remove(pageIndex) }
            }

            override fun onInvalidate() = onInvalidateInternal()
        })
    }

    protected open fun onInvalidateInternal() {
        coroutineScope.launch(processingDispatcher) {
            pageIndexes.clear()
            pageIndexesKeys = emptyList()
            if (invalidateBehavior == InvalidateBehavior.INVALIDATE_IMMEDIATELY) buildList()
            else isInvalidated = true
            changeDataJob.cancel()
            changeDataJob = Job()
        }
    }

    protected open fun updateData(update: MutableMap<Int, List<Data?>>.() -> Unit) {
        coroutineScope.launch(processingDispatcher + changeDataJob) {
            pageIndexes.update()
            pageIndexesKeys = pageIndexes.keys.sorted()
            buildList()
        }
    }

    protected fun buildList() {
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

