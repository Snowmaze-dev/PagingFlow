package ru.snowmaze.pagingflow.internal

import androidx.collection.MutableObjectList
import androidx.collection.MutableScatterMap
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.RENDEZVOUS
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.snowmaze.pagingflow.LoadParams
import ru.snowmaze.pagingflow.MaxItemsConfiguration
import ru.snowmaze.pagingflow.PaginationDirection
import ru.snowmaze.pagingflow.UpdatableData
import ru.snowmaze.pagingflow.diff.AwaitDataSetEvent
import ru.snowmaze.pagingflow.diff.EventFromDataSource
import ru.snowmaze.pagingflow.diff.InvalidateEvent
import ru.snowmaze.pagingflow.diff.OnDataLoaded
import ru.snowmaze.pagingflow.diff.PageAddedEvent
import ru.snowmaze.pagingflow.diff.PageChangedEvent
import ru.snowmaze.pagingflow.diff.PageRemovedEvent
import ru.snowmaze.pagingflow.diff.PagingEvent
import ru.snowmaze.pagingflow.diff.PagingEventsListener
import ru.snowmaze.pagingflow.diff.mediums.DefaultPagingEventsMedium
import ru.snowmaze.pagingflow.diff.mediums.PagingEventsMediumConfig
import ru.snowmaze.pagingflow.params.MutablePagingParams
import ru.snowmaze.pagingflow.params.PagingLibraryParamsKeys
import ru.snowmaze.pagingflow.params.PagingParams
import ru.snowmaze.pagingflow.presenters.InvalidateBehavior
import ru.snowmaze.pagingflow.result.LoadResult
import ru.snowmaze.pagingflow.source.ConcatPagingSourceConfig
import ru.snowmaze.pagingflow.source.PagingSource
import ru.snowmaze.pagingflow.utils.elementAtOrNull
import ru.snowmaze.pagingflow.utils.fastForEach
import kotlin.concurrent.Volatile

