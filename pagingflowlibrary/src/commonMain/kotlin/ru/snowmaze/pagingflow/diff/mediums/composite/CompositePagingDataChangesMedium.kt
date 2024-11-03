package ru.snowmaze.pagingflow.diff.mediums.composite

import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.snowmaze.pagingflow.diff.DataChangedCallback
import ru.snowmaze.pagingflow.diff.DataChangedEvent
import ru.snowmaze.pagingflow.diff.InvalidateEvent
import ru.snowmaze.pagingflow.diff.PageAddedEvent
import ru.snowmaze.pagingflow.diff.PageChangedEvent
import ru.snowmaze.pagingflow.diff.PageChangedEvent.ChangeType
import ru.snowmaze.pagingflow.diff.PageRemovedEvent
import ru.snowmaze.pagingflow.diff.handle
import ru.snowmaze.pagingflow.diff.mediums.DataChangesMediumConfig
import ru.snowmaze.pagingflow.diff.mediums.PagingDataChangesMedium
import ru.snowmaze.pagingflow.diff.mediums.SubscribeForChangesDataChangesMedium
import ru.snowmaze.pagingflow.params.PagingParams
import ru.snowmaze.pagingflow.utils.flattenWithSize

open class CompositePagingDataChangesMedium<Key : Any, Data : Any, NewData : Any> internal constructor(
    pagingDataChangesMedium: PagingDataChangesMedium<Key, Data>,
    private val sections: List<CompositePresenterSection<Key, Data, NewData>>,
    final override val config: DataChangesMediumConfig = pagingDataChangesMedium.config
) : SubscribeForChangesDataChangesMedium<Key, NewData, Data>(pagingDataChangesMedium) {

    private val dataSourcesSections =
        mutableMapOf<Int, CompositePresenterSection.DataSourceSection<Key, Data, NewData>>()
    private val mutex = Mutex()

    init {
        for ((index, section) in sections.withIndex()) {
            section.sourceIndex = index
            if (section is CompositePresenterSection.DataSourceSection<Key, Data, NewData>) {
                if (dataSourcesSections.containsKey(section.dataSourceIndex)) { // TODO support multiple same datasources indexes
                    throw IllegalArgumentException("For now library not supports multiple same data sources in single composite changes medium.")
                }
                dataSourcesSections[section.dataSourceIndex] = section
            } else if (
                section is CompositePresenterSection.FlowSection<Key, Data, NewData>
            ) config.coroutineScope.launch {
                section.itemsFlow.collectLatest {
                    mutex.withLock {
                        onCompositeSectionChanged(
                            section = section,
                            data = it,
                            pagingParams = PagingParams.EMPTY
                        )?.let { flowValue -> notifyOnEventsInternal(flowValue) }
                    }
                }
            }
        }

        updateFirstIndexes()
        updateSectionsData()
    }

    override fun getChangesCallback() = object : DataChangedCallback<Key, Data> {

        private inline fun mapEvent(
            event: DataChangedEvent<Key, Data>
        ): Any? {
            return event.handle(
                onPageAdded = {
                    val section = dataSourcesSections[it.sourceIndex] ?: return@handle null
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
                    PageChangedEvent(
                        key = it.key,
                        pageIndex = section.firstPageIndex + it.pageIndexInSource,
                        sourceIndex = section.sourceIndex,
                        pageIndexInSource = it.pageIndexInSource,
                        previousList = section.pages[it.pageIndexInSource].items,
                        items = if (it.changeType == ChangeType.CHANGE_TO_NULLS) {
                            it as List<NewData>
                        } else section.mapData(it.items as List<Data>),
                        params = it.params
                    )
                },
                onPageRemovedEvent = {
                    val section = dataSourcesSections[it.sourceIndex] ?: return@handle null
                    onRemovePage(section, it.pageIndexInSource)
                },
                onInvalidate = {
                    onInvalidate()
                    it as InvalidateEvent<Key, NewData>
                },
                onElse = { it as DataChangedEvent<Key, NewData> }
            )
        }

        override suspend fun onEvents(
            events: List<DataChangedEvent<Key, Data>>
        ): Unit = mutex.withLock {
            notifyOnEvents(buildList {
                for (event in events) {
                    add(mapEvent(event) ?: continue)
                }
                addChangeSimpleSectionsEvent()
            }.flattenWithSize())
        }

        override suspend fun onEvent(
            event: DataChangedEvent<Key, Data>
        ): Unit = mutex.withLock {
            mapEvent(event)?.let { mappedEvents ->
                val additionalEvents = ArrayList<Any>()
                additionalEvents.addChangeSimpleSectionsEvent()
                notifyOnEventsInternal(
                    if (additionalEvents.isEmpty()) mappedEvents
                    else {
                        additionalEvents.add(0, mappedEvents)
                        additionalEvents.flattenWithSize<Any, DataChangedEvent<Key, NewData>>()
                    }
                )
            }
        }

        fun MutableList<Any>.addChangeSimpleSectionsEvent() {
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
        config.coroutineScope.launch {
            mutex.withLock {
                callback.onEvents(sections.flatMap { it.pages })
            }
        }
    }

    fun updateSectionsData() = config.coroutineScope.launch {
        mutex.withLock {
            for (section in sections) {
                if (section is CompositePresenterSection.SimpleSection<Key, Data, NewData>) {
                    onCompositeSectionChanged(
                        section = section,
                        data = section.itemsProvider(),
                        pagingParams = PagingParams.EMPTY
                    )?.let { notifyOnEventsInternal(it) }
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
    ): Any? = when {
        section.pages.isEmpty() -> onAddPage(
            section = section,
            key = null,
            data = data,
            pagingParams = pagingParams,
            addIndex = 0
        )

        data.isEmpty() -> onRemovePage(section, 0)
        data == section.pages[0].items -> null

        else -> {
            val event = PageChangedEvent<Key, NewData>(
                null,
                pageIndex = section.firstPageIndex,
                sourceIndex = section.sourceIndex,
                pageIndexInSource = 0,
                previousList = section.pages[0].items,
                items = data,
                params = pagingParams
            )
            section.pages[0] = event
            event
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
        val indexShift = if (section is CompositePresenterSection.DataSourceSection) {
            val result = section.removedPagesNumbers.size
            section.removedPagesNumbers.remove(addIndex)
            result
        } else 0
        val pickedAddIndex = (addIndex - indexShift).coerceAtLeast(0)
        val event = PageAddedEvent(
            key = key,
            sourceIndex = section.sourceIndex,
            pageIndex = section.firstPageIndex + pickedAddIndex - 1,
            pageIndexInSource = pickedAddIndex - 1,
            items = data,
            params = pagingParams
        )
        if (pickedAddIndex >= section.pages.size) section.pages.add(event)
        else section.pages.add(pickedAddIndex, event)
        val pagesSize = sections.sumOf { it.pages.size }
        var index = section.firstPageIndex + pickedAddIndex
        while (pagesSize > index) {
            val currentSection = sections.getOrNull(currentSectionIndex) ?: break
            val isAddSection = currentSection == section

            val currentFirstPageIndex = if (isAddSection) section.firstPageIndex
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
                pageIndexInSource = if (isAddSection) currentEvent.pageIndexInSource + 1
                else currentEvent.pageIndexInSource,
                previousList = nextEvent?.items,
                items = currentEvent.items,
                changeType = ChangeType.COMMON_CHANGE,
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
            currentSection.pages[sourcePageIndex] = newEvent
            index++
        }
        updateFirstIndexes()
    }

    private fun onRemovePage(
        section: CompositePresenterSection<Key, Data, NewData>,
        indexInSource: Int
    ): List<DataChangedEvent<Key, NewData>> = buildList {
        val indexShift = if (section is CompositePresenterSection.DataSourceSection) {
            val result = section.removedPagesNumbers.size
            if (indexInSource < section.pages.maxOf { it.pageIndexInSource }) {
                section.removedPagesNumbers.add(indexInSource)
            }
            result
        } else 0
        val pickedIndexInSource = indexInSource - indexShift
        var currentSectionIndex = section.sourceIndex
        val pagesSize = sections.sumOf { it.pages.size }
        val removedEvent = section.pages.removeAt(pickedIndexInSource)
        var index = section.firstPageIndex + pickedIndexInSource
        var previousEvent = removedEvent
        while (pagesSize > index) {
            val currentSection = sections.getOrNull(currentSectionIndex) ?: break
            val isRemoveSection = currentSection === section

            if (currentSection.pages.size == 0) {
                currentSectionIndex++
                continue
            }

            val currentFirstPageIndex = if (isRemoveSection) section.firstPageIndex
            else currentSection.firstPageIndex + 1

            val sourcePageIndex = (index - currentFirstPageIndex).coerceAtLeast(0)
            val currentEvent = currentSection.pages.getOrNull(sourcePageIndex)
            if (currentEvent == null) {
                if (sourcePageIndex == section.pages.size) {
                    currentSectionIndex++
                } else {
                    index++
                }
                continue
            }

            val nextEvent = currentSection.pages.getOrNull(sourcePageIndex + 1)
            if (nextEvent == null) currentSectionIndex += 1
            if (nextEvent == null) {
                for (nextSectionIndex in currentSectionIndex until sections.size) {
                    val nextSection = sections[nextSectionIndex]
                    if (nextSection.pages.size != 0) {
                        break
                    }
                }
            }
            val changedEvent = PageChangedEvent(
                key = currentEvent.key,
                sourceIndex = currentEvent.sourceIndex,
                pageIndex = currentEvent.pageIndex - 1,
                pageIndexInSource = (currentEvent.pageIndexInSource + if (isRemoveSection) -1 else 0)
                    .coerceAtLeast(0),
                previousList = previousEvent.items,
                items = currentEvent.items,
                changeType = ChangeType.COMMON_CHANGE,
                params = currentEvent.params,
            )
            currentSection.pages[sourcePageIndex] = changedEvent
            previousEvent = currentEvent
            add(changedEvent)
            index++
        }
        add(
            if (isEmpty()) PageRemovedEvent(
                key = removedEvent.key,
                sourceIndex = removedEvent.sourceIndex,
                pageIndex = removedEvent.pageIndex,
                pageIndexInSource = removedEvent.pageIndexInSource,
                itemsCount = removedEvent.items.size,
            ) else PageRemovedEvent(
                key = previousEvent.key,
                sourceIndex = previousEvent.sourceIndex,
                pageIndex = previousEvent.pageIndex,
                pageIndexInSource = previousEvent.pageIndexInSource,
                itemsCount = previousEvent.items.size,
            )
        )
        updateFirstIndexes()
    }

    private fun onInvalidate() {
        for (section in sections) {
            section.pages = mutableListOf()
        }
    }

    private suspend inline fun notifyOnEventsInternal(events: Any) {
        if (events is List<*>) notifyOnEvents(events as List<DataChangedEvent<Key, NewData>>)
        else notifyOnEvent(events as DataChangedEvent<Key, NewData>)
    }
}