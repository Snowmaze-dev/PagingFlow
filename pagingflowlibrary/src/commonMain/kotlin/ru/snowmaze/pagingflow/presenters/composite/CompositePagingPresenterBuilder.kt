package ru.snowmaze.pagingflow.presenters.composite

import ru.snowmaze.pagingflow.diff.mediums.PagingDataChangesMedium
import ru.snowmaze.pagingflow.diff.mediums.DataChangesMediumConfig
import ru.snowmaze.pagingflow.internal.CompositePresenterSection
import ru.snowmaze.pagingflow.presenters.InvalidateBehavior

class CompositePagingPresenterBuilder<Key : Any, Data : Any>(
    private val pagingDataChangesMedium: PagingDataChangesMedium<Key, Data>,
    private val invalidateBehavior: InvalidateBehavior,
    private val config: DataChangesMediumConfig = pagingDataChangesMedium.config
) {

    companion object {

        fun <Key : Any, Data : Any> create(
            pagingDataChangesMedium: PagingDataChangesMedium<Key, Data>,
            invalidateBehavior: InvalidateBehavior = InvalidateBehavior.INVALIDATE_AND_SEND_EMPTY_LIST_BEFORE_NEXT_VALUE,
            config: DataChangesMediumConfig = pagingDataChangesMedium.config,
            builder: CompositePagingPresenterBuilder<Key, Data>.() -> Unit
        ) = CompositePagingPresenterBuilder(
            pagingDataChangesMedium = pagingDataChangesMedium,
            invalidateBehavior = invalidateBehavior,
            config = config
        ).apply(builder).build()
    }

    private val sections = mutableListOf<CompositePresenterSection<Data>>()

    fun section(itemsProvider: () -> List<Data>) {
        sections.add(CompositePresenterSection.SimpleSection(itemsProvider))
    }

    fun dataSourceSection(dataSourceIndex: Int) {
        sections.add(CompositePresenterSection.DataSourceSection(dataSourceIndex))
    }

    fun build() = CompositePagingPresenter(
        pagingDataChangesMedium = pagingDataChangesMedium,
        sections = sections.toList(),
        invalidateBehavior = invalidateBehavior,
        config = config
    )
}