internal class DataPagesManager<Key : Any, Data : Any>(
    private val pageLoaderConfig: ConcatPagingSourceConfig<Key>,
    private val setDataMutex: Mutex,
    private val pagingSourcesManager: PagingSourcesManager<Key, Data>,
    private val onPageRemoved: (inBeginning: Boolean, pageIndex: Int) -> Unit,
    private val onLastPageNextKeyChanged: suspend (Pair<PagingSource<Key, Data>, Int>, Key?, Boolean) -> Unit,
) : DefaultPagingEventsMedium<Key, Data>() {

    private val _dataPages = MutableObjectList<DataPage<Key, Data>>()
    val dataPagesList = _dataPages.asList()
    val dataPages = _dataPages

    private var nullItemsCount = 0
    private var itemsCount = 0

    private var cachedData = MutableScatterMap<Int, Pair<Key?, MutablePagingParams>>()
    inline val pagesCount get() = dataPages.size

    private var lastPaginationDirection = true
    private var subscribed = false

    @Volatile
    private var isAnyDataChanged = false

    override val config = PagingEventsMediumConfig(
        pageLoaderConfig.coroutineScope, pageLoaderConfig.processingContext
    )

    override fun addPagingEventsListener(listener: PagingEventsListener<Key, Data>) {
        if (!pageLoaderConfig.storePageItems && isAnyDataChanged) {
            throw IllegalStateException("Can't subscribe after data is loaded because shouldStorePageItems = false.")
        }
        if (!subscribed) launchAtomically { // TODO Cover with test
            if (!subscribed) {
                subscribed = true
                notifyOnEvent(InvalidateEvent(null))
                _dataPages.forEach { page ->
                    val flow = page.flow
                    if (page.listenJob == null || page.listenJob?.isCancelled == true) {
                        if (flow == null) {
                            setNewPageData(
                                page = page,
                                value = page.data,
                                invalidateData = false,
                                isExistingPage = false,
                                awaitDataSetChannel = null,
                                awaitDataSetChannelCalled = null,
                                isFirst = true
                            )
                        } else setupSubscriber(
                            dataFlow = flow,
                            page = page,
                            awaitDataSetChannel = null,
                            isExistingPage = false,
                            invalidateData = false
                        )
                    } else launchAtomically {
                        notifyOnEvent(
                            PageAddedEvent(
                                key = page.currentPageKey,
                                sourceIndex = page.dataSourceIndex,
                                pageIndexInSource = page.pageIndexInPagingSource,
                                pageIndex = page.pageIndex,
                                items = page.data?.data ?: return@launchAtomically,
                                params = page.data?.params
                            )
                        )
                    }
                }
            }
        } else launchAtomically {
            resendAllPages(listOf(listener))
        }
        super.addPagingEventsListener(listener)
    }

    override fun removePagingEventsListener(listener: PagingEventsListener<Key, Data>): Boolean {
        val unsubscribed = super.removePagingEventsListener(listener)
        if (pagingEventsListeners.isEmpty()) launchAtomically {
            if (pagingEventsListeners.isEmpty()) {
                subscribed = false
                _dataPages.forEach {
                    it.listenJob?.cancel()
                    it.listenJob = null
                }
            }
        }
        return unsubscribed
    }

    fun notifyIfNeeded(pagingParams: PagingParams?) {
        if (!isAnyDataChanged) launchAtomically {
            notifyOnEvent(OnDataLoaded(pagingParams))
        }
    }

    suspend fun invalidate(
        invalidateBehavior: InvalidateBehavior? = null,
        removeCachedData: Boolean = true,
        skipPage: DataPage<Key, Data>? = null,
    ) {
        val dataPages = _dataPages
        dataPages.forEach { page ->
            if (page == skipPage) return@forEach
            page.listenJob?.cancel()
            page.data = null
        }
        isAnyDataChanged = false
        if (dataPages.isNotEmpty()) {
            val invalidateChannel = Channel<Unit>(1)
            if (notifyOnEvents(
                    listOf(
                        AwaitDataSetEvent { invalidateChannel.send(Unit) },
                        InvalidateEvent(invalidateBehavior)
                    )
                )
            ) invalidateChannel.receive()
        } else {
            notifyOnEvent(InvalidateEvent(invalidateBehavior))
        }

        dataPages.clear()
        itemsCount = 0
        nullItemsCount = 0
        skipPage?.let { dataPages.add(it)
            if (it.data == null) {
                nullItemsCount += it.itemCount ?: 0
            } else {
                itemsCount += it.data?.data?.size ?: 0
            }
        }
        if (removeCachedData) cachedData.clear()
    }

    fun removePagingSourcePages(dataSourceIndex: Int) {
        _dataPages.removeIf { it.dataSourceIndex == dataSourceIndex }
        updateIndexes()
    }

    fun movePages(fromDataSourceIndex: Int, toDataSourceIndex: Int) {
        val fromPages = dataPagesList.filter { it.dataSourceIndex == fromDataSourceIndex }
        if (fromPages.isEmpty()) return

        val firstFromIndex =
            _dataPages.indexOfFirst { it.dataSourceIndex == fromDataSourceIndex }
        val firstToIndex =
            _dataPages.indexOfLast { it.dataSourceIndex == toDataSourceIndex }.coerceAtLeast(0)
        repeat(fromPages.size) { _dataPages.removeAt(firstFromIndex) }
        _dataPages.addAll(firstToIndex, fromPages)
        updateIndexes()
    }

    fun updateIndexes() {
        val newCachedData = MutableScatterMap<Int, Pair<Key?, MutablePagingParams>>()
        val dataPages = _dataPages
        for (index in dataPages.indices) {
            dataPages[index].apply {
                val pagingSource = pagingSourceWithIndex.first
                cachedData[pageIndex]?.let { newCachedData[index] = it }
                pageIndex = index
                dataSourceIndex = pagingSourcesManager.getSourceIndex(pagingSource)
                pagingSourceWithIndex = pagingSource to dataSourceIndex

                val previousPage = dataPages.elementAtOrNull(index - 1)
                val isPreviousPageHaveSameDataSource =
                    previousPage?.pagingSourceWithIndex?.first == pagingSource
                previousPageKey = if (isPreviousPageHaveSameDataSource) {
                    previousPage.currentPageKey
                } else null
                val nextPage = dataPages.elementAtOrNull(index + 1)
                nextPageKey = if (isPreviousPageHaveSameDataSource) {
                    nextPage?.currentPageKey
                } else null
                pageIndexInPagingSource = if (isPreviousPageHaveSameDataSource) {
                    previousPage.pageIndexInPagingSource + 1
                } else 0
            }
        }
        cachedData = newCachedData
    }

    suspend fun resendAllPages(
        toCallbacks: Collection<PagingEventsListener<Key, Data>> = pagingEventsListeners
    ) {
        val events = buildList(1 + dataPages.size) {
            dataPages.forEach { page ->
                add(
                    PageAddedEvent(
                        key = page.currentPageKey,
                        sourceIndex = page.dataSourceIndex,
                        pageIndexInSource = page.pageIndexInPagingSource,
                        pageIndex = page.pageIndex,
                        items = page.data?.data ?: return@forEach,
                        params = page.data?.params
                    )
                )
            }
            add(
                0, if (isEmpty()) InvalidateEvent(InvalidateBehavior.INVALIDATE_IMMEDIATELY)
                else InvalidateEvent(null)
            )
        }
        toCallbacks.forEach { it.onEvents(events) }
    }

    fun getCachedData(index: Int) = cachedData[index]

    /**
     * Adding page to pages list and starting listening to page changes
     */
    suspend inline fun setupPage(
        newIndex: Int,
        result: LoadResult.Success<Key, Data>,
        page: DataPage<Key, Data>,
        loadParams: LoadParams<Key>,
        shouldReplaceOnConflict: Boolean,
        awaitDataSetChannel: Channel<Unit>?
    ) {
        isAnyDataChanged = true
        result.cachedResult?.let { cachedData[page.pageIndex] = page.currentPageKey to it }
        val isExistingPage = dataPages.elementAtOrNull(newIndex) != null
        val isPaginationDown = loadParams.paginationDirection == PaginationDirection.DOWN
        if (isExistingPage && shouldReplaceOnConflict) {
            page.itemCount = _dataPages.elementAtOrNull(newIndex)?.itemCount ?: 0
            _dataPages[newIndex] = page
        } else _dataPages.add(newIndex.coerceAtLeast(0), page)
        val invalidateData = loadParams.pagingParams?.getOrNull(
            PagingLibraryParamsKeys.InvalidateData
        ) == true

        lastPaginationDirection = isPaginationDown

        // page flow listening starts here
        if (result is LoadResult.Success.FlowSuccess && result.dataFlow != null) {
            setupSubscriber(
                dataFlow = result.dataFlow,
                page = page,
                awaitDataSetChannel = awaitDataSetChannel,
                isExistingPage = isExistingPage,
                invalidateData = invalidateData
            )
        } else if (result is LoadResult.Success.SimpleSuccess && result.data != null) try {
            setNewPageData(
                page = page,
                value = UpdatableData(
                    data = result.data, nextPageKey = result.nextPageKey, params = result.returnData
                ),
                invalidateData = invalidateData,
                isExistingPage = isExistingPage,
                awaitDataSetChannel = awaitDataSetChannel,
                awaitDataSetChannelCalled = null,
                isFirst = true
            )
        } finally {
            awaitDataSetChannel?.send(Unit)
        } else {
            awaitDataSetChannel?.send(Unit)
        }
    }

    private inline fun setupSubscriber(
        dataFlow: Flow<UpdatableData<Key, Data>>,
        page: DataPage<Key, Data>,
        awaitDataSetChannel: Channel<Unit>?,
        isExistingPage: Boolean,
        invalidateData: Boolean
    ) {
        var awaitDataSetChannel = awaitDataSetChannel
        val job = page.listenJob ?: SupervisorJob()
        page.listenJob = job
        pageLoaderConfig.coroutineScope.launch(
            pageLoaderConfig.processingContext + job
        ) {
            var isFirst = true
            val setData: suspend (data: UpdatableData<Key, Data>?) -> Unit = { value ->
                setDataMutex.lock()
                try {
                    setNewPageData(
                        page = page,
                        value = value,
                        invalidateData = invalidateData,
                        isExistingPage = isExistingPage,
                        awaitDataSetChannel = awaitDataSetChannel,
                        awaitDataSetChannelCalled = { awaitDataSetChannel = null },
                        isFirst = isFirst
                    )
                } finally {
                    setDataMutex.unlock()
                    if (isFirst) awaitDataSetChannel?.send(Unit)
                    isFirst = false
                }
            }
            try {
                if (pageLoaderConfig.collectOnlyLatest) dataFlow.buffer(
                    RENDEZVOUS,
                    onBufferOverflow = BufferOverflow.DROP_OLDEST
                ).collect(setData)
                else dataFlow.collect(setData)
            } finally {
                awaitDataSetChannel?.send(Unit)
            }
        }
    }

    private suspend fun setNewPageData(
        page: DataPage<Key, Data>,
        value: UpdatableData<Key, Data>?,
        invalidateData: Boolean,
        isExistingPage: Boolean,
        awaitDataSetChannel: Channel<Unit>?,
        awaitDataSetChannelCalled: (() -> Unit)?,
        isFirst: Boolean,
    ) {
        if (value == null) return
        val previousItemCount = page.data?.data?.size ?: page.itemCount
        val newItemCount = value.data.size

        val isPageDataSizeChanged = if (isFirst) true else newItemCount != previousItemCount

        var isDataChanged = true
        if (pageLoaderConfig.storePageItems) {
            isDataChanged = page.data?.data !== value.data
            page.data = value
        }
        page.itemCount = newItemCount
        val isPaginationDown = page.isPaginationDown

        val isLastPageChanged: Boolean
        if (isPaginationDown) {
            isLastPageChanged = page === dataPages.lastOrNull { !it.isNullified }
            page.nextPageKey = value.nextPageKey
        } else {
            isLastPageChanged = page === dataPages.firstOrNull { !it.isNullified }
            page.previousPageKey = value.nextPageKey
        }
        if (isLastPageChanged) onLastPageNextKeyChanged(
            page.pagingSourceWithIndex,
            value.nextPageKey,
            isPaginationDown
        )

        if (isDataChanged) {
            if (isFirst && isExistingPage) {
                nullItemsCount = (nullItemsCount - (previousItemCount ?: 0))
                    .coerceAtLeast(0)
            } else itemsCount -= previousItemCount ?: 0
            itemsCount += newItemCount

            val currentKey = page.currentPageKey
            var additionalLoadEvent: PageChangedEvent<Key, Data>? = null
            var event: PagingEvent<Key, Data>? = if (isFirst) {
                if (invalidateData) invalidate(skipPage = page)
                if (isExistingPage) {
                    val event = PageChangedEvent(
                        key = currentKey,
                        sourceIndex = page.dataSourceIndex,
                        pageIndexInSource = page.pageIndexInPagingSource,
                        pageIndex = page.pageIndex,
                        items = value.data,
                        changeType = PageChangedEvent.ChangeType.CHANGE_FROM_NULLS_TO_ITEMS,
                        params = value.params,
                        previousItemCount = previousItemCount ?: 0
                    )
                    page.isNullified = false
                    additionalLoadEvent = getEventToLoadNullsPageIfNeeded(
                        isPaginationDown = isPaginationDown
                    )
                    event
                } else PageAddedEvent(
                    key = currentKey,
                    sourceIndex = page.dataSourceIndex,
                    pageIndex = page.pageIndex,
                    pageIndexInSource = page.pageIndexInPagingSource,
                    items = value.data,
                    params = value.params
                )
            } else PageChangedEvent(
                key = currentKey,
                sourceIndex = page.dataSourceIndex,
                pageIndex = page.pageIndex,
                pageIndexInSource = page.pageIndexInPagingSource,
                items = value.data,
                params = value.params,
                previousItemCount = previousItemCount ?: 0
            )

            val trimEvents = if (isPageDataSizeChanged) trimPages(lastPaginationDirection, page)
            else emptyList()
            val shouldAwaitFirst = awaitDataSetChannel != null && isFirst
            val awaitChannel = if (pageLoaderConfig.collectOnlyLatest) Channel<Unit>(1)
            else null
            val awaitEvent = if (shouldAwaitFirst || pageLoaderConfig.collectOnlyLatest) {
                AwaitDataSetEvent<Key, Data> {
                    awaitDataSetChannelCalled?.invoke()
                    awaitDataSetChannel?.trySend(Unit)
                    awaitChannel?.trySend(Unit)
                }
            } else null
            val notified = if (trimEvents.isNullOrEmpty()) {
                if (awaitEvent == null && additionalLoadEvent == null) {
                    notifyOnEvent(event!!)
                } else notifyOnEvents(
                    buildList(
                        1 + awaitEvent.nullableToInt() +
                                additionalLoadEvent.nullableToInt()
                    ) {
                        add(event!!)
                        awaitEvent?.let { add(it) }
                        additionalLoadEvent?.let { add(it) }
                    }
                )
            } else {
                trimEvents.fastForEach { trimEvent ->
                    onPageRemoved(isPaginationDown, trimEvent.pageIndex)
                    if (trimEvent.pageIndex == page.pageIndex) {
                        event = null
                    }
                    if (additionalLoadEvent?.pageIndex == trimEvent.pageIndex) {
                        additionalLoadEvent = null
                    }
                }

                val events = ArrayList<PagingEvent<Key, Data>>(
                    trimEvents.size + if (event != null) 1 else 0 +
                            if (awaitEvent == null) 0 else 1 +
                                    if (additionalLoadEvent != null) 1 else 0
                )
                if (additionalLoadEvent != null) events.add(additionalLoadEvent)
                if (event != null) events.add(event)
                events.addAll(trimEvents as List<PagingEvent<Key, Data>>)
                if (awaitEvent != null) events.add(awaitEvent)
                notifyOnEvents(events)
            }
            if (!notified) {
                if (awaitEvent != null) {
                    delay(1000)
                    awaitEvent.callback.invoke()
                }
            } else if (pageLoaderConfig.collectOnlyLatest) awaitChannel?.receive()
        }

        if (page.isCancelled) page.listenJob?.cancel()
    }

    /**
     * Removes pages that not needed anymore
     * Also can replace removed pages to null pages if [MaxItemsConfiguration.maxDroppedPagesItemsCount] is not null
     */
    private fun trimPages(
        isPaginationDown: Boolean, currentPage: DataPage<Key, Data>
    ): List<EventFromDataSource<Key, Data>>? {
        val trimConfig = pageLoaderConfig.maxItemsConfiguration ?: return null
        val maxItemsCount = trimConfig.maxItemsCount
        if (itemsCount > maxItemsCount) {

            val pageIndex = if (isPaginationDown) {
                dataPages.indexOfFirst { item: DataPage<Key, Data> ->
                    !item.isNullified
                }
            } else dataPages.indexOfLast { item: DataPage<Key, Data> ->
                !item.isNullified
            }
            val page = dataPages.elementAtOrNull(pageIndex) ?: return null

            /**
             * Tested by [PagingBothDirectionsTest.testPagingBothDirections]
             */
            val maxCachedResultPagesCount = trimConfig.maxCachedResultPagesCount
            if (maxCachedResultPagesCount != null && cachedData.size > maxCachedResultPagesCount) {
                var maxKey = Int.MIN_VALUE
                var minKey = Int.MAX_VALUE
                cachedData.forEachKey {
                    if (it > maxKey) maxKey = it
                    if (minKey > it) minKey = it
                }

                val removeCacheIndex = if (isPaginationDown) {
                    maxKey - maxCachedResultPagesCount
                } else {
                    minKey + maxCachedResultPagesCount
                }
                if (isPaginationDown) {
                    for (i in minKey..removeCacheIndex) {
                        cachedData.remove(i)
                    }
                } else {
                    for (i in removeCacheIndex..maxKey) {
                        cachedData.remove(i)
                    }
                }
            }

            page.itemCount?.let { itemsCount -= it }

            val maxDroppedPagesItemsCount = trimConfig.maxDroppedPagesItemsCount
            val enableDroppedPagesNullPlaceholders = maxDroppedPagesItemsCount != null
            var dropPageEvent: PageRemovedEvent<Key, Data>? = null

            val trimEvent = if (enableDroppedPagesNullPlaceholders &&
                (isPaginationDown && page.pageIndex >= 0) ||
                (!isPaginationDown && 0 > page.pageIndex)
            ) {

                if (page == currentPage) page.isCancelled = true
                else page.listenJob?.cancel()

                val lastData = page.data?.data
                val itemCount = lastData?.size ?: page.itemCount ?: return null
                val trimEvent = PageChangedEvent(
                    key = page.currentPageKey,
                    sourceIndex = page.dataSourceIndex,
                    pageIndex = page.pageIndex,
                    pageIndexInSource = page.pageIndexInPagingSource,
                    items = listOfNulls(itemCount),
                    changeType = PageChangedEvent.ChangeType.CHANGE_TO_NULLS,
                    previousItemCount = page.currentItemCount
                )
                nullItemsCount += itemCount
                page.data = null
                page.isNullified = true
                page.flow = null
                page.itemCount = itemCount

                /**
                 * Tested by [PagingBothDirectionsTest.pagingWithLimitedNulls]
                 */
                if (maxDroppedPagesItemsCount != 0 &&
                    nullItemsCount > (maxDroppedPagesItemsCount ?: 0)
                ) {
                    val nullPageIndex = if (isPaginationDown) _dataPages.indexOfFirst { it.isNullified }
                    else _dataPages.indexOfLast { it.isNullified }
                    if (nullPageIndex != -1) {
                        val page = _dataPages.removeAt(nullPageIndex)
                        nullItemsCount -= page.itemCount ?: 0
                        dropPageEvent = page.removeEvent()
                        page.data = null
                        page.listenJob?.cancel()
                    }
                }
                trimEvent
            } else {
                if (page == currentPage) page.isCancelled = true
                else page.listenJob?.cancel()
                _dataPages.removeAt(pageIndex)

                val trimEvent = page.removeEvent()
                page.data = null
                page.itemCount = null
                trimEvent
            }
            val anotherEvents = trimPages(isPaginationDown, currentPage)

            return buildList(1 + (anotherEvents?.size ?: 0) + dropPageEvent.nullableToInt()) {
                add(trimEvent)
                anotherEvents?.let { addAll(it) }
                dropPageEvent?.let { add(it) }
            }
        }
        return null
    }

    /**
     * Tested by [PagingBothDirectionsTest.pagingWithLimitedNulls]
     */
    private inline fun getEventToLoadNullsPageIfNeeded(
        isPaginationDown: Boolean
    ): PageAddedEvent<Key, Data>? = if (nullItemsCount > 0 &&
        (pageLoaderConfig.maxItemsConfiguration?.maxDroppedPagesItemsCount ?: 0) > 0 &&
        pageLoaderConfig.maxItemsConfiguration?.restoreDroppedNullPagesWhenNeeded == true
    ) {
        val lastNullsPage: DataPage<Key, Data>
        val pageSize = pageLoaderConfig.defaultParamsProvider().pageSize
        val event = if (isPaginationDown) {
            lastNullsPage = _dataPages.lastOrNull { it.isNullified } ?: return null
            if (lastNullsPage.pageIndex >= -1) return null
            val newPageIndex = lastNullsPage.pageIndex + 1
            PageAddedEvent<Key, Data>(
                key = null,
                pageIndex = newPageIndex,
                pageIndexInSource = lastNullsPage.pageIndexInPagingSource + 1, // TODO
                sourceIndex = lastNullsPage.dataSourceIndex,
                items = listOfNulls(pageSize),
                changeType = PageChangedEvent.ChangeType.CHANGE_TO_NULLS
            )
        } else {
            lastNullsPage = _dataPages.firstOrNull { it.isNullified } ?: return null
            if (0 >= lastNullsPage.pageIndex) return null
            val newPageIndex = lastNullsPage.pageIndex - 1
            PageAddedEvent<Key, Data>(
                key = null,
                pageIndex = newPageIndex,
                pageIndexInSource = lastNullsPage.pageIndexInPagingSource - 1, // TODO
                sourceIndex = lastNullsPage.dataSourceIndex,
                items = listOfNulls(pageSize),
                changeType = PageChangedEvent.ChangeType.CHANGE_TO_NULLS
            )
        }
        nullItemsCount += event.items.size
        _dataPages.add(
            if (isPaginationDown) _dataPages.size else 0,
            DataPage(
                data = null,
                event.items.size,
                isPaginationDown = isPaginationDown,
                isCancelled = false,
                isNullified = true,
                previousPageKey = lastNullsPage.currentPageKey,
                currentPageKey = null,
                nextPageKey = null,
                pagingSourceWithIndex = lastNullsPage.pagingSourceWithIndex,
                listenJob = null,
                pageIndex = event.pageIndex,
                dataSourceIndex = lastNullsPage.dataSourceIndex,
                pageIndexInPagingSource = event.pageIndexInSource,
                flow = null,
            )
        )
        event
    } else null

    private fun DataPage<Key, Data>.removeEvent() = PageRemovedEvent<Key, Data>(
        key = currentPageKey,
        sourceIndex = dataSourceIndex,
        pageIndex = pageIndex,
        pageIndexInSource = pageIndexInPagingSource,
        itemsCount = currentItemCount
    )

    private inline fun launchAtomically(
        crossinline block: suspend () -> Unit
    ) = config.coroutineScope.launch(config.processingContext) {
        setDataMutex.withLock {
            block()
        }
    }

    private inline fun listOfNulls(count: Int) = buildList<Data?>(count) {
        repeat(count) {
            add(null)
        }
    }

    private inline fun Any?.nullableToInt() = if (this == null) 0 else 1
}
