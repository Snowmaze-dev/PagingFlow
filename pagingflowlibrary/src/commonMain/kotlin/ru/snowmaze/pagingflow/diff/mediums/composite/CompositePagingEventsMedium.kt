package ru.snowmaze.pagingflow.diff.mediums.composite

import androidx.collection.MutableScatterMap
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.snowmaze.pagingflow.diff.InvalidateEvent
import ru.snowmaze.pagingflow.diff.PageAddedEvent
import ru.snowmaze.pagingflow.diff.PageChangedEvent
import ru.snowmaze.pagingflow.diff.PageChangedEvent.ChangeType
import ru.snowmaze.pagingflow.diff.PageRemovedEvent
import ru.snowmaze.pagingflow.diff.PagingEvent
import ru.snowmaze.pagingflow.diff.PagingEventsListener
import ru.snowmaze.pagingflow.diff.handle
import ru.snowmaze.pagingflow.diff.mediums.PagingEventsMedium
import ru.snowmaze.pagingflow.diff.mediums.PagingEventsMediumConfig
import ru.snowmaze.pagingflow.diff.mediums.SubscribeForChangesEventsMedium
import ru.snowmaze.pagingflow.params.MutablePagingParams
import ru.snowmaze.pagingflow.utils.fastForEach
import ru.snowmaze.pagingflow.utils.flattenWithSize

