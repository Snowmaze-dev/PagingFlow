package ru.snowmaze.pagingflow

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

class PagingTrigger(
    private val pagingFlowProvider: () -> PagingFlow<*, *, *>,
    val coroutineScope: CoroutineScope = GlobalScope,
    val prefetchDistance: Int = 1,
    val debounceTimeSeconds: Int = 2,
    val paginationDownEnabled: Boolean = true,
    val paginationUpEnabled: Boolean = true,
    val currentTimeSecondsProvider: () -> Long = { Clock.System.now().epochSeconds },
    private val onEndReached: suspend (PaginationDirection) -> Unit = { direction ->
        pagingFlowProvider().loadNextPageWithResult(direction)
    }
) {

    val isLoading get() = pagingFlowProvider().isLoading
    private var lastTimeTriggered = 0L

    fun onItemVisible(index: Int): Boolean {
        val currentTime = currentTimeSecondsProvider()
        if (debounceTimeSeconds > currentTime - lastTimeTriggered) return false
        val pagingFlow = pagingFlowProvider()
        if (pagingFlow.isLoading) return false
        val itemCount = pagingFlow.dataFlow.value.size
        val direction = if (index >= (itemCount - prefetchDistance) && paginationDownEnabled) {
            PaginationDirection.DOWN
        } else if (prefetchDistance >= index && paginationUpEnabled) {
            PaginationDirection.UP
        } else return false
        coroutineScope.launch {
            onEndReached(direction)
            lastTimeTriggered = currentTime
        }
        return true
    }
}