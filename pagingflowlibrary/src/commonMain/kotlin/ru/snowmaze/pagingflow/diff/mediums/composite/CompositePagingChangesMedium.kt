package ru.snowmaze.pagingflow.diff.mediums.composite

import kotlinx.coroutines.launch
import ru.snowmaze.pagingflow.diff.DataChangedCallback
import ru.snowmaze.pagingflow.diff.DataChangedEvent
import ru.snowmaze.pagingflow.diff.InvalidateEvent
import ru.snowmaze.pagingflow.diff.PageAddedEvent
import ru.snowmaze.pagingflow.diff.PageChangedEvent
import ru.snowmaze.pagingflow.diff.PageRemovedEvent
import ru.snowmaze.pagingflow.diff.mediums.PagingDataChangesMedium
import ru.snowmaze.pagingflow.diff.mediums.DataChangesMediumConfig
import ru.snowmaze.pagingflow.diff.mediums.DefaultPagingDataChangesMedium
import ru.snowmaze.pagingflow.diff.mediums.handle
import ru.snowmaze.pagingflow.internal.CompositePresenterSection
import ru.snowmaze.pagingflow.internal.CompositePresenterSection.SimpleSection
import ru.snowmaze.pagingflow.internal.CompositePresenterSection.FlowSimpleSection

class CompositePagingChangesMedium<Key : Any, Data : Any> internal constructor(
    pagingDataChangesMedium: PagingDataChangesMedium<Key, Data>,
    private val sections: List<CompositePresenterSection<Data>>,
    override val config: DataChangesMediumConfig = pagingDataChangesMedium.config,
) : DefaultPagingDataChangesMedium<Key, Data>() {

    // absolute source index to prepend simple pages count
    private val sourcesIndexShift = mutableMapOf<Int, Int>()
    private val simplePagesIndexes = mutableMapOf<Int, CompositePresenterSection<Data>>()

    init {
        var simpleSectionsCount = 0
        var dataSourceIndex = 0
        for (section in sections) {
            when (section) {
                is SimpleSection,
                is FlowSimpleSection, -> {
                    val currentPageIndex = simpleSectionsCount
                    simplePagesIndexes[currentPageIndex] = section
                    section.pageIndex = simpleSectionsCount
                    simpleSectionsCount++
                    if (section is FlowSimpleSection<Data>) {
                        config.coroutineScope.launch {
                            section.flow.collect {
                                notifyOnEvent(
                                    PageChangedEvent(
                                        key = null,
                                        pageIndex = currentPageIndex,
                                        sourceIndex = -1,
                                        previousList = null,
                                        items = it
                                    )
                                )
                            }
                        }
                    }
                }

                is CompositePresenterSection.DataSourceSection -> {
                    sourcesIndexShift[dataSourceIndex] = simpleSectionsCount
                    dataSourceIndex++
                }
            }
        }

        updateSectionsData()
        pagingDataChangesMedium.addDataChangedCallback(object : DataChangedCallback<Key, Data> {

            override fun onEvent(event: DataChangedEvent<Key, Data>) {
                notifyOnEvents(mapEvent(event) ?: return)
            }

            override fun onEvents(events: List<DataChangedEvent<Key, Data>>) {
                notifyOnEvents(events.mapNotNull { mapEvent(it) }.flatten())
            }
        })
    }

    private fun mapEvent(event: DataChangedEvent<Key, Data>): List<DataChangedEvent<Key, Data>>? {
        return event.handle(
            onPageAdded = {
                var shiftedPageIndex = (sourcesIndexShift[it.sourceIndex]
                    ?: return@handle null) + it.pageIndex
                val existingPage = simplePagesIndexes[shiftedPageIndex]
                var wasShifted = false
                val pageEvent = if (existingPage != null) {
                    wasShifted = true
                    PageChangedEvent(
                        key = it.key,
                        sourceIndex = it.sourceIndex,
                        pageIndex = shiftedPageIndex,
                        items = it.items
                    )
                } else {
                    PageAddedEvent(
                        key = it.key,
                        sourceIndex = it.sourceIndex,
                        pageIndex = shiftedPageIndex,
                        items = it.items
                    )
                }

                if (wasShifted) {
                    buildList {
                        add(pageEvent)
                        val shiftedSections = mutableListOf<CompositePresenterSection<Data>>()
                        while (true) {
                            val section = simplePagesIndexes.getValue(shiftedPageIndex)
                            shiftedSections.add(section)
                            simplePagesIndexes.remove(shiftedPageIndex)
                            section.pageIndex++
                            shiftedPageIndex++
                            val items = if (section is SimpleSection<Data>) {
                                section.items
                            } else if (section is FlowSimpleSection<Data>) {
                                section.flow.value
                            } else continue
                            val isLastPage = simplePagesIndexes[shiftedPageIndex] == null
                            add(
                                if (isLastPage) PageAddedEvent(
                                    key = null,
                                    pageIndex = section.pageIndex,
                                    sourceIndex = -1,
                                    items = items
                                )
                                else PageChangedEvent(
                                    key = null,
                                    pageIndex = section.pageIndex,
                                    sourceIndex = -1,
                                    items = items
                                )
                            )
                            if (isLastPage) break
                        }
                        for (section in shiftedSections) {
                            simplePagesIndexes[section.pageIndex] = section
                        }
                    }
                } else listOf(pageEvent)
            },
            onPageChanged = {
                listOf(
                    PageChangedEvent(
                        key = it.key,
                        sourceIndex = it.sourceIndex,
                        pageIndex = (sourcesIndexShift[it.sourceIndex]
                            ?: return@handle null) + it.pageIndex,
                        items = it.items,
                        changeType = it.changeType
                    )
                )
            },
            onPageRemovedEvent = {
                listOf(
                    PageRemovedEvent(
                        key = it.key,
                        sourceIndex = it.sourceIndex,
                        pageIndex = (sourcesIndexShift[it.sourceIndex]
                            ?: return@handle null) + it.pageIndex,
                        itemsCount = it.itemsCount
                    )
                )
            },
            onInvalidate = {
                buildList {
                    add(InvalidateEvent())
                    addAll(getSimpleSectionsEvents(true))
                }
            },
            onElse = { listOf(it) }
        )
    }

    override fun addDataChangedCallback(callback: DataChangedCallback<Key, Data>) {
        super.addDataChangedCallback(callback)
        callback.onEvents(getSimpleSectionsEvents(true))
    }

    fun invalidateSections() {
        updateSectionsData()
        config.coroutineScope.launch(config.processingDispatcher) {
            notifyOnEvents(getSimpleSectionsEvents(false))
        }
    }

    private fun updateSectionsData() {
        for (section in sections) {
            if (section is SimpleSection<Data>) {
                section.items = section.itemsProvider()
            }
        }
    }

    private fun getSimpleSectionsEvents(
        isCreating: Boolean,
    ): List<PageChangedEvent<Key, Data>> = buildList {
        for (section in sections) {
            val items: List<Data?> = if (section is SimpleSection<Data>) {
                section.items
            } else if (section is FlowSimpleSection<Data>) {
                section.flow.value
            } else continue
            add(
                if (isCreating) PageAddedEvent(
                    key = null,
                    sourceIndex = -1,
                    pageIndex = section.pageIndex,
                    items = items
                ) else PageChangedEvent(
                    key = null,
                    sourceIndex = -1,
                    pageIndex = section.pageIndex,
                    items = items
                )
            )
        }
    }
}