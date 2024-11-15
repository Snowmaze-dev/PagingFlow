package ru.snowmaze.pagingflow.presenters.list

import ru.snowmaze.pagingflow.DelicatePagingApi
import ru.snowmaze.pagingflow.diff.mediums.BatchingPagingDataChangesMedium

/**
 * Should be used with care
 * it's preferable to use event batching [BatchingPagingDataChangesMedium] with this list builder
 * so using list from presenter wouldn't throw [ConcurrentModificationException] for short amount of time
 * while for example it being mapped to other list
 */
@DelicatePagingApi
class ReuseListDiffListBuildStrategy<Key : Any, Data : Any> : DiffListBuildStrategy<Key, Data>(
    reuseList = true
)