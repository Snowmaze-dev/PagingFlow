package ru.snowmaze.pagingflow.diff.mediums

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import ru.snowmaze.pagingflow.diff.DataChangedCallback

/**
 * An interface that provides a way to subscribe to changes of paged data.
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
    fun removeDataChangedCallback(callback: DataChangedCallback<Key, Data>): Boolean
}

class DataChangesMediumConfig(
    val coroutineScope: CoroutineScope,
    val processingDispatcher: CoroutineDispatcher
)