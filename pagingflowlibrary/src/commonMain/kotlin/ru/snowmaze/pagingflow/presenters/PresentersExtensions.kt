package ru.snowmaze.pagingflow.presenters

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import ru.snowmaze.pagingflow.PagingFlow
import ru.snowmaze.pagingflow.diff.mediums.DataChangesMedium
import ru.snowmaze.pagingflow.diff.mediums.PagingDataMappingMedium
import ru.snowmaze.pagingflow.diff.mediums.ThrottleDataChangesMedium

/**
 * Creates mapping presenter, which maps only changed pages and have throttling mechanism
 * @see pagingDataPresenter for arguments docs
 */
fun <Key : Any, Data : Any, NewData : Any> DataChangesMedium<Key, Data>.mappingDataPresenter(
    invalidateBehavior: InvalidateBehavior =
        InvalidateBehavior.INVALIDATE_AND_SEND_EMPTY_LIST_BEFORE_NEXT_VALUE,
    coroutineScope: CoroutineScope,
    processingDispatcher: CoroutineDispatcher = Dispatchers.Default,
    transform: (List<Data?>) -> List<NewData?>
) = SimpleBuildListPagingPresenter(
    dataChangesMedium = PagingDataMappingMedium(
        dataChangesMedium = this,
        transform = transform
    ),
    invalidateBehavior = invalidateBehavior,
    coroutineScope = coroutineScope,
    processingDispatcher = processingDispatcher
)

fun <Key : Any, Data : Any, NewData : Any> PagingFlow<Key, Data, *>.mappingDataPresenter(
    invalidateBehavior: InvalidateBehavior =
        InvalidateBehavior.INVALIDATE_AND_SEND_EMPTY_LIST_BEFORE_NEXT_VALUE,
    throttleDurationMs: Long = 0L,
    transform: (List<Data?>) -> List<NewData?>
): SimpleBuildListPagingPresenter<Key, NewData> {
    return (if (throttleDurationMs == 0L) this else ThrottleDataChangesMedium(
        this,
        throttleDurationMs = throttleDurationMs,
        coroutineScope = pagingFlowConfiguration.coroutineScope,
        processingDispatcher = pagingFlowConfiguration.processingDispatcher
    )).mappingDataPresenter(
        invalidateBehavior = invalidateBehavior,
        coroutineScope = pagingFlowConfiguration.coroutineScope,
        processingDispatcher = pagingFlowConfiguration.processingDispatcher,
        transform = transform
    )
}

/**
 * Creates simple presenter, which builds list from pages and have throttling mechanism
 * @param invalidateBehavior behavior of invalidate, by default it clears list after new value received, it means that list wouldn't blink when invalidate happens
 * @param throttleDurationMs duration of throttle window
 * @see InvalidateBehavior
 */
fun <Key : Any, Data : Any> DataChangesMedium<Key, Data>.pagingDataPresenter(
    invalidateBehavior: InvalidateBehavior =
        InvalidateBehavior.INVALIDATE_AND_SEND_EMPTY_LIST_BEFORE_NEXT_VALUE,
    coroutineScope: CoroutineScope,
    processingDispatcher: CoroutineDispatcher = Dispatchers.Default
) = SimpleBuildListPagingPresenter(
    dataChangesMedium = this,
    invalidateBehavior = invalidateBehavior,
    coroutineScope = coroutineScope,
    processingDispatcher = processingDispatcher
)

fun <Key : Any, Data : Any> PagingFlow<Key, Data, *>.pagingDataPresenter(
    invalidateBehavior: InvalidateBehavior =
        InvalidateBehavior.INVALIDATE_AND_SEND_EMPTY_LIST_BEFORE_NEXT_VALUE,
    throttleDurationMs: Long = 0L
): SimpleBuildListPagingPresenter<Key, Data> {
    return (if (throttleDurationMs == 0L) this else ThrottleDataChangesMedium(
        this,
        throttleDurationMs = throttleDurationMs,
        coroutineScope = pagingFlowConfiguration.coroutineScope,
        processingDispatcher = pagingFlowConfiguration.processingDispatcher
    )).pagingDataPresenter(
        invalidateBehavior = invalidateBehavior,
        coroutineScope = pagingFlowConfiguration.coroutineScope,
        processingDispatcher = pagingFlowConfiguration.processingDispatcher
    )
}