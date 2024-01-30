package ru.snowmaze.pagingflow.presenters

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import ru.snowmaze.pagingflow.PagingFlow
import ru.snowmaze.pagingflow.diff.mediums.DataChangesMedium
import ru.snowmaze.pagingflow.diff.mediums.PagingDataMappingMedium

/**
 * Creates mapping presenter, which maps only changed pages and have throttling mechanism
 * @see pagingDataPresenter for arguments docs
 */
fun <Key : Any, Data : Any, NewData : Any> DataChangesMedium<Key, Data>.mappingDataPresenter(
    invalidateBehavior: InvalidateBehavior =
        InvalidateBehavior.INVALIDATE_AND_CLEAR_LIST_BEFORE_NEXT_VALUE_RECEIVED,
    throttleDurationMs: Long = 0L,
    coroutineScope: CoroutineScope,
    processingDispatcher: CoroutineDispatcher = Dispatchers.Default,
    transform: (List<Data?>) -> List<NewData?>
) = if (throttleDurationMs == 0L) BuildListPagingPresenter(
    dataChangesMedium = PagingDataMappingMedium(
        dataChangedCallback = this,
        transform = transform
    ),
    invalidateBehavior = invalidateBehavior,
    coroutineScope = coroutineScope,
    processingDispatcher = processingDispatcher
) else ThrottleEventsPagingPresenter(
    dataChangesMedium = PagingDataMappingMedium(
        dataChangedCallback = this,
        transform = transform
    ),
    invalidateBehavior = invalidateBehavior,
    throttleDurationMs = throttleDurationMs,
    coroutineScope = coroutineScope,
    processingDispatcher = processingDispatcher
)

fun <Key : Any, Data : Any, NewData : Any> PagingFlow<Key, Data, *>.mappingDataPresenter(
    invalidateBehavior: InvalidateBehavior =
        InvalidateBehavior.INVALIDATE_AND_CLEAR_LIST_BEFORE_NEXT_VALUE_RECEIVED,
    throttleDurationMs: Long = 0L,
    transform: (List<Data?>) -> List<NewData?>
) = mappingDataPresenter(
    invalidateBehavior = invalidateBehavior,
    throttleDurationMs = throttleDurationMs,
    coroutineScope = pagingFlowConfiguration.coroutineScope,
    processingDispatcher = pagingFlowConfiguration.processingDispatcher,
    transform = transform
)

/**
 * Creates simple presenter, which builds list from pages and have throttling mechanism
 * @param invalidateBehavior behavior of invalidate, by default it clears list after new value received, it means that list wouldn't blink when invalidate happens
 * @param throttleDurationMs duration of throttle window
 * @see InvalidateBehavior
 */
fun <Key : Any, Data : Any> DataChangesMedium<Key, Data>.pagingDataPresenter(
    invalidateBehavior: InvalidateBehavior =
        InvalidateBehavior.INVALIDATE_AND_CLEAR_LIST_BEFORE_NEXT_VALUE_RECEIVED,
    throttleDurationMs: Long = 0L,
    coroutineScope: CoroutineScope,
    processingDispatcher: CoroutineDispatcher = Dispatchers.Default
) = if (throttleDurationMs == 0L) BuildListPagingPresenter(
    dataChangesMedium = this,
    invalidateBehavior = invalidateBehavior,
    coroutineScope = coroutineScope,
    processingDispatcher = processingDispatcher
) else ThrottleEventsPagingPresenter(
    dataChangesMedium = this,
    invalidateBehavior = invalidateBehavior,
    throttleDurationMs = throttleDurationMs,
    coroutineScope = coroutineScope,
    processingDispatcher = processingDispatcher
)

fun <Key : Any, Data : Any> PagingFlow<Key, Data, *>.pagingDataPresenter(
    invalidateBehavior: InvalidateBehavior =
        InvalidateBehavior.INVALIDATE_AND_CLEAR_LIST_BEFORE_NEXT_VALUE_RECEIVED,
    throttleDurationMs: Long = 0L
) = pagingDataPresenter(
    invalidateBehavior = invalidateBehavior,
    throttleDurationMs = throttleDurationMs,
    coroutineScope = pagingFlowConfiguration.coroutineScope,
    processingDispatcher = pagingFlowConfiguration.processingDispatcher
)