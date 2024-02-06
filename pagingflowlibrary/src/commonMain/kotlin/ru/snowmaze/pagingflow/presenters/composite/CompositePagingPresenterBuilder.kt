package ru.snowmaze.pagingflow.presenters.composite

import ru.snowmaze.pagingflow.diff.mediums.DataChangesMedium
import ru.snowmaze.pagingflow.diff.mediums.DataChangesMediumConfig
import ru.snowmaze.pagingflow.internal.CompositePresenterSection
import ru.snowmaze.pagingflow.presenters.InvalidateBehavior

class CompositePagingPresenterBuilder<Key : Any, Data : Any>(
    private val dataChangesMedium: DataChangesMedium<Key, Data>,
    private val invalidateBehavior: InvalidateBehavior,
    private val config: DataChangesMediumConfig = dataChangesMedium.config
) {

    companion object {
        fun <Key : Any, Data : Any> create(
            dataChangesMedium: DataChangesMedium<Key, Data>,
            invalidateBehavior: InvalidateBehavior = InvalidateBehavior.INVALIDATE_AND_SEND_EMPTY_LIST_BEFORE_NEXT_VALUE,
            config: DataChangesMediumConfig = dataChangesMedium.config,
            builder: CompositePagingPresenterBuilder<Key, Data>.() -> Unit
        ) = CompositePagingPresenterBuilder(
            dataChangesMedium = dataChangesMedium,
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
        dataChangesMedium = dataChangesMedium,
        sections = sections.toList(),
        invalidateBehavior = invalidateBehavior,
        config = config
    )
}