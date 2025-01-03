package ru.snowmaze.pagingflow.presenters

import kotlinx.coroutines.flow.Flow
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
    configuration: BasicPresenterConfiguration<Key, NewData> = BasicPresenterConfiguration(),
    transform: (PageChangedEvent<Key, Data>) -> List<NewData?>
) = MappingPagingDataChangesMedium(
    pagingDataChangesMedium = this,
    transform = transform
).pagingDataPresenter(configuration)

/**
 * Maps events to flow of data
 */
fun <Key : Any, Data : Any, NewData : Any> PagingDataChangesMedium<Key, Data>.mapDataFlowPresenter(
    configuration: BasicPresenterConfiguration<Key, NewData> = BasicPresenterConfiguration(),
    transform: (PageChangedEvent<Key, Data>) -> Flow<List<NewData?>>
) = MappingFlowPagingDataChangesMedium(
    pagingDataChangesMedium = this,
    transform = transform
).pagingDataPresenter(configuration)

/**
 * @param invalidateBehavior see [InvalidateBehavior]
 * @param eventsBatchingDurationMsProvider provider of duration of throttle window
 * @param shouldBatchAddPagesEvents defines whether should throttle add pages events or not
 * @param shouldBufferEvents see [BufferEventsDataChangesMedium]
 * @param transform mapping lambda
 */
fun <Key : Any, Data : Any, NewData : Any> PagingDataChangesMedium<Key, Data>.mapDataPresenter(
    configuration: BasicPresenterConfiguration<Key, NewData> = BasicPresenterConfiguration(),
    eventsBatchingDurationMsProvider: () -> Long = { 0 },
    shouldBatchAddPagesEvents: Boolean = false,
    shouldBufferEvents: Boolean = false,
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
).pagingDataPresenter(configuration)

/**
 * Maps events to flow of data
 */
fun <Key : Any, Data : Any, NewData : Any> PagingDataChangesMedium<Key, Data>.mapDataFlowPresenter(
    configuration: BasicPresenterConfiguration<Key, NewData> = BasicPresenterConfiguration(),
    eventsBatchingDurationMsProvider: () -> Long = { 0 },
    shouldBatchAddPagesEvents: Boolean = false,
    shouldBufferEvents: Boolean = false,
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
).pagingDataPresenter(configuration)

/**
 * Creates simple presenter, which builds list from pages
 * @param invalidateBehavior behavior of invalidate, by default it clears list after new value received, it means that list wouldn't blink when invalidate happens
 * @see InvalidateBehavior
 */
fun <Key : Any, Data : Any> PagingDataChangesMedium<Key, Data>.pagingDataPresenter(
    configuration: BasicPresenterConfiguration<Key, Data> = BasicPresenterConfiguration()
) = BasicBuildListPagingPresenter(
    pagingDataChangesMedium = this,
    presenterConfiguration = configuration
)

/**
 * @param invalidateBehavior see [InvalidateBehavior]
 * @param eventsBatchingDurationMsProvider provider of duration of throttle window
 * @param shouldBatchAddPagesEvents defines whether should throttle add pages events or not
 * @param shouldBufferEvents see [BufferEventsDataChangesMedium]
 */
fun <Key : Any, Data : Any> PagingDataChangesMedium<Key, Data>.pagingDataPresenter(
    configuration: BasicPresenterConfiguration<Key, Data> = BasicPresenterConfiguration(),
    eventsBatchingDurationMsProvider: () -> Long = { 0 },
    shouldBatchAddPagesEvents: Boolean = false,
    shouldBufferEvents: Boolean = false,
) = BatchingPagingDataChangesMedium(
    if (shouldBufferEvents) BufferEventsDataChangesMedium(this) else this,
    eventsBatchingDurationMsProvider = eventsBatchingDurationMsProvider,
    shouldBatchAddPagesEvents = shouldBatchAddPagesEvents
).pagingDataPresenter(configuration)

fun <Key : Any, Data : Any, NewData : Any> PagingDataChangesMedium<Key, Data>.compositeDataPresenter(
    configuration: BasicPresenterConfiguration<Key, NewData> = BasicPresenterConfiguration(),
    shouldBufferEvents: Boolean = false,
    builder: CompositePagingDataChangesMediumBuilder<Key, Data, NewData>.() -> Unit
) = CompositePagingDataChangesMediumBuilder.build(
    if (shouldBufferEvents) BufferEventsDataChangesMedium(this) else this,
    builder = builder
).pagingDataPresenter(configuration)

fun <Key : Any, Data : Any, NewData : Any> PagingDataChangesMedium<Key, Data>.compositeDataPresenter(
    configuration: BasicPresenterConfiguration<Key, NewData> = BasicPresenterConfiguration(),
    eventsBatchingDurationMsProvider: () -> Long = { 0 },
    shouldBatchAddPagesEvents: Boolean = false,
    shouldBufferEvents: Boolean = false,
    builder: CompositePagingDataChangesMediumBuilder<Key, Data, NewData>.() -> Unit
) = CompositePagingDataChangesMediumBuilder.build(
    BatchingPagingDataChangesMedium(
        if (shouldBufferEvents) BufferEventsDataChangesMedium(this) else this,
        eventsBatchingDurationMsProvider = eventsBatchingDurationMsProvider,
        shouldBatchAddPagesEvents = shouldBatchAddPagesEvents
    ), builder = builder
).pagingDataPresenter(configuration)