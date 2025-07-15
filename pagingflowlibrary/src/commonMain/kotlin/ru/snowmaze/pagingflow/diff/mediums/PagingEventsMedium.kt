package ru.snowmaze.pagingflow.diff.mediums

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import ru.snowmaze.pagingflow.diff.PagingEventsListener

/**
 * An interface that provides a way to subscribe to changes of paged data.
 */
interface PagingEventsMedium<Key : Any, Data : Any> {

    val config: PagingEventsMediumConfig

    /**
     * Adds listener which called when paging events occurred
     */
    fun addPagingEventsListener(listener: PagingEventsListener<Key, Data>)

    /**
     * Removes paging events listener
     */
    fun removePagingEventsListener(listener: PagingEventsListener<Key, Data>): Boolean
}

class PagingEventsMediumConfig(
    val coroutineScope: CoroutineScope,
    val processingDispatcher: CoroutineDispatcher
)