open class CompositePagingEventsMedium<Key : Any, Data : Any, NewData : Any> internal constructor(
    pagingEventsMedium: PagingEventsMedium<Key, Data>,
    private val sections: List<CompositePresenterSection<Key, Data, NewData>>,
    final override val config: PagingEventsMediumConfig = pagingEventsMedium.config
) : SubscribeForChangesEventsMedium<Key, Data, NewData>(pagingEventsMedium), PagingEventsListener<Key, Data> {

    private val dataSourcesSections =
        MutableScatterMap<Int, CompositePresenterSection.DataSourceSection<Key, Data, NewData>>()
    private val mutex = Mutex()

    init {
        for ((index, section) in sections.withIndex()) {
            section.sourceIndex = index
            if (section is CompositePresenterSection.DataSourceSection<Key, Data, NewData>) {
                if (dataSourcesSections.containsKey(section.dataSourceIndex)) { // TODO support multiple same datasources indexes
                    throw IllegalArgumentException("For now library not supports multiple same paging sources in single composite changes medium.")
                }
                dataSourcesSections[section.dataSourceIndex] = section
            } else if (
                section is CompositePresenterSection.FlowSection<Key, Data, NewData>
            ) config.coroutineScope.launch {
                section.itemsFlow.collect {
                    mutex.withLock {
                        onCompositeSectionChanged(
                            section = section,
                            data = it,
                            pagingParams = MutablePagingParams.noCapacity()
                        )?.let { flowValue -> notifyOnEventsInternal(flowValue) }
                    }
                }
            }
        }

        updateFirstIndexes()
        updateSectionsData()
    }

    override fun getChangesCallback() = this

    override fun addPagingEventsListener(listener: PagingEventsListener<Key, NewData>) {
        super.addPagingEventsListener(listener)
        config.coroutineScope.launch {
            mutex.withLock {
                listener.onEvents(sections.flatMap { it.pages })
            }
        }
    }

    private fun mapEvent(
        event: PagingEvent<Key, Data>
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
//                        val indexShift = section.removedPagesNumbers.size TODO
                val event = PageChangedEvent(
                    key = it.key,
                    pageIndex = section.firstPageIndex + it.pageIndexInSource,
                    sourceIndex = section.sourceIndex,
                    pageIndexInSource = it.pageIndexInSource,
                    previousList = section.pages.getOrNull(it.pageIndexInSource)?.items,
                    previousItemCount = section.pages.getOrNull(
                        it.pageIndexInSource
                    )?.items?.size ?: 0, // TODO
                    items = if (it.changeType == ChangeType.CHANGE_TO_NULLS) {
                        it.items as List<NewData>
                    } else section.mapData(it.items as List<Data>),
                    params = it.params,
                    changeType = it.changeType
                )
                section.pages[event.pageIndexInSource] = event
                event
            },
            onPageRemovedEvent = {
                val section = dataSourcesSections[it.sourceIndex] ?: return@handle null
                onRemovePage(section, it.pageIndexInSource)
            },
            onInvalidate = {
                onInvalidate()
                updateFirstIndexes { section ->
                    section.pages.forEachIndexed { index, page ->
                        section.pages[index] = page.copyWithNewPositionData(
                            pageIndex = section.firstPageIndex + index,
                            sourceIndex = section.sourceIndex
                        )
                    }
                }
                it as InvalidateEvent<Key, NewData>
            },
            onElse = { it as PagingEvent<Key, NewData> }
        )
    }

    override suspend fun onEvents(
        events: List<PagingEvent<Key, Data>>
    ): Unit = mutex.withLock {
        notifyOnEvents(buildList {
            for (event in events) {
                add(mapEvent(event) ?: continue)
            }
            addChangeSimpleSectionsEvent()
        }.flattenWithSize())
    }

    override suspend fun onEvent(
        event: PagingEvent<Key, Data>
    ): Unit = mutex.withLock {
        mapEvent(event)?.let { mappedEvents ->
            val additionalEvents = ArrayList<Any>()
            additionalEvents.addChangeSimpleSectionsEvent()
            notifyOnEventsInternal(
                if (additionalEvents.isEmpty()) mappedEvents
                else {
                    additionalEvents.add(0, mappedEvents)
                    additionalEvents.flattenWithSize<Any, PagingEvent<Key, NewData>>()
                }
            )
        }
    }

    fun updateSectionsData() = config.coroutineScope.launch {
        mutex.withLock {
            for (section in sections) {
                if (section is CompositePresenterSection.SimpleSection<Key, Data, NewData>) {
                    onCompositeSectionChanged(
                        section = section,
                        data = section.itemsProvider(),
                        pagingParams = MutablePagingParams.noCapacity()
                    )?.let { notifyOnEventsInternal(it) }
                }
            }
        }
    }

    private inline fun updateFirstIndexes(
        update: (CompositePresenterSection<Key, Data, NewData>) -> Unit = {}
    ) {
        var firstPageIndex = 0
        sections.fastForEach { section ->
            section.firstPageIndex = firstPageIndex
            firstPageIndex += section.pages.size
            update(section)
        }
    }

    private fun onCompositeSectionChanged(
        section: CompositePresenterSection<Key, Data, NewData>,
        data: List<NewData>,
        pagingParams: MutablePagingParams
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
                previousItemCount = section.pages[0].items.size,
                items = data,
                params = pagingParams
            )
            section.pages[0] = event
            event
        }
    }

    private suspend inline fun notifyOnEventsInternal(events: Any) {
        if (events is List<*>) notifyOnEvents(events as List<PagingEvent<Key, NewData>>)
        else notifyOnEvent(events as PagingEvent<Key, NewData>)
    }

    private fun onAddPage(
        section: CompositePresenterSection<Key, Data, NewData>,
        key: Key?,
        data: List<NewData>,
        pagingParams: MutablePagingParams?,
        addIndex: Int
    ) = buildList {
        val indexShift = if (section is CompositePresenterSection.DataSourceSection) {
            val result = section.removedPagesNumbers.size
            section.removedPagesNumbers.remove(addIndex)
            result
        } else 0
        var currentSectionIndex = section.sourceIndex
        var currentEventIndexInSource = (addIndex - indexShift).coerceAtMost(section.pages.size)
        var isNewEvent = true
        while (true) {
            val currentSection = sections.getOrNull(currentSectionIndex) ?: break
            val currentEvent = if (isNewEvent) {
                val currentEvent = currentSection.pages.getOrNull(currentEventIndexInSource)
                PageChangedEvent(
                    key = key,
                    sourceIndex = section.sourceIndex,
                    pageIndex = section.firstPageIndex + currentEventIndexInSource,
                    pageIndexInSource = currentEventIndexInSource,
                    items = data,
                    params = pagingParams,
                    previousList = currentEvent?.items,
                    previousItemCount = currentEvent?.items?.size
                        ?: currentEvent?.previousItemCount ?: 0
                )
            } else {
                if (currentEventIndexInSource == 0) {
                    val event = currentSection.pages.firstOrNull()
                    if (event != null) currentEventIndexInSource = event.pageIndexInSource
                    event
                } else currentSection.pages.getOrNull(currentEventIndexInSource)
            }

            if (currentEvent == null) {
                currentSectionIndex++
                currentEventIndexInSource = 0
                continue
            }

            var nextEvent = currentSection.pages.getOrNull(currentEventIndexInSource + 1)
            var hasNextData = nextEvent != null
            if (!hasNextData) {
                for (nextSectionIndex in currentSectionIndex + 1 until sections.size) {
                    val nextSection = sections[nextSectionIndex]
                    if (nextSection.pages.isNotEmpty()) {
                        nextEvent = nextSection.pages.first()
                        hasNextData = true
                        break
                    }
                }
            }

            val newEvent = if (hasNextData) PageChangedEvent(
                key = currentEvent.key,
                sourceIndex = currentEvent.sourceIndex,
                pageIndex = if (isNewEvent) currentEvent.pageIndex
                else currentEvent.pageIndex + 1,
                pageIndexInSource = currentEventIndexInSource,
                previousList = if (isNewEvent) currentEvent.previousList else nextEvent?.items,
                previousItemCount = if (isNewEvent) currentEvent.previousItemCount
                else nextEvent?.items?.size ?: 0,
                items = currentEvent.items,
                changeType = ChangeType.COMMON_CHANGE,
                params = currentEvent.params,
            ) else PageAddedEvent(
                key = currentEvent.key,
                sourceIndex = currentEvent.sourceIndex,
                pageIndex = if (isNewEvent) currentEvent.pageIndex
                else currentEvent.pageIndex + 1,
                pageIndexInSource = currentEventIndexInSource,
                items = currentEvent.items as List<NewData>,
                params = currentEvent.params
            )
            add(newEvent)
            if (isNewEvent) {
                currentSection.pages.add(currentEventIndexInSource, newEvent)
            } else {
                currentSection.pages[currentEventIndexInSource] = newEvent
            }
            if (!hasNextData) break
            currentEventIndexInSource++
            isNewEvent = false

        }
        updateFirstIndexes()
    }

    private fun onRemovePage(
        section: CompositePresenterSection<Key, Data, NewData>,
        indexInSource: Int
    ) = buildList {
        val indexShift = if (section is CompositePresenterSection.DataSourceSection) {
            val result = section.removedPagesNumbers.size
            if (indexInSource < section.pages.maxOf { it.pageIndexInSource }) {
                section.removedPagesNumbers.add(indexInSource)
            }
            result
        } else 0
        var currentSectionIndex = section.sourceIndex
        var currentEventIndexInSource = (indexInSource - indexShift).coerceAtMost(section.pages.size - 1)
        var isNewEvent = true
        var previousEvent: PageChangedEvent<Key, NewData> = section.pages.get(currentEventIndexInSource)
        while (true) {
            val currentSection = sections.getOrNull(currentSectionIndex) ?: break

            if (currentSection.pages.isEmpty()) {
                currentSectionIndex++
                currentEventIndexInSource = 0
                continue
            }

            val currentEvent = currentSection.pages.getOrNull(currentEventIndexInSource)

            if (currentEvent == null) {
                currentSectionIndex++
                currentEventIndexInSource = 0
                continue
            }

            var hasNextData = currentSection.pages.getOrNull(currentEventIndexInSource + 1) != null
            if (!hasNextData) {
                for (nextSectionIndex in currentSectionIndex + 1 until sections.size) {
                    val nextSection = sections[nextSectionIndex]
                    if (nextSection.pages.isNotEmpty()) {
                        hasNextData = true
                        break
                    }
                }
            }

            if (isNewEvent) {
                section.pages.removeAt(currentEventIndexInSource)
                if (hasNextData) {
                    previousEvent = currentEvent
                    isNewEvent = false
                    continue
                }
            }

            val isInChangedSection = section === currentSection
            if (!isNewEvent) {
                val changedEvent = PageChangedEvent(
                    key = currentEvent.key,
                    items = currentEvent.items,
                    previousList = previousEvent.items,
                    previousItemCount = previousEvent.items.size,
                    params = currentEvent.params,
                    pageIndex = currentEvent.pageIndex - 1,
                    pageIndexInSource = if (isInChangedSection) currentEvent.pageIndexInSource - 1
                    else currentEvent.pageIndexInSource,
                    sourceIndex = currentEvent.sourceIndex
                )
                currentSection.pages[changedEvent.pageIndexInSource] = changedEvent

                add(changedEvent)
            }
            if (!hasNextData) add(
                PageRemovedEvent(
                    key = currentEvent.key,
                    sourceIndex = section.sourceIndex,
                    pageIndex = currentEvent.pageIndex,
                    pageIndexInSource = currentEventIndexInSource,
                    itemsCount = if (isNewEvent) currentEvent.previousItemCount
                    else currentEvent.items.size,
                )
            )

            if (!hasNextData) break
            previousEvent = currentEvent
            currentEventIndexInSource++
            isNewEvent = false
        }

        updateFirstIndexes()
    }

    private fun onInvalidate() {
        for (section in sections) {
            if (section is CompositePresenterSection.DataSourceSection) {
                section.removedPagesNumbers = mutableSetOf()
                section.pages = mutableListOf()
            }
        }
    }

    private fun MutableList<Any>.addChangeSimpleSectionsEvent() {
        for (section in sections) {
            if (section is CompositePresenterSection.SimpleSection
                && section.updateWhenDataUpdated
            ) {
                add(
                    onCompositeSectionChanged(
                        section = section,
                        data = section.itemsProvider(),
                        pagingParams = MutablePagingParams.noCapacity()
                    ) ?: continue
                )
            }
        }
    }
}