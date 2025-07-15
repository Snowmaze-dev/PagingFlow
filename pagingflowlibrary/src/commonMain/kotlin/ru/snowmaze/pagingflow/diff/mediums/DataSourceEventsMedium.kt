package ru.snowmaze.pagingflow.diff.mediums

import ru.snowmaze.pagingflow.diff.PagingEventsListener
import ru.snowmaze.pagingflow.diff.PagingEvent
import ru.snowmaze.pagingflow.diff.EventFromDataSource

class DataSourceEventsMedium<Key : Any, Data : Any, NewData : Any>(
    dataChangesMedium: PagingEventsMedium<Key, Data>,
    private val dataSourceIndex: Int,
    override val config: PagingEventsMediumConfig = dataChangesMedium.config,
) : SubscribeForChangesEventsMedium<Key, Data, NewData>(dataChangesMedium),
    PagingEventsListener<Key, Data> {

    override fun getChangesCallback() = this

    private fun mapEvent(
        event: PagingEvent<Key, Data>
    ): PagingEvent<Key, NewData>? {
        return (if (event is EventFromDataSource<*, *> &&
            event.sourceIndex == dataSourceIndex
        ) {
            event.copyWithNewPositionData(
                0,
                pageIndex = event.pageIndexInSource,
                pageIndexInSource = event.pageIndexInSource
            )
        } else if (event !is EventFromDataSource<*, *>) event
        else null) as? PagingEvent<Key, NewData>
    }

    override suspend fun onEvents(events: List<PagingEvent<Key, Data>>) {
        notifyOnEvents(events.mapNotNullTo(ArrayList(events.count {
            (it as? EventFromDataSource<*, *>)?.sourceIndex == dataSourceIndex
        })) { mapEvent(it) })
    }

    override suspend fun onEvent(event: PagingEvent<Key, Data>) {
        mapEvent(event)?.let { notifyOnEvent(it) }
    }
}