package ru.snowmaze.pagingflow.diff

import kotlinx.coroutines.flow.Flow
import ru.snowmaze.pagingflow.diff.mediums.BatchingPagingDataChangesMedium
import ru.snowmaze.pagingflow.diff.mediums.BufferEventsDataChangesMedium
import ru.snowmaze.pagingflow.diff.mediums.MappingFlowPagingDataChangesMedium
import ru.snowmaze.pagingflow.diff.mediums.MappingPagingDataChangesMedium
import ru.snowmaze.pagingflow.diff.mediums.PagingDataChangesMedium

/**
 * @see BufferEventsDataChangesMedium
 */
inline fun <Key : Any, Data : Any> PagingDataChangesMedium<Key, Data>.bufferEvents(
) = BufferEventsDataChangesMedium(this)

inline fun <Key : Any, Data : Any, NewData : Any> PagingDataChangesMedium<Key, Data>.mapDataMedium(
    noinline transform: suspend (PageChangedEvent<Key, Data>) -> List<NewData?>
) = MappingPagingDataChangesMedium(
    pagingDataChangesMedium = this,
    transform = transform
)

inline fun <Key : Any, Data : Any, NewData : Any> PagingDataChangesMedium<Key, Data>.mapDataToFlowMedium(
    noinline transform: (PageChangedEvent<Key, Data>) -> Flow<List<NewData?>>
) = MappingFlowPagingDataChangesMedium(
    pagingDataChangesMedium = this,
    transform = transform
)

/**
 * @param eventsBatchingDurationMsProvider provider of duration of batching window
 *
 * @param shouldBatchAddPagesEvents defines whether should throttle add pages events or not
 * speeds up adding new ages if enabled
 *
 */
inline fun <Key : Any, Data : Any> PagingDataChangesMedium<Key, Data>.batchEventsMedium(
    noinline eventsBatchingDurationMsProvider: (List<DataChangedEvent<Key, Data>>) -> Long = { 0 },
    shouldBatchAddPagesEvents: Boolean = false,
) = BatchingPagingDataChangesMedium(
    pagingDataChangesMedium = this,
    eventsBatchingDurationMsProvider = eventsBatchingDurationMsProvider,
    shouldBatchAddPagesEvents = shouldBatchAddPagesEvents
)