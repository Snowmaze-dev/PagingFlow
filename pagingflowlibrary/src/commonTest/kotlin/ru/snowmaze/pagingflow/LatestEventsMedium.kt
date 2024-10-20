package ru.snowmaze.pagingflow

import kotlinx.coroutines.flow.MutableStateFlow
import ru.snowmaze.pagingflow.diff.DataChangedCallback
import ru.snowmaze.pagingflow.diff.DataChangedEvent
import ru.snowmaze.pagingflow.diff.mediums.DataChangesMediumConfig
import ru.snowmaze.pagingflow.diff.mediums.DefaultPagingDataChangesMedium
import ru.snowmaze.pagingflow.diff.mediums.PagingDataChangesMedium

class LatestEventsMedium<Key : Any, Data : Any>(
    pagingDataChangesMedium: PagingDataChangesMedium<Key, Data>,
    override val config: DataChangesMediumConfig = pagingDataChangesMedium.config
) : DefaultPagingDataChangesMedium<Key, Data>() {

    var lastEvents = emptyList<DataChangedEvent<Key, Data>>()
    val eventsFlow = MutableStateFlow(emptyList<DataChangedEvent<Key, Data>>())

    init {
        pagingDataChangesMedium.addDataChangedCallback(
            object : DataChangedCallback<Key, Data> {
                override suspend fun onEvents(events: List<DataChangedEvent<Key, Data>>) {
                    lastEvents = events
                    eventsFlow.emit(events)
                }
            }
        )
    }
}