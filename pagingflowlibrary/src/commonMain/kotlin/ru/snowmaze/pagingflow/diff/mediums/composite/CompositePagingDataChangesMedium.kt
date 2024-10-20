package ru.snowmaze.pagingflow.diff.mediums.composite

import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.snowmaze.pagingflow.diff.DataChangedCallback
import ru.snowmaze.pagingflow.diff.DataChangedEvent
import ru.snowmaze.pagingflow.diff.InvalidateEvent
import ru.snowmaze.pagingflow.diff.PageAddedEvent
import ru.snowmaze.pagingflow.diff.PageChangedEvent
import ru.snowmaze.pagingflow.diff.PageRemovedEvent
import ru.snowmaze.pagingflow.diff.handle
import ru.snowmaze.pagingflow.diff.mediums.DataChangesMediumConfig
import ru.snowmaze.pagingflow.diff.mediums.DefaultPagingDataChangesMedium
import ru.snowmaze.pagingflow.diff.mediums.PagingDataChangesMedium
import ru.snowmaze.pagingflow.diff.mediums.SubscribeForChangesDataChangesMedium
import ru.snowmaze.pagingflow.params.PagingParams
import ru.snowmaze.pagingflow.utils.flattenWithSize
import ru.snowmaze.pagingflow.utils.limitedParallelismCompat

