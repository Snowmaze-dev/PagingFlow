package ru.snowmaze.pagingflow.diff.mediums

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import ru.snowmaze.pagingflow.diff.DataChangedCallback
import ru.snowmaze.pagingflow.diff.DataChangedEvent
import ru.snowmaze.pagingflow.diff.InvalidateEvent
import ru.snowmaze.pagingflow.diff.PageAddedEvent
import ru.snowmaze.pagingflow.diff.PageChangedEvent
import ru.snowmaze.pagingflow.diff.PageRemovedEvent

/**
 * An interface that provides changes of paged data.
 */
interface PagingDataChangesMedium<Key : Any, Data : Any> {

    val config: DataChangesMediumConfig

    /**
     * Adds callback which called when data has been changed
     */
    fun addDataChangedCallback(callback: DataChangedCallback<Key, Data>)

    /**
     * Removes data changed callback
     */
    fun removeDataChangedCallback(callback: DataChangedCallback<Key, Data>)
}

class DataChangesMediumConfig(
    val coroutineScope: CoroutineScope,
    val processingDispatcher: CoroutineDispatcher
)

inline fun <Key : Any, Data : Any, T : Any> DataChangedEvent<Key, Data>.handle(
    onPageAdded: (PageAddedEvent<Key, Data>) -> T?,
    onPageChanged: (PageChangedEvent<Key, Data>) -> T?,
    onPageRemovedEvent: (PageRemovedEvent<Key, Data>) -> T?,
    onInvalidate: (InvalidateEvent<Key, Data>) -> T?,
    onElse: ((DataChangedEvent<Key, Data>) -> T?) = { null }
): T? {
    return when (this) {
        is PageAddedEvent -> onPageAdded(this)
        is PageChangedEvent -> onPageChanged(this)
        is PageRemovedEvent -> onPageRemovedEvent(this)
        is InvalidateEvent -> onInvalidate(this)
        else -> onElse(this)
    }
}