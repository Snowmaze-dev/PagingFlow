package ru.snowmaze.pagingflow

import ru.snowmaze.pagingflow.result.LoadResult
import ru.snowmaze.pagingflow.result.simpleResult
import ru.snowmaze.pagingflow.sources.SegmentedDataSource

class DefinedItemsTestDataSource<T : Any>(
    val key: String, val items: List<T>
) : SegmentedDataSource<T, DefaultPagingStatus>() {

    override val totalCount = items.size

    override suspend fun loadData(
        loadParams: LoadParams<Int>,
        startIndex: Int,
        endIndex: Int
    ): LoadResult<Int, T, DefaultPagingStatus> {
        return simpleResult(items.subList(startIndex, endIndex))
    }

    override fun hashCode() = key.hashCode()

    override fun equals(other: Any?) = if (other is DefinedItemsTestDataSource<*>) key == other.key
    else false

    override fun toString(): String {
        return key
    }
}