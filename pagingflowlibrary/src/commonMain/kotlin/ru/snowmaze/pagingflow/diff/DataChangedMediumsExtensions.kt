package ru.snowmaze.pagingflow.diff

import kotlinx.coroutines.flow.Flow
import ru.snowmaze.pagingflow.diff.mediums.BatchingPagingEventsMedium
import ru.snowmaze.pagingflow.diff.mediums.BufferEventsEventsMedium
import ru.snowmaze.pagingflow.diff.mediums.MappingFlowPagingEventsMedium
import ru.snowmaze.pagingflow.diff.mediums.MappingPagingEventsMedium
import ru.snowmaze.pagingflow.diff.mediums.PagingEventsMedium

/**
 * @see BufferEventsEventsMedium
 */
inline fun <Key : Any, Data : Any> PagingEventsMedium<Key, Data>.bufferEvents(
) = BufferEventsEventsMedium(this)

inline fun <Key : Any, Data : Any, NewData : Any> PagingEventsMedium<Key, Data>.mapDataMedium(
    noinline transform: suspend (PageChangedEvent<Key, Data>) -> List<NewData?>
) = MappingPagingEventsMedium(
    pagingEventsMedium = this,
    transform = transform
)

inline fun <Key : Any, Data : Any, NewData : Any> PagingEventsMedium<Key, Data>.mapDataToFlowMedium(
    noinline transform: (PageChangedEvent<Key, Data>) -> Flow<List<NewData?>>
) = MappingFlowPagingEventsMedium(
    pagingEventsMedium = this,
    transform = transform
)

/**
 * @param eventsBatchingDurationMsProvider provider of duration of batching window
 *
 * @param shouldBatchAddPagesEvents defines whether should throttle add pages events or not
 * speeds up adding new ages if enabled
 *
 */
inline fun <Key : Any, Data : Any> PagingEventsMedium<Key, Data>.batchEventsMedium(
    noinline eventsBatchingDurationMsProvider: (List<PagingEvent<Key, Data>>) -> Long = { 0 },
    shouldBatchAddPagesEvents: Boolean = false,
) = BatchingPagingEventsMedium(
    pagingEventsMedium = this,
    eventsBatchingDurationMsProvider = eventsBatchingDurationMsProvider,
    shouldBatchAddPagesEvents = shouldBatchAddPagesEvents
)