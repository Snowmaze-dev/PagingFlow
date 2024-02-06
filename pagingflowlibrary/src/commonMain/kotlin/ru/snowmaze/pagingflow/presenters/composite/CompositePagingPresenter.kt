package ru.snowmaze.pagingflow.presenters.composite

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ru.snowmaze.pagingflow.diff.DataChangedCallback
import ru.snowmaze.pagingflow.diff.DataChangedEvent
import ru.snowmaze.pagingflow.diff.InvalidateEvent
import ru.snowmaze.pagingflow.diff.mediums.DataChangesMedium
import ru.snowmaze.pagingflow.diff.mediums.handle
import ru.snowmaze.pagingflow.internal.CompositePresenterSection
import ru.snowmaze.pagingflow.presenters.BuildListPagingPresenter
import ru.snowmaze.pagingflow.presenters.InvalidateBehavior

open class CompositePagingPresenter<Key : Any, Data : Any> internal constructor(
    dataChangesMedium: DataChangesMedium<Key, Data>,
    private val sections: List<CompositePresenterSection<Data>>,
    invalidateBehavior: InvalidateBehavior,
    coroutineScope: CoroutineScope,
    processingDispatcher: CoroutineDispatcher
) : BuildListPagingPresenter<Key, Data>(invalidateBehavior, coroutineScope, processingDispatcher) {

    private val dataSourcesSections = mutableMapOf<Int, CompositePresenterSection.DataSourceSection<Data>>()

    init {
        for (section in sections) {
            if (section is CompositePresenterSection.DataSourceSection<Data>) {
                dataSourcesSections[section.dataSourceIndex ?: continue] = section
            }
        }

        val applyEvent: (event: DataChangedEvent<Key, Data>) -> Unit = { event ->
            event.handle(
                onPageAdded = { dataSourcesSections[it.sourceIndex]?.items?.set(it.pageIndex, it.items) },
                onPageChanged = { dataSourcesSections[it.sourceIndex]?.items?.set(it.pageIndex, it.items) },
                onPageRemovedEvent = { dataSourcesSections[it.sourceIndex]?.items?.remove(it.pageIndex) },
                onInvalidate = { onInvalidateInternal() }
            )
        }
        dataChangesMedium.addDataChangedCallback(object : DataChangedCallback<Key, Data> {

            override fun onEvents(events: List<DataChangedEvent<Key, Data>>) {
                updateData {
                    var lastEvent: DataChangedEvent<Key, Data>? = null
                    for (event in events) {
                        applyEvent(event)
                        lastEvent = event
                    }
                    lastEvent !is InvalidateEvent<*, *>
                }
            }

            override fun onEvent(event: DataChangedEvent<Key, Data>) {
                updateData {
                    applyEvent(event)
                    event !is InvalidateEvent<*, *>
                }
            }
        })
    }

    protected open fun updateData(update: () -> Unit) {
        coroutineScope.launch(processingDispatcher) {
            update()
            buildList()
        }
    }

    override fun onInvalidateAdditionalAction() {
        for (section in sections) {
            section.items.clear()
        }
    }

    override fun buildListInternal(): List<Data?> {
        for (section in sections) {
            if (section is CompositePresenterSection.SimpleSection<Data>) {
                section.items[0] = section.itemsProvider()
            }
        }
        return buildList(sections.sumOf { section -> section.items.keys.sumOf {
            section.items.getValue(it).size
        } }) {
            for (section in sections) {
                for (key in section.items.keys.sorted()) {
                    addAll(section.items[key] ?: continue)
                }
            }
        }
    }
}