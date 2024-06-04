package ru.snowmaze.pagingflow.diff

import ru.snowmaze.pagingflow.diff.mediums.BufferEventsDataChangesMedium
import ru.snowmaze.pagingflow.diff.mediums.PagingDataChangesMedium

/**
 * @see BufferEventsDataChangesMedium
 */
fun <Key : Any, Data : Any> PagingDataChangesMedium<Key, Data>.bufferEvents(
) = BufferEventsDataChangesMedium(this)