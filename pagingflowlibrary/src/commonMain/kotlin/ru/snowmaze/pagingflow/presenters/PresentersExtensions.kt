package ru.snowmaze.pagingflow.presenters

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import ru.snowmaze.pagingflow.diff.PageChangedEvent
import ru.snowmaze.pagingflow.diff.mediums.BufferEventsDataChangesMedium
import ru.snowmaze.pagingflow.diff.mediums.MappingFlowPagingDataChangesMedium
import ru.snowmaze.pagingflow.diff.mediums.MappingPagingDataChangesMedium
import ru.snowmaze.pagingflow.diff.mediums.PagingDataChangesMedium
import ru.snowmaze.pagingflow.diff.mediums.BatchingPagingDataChangesMedium
import ru.snowmaze.pagingflow.diff.mediums.composite.CompositePagingDataChangesMediumBuilder

/**
 * Creates mapping presenter, which maps only changed pages and have throttling mechanism
 * @param invalidateBehavior see [InvalidateBehavior]
 * @param transform transforms event to list of data
 * @param presenterFlow creates flow which will be used as dataFlow in presenter
 * @see pagingDataPresenter for arguments docs on arguments
 */
fun <Key : Any, Data : Any, NewData : Any> PagingDataChangesMedium<Key, Data>.mapDataPresenter(
    invalidateBehavior: InvalidateBehavior = InvalidateBehavior.WAIT_FOR_NEW_LIST,
    presenterFlow: () -> MutableSharedFlow<LatestData<NewData>> = defaultPresenterFlowCreator(),
    transform: (PageChangedEvent<Key, Data>) -> List<NewData?>
) = MappingPagingDataChangesMedium(
    pagingDataChangesMedium = this,
    transform = transform
).pagingDataPresenter(invalidateBehavior, presenterFlow)

/**
 * Maps events to flow of data
 */
fun <Key : Any, Data : Any, NewData : Any> PagingDataChangesMedium<Key, Data>.mapDataFlowPresenter(
    invalidateBehavior: InvalidateBehavior = InvalidateBehavior.WAIT_FOR_NEW_LIST,
    presenterFlow: () -> MutableSharedFlow<LatestData<NewData>> = defaultPresenterFlowCreator(),
    transform: (PageChangedEvent<Key, Data>) -> Flow<List<NewData?>>
) = MappingFlowPagingDataChangesMedium(
    pagingDataChangesMedium = this,
    transform = transform
).pagingDataPresenter(
    invalidateBehavior = invalidateBehavior,
    presenterFlow = presenterFlow
)

/**
 * @param invalidateBehavior see [InvalidateBehavior]
 * @param eventsBatchingDurationMsProvider provider of duration of throttle window
 * @param shouldBatchAddPagesEvents defines whether should throttle add pages events or not
 * @param shouldBufferEvents see [BufferEventsDataChangesMedium]
 * @param transform mapping lambda
 */
fun <Key : Any, Data : Any, NewData : Any> PagingDataChangesMedium<Key, Data>.mapDataPresenter(
    invalidateBehavior: InvalidateBehavior = InvalidateBehavior.WAIT_FOR_NEW_LIST,
    eventsBatchingDurationMsProvider: () -> Long = { 0 },
    shouldBatchAddPagesEvents: Boolean = false,
    shouldBufferEvents: Boolean = false,
    presenterFlow: () -> MutableSharedFlow<LatestData<NewData>> = defaultPresenterFlowCreator(),
    transform: (PageChangedEvent<Key, Data>) -> List<NewData?>
) = BatchingPagingDataChangesMedium(
    pagingDataChangesMedium = MappingPagingDataChangesMedium(
        pagingDataChangesMedium = this,
        transform = transform
    ).let {
        if (shouldBufferEvents) BufferEventsDataChangesMedium(it)
        else it
    },
    eventsBatchingDurationMsProvider = eventsBatchingDurationMsProvider,
    shouldBatchAddPagesEvents = shouldBatchAddPagesEvents
).pagingDataPresenter(invalidateBehavior, presenterFlow)

/**
 * Maps events to flow of data
 */
