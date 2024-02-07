package ru.snowmaze.pagingflow.presenters.composite

import kotlinx.coroutines.launch
import ru.snowmaze.pagingflow.diff.DataChangedCallback
import ru.snowmaze.pagingflow.diff.DataChangedEvent
import ru.snowmaze.pagingflow.diff.InvalidateEvent
import ru.snowmaze.pagingflow.diff.mediums.PagingDataChangesMedium
import ru.snowmaze.pagingflow.diff.mediums.DataChangesMediumConfig
import ru.snowmaze.pagingflow.diff.mediums.handle
import ru.snowmaze.pagingflow.internal.CompositePresenterSection
import ru.snowmaze.pagingflow.presenters.BuildListPagingPresenter
import ru.snowmaze.pagingflow.presenters.InvalidateBehavior

open class CompositePagingPresenter<Key : Any, Data : Any> internal constructor(
    pagingDataChangesMedium: PagingDataChangesMedium<Key, Data>,
    private val sections: List<CompositePresenterSection<Data>>,
    invalidateBehavior: InvalidateBehavior,
    config: DataChangesMediumConfig = pagingDataChangesMedium.config
) : BuildListPagingPresenter<Key, Data>(
    invalidateBehavior = invalidateBehavior,
    coroutineScope = config.coroutineScope,
    processingDispatcher = config.processingDispatcher
) {

    private val dataSourcesSections =
        mutableMapOf<Int, CompositePresenterSection.DataSourceSection<Data>>()

    init {
        for (section in sections) {
            if (section is CompositePresenterSection.DataSourceSection<Data>) {
                dataSourcesSections[section.dataSourceIndex ?: continue] = section
            }
        }

        updateSectionsData()

        val applyEvent: (event: DataChangedEvent<Key, Data>) -> Unit = { event ->
            event.handle(
                onPageAdded = {
                    dataSourcesSections[it.sourceIndex]?.items?.set(it.pageIndex, it.items)
                },
                onPageChanged = {
                    dataSourcesSections[it.sourceIndex]?.items?.set(it.pageIndex, it.items)
                },
                onPageRemovedEvent = {
                    dataSourcesSections[it.sourceIndex]?.items?.remove(it.pageIndex)
                },
                onInvalidate = { onInvalidateInternal() }
            )
        }
        pagingDataChangesMedium.addDataChangedCallback(object : DataChangedCallback<Key, Data> {

            override fun onEvents(events: List<DataChangedEvent<Key, Data>>) {
                updateData {
                    for (event in events) {
                        applyEvent(event)
                    }
                    events
                }
            }
        })
    }

    override fun onInvalidateAdditionalAction() {
        for (section in sections) {
            section.items.clear()
        }
        updateSectionsData()
    }

    override fun buildListInternal(): List<Data?> {
        return buildList(sections.sumOf { section ->
            section.items.keys.sumOf {
                section.items.getValue(it).size
            }
        }) {
            for (section in sections) {
                for (key in section.items.keys.sorted()) {
                    addAll(section.items[key] ?: break)
                }
            }
        }
    }

    private fun updateSectionsData() {
        for (section in sections) {
            if (section is CompositePresenterSection.SimpleSection<Data>) {
                section.items[0] = section.itemsProvider()
            }
        }
    }

    protected open fun updateData(update: () -> List<DataChangedEvent<Key, Data>>) {
        coroutineScope.launch(processingDispatcher) {
            val events = update()
            if (events.lastOrNull() !is InvalidateEvent<*, *>) buildList(events)
        }
    }
}