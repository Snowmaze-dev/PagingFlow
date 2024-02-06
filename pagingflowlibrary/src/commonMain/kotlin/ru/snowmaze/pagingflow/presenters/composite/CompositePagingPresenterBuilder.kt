package ru.snowmaze.pagingflow.presenters.composite

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import ru.snowmaze.pagingflow.diff.mediums.DataChangesMedium
import ru.snowmaze.pagingflow.internal.CompositePresenterSection
import ru.snowmaze.pagingflow.presenters.InvalidateBehavior
import ru.snowmaze.pagingflow.sources.DataSource
import kotlin.reflect.KClass

class CompositePagingPresenterBuilder<Key : Any, Data : Any>(
    private val dataChangesMedium: DataChangesMedium<Key, Data>,
    private val invalidateBehavior: InvalidateBehavior,
    private val coroutineScope: CoroutineScope,
    private val processingDispatcher: CoroutineDispatcher
) {

    companion object {
        fun <Key : Any, Data : Any> create(
            dataChangesMedium: DataChangesMedium<Key, Data>,
            invalidateBehavior: InvalidateBehavior = InvalidateBehavior.INVALIDATE_AND_SEND_EMPTY_LIST_BEFORE_NEXT_VALUE,
            coroutineScope: CoroutineScope,
            processingDispatcher: CoroutineDispatcher = Dispatchers.Default,
            builder: CompositePagingPresenterBuilder<Key, Data>.() -> Unit
        ) = CompositePagingPresenterBuilder(
            dataChangesMedium = dataChangesMedium,
            invalidateBehavior = invalidateBehavior,
            coroutineScope = coroutineScope,
            processingDispatcher = processingDispatcher
        ).apply(builder).build()
    }

    private val sections = mutableListOf<CompositePresenterSection<Data>>()

    fun section(itemsProvider: () -> List<Data>) {
        sections.add(CompositePresenterSection.SimpleSection(itemsProvider))
    }

    fun dataSourceSection(dataSourceIndex: Int) {
        sections.add(CompositePresenterSection.DataSourceSection(dataSourceIndex))
    }

    fun dataSourceSection(clazz: KClass<out DataSource<Key, Data, *>>) {
        sections.add(CompositePresenterSection.DataSourceSection(dataSourceClass = clazz))
    }

    fun build() = CompositePagingPresenter(
        dataChangesMedium = dataChangesMedium,
        sections = sections.toList(),
        invalidateBehavior = invalidateBehavior,
        coroutineScope = coroutineScope,
        processingDispatcher = processingDispatcher
    )
}

inline fun <Key : Any, Data : Any, reified Source : DataSource<Key, Data, *>> CompositePagingPresenterBuilder<Key, Data>.dataSourceSection() {
    dataSourceSection(Source::class)
}