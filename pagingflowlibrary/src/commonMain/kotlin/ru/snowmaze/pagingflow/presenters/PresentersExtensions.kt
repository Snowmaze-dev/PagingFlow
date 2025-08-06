package ru.snowmaze.pagingflow.presenters

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import ru.snowmaze.pagingflow.diff.InvalidateEvent
import ru.snowmaze.pagingflow.diff.PageAddedEvent
import ru.snowmaze.pagingflow.diff.PagingEvent
import ru.snowmaze.pagingflow.diff.PageChangedEvent
import ru.snowmaze.pagingflow.diff.batchEventsMedium
import ru.snowmaze.pagingflow.diff.bufferEvents
import ru.snowmaze.pagingflow.diff.mapChangesToFlowMedium
import ru.snowmaze.pagingflow.diff.mapChangesMedium
import ru.snowmaze.pagingflow.diff.mediums.BufferEventsMedium
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
) = mapChangesMedium(transform).pagingDataPresenter(configuration)

/**
 * Maps events to flow of data
 */
fun <Key : Any, Data : Any, NewData : Any> PagingEventsMedium<Key, Data>.mapDataFlowPresenter(
    configuration: BasicPresenterConfiguration<Key, NewData> = BasicPresenterConfiguration(),
    transform: (PageChangedEvent<Key, Data>) -> Flow<List<NewData?>>
) = mapChangesToFlowMedium(transform).pagingDataPresenter(configuration)

/**
 * @param eventsBatchingDurationMsProvider provider of duration of batching window
 * @param shouldBatchEvent lambda which lets you define that should batch medium batch some events or not
 * @param shouldBufferEvents see [BufferEventsMedium]
 * @param transform mapping lambda
 */
fun <Key : Any, Data : Any, NewData : Any> PagingEventsMedium<Key, Data>.mapDataPresenter(
    configuration: BasicPresenterConfiguration<Key, NewData> = BasicPresenterConfiguration(),
    eventsBatchingDurationMsProvider: (List<PagingEvent<Key, NewData>>) -> Long = { 0 },
    shouldBufferEvents: Boolean = false,
    shouldBatchEvent: ((PagingEvent<Key, NewData>) -> Boolean)? = {
        when (it) {
            is PageAddedEvent, is InvalidateEvent -> false
            else -> true
        }
    },
    transform: suspend (PageChangedEvent<Key, Data>) -> List<NewData?>
) = mapChangesMedium(transform).also {
    if (shouldBufferEvents) it.bufferEvents()
    else it
}.batchEventsMedium(
    eventsBatchingDurationMsProvider = eventsBatchingDurationMsProvider,
    shouldBatchEvent = shouldBatchEvent
).pagingDataPresenter(configuration)

/**
 * Maps events to flow of data
 */
fun <Key : Any, Data : Any, NewData : Any> PagingEventsMedium<Key, Data>.mapDataFlowPresenter(
    configuration: BasicPresenterConfiguration<Key, NewData> = BasicPresenterConfiguration(),
    eventsBatchingDurationMsProvider: (List<PagingEvent<Key, NewData>>) -> Long = { 0 },
    shouldBufferEvents: Boolean = false,
    shouldBatchEvent: ((PagingEvent<Key, NewData>) -> Boolean)? = {
        when (it) {
            is PageAddedEvent, is InvalidateEvent -> false
            else -> true
        }
    },
    transform: (PageChangedEvent<Key, Data>) -> Flow<List<NewData?>>
) = mapChangesToFlowMedium(transform).also {
    if (shouldBufferEvents) it.bufferEvents()
    else it
}.batchEventsMedium(
    eventsBatchingDurationMsProvider = eventsBatchingDurationMsProvider,
    shouldBatchEvent = shouldBatchEvent
).pagingDataPresenter(configuration)

/**
 * @param eventsBatchingDurationMsProvider provider of duration of batching window
 * @param shouldBatchEvent lambda which lets you define that should batch medium batch some events or not
 * @param shouldBufferEvents see [BufferEventsMedium]
 */
fun <Key : Any, Data : Any> PagingEventsMedium<Key, Data>.pagingDataPresenter(
    configuration: BasicPresenterConfiguration<Key, Data> = BasicPresenterConfiguration(),
    eventsBatchingDurationMsProvider: (List<PagingEvent<Key, Data>>) -> Long = { 0 },
    shouldBatchEvent: ((PagingEvent<Key, Data>) -> Boolean)? = {
        when (it) {
            is PageAddedEvent, is InvalidateEvent -> false
            else -> true
        }
    },
    shouldBufferEvents: Boolean = false,
) = (if (shouldBufferEvents) bufferEvents() else this).batchEventsMedium(
    eventsBatchingDurationMsProvider = eventsBatchingDurationMsProvider,
    shouldBatchEvent = shouldBatchEvent
).pagingDataPresenter(configuration)