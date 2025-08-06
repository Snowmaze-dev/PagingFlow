package ru.snowmaze.pagingflow.diff

import kotlinx.coroutines.flow.Flow
import ru.snowmaze.pagingflow.diff.mediums.BatchingPagingEventsMedium
import ru.snowmaze.pagingflow.diff.mediums.BufferEventsMedium
import ru.snowmaze.pagingflow.diff.mediums.MapFlowPagingEventsMedium
import ru.snowmaze.pagingflow.diff.mediums.MapPagingEventsMedium
import ru.snowmaze.pagingflow.diff.mediums.PagingEventsMedium
import ru.snowmaze.pagingflow.diff.mediums.PagingSourceEventsMedium
import ru.snowmaze.pagingflow.diff.mediums.composite.CompositePagingDataChangesMediumBuilder

/**
 * @see BufferEventsMedium
 */
inline fun <Key : Any, Data : Any> PagingEventsMedium<Key, Data>.bufferEvents(
) = BufferEventsMedium(this)

inline fun <Key : Any, Data : Any, NewData : Any> PagingEventsMedium<Key, Data>.mapChangesMedium(
    noinline transform: suspend (PageChangedEvent<Key, Data>) -> List<NewData?>
) = MapPagingEventsMedium(
    pagingEventsMedium = this,
    transform = transform
)

inline fun <Key : Any, Data : Any, NewData : Any> PagingEventsMedium<Key, Data>.mapChangesToFlowMedium(
    noinline transform: (PageChangedEvent<Key, Data>) -> Flow<List<NewData?>>
) = MapFlowPagingEventsMedium(
    pagingEventsMedium = this,
    transform = transform
)

/**
 * @param eventsBatchingDurationMsProvider provider of duration of batching window
 *
 * @param shouldBatchEvent lambda which lets you define that should batch medium batch some events or not
 * speeds up adding new ages if enabled
 *
 */
inline fun <Key : Any, Data : Any> PagingEventsMedium<Key, Data>.batchEventsMedium(
    noinline eventsBatchingDurationMsProvider: (List<PagingEvent<Key, Data>>) -> Long = { 50L },
    noinline shouldBatchEvent: ((PagingEvent<Key, Data>) -> Boolean)? = {
        when (it) {
            is PageAddedEvent, is InvalidateEvent -> false
            else -> true
        }
    }
) = BatchingPagingEventsMedium(
    pagingEventsMedium = this,
    shouldBatch = shouldBatchEvent,
    eventsBatchingDurationMsProvider = eventsBatchingDurationMsProvider,
)

fun <Key : Any, Data : Any, NewData : Any> PagingEventsMedium<Key, Data>.compositeListMedium(
    builder: CompositePagingDataChangesMediumBuilder<Key, Data, NewData>.() -> Unit
) = CompositePagingDataChangesMediumBuilder.build(
    this,
    builder = builder
)

inline fun <Key : Any, Data : Any> PagingEventsMedium<Key, Data>.specificPagingSourceMedium(
    index: Int
) = PagingSourceEventsMedium<Key, Data, Data>(this, index)

inline fun <Key : Any, Data : Any, NewData: Any> PagingEventsMedium<Key, Data>.specificPagingSourceWithCastMedium(
    index: Int
) = PagingSourceEventsMedium<Key, Data, NewData>(this, index)