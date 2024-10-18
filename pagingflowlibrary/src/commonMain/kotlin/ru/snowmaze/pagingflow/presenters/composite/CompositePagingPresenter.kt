package ru.snowmaze.pagingflow.presenters.composite

import kotlinx.coroutines.withContext
import ru.snowmaze.pagingflow.diff.DataChangedCallback
import ru.snowmaze.pagingflow.diff.DataChangedEvent
import ru.snowmaze.pagingflow.diff.InvalidateEvent
import ru.snowmaze.pagingflow.diff.PageAddedEvent
import ru.snowmaze.pagingflow.diff.PageChangedEvent
import ru.snowmaze.pagingflow.diff.mediums.DataChangesMediumConfig
import ru.snowmaze.pagingflow.diff.mediums.PagingDataChangesMedium
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

        val applyEvent: suspend (event: DataChangedEvent<Key, Data>) -> Unit = { event ->
            event.handle(
                onPageAdded = {
                    dataSourcesSections[it.sourceIndex]?.items?.set(it.pageIndex, it)
                },
                onPageChanged = {
                    dataSourcesSections[it.sourceIndex]?.items?.set(it.pageIndex, it)
                },
                onPageRemovedEvent = {
                    dataSourcesSections[it.sourceIndex]?.items?.remove(it.pageIndex)
                },
                onInvalidate = {
                    onInvalidateInternal(specifiedInvalidateBehavior = it.invalidateBehavior)
                }
            )
        }
        pagingDataChangesMedium.addDataChangedCallback(object : DataChangedCallback<Key, Data> {

            override suspend fun onEvents(events: List<DataChangedEvent<Key, Data>>) {
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

    override suspend fun buildListInternal(): List<Data?> {
        return buildList(sections.sumOf { section ->
            section.items.keys.sumOf {
                section.items.getValue(it).items.size
            }
        }) {
            var newStartIndex = 0
            for (section in sections) {
                for (key in section.items.keys.sorted()) {
                    val lastEvent = section.items[key]
                    addAll(lastEvent?.items ?: break)
                    if (lastEvent.changeType == PageChangedEvent.ChangeType.CHANGE_TO_NULLS) {
                        newStartIndex += lastEvent.items.size
                    }
                }
            }
            _startIndex = newStartIndex
        }
    }

    private fun updateSectionsData() {
        for (section in sections) {
            if (section is CompositePresenterSection.SimpleSection<Data>) {
                section.items[0] = PageAddedEvent(
                    key = null,
                    pageIndex = -1,
                    sourceIndex = -1,
                    items = section.itemsProvider()
                )
            }
        }
    }

    protected open suspend fun updateData(update: suspend () -> List<DataChangedEvent<Key, Data>>) {
        withContext(processingDispatcher) {
            val events = update()
            if (events.lastOrNull() !is InvalidateEvent<*, *>) buildList(events)
        }
    }
}