fun <Key : Any, Data : Any, NewData : Any> PagingDataChangesMedium<Key, Data>.mapDataFlowPresenter(
    invalidateBehavior: InvalidateBehavior = InvalidateBehavior.WAIT_FOR_NEW_LIST,
    eventsBatchingDurationMsProvider: () -> Long = { 0 },
    shouldBatchAddPagesEvents: Boolean = false,
    shouldBufferEvents: Boolean = false,
    presenterFlow: () -> MutableSharedFlow<LatestData<NewData>> = defaultPresenterFlowCreator(),
    transform: (PageChangedEvent<Key, Data>) -> Flow<List<NewData?>>
) = BatchingPagingDataChangesMedium(
    pagingDataChangesMedium = MappingFlowPagingDataChangesMedium(
        pagingDataChangesMedium = this,
        transform = transform
    ).let {
        if (shouldBufferEvents) BufferEventsDataChangesMedium(it)
        else it
    },
    eventsBatchingDurationMsProvider = eventsBatchingDurationMsProvider,
    shouldBatchAddPagesEvents = shouldBatchAddPagesEvents
).pagingDataPresenter(invalidateBehavior, presenterFlow)

/**
 * Creates simple presenter, which builds list from pages
 * @param invalidateBehavior behavior of invalidate, by default it clears list after new value received, it means that list wouldn't blink when invalidate happens
 * @see InvalidateBehavior
 */
fun <Key : Any, Data : Any> PagingDataChangesMedium<Key, Data>.pagingDataPresenter(
    invalidateBehavior: InvalidateBehavior = InvalidateBehavior.WAIT_FOR_NEW_LIST,
    presenterFlow: () -> MutableSharedFlow<LatestData<Data>> = defaultPresenterFlowCreator()
) = SimpleBuildListPagingPresenter(
    pagingDataChangesMedium = this,
    invalidateBehavior = invalidateBehavior,
    presenterFlow = presenterFlow
)

/**
 * @param invalidateBehavior see [InvalidateBehavior]
 * @param eventsBatchingDurationMsProvider provider of duration of throttle window
 * @param shouldBatchAddPagesEvents defines whether should throttle add pages events or not
 * @param shouldBufferEvents see [BufferEventsDataChangesMedium]
 */
fun <Key : Any, Data : Any> PagingDataChangesMedium<Key, Data>.pagingDataPresenter(
    invalidateBehavior: InvalidateBehavior = InvalidateBehavior.WAIT_FOR_NEW_LIST,
    eventsBatchingDurationMsProvider: () -> Long = { 0 },
    shouldBatchAddPagesEvents: Boolean = false,
    shouldBufferEvents: Boolean = false,
    presenterFlow: () -> MutableSharedFlow<LatestData<Data>> = defaultPresenterFlowCreator()
) = BatchingPagingDataChangesMedium(
    if (shouldBufferEvents) BufferEventsDataChangesMedium(this) else this,
    eventsBatchingDurationMsProvider = eventsBatchingDurationMsProvider,
    shouldBatchAddPagesEvents = shouldBatchAddPagesEvents
).pagingDataPresenter(invalidateBehavior, presenterFlow)

fun <Key : Any, Data : Any, NewData : Any> PagingDataChangesMedium<Key, Data>.compositeDataPresenter(
    invalidateBehavior: InvalidateBehavior = InvalidateBehavior.WAIT_FOR_NEW_LIST,
    shouldBufferEvents: Boolean = false,
    presenterFlow: () -> MutableSharedFlow<LatestData<NewData>> = defaultPresenterFlowCreator(),
    builder: CompositePagingDataChangesMediumBuilder<Key, Data, NewData>.() -> Unit
) = CompositePagingDataChangesMediumBuilder.build(
    if (shouldBufferEvents) BufferEventsDataChangesMedium(this) else this,
    builder = builder
).pagingDataPresenter(invalidateBehavior, presenterFlow)

fun <Key : Any, Data : Any, NewData : Any> PagingDataChangesMedium<Key, Data>.compositeDataPresenter(
    invalidateBehavior: InvalidateBehavior = InvalidateBehavior.WAIT_FOR_NEW_LIST,
    eventsBatchingDurationMsProvider: () -> Long = { 0 },
    shouldBatchAddPagesEvents: Boolean = false,
    shouldBufferEvents: Boolean = false,
    presenterFlow: () -> MutableSharedFlow<LatestData<NewData>> = defaultPresenterFlowCreator(),
    builder: CompositePagingDataChangesMediumBuilder<Key, Data, NewData>.() -> Unit
) = CompositePagingDataChangesMediumBuilder.build(
    BatchingPagingDataChangesMedium(
        if (shouldBufferEvents) BufferEventsDataChangesMedium(this) else this,
        eventsBatchingDurationMsProvider = eventsBatchingDurationMsProvider,
        shouldBatchAddPagesEvents = shouldBatchAddPagesEvents
    ), builder = builder
).pagingDataPresenter(invalidateBehavior, presenterFlow)