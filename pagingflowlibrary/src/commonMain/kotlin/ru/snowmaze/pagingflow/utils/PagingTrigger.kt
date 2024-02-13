package ru.snowmaze.pagingflow.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
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
    var itemCount: () -> Int = { 0 },
    var currentStartIndexProvider: () -> Int = { 0 },
    val maxPagesCount: () -> Int? = { null },
    val prefetchDownDistance: Int = 1,
    val prefetchUpDistance: Int = prefetchDownDistance,
    private val debounceTimeMillis: Int = 500,
    val paginationDownEnabled: Boolean = true,
    val paginationUpEnabled: Boolean = true,
    private val coroutineScope: CoroutineScope = GlobalScope,
    var currentTimeMillisProvider: () -> Long = {
        Clock.System.now().toEpochMilliseconds()
    }
) {

    constructor(
        pagingFlow: () -> PagingFlow<*, *, *>,
        itemCount: () -> Int = { 0 },
        currentStartIndex: () -> Int = { 0 },
        prefetchDownDistance: Int = 1,
        prefetchUpDistance: Int = prefetchDownDistance,
        debounceTimeMillis: Int = 500,
        paginationDownEnabled: Boolean = true,
        paginationUpEnabled: Boolean = true,
        coroutineScope: CoroutineScope = GlobalScope,
        currentTimeMillisProvider: () -> Long = { Clock.System.now().toEpochMilliseconds() },
        onEndReached: suspend (PaginationDirection) -> Unit = { direction ->
            pagingFlow().loadNextPageWithResult(direction)
        }
    ) : this(
        itemCount = itemCount,
        isLoadingCallback = { pagingFlow().isLoading },
        maxPagesCount = { pagingFlow().pagingFlowConfiguration.maxPagesCount },
        currentStartIndexProvider = currentStartIndex,
        prefetchDownDistance = prefetchDownDistance,
        prefetchUpDistance = prefetchUpDistance,
        debounceTimeMillis = debounceTimeMillis,
        paginationDownEnabled = paginationDownEnabled,
        paginationUpEnabled = paginationUpEnabled,
        coroutineScope = coroutineScope,
        currentTimeMillisProvider = currentTimeMillisProvider,
        onEndReached = onEndReached
    )

    private var _isLoading = false

    val isLoading get() = _isLoading || isLoadingCallback()
    private var lastTimeTriggered = 0L

    fun onItemVisible(index: Int): Boolean {
        val currentTime = currentTimeMillisProvider()
        if (debounceTimeMillis != 0 &&
            debounceTimeMillis > currentTime - lastTimeTriggered
        ) return false
        if (isLoadingCallback()) return false
        val maxPagesCount = maxPagesCount()
        val itemCount = itemCount()
        val relativeStartIndex = if (maxPagesCount == null) index else {
            index - currentStartIndexProvider()
        }
        val direction = if (index >= (itemCount - prefetchDownDistance) && paginationDownEnabled) {
            PaginationDirection.DOWN
        } else if (paginationUpEnabled && prefetchUpDistance >= relativeStartIndex) {
            PaginationDirection.UP
        } else return false
        _isLoading = true
        coroutineScope.launch {
            onEndReached(direction)
            lastTimeTriggered = currentTime
            _isLoading = false
        }
        return true
    }
}