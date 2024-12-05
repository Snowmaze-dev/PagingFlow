package ru.snowmaze.pagingflow

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import ru.snowmaze.pagingflow.diff.PageAddedEvent
import ru.snowmaze.pagingflow.diff.PageChangedEvent
import ru.snowmaze.pagingflow.diff.PageRemovedEvent
import ru.snowmaze.pagingflow.diff.mediums.composite.CompositePagingDataChangesMediumBuilder
import ru.snowmaze.pagingflow.diff.mediums.composite.section
import ru.snowmaze.pagingflow.presenters.compositeDataPresenter
import ru.snowmaze.pagingflow.presenters.pagingDataPresenter
import ru.snowmaze.pagingflow.source.MaxItemsConfiguration
import ru.snowmaze.pagingflow.source.TestPagingSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CompositeMediumTest {

    private val pageSize = 20
    private val removePagesOffset = 2
    private val totalCount = 10000

    private val basePagingFlowConfiguration = PagingFlowConfiguration(
        defaultParams = LoadParams(pageSize, 0),
        processingDispatcher = Dispatchers.Default,
        maxItemsConfiguration = MaxItemsConfiguration(
            maxItemsCount = removePagesOffset * pageSize, enableDroppedPagesNullPlaceholders = false
        )
    )

    @Test
    fun baseMediumExtensionTest() = runTestOnDispatchersDefault {
        val testDataSource = TestPagingSource(totalCount)
        val pagingFlow = buildPagingFlow(basePagingFlowConfiguration) {
            addDownPagingSource(testDataSource)
        }
        val data = listOf(123)
        val presenter = pagingFlow.compositeDataPresenter {
            section(data)
        }
        presenter.latestDataFlow.firstWithTimeout { it.data.size == 1 }
        assertEquals(data, presenter.latestData.data)
    }

    private fun mapToInts(data: List<String>) = data.map { it.drop(5).toInt() }

    @Test
    fun baseMediumTest() = runTestOnDispatchersDefault {
        val testDataSource = TestPagingSource(pageSize * 3)
        val testDataSource1 = TestPagingSource(totalCount, startFrom = 500)
        val pagingFlow = buildPagingFlow(basePagingFlowConfiguration) {
            addDownPagingSource(testDataSource)
            addDownPagingSource(testDataSource1)
        }
        var startList = listOf(123)
        val second = listOf(1235)
        val third = listOf(12)
        val fourth = listOf(21)
        val fifth = listOf(577)

        val firstFlow = MutableSharedFlow<List<Int>>()
        val secondFlow = MutableSharedFlow<List<Int>>()
        val thirdFlow = MutableSharedFlow<List<Int>>()
        val fourthFlow = MutableSharedFlow<List<Int>>()
        val medium = CompositePagingDataChangesMediumBuilder.build(pagingFlow) {
            dataSourceSection(1, mapper = ::mapToInts)
            dataSourceSection(0, mapper = ::mapToInts)
            flowSection(thirdFlow)
            flowSection(secondFlow)
            flowSection(fourthFlow)
            section(updateWhenDataUpdated = true) { startList }
            flowSection(firstFlow)
        }
        val latestEventsMedium = LatestEventsMedium(medium)

        val presenter = medium.pagingDataPresenter()
        firstFlow.emit(second)
        presenter.latestDataFlow.firstWithTimeout { it.data.size == 2 }
        val lastEvents = latestEventsMedium.eventsFlow.first()
        assertEquals(startList + second, presenter.latestData.data)
        val mappedEvents = lastEvents.map {
            it::class.simpleName + " source " + (it as PageChangedEvent).sourceIndex
        }
        assertEquals(
            1,
            lastEvents.size,
            "expected one event but got $mappedEvents"
        )
        assertIs<PageAddedEvent<*, *>>(lastEvents.first())
        secondFlow.emit(third)
        presenter.latestDataFlow.firstWithTimeout { it.data.size == 3 }
        val lastEventsNew = latestEventsMedium.eventsFlow.first()
        assertEquals(third + startList + second, presenter.latestData.data)
        assertEquals(3, lastEventsNew.size)

        assertIs<PageChangedEvent<*, *>>(lastEventsNew.first())
        assertIs<PageChangedEvent<*, *>>(lastEventsNew[1])
        assertIs<PageAddedEvent<*, *>>(lastEventsNew[2])

        thirdFlow.emit(fourth)
        presenter.latestDataFlow.firstWithTimeout { it.data.size == 4 }
        assertEquals(fourth + third + startList + second, presenter.latestData.data)
        fourthFlow.emit(fifth)
        presenter.latestDataFlow.firstWithTimeout { it.data.size == 5 }
        assertEquals(fourth + third + fifth + startList + second, presenter.latestData.data)

        pagingFlow.loadNextPageWithResult()
        presenter.latestDataFlow.firstWithTimeout { it.data.size == 25 }
        val sourceItems = testDataSource.getItems(pageSize)
            .map { it.drop(5).toInt() }
        assertEquals(
            sourceItems + fourth + third + fifth + startList + second,
            presenter.latestData.data
        )

        thirdFlow.emit(emptyList())

        presenter.latestDataFlow.firstWithTimeout { it.data.size == 24 }
        val lastEventsWithRemove = latestEventsMedium.eventsFlow.first()
        assertEquals(sourceItems + third + fifth + startList + second, presenter.latestData.data)
        assertIs<PageChangedEvent<*, *>>(lastEventsWithRemove.first())
        assertIs<PageChangedEvent<*, *>>(lastEventsWithRemove[1])
        assertIs<PageChangedEvent<*, *>>(lastEventsWithRemove[2])
        assertIs<PageChangedEvent<*, *>>(lastEventsWithRemove[3])
        assertIs<PageRemovedEvent<*, *>>(lastEventsWithRemove[4])

        firstFlow.emit(emptyList())

        (sourceItems + third + fifth + startList).also { list ->
            presenter.latestDataFlow.firstWithTimeout { it.data.size == list.size }
            assertEquals(list, presenter.latestData.data)
        }

        // testing updateWhenDataUpdated = true
        startList = listOf(1234)

        pagingFlow.loadNextPageAndAwaitDataSet()
        presenter.latestDataFlow.firstWithTimeout { it.data.size == 43 }
        assertEquals(
            testDataSource.getItems(pageSize * 2)
                .map { it.drop(5).toInt() } + third + fifth + startList,
            presenter.latestData.data)

        pagingFlow.loadNextPageAndAwaitDataSet()
        assertEquals(
            mapToInts(testDataSource.getItems(pageSize * 3).drop(pageSize))
                    + third + fifth + startList,
            presenter.latestData.data
        )

        pagingFlow.loadNextPageAndAwaitDataSet()
        assertEquals(
            mapToInts(
                testDataSource1.getItems(pageSize) + testDataSource.getItems(pageSize * 3)
                    .drop(pageSize * 2)
            ) + third + fifth + startList,
            presenter.latestData.data
        )
    }
}