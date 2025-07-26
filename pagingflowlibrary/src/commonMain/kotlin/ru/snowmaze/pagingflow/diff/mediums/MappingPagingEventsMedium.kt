package ru.snowmaze.pagingflow.diff.mediums

import ru.snowmaze.pagingflow.diff.PagingEventsListener
import ru.snowmaze.pagingflow.diff.PagingEvent
import ru.snowmaze.pagingflow.diff.InvalidateEvent
import ru.snowmaze.pagingflow.diff.PageAddedEvent
import ru.snowmaze.pagingflow.diff.PageChangedEvent
import ru.snowmaze.pagingflow.diff.PageRemovedEvent
import ru.snowmaze.pagingflow.diff.handle

class MappingPagingEventsMedium<Key : Any, Data : Any, NewData : Any>(
    pagingEventsMedium: PagingEventsMedium<Key, Data>,
    override val config: PagingEventsMediumConfig = pagingEventsMedium.config,
    private val transformOtherEvents: (
        suspend (PagingEvent<Key, Data>) -> PagingEvent<Key, NewData>?
    )? = null,
    private val transform: suspend (PageChangedEvent<Key, Data>) -> List<NewData?>,
) : SubscribeForChangesEventsMedium<Key, Data, NewData>(pagingEventsMedium),
    PagingEventsListener<Key, Data> {

    override suspend fun onEvents(events: List<PagingEvent<Key, Data>>) {
        notifyOnEvents(
            events.mapNotNullTo(ArrayList(events.size)) { handleEvent(it) }
        )
    }

    override suspend fun onEvent(event: PagingEvent<Key, Data>) {
        notifyOnEvent(handleEvent(event) ?: return)
    }

    override fun getChangesCallback() = this

    private suspend inline fun handleEvent(
        event: PagingEvent<Key, Data>
    ) = event.handle(
        onPageAdded = {
            PageAddedEvent(
                key = it.key,
                sourceIndex = it.sourceIndex,
                pageIndex = it.pageIndex,
                pageIndexInSource = it.pageIndexInSource,
                items = transform(it) as List<NewData>,
                params = it.params,
                changeType = it.changeType
            )
        },
        onPageChanged = {
            PageChangedEvent(
                key = it.key,
                sourceIndex = it.sourceIndex,
                pageIndex = it.pageIndex,
                pageIndexInSource = it.pageIndexInSource,
                items = transform(it),
                params = it.params,
                previousItemCount = it.previousItemCount,
                changeType = it.changeType
            )
        },
        onPageRemovedEvent = { it as PageRemovedEvent<Key, NewData> },
        onInvalidate = { it as InvalidateEvent<Key, NewData> },
        onElse = { transformOtherEvents?.invoke(it) }
    )
}