open class CompositePagingDataChangesMedium<Key : Any, Data : Any, NewData : Any> internal constructor(
    pagingDataChangesMedium: PagingDataChangesMedium<Key, Data>,
    private val sections: List<CompositePresenterSection<Key, Data, NewData>>,
    final override val config: DataChangesMediumConfig = pagingDataChangesMedium.config
) : SubscribeForChangesDataChangesMedium<Key, NewData, Data>(pagingDataChangesMedium) {

    private val dataSourcesSections =
        mutableMapOf<Int, CompositePresenterSection.DataSourceSection<Key, Data, NewData>>()
    protected val processingDispatcher = config.processingDispatcher.limitedParallelismCompat(1)

    init {

        for ((index, section) in sections.withIndex()) {
            section.sourceIndex = index
            if (section is CompositePresenterSection.DataSourceSection<Key, Data, NewData>) {
                if (dataSourcesSections.containsKey(section.dataSourceIndex)) {
                    throw IllegalArgumentException("For now library not supports multiple same data sources in single composite changes medium.")
                }
                dataSourcesSections[section.dataSourceIndex] = section
            } else if (
                section is CompositePresenterSection.FlowSection<Key, Data, NewData>
            ) config.coroutineScope.launch {
                section.itemsFlow.collectLatest {
                    withContext(processingDispatcher) {
                        onCompositeSectionChanged(
                            section = section,
                            data = it,
                            pagingParams = PagingParams.EMPTY
                        )?.let { flowValue -> notifyOnEvents(flowValue) }
                    }
                }
            }
        }

        updateFirstIndexes()
        updateSectionsData()
    }

    override fun getChangesCallback() = object : DataChangedCallback<Key, Data> {

        private var addIndex: Int = 0

        inline fun mapEvent(
            event: DataChangedEvent<Key, Data>
        ): List<DataChangedEvent<Key, NewData>>? {
            return event.handle(
                onPageAdded = {
                    val section = dataSourcesSections[it.sourceIndex]
                        ?: return@handle null // TODO сделать поддержу множества датасорсов одного индекса
                    onAddPage(
                        section = section,
                        key = it.key,
                        data = section.mapData(it.items as List<Data>),
                        pagingParams = it.params,
                        addIndex = it.pageIndexInSource
                    )
                },
                onPageChanged = {
                    val section = dataSourcesSections[it.sourceIndex] ?: return@handle null
                    listOf(
                        PageChangedEvent(
                            key = it.key,
                            pageIndex = section.firstPageIndex + it.pageIndexInSource,
                            sourceIndex = section.sourceIndex,
                            pageIndexInSource = it.pageIndexInSource,
                            previousList = section.pages[it.pageIndexInSource].items,
                            items = if (it.changeType == PageChangedEvent.ChangeType.CHANGE_TO_NULLS) {
                                it as List<NewData>
                            } else section.mapData(it.items as List<Data>),
                            params = it.params
                        )
                    )
                },
                onPageRemovedEvent = {
                    val section = dataSourcesSections[it.sourceIndex] ?: return@handle null
                    onRemovePage(section, it.pageIndexInSource)
                },
                onInvalidate = {
                    onInvalidate()
                    listOf(it as InvalidateEvent<Key, NewData>)
                },
                onElse = { listOf(it as DataChangedEvent<Key, NewData>) }
            )
        }

        override suspend fun onEvents(
            events: List<DataChangedEvent<Key, Data>>
        ): Unit = withContext(processingDispatcher) {
            notifyOnEvents(buildList {
                for (event in events) {
                    add(mapEvent(event) ?: continue)
                }
                addChangeSimpleSectionsEvent()
            }.flattenWithSize())
        }

        override suspend fun onEvent(event: DataChangedEvent<Key, Data>) {
            withContext(processingDispatcher) {
                mapEvent(event)?.let { mappedEvents ->
                    val additionalEvents = ArrayList<List<DataChangedEvent<Key, NewData>>>()
                    additionalEvents.addChangeSimpleSectionsEvent()
                    notifyOnEvents(
                        if (additionalEvents.isEmpty()) mappedEvents
                        else {
                            additionalEvents.add(0, mappedEvents)
                            additionalEvents.flattenWithSize()
                        }
                    )
                    addIndex = 0
                }
            }
        }

        fun MutableList<List<DataChangedEvent<Key, NewData>>>.addChangeSimpleSectionsEvent() {
            for (section in sections) {
                if (section is CompositePresenterSection.SimpleSection
                    && section.updateWhenDataUpdated
                ) {
                    add(
                        onCompositeSectionChanged(
                            section = section,
                            data = section.itemsProvider(),
                            pagingParams = PagingParams.EMPTY
                        ) ?: continue
                    )
                }
            }
        }
    }

    override fun addDataChangedCallback(callback: DataChangedCallback<Key, NewData>) {
        super.addDataChangedCallback(callback)
        config.coroutineScope.launch(processingDispatcher) {
            callback.onEvents(sections.flatMap { it.pages })
        }
    }

    fun updateSectionsData() {
        config.coroutineScope.launch(processingDispatcher) {
            for (section in sections) {
                if (section is CompositePresenterSection.SimpleSection<Key, Data, NewData>) {
                    onCompositeSectionChanged(
                        section = section,
                        data = section.itemsProvider(),
                        pagingParams = PagingParams.EMPTY
                    )?.let { notifyOnEvents(it) }
                }
            }
        }
    }

    private fun updateFirstIndexes() {
        var firstPageIndex = 0
        for (section in sections) {
            section.firstPageIndex = firstPageIndex
            firstPageIndex += section.pages.size
        }
    }

    private fun onCompositeSectionChanged(
        section: CompositePresenterSection<Key, Data, NewData>,
        data: List<NewData>,
        pagingParams: PagingParams
    ): List<DataChangedEvent<Key, NewData>>? = when {
        section.pages.isEmpty() -> onAddPage(
            section = section,
            key = null,
            data = data,
            pagingParams = pagingParams,
            addIndex = 0
        )

        data.isEmpty() -> onRemovePage(section, 0)
        data == section.pages[0].items -> null

        else -> section.pages.also {
            it[0] = PageChangedEvent(
                null,
                pageIndex = section.firstPageIndex,
                sourceIndex = section.sourceIndex,
                pageIndexInSource = 0,
                previousList = section.pages[0].items,
                items = data,
                params = pagingParams
            )
        }
    }

    private fun onAddPage(
        section: CompositePresenterSection<Key, Data, NewData>,
        key: Key?,
        data: List<NewData>,
        pagingParams: PagingParams?,
        addIndex: Int
    ): List<DataChangedEvent<Key, NewData>> = buildList {
        var currentSectionIndex = section.sourceIndex
        val event = PageAddedEvent(
            key = key,
            sourceIndex = section.sourceIndex,
            pageIndex = section.firstPageIndex + addIndex - 1,
            pageIndexInSource = addIndex - 1,
            items = data,
            params = pagingParams
        )
        if (addIndex >= section.pages.size) section.pages.add(event)
        else section.pages.add(addIndex, event)
        val pagesSize = sections.sumOf { it.pages.size }
        var index = section.firstPageIndex + addIndex
        while (pagesSize > index) {
            val currentSection = sections.getOrNull(currentSectionIndex) ?: break

            val currentFirstPageIndex = if (currentSection == section) section.firstPageIndex
            else currentSection.firstPageIndex + 1

            if (currentSection.pages.size == 0) {
                currentSectionIndex++
                continue
            }
            val sourcePageIndex = (index - currentFirstPageIndex).coerceAtLeast(0)
            val currentEvent = currentSection.pages[sourcePageIndex]
            val nextEvent = currentSection.pages.getOrNull(sourcePageIndex + 1)
            if (nextEvent == null) currentSectionIndex += 1
            var hasNextData = nextEvent != null
            if (!hasNextData) {
                for (nextSectionIndex in currentSectionIndex until sections.size) {
                    val nextSection = sections[nextSectionIndex]
                    if (nextSection.pages.size != 0) {
                        hasNextData = true
                        break
                    }
                }
            }
            val newEvent = if (hasNextData) PageChangedEvent(
                key = currentEvent.key,
                sourceIndex = currentEvent.sourceIndex,
                pageIndex = currentEvent.pageIndex + 1,
                pageIndexInSource = sourcePageIndex,
                previousList = nextEvent?.items,
                items = currentEvent.items,
                changeType = PageChangedEvent.ChangeType.COMMON_CHANGE,
                params = currentEvent.params,
            ) else PageAddedEvent(
                key = currentEvent.key,
                sourceIndex = currentEvent.sourceIndex,
                pageIndex = currentEvent.pageIndex + 1,
                pageIndexInSource = sourcePageIndex,
                items = currentEvent.items as List<NewData>,
                params = currentEvent.params
            )
            add(newEvent)
            currentSection.pages.removeAt(sourcePageIndex)
            currentSection.pages.add(sourcePageIndex, newEvent)
            index++
        }
        updateFirstIndexes()
    }

    private fun onRemovePage(
        section: CompositePresenterSection<Key, Data, NewData>,
        indexInSource: Int
    ): List<DataChangedEvent<Key, NewData>> = buildList {
        var currentSectionIndex = section.sourceIndex
        val pagesSize = sections.sumOf { it.pages.size }
        val removedEvent = section.pages.removeAt(indexInSource)
        var index = section.firstPageIndex + indexInSource
        var previousEvent = removedEvent
        while (pagesSize > index) {
            val currentSection = sections.getOrNull(currentSectionIndex) ?: break

            val currentFirstPageIndex = if (currentSection == section) section.firstPageIndex
            else currentSection.firstPageIndex + 1

            if (currentSection.pages.size == 0) {
                currentSectionIndex++
                continue
            }

            val sourcePageIndex = (index - currentFirstPageIndex).coerceAtLeast(0)
            val currentEvent = currentSection.pages[sourcePageIndex]

            val nextEvent = currentSection.pages.getOrNull(sourcePageIndex + 1)
            if (nextEvent == null) currentSectionIndex += 1
            var hasNextData = nextEvent != null
            if (!hasNextData) {
                for (nextSectionIndex in currentSectionIndex until sections.size) {
                    val nextSection = sections[nextSectionIndex]
                    if (nextSection.pages.size != 0) {
                        hasNextData = true
                        break
                    }
                }
            }
            val newEvent = if (hasNextData) {
                val event = PageChangedEvent(
                    key = currentEvent.key,
                    sourceIndex = currentEvent.sourceIndex,
                    pageIndex = currentEvent.pageIndex - 1,
                    pageIndexInSource = sourcePageIndex + if (section == currentSection) -1 else 0,
                    previousList = previousEvent.items,
                    items = currentEvent.items,
                    changeType = PageChangedEvent.ChangeType.COMMON_CHANGE,
                    params = currentEvent.params,
                )

                currentSection.pages[sourcePageIndex] = event

                event
            } else PageRemovedEvent(
                key = currentEvent.key,
                sourceIndex = currentEvent.sourceIndex,
                pageIndex = currentEvent.pageIndex - 1,
                pageIndexInSource = sourcePageIndex,
                itemsCount = currentEvent.items.size,
            )
            previousEvent = currentEvent
            add(newEvent)
            index++
        }
        if (isEmpty()) add(
            PageRemovedEvent(
                key = removedEvent.key,
                sourceIndex = removedEvent.sourceIndex,
                pageIndex = removedEvent.pageIndex,
                pageIndexInSource = removedEvent.pageIndexInSource,
                itemsCount = removedEvent.items.size,
            )
        )
        updateFirstIndexes()
    }

    private fun onInvalidate() {
        for (section in sections) {
            section.pages = mutableListOf()
        }
    }
}