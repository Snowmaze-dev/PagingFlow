package ru.snowmaze.pagingflow.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import ru.snowmaze.pagingflow.PaginationDirection
import ru.snowmaze.pagingflow.PagingFlow

/**
 * The helper class which calls pagination when end of page reached.
 */
class PagingTrigger(
    private val isLoadingCallback: () -> Boolean,
    private val onEndReached: suspend (PaginationDirection) -> Unit,
    var itemCount: () -> Int,
    val pageSize: () -> Int,
    var currentStartIndexProvider: () -> Int = { 0 },
    val maxItemsCount: () -> Int? = { null },
    val prefetchDownDistance: Int = 1,
    val prefetchUpDistance: Int = prefetchDownDistance,
    private val throttleTimeMillis: Int = 500,
    val paginationDownEnabled: Boolean = true,
    val paginationUpEnabled: Boolean = true,
    private val shouldTryPaginateBackOnMaxItemsOffset: Boolean = false,
    private val saveLastThrottledEvent: Boolean = true,
    private val coroutineScope: CoroutineScope,
    var currentTimeMillisProvider: () -> Long = {
        Clock.System.now().toEpochMilliseconds()
    },
) {

    constructor(
        pagingFlow: () -> PagingFlow<*, *, *>,
        itemCount: () -> Int = { 0 },
        currentStartIndex: () -> Int = { 0 },
        prefetchDownDistance: Int = 1,
        prefetchUpDistance: Int = prefetchDownDistance,
        throttleTimeMillis: Int = 500,
        paginationDownEnabled: Boolean = true,
        paginationUpEnabled: Boolean = true,
        shouldTryPaginateBackOnMaxItemsOffset: Boolean = false,
        saveLastThrottledEvent: Boolean = true,
        coroutineScope: CoroutineScope,
        currentTimeMillisProvider: () -> Long = { Clock.System.now().toEpochMilliseconds() },
        onEndReached: suspend (PaginationDirection) -> Unit = { direction ->
            pagingFlow().loadNextPageWithResult(direction)
        },
    ) : this(
        itemCount = itemCount,
        isLoadingCallback = { pagingFlow().isLoading },
        maxItemsCount = { pagingFlow().pagingFlowConfiguration.maxItemsConfiguration?.maxItemsCount },
        pageSize = { pagingFlow().pagingFlowConfiguration.defaultParamsProvider().pageSize },
        currentStartIndexProvider = currentStartIndex,
        prefetchDownDistance = prefetchDownDistance,
        prefetchUpDistance = prefetchUpDistance,
        throttleTimeMillis = throttleTimeMillis,
        paginationDownEnabled = paginationDownEnabled,
        paginationUpEnabled = paginationUpEnabled,
        shouldTryPaginateBackOnMaxItemsOffset = shouldTryPaginateBackOnMaxItemsOffset,
        saveLastThrottledEvent = saveLastThrottledEvent,
        coroutineScope = coroutineScope,
        currentTimeMillisProvider = currentTimeMillisProvider,
        onEndReached = onEndReached
    )

    private var _isLoading = false
    private var job: Job? = null

    val isLoading get() = _isLoading || isLoadingCallback()
    private var lastTimeTriggered = 0L
    private var lastIndex = 0

    fun checkLastIndexAgain() {
        onItemVisible(lastIndex)
    }

    fun onItemVisible(index: Int): Boolean {
        lastIndex = index
        if (isLoading) return false
        val currentTime = currentTimeMillisProvider()
        job?.cancel()
        if (throttleTimeMillis != 0 &&
            throttleTimeMillis > currentTime - lastTimeTriggered
        ) {
            if (saveLastThrottledEvent) job = coroutineScope.launch(Dispatchers.Main) {
                delay(throttleTimeMillis.toLong())
                onItemVisible(index)
            }
            return false
        }
        val itemCount = itemCount()
        val maxItemsCount = maxItemsCount()
        val relativeStartIndex = if (maxItemsCount == null) index else {
            index - currentStartIndexProvider()
        }
        val direction = if (paginationDownEnabled && index >= (itemCount - prefetchDownDistance)) {
            PaginationDirection.DOWN
        } else if (paginationUpEnabled &&
            if (shouldTryPaginateBackOnMaxItemsOffset && maxItemsCount != null) {
                val pageSize = pageSize()
                prefetchUpDistance * ((maxItemsCount / pageSize) - 1) >= relativeStartIndex
            } else prefetchUpDistance >= relativeStartIndex
        ) PaginationDirection.UP
        else return false
        _isLoading = true
        coroutineScope.launch {
            onEndReached(direction)
            lastTimeTriggered = currentTime
            _isLoading = false
        }
        return true
    }
}