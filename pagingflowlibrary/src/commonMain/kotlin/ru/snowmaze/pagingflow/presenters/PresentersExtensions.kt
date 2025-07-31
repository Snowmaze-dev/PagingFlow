package ru.snowmaze.pagingflow.presenters

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import ru.snowmaze.pagingflow.diff.PagingEvent
import ru.snowmaze.pagingflow.diff.PageChangedEvent
import ru.snowmaze.pagingflow.diff.batchEventsMedium
import ru.snowmaze.pagingflow.diff.bufferEvents
import ru.snowmaze.pagingflow.diff.mapDataToFlowMedium
import ru.snowmaze.pagingflow.diff.mapDataMedium
import ru.snowmaze.pagingflow.diff.mediums.BufferEventsEventsMedium
import ru.snowmaze.pagingflow.diff.mediums.PagingEventsMedium
import ru.snowmaze.pagingflow.diff.mediums.PagingEventsMediumConfig

/**
 * Creates simple presenter, which builds list from pages
 * @param configuration configuration for presenter
 */
fun <Key : Any, Data : Any> PagingEventsMedium<Key, Data>.pagingDataPresenter(
    configuration: BasicPresenterConfiguration<Key, Data> = BasicPresenterConfiguration()
) = BasicBuildListPagingPresenter(
    pagingEventsMedium = this,
    presenterConfiguration = configuration
)

inline fun <Key : Any, Data : Any> PagingDataPresenter<Key, Data>.statePresenter(
    coroutineScope: CoroutineScope = if (this is BasicBuildListPagingPresenter<*, *>) config.coroutineScope
    else throw IllegalArgumentException("No coroutine scope provided."),
    sharingStarted: SharingStarted = SharingStarted.WhileSubscribed(5000)
): StatePagingDataPresenter<Key, Data> = BasicStatePagingDataPresenter(
    presenter = this,
    coroutineScope = coroutineScope,
    sharingStarted = sharingStarted
)

inline fun <Key : Any, Data : Any> PagingEventsMedium<Key, Data>.statePresenter(
    configuration: BasicPresenterConfiguration<Key, Data> = BasicPresenterConfiguration(),
    mediumConfig: PagingEventsMediumConfig = this.config,
    sharingStarted: SharingStarted = SharingStarted.WhileSubscribed(5000)
) = pagingDataPresenter(configuration).statePresenter(
    coroutineScope = mediumConfig.coroutineScope,
    sharingStarted = sharingStarted
)

/**
 * Creates mapping presenter, which maps only changed pages and have batching mechanism
 * @param transform transforms event to list of data
 * @see pagingDataPresenter for arguments docs on arguments
 */
fun <Key : Any, Data : Any, NewData : Any> PagingEventsMedium<Key, Data>.mapDataPresenter(
    configuration: BasicPresenterConfiguration<Key, NewData> = BasicPresenterConfiguration(),
    transform: suspend (PageChangedEvent<Key, Data>) -> List<NewData?>
) = mapDataMedium(transform).pagingDataPresenter(configuration)

/**
 * Maps events to flow of data
 */
fun <Key : Any, Data : Any, NewData : Any> PagingEventsMedium<Key, Data>.mapDataFlowPresenter(
    configuration: BasicPresenterConfiguration<Key, NewData> = BasicPresenterConfiguration(),
    transform: (PageChangedEvent<Key, Data>) -> Flow<List<NewData?>>
) = mapDataToFlowMedium(transform).pagingDataPresenter(configuration)

/**
 * @param eventsBatchingDurationMsProvider provider of duration of batching window
 * @param shouldBatchAddPagesEvents defines whether should batching add pages events or not
 * @param shouldBufferEvents see [BufferEventsEventsMedium]
 * @param transform mapping lambda
 */
fun <Key : Any, Data : Any, NewData : Any> PagingEventsMedium<Key, Data>.mapDataPresenter(
    configuration: BasicPresenterConfiguration<Key, NewData> = BasicPresenterConfiguration(),
    eventsBatchingDurationMsProvider: (List<PagingEvent<Key, NewData>>) -> Long = { 0 },
    shouldBatchAddPagesEvents: Boolean = false,
    shouldBufferEvents: Boolean = false,
    transform: suspend (PageChangedEvent<Key, Data>) -> List<NewData?>
) = mapDataMedium(transform).also {
    if (shouldBufferEvents) it.bufferEvents()
    else it
}.batchEventsMedium(
    eventsBatchingDurationMsProvider = eventsBatchingDurationMsProvider,
    shouldBatchAddPagesEvents = shouldBatchAddPagesEvents
).pagingDataPresenter(configuration)

/**
 * Maps events to flow of data
 */
fun <Key : Any, Data : Any, NewData : Any> PagingEventsMedium<Key, Data>.mapDataFlowPresenter(
    configuration: BasicPresenterConfiguration<Key, NewData> = BasicPresenterConfiguration(),
    eventsBatchingDurationMsProvider: (List<PagingEvent<Key, NewData>>) -> Long = { 0 },
    shouldBatchAddPagesEvents: Boolean = false,
    shouldBufferEvents: Boolean = false,
    transform: (PageChangedEvent<Key, Data>) -> Flow<List<NewData?>>
) = mapDataToFlowMedium(transform).also {
    if (shouldBufferEvents) it.bufferEvents()
    else it
}.batchEventsMedium(
    eventsBatchingDurationMsProvider = eventsBatchingDurationMsProvider,
    shouldBatchAddPagesEvents = shouldBatchAddPagesEvents
).pagingDataPresenter(configuration)

/**
 * @param eventsBatchingDurationMsProvider provider of duration of batching window
 * @param shouldBatchAddPagesEvents defines whether should batching add pages events or not
 * @param shouldBufferEvents see [BufferEventsEventsMedium]
 */
fun <Key : Any, Data : Any> PagingEventsMedium<Key, Data>.pagingDataPresenter(
    configuration: BasicPresenterConfiguration<Key, Data> = BasicPresenterConfiguration(),
    eventsBatchingDurationMsProvider: (List<PagingEvent<Key, Data>>) -> Long = { 0 },
    shouldBatchAddPagesEvents: Boolean = false,
    shouldBufferEvents: Boolean = false,
) = (if (shouldBufferEvents) bufferEvents() else this).batchEventsMedium(
    eventsBatchingDurationMsProvider = eventsBatchingDurationMsProvider,
    shouldBatchAddPagesEvents = shouldBatchAddPagesEvents
).pagingDataPresenter(configuration)