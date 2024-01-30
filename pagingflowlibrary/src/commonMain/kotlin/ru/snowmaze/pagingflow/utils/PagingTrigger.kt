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
    private val pagingFlow: () -> PagingFlow<*, *, *>,
    private val itemCount: () -> Int,
    val prefetchDistance: Int = 1,
    private val debounceTimeSeconds: Int = 1,
    val paginationDownEnabled: Boolean = true,
    val paginationUpEnabled: Boolean = true,
    val shouldTryPaginateBackOnEveryPageStartVisible: Boolean = true,
    private val coroutineScope: CoroutineScope = GlobalScope,
    private val currentTimeSecondsProvider: () -> Long = { Clock.System.now().epochSeconds },
    private val onEndReached: suspend (PaginationDirection) -> Unit = { direction ->
        pagingFlow().loadNextPageWithResult(direction)
    }
) {

    private var _isLoading = false

    val isLoading get() = _isLoading || pagingFlow().isLoading
    private var lastTimeTriggered = 0L

    fun onItemVisible(index: Int): Boolean {
        val currentTime = currentTimeSecondsProvider()
        if (debounceTimeSeconds != 0 &&
            debounceTimeSeconds > currentTime - lastTimeTriggered
        ) return false
        val pagingFlow = pagingFlow()
        if (pagingFlow.isLoading) return false
        val removePagesOffset = pagingFlow.pagingFlowConfiguration.maxPagesCount
        val itemCount = itemCount()
        val direction = if (index >= (itemCount - prefetchDistance) && paginationDownEnabled) {
            PaginationDirection.DOWN
        } else if (paginationUpEnabled &&
            if (shouldTryPaginateBackOnEveryPageStartVisible && removePagesOffset != null) {
                prefetchDistance * (removePagesOffset - 1) >= index
            } else prefetchDistance >= index
        ) {
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