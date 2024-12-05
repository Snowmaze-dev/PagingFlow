package ru.snowmaze.pagingflow.diff.mediums

import ru.snowmaze.pagingflow.diff.DataChangedCallback
import ru.snowmaze.pagingflow.diff.DataChangedEvent
import ru.snowmaze.pagingflow.diff.EventFromDataSource

class DataSourceDataChangesMedium<Key : Any, Data : Any, NewData : Any>(
    dataChangesMedium: PagingDataChangesMedium<Key, Data>,
    private val dataSourceIndex: Int,
    override val config: DataChangesMediumConfig = dataChangesMedium.config,
) : SubscribeForChangesDataChangesMedium<Key, Data, NewData>(dataChangesMedium),
    DataChangedCallback<Key, Data> {

    override fun getChangesCallback() = this

    private fun mapEvent(
        event: DataChangedEvent<Key, Data>
    ): DataChangedEvent<Key, NewData>? {
        return (if (event is EventFromDataSource<*, *> &&
            event.sourceIndex == dataSourceIndex
        ) {
            event.copyWithNewPositionData(
                0,
                pageIndex = event.pageIndexInSource,
                pageIndexInSource = event.pageIndexInSource
            )
        } else if (event !is EventFromDataSource<*, *>) event
        else null) as? DataChangedEvent<Key, NewData>
    }

    override suspend fun onEvents(events: List<DataChangedEvent<Key, Data>>) {
        notifyOnEvents(events.mapNotNullTo(ArrayList(events.count {
            (it as? EventFromDataSource<*, *>)?.sourceIndex == dataSourceIndex
        })) { mapEvent(it) })
    }

    override suspend fun onEvent(event: DataChangedEvent<Key, Data>) {
        mapEvent(event)?.let { notifyOnEvent(it) }
    }
}