package ru.snowmaze.pagingflow.source

import ru.snowmaze.pagingflow.LoadParams
import ru.snowmaze.pagingflow.result.LoadResult
import ru.snowmaze.pagingflow.result.result

class DefinedItemsTestPagingSource<T : Any>(
    val key: String, val items: List<T>
) : SegmentedPagingSource<T>() {

    override val totalCount = items.size

    override suspend fun loadData(
        loadParams: LoadParams<Int>,
        startIndex: Int,
        endIndex: Int
    ): LoadResult<Int, T> {
        return result(items.subList(startIndex, endIndex))
    }

    override fun hashCode() = key.hashCode()

    override fun equals(other: Any?) = if (other is DefinedItemsTestPagingSource<*>) key == other.key
    else false

    override fun toString(): String {
        return key
    }
}