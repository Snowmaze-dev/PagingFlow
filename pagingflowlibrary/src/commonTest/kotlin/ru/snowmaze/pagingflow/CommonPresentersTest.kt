package ru.snowmaze.pagingflow

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.invoke
import kotlinx.coroutines.test.runTest
import ru.snowmaze.pagingflow.diff.mediums.MappingPagingDataMedium
import ru.snowmaze.pagingflow.presenters.InvalidateBehavior
import ru.snowmaze.pagingflow.diff.mediums.composite.CompositePagingChangesMediumBuilder
import ru.snowmaze.pagingflow.presenters.pagingDataPresenter
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class CommonPresentersTest {

//    val pageSize = Random.nextInt(5, 30)
    val pageSize = 5

    private val basePagingFlowConfiguration = PagingFlowConfiguration(
        defaultParams = LoadParams(pageSize, 0),
        processingDispatcher = testDispatcher
    )

    @Test
    fun compositePresenterTest() = runTest {
//        val totalCount = Random.nextInt(80, 1000)
        val totalCount = 10
        val testDataSource = TestDataSource(totalCount)
        val pagingFlow = buildPagingFlow(basePagingFlowConfiguration) {
            addDataSource(testDataSource)
        }
        val prependItems = listOf(-1, -5)
        val otherPrependItems = listOf(-2, -6)
        val newList = listOf(-3, -6, -7)
        val firstAfterSection = MutableStateFlow(prependItems)
        val presenter = CompositePagingChangesMediumBuilder.create(
            pagingDataChangesMedium = MappingPagingDataMedium(pagingFlow) { event ->
                event.items.mapIndexed { _: Int, s: String? ->
                    s?.let { s.split(" ")[1].toInt() }
                }
            }
        ) {
            section { prependItems }
            section { otherPrependItems }
            dataSourceSection()
            flowSection(firstAfterSection)
            section { otherPrependItems }
        }.pagingDataPresenter()

        pagingFlow.testLoadEverything(
            dataSources = listOf(testDataSource),
            shouldTestItems = false
        )
        println("dataFlow ${presenter.dataFlow.value}")
        val doublePrepend = prependItems + otherPrependItems
        assertEquals(
            doublePrepend + List(totalCount) { index: Int ->
                index
            } + doublePrepend,
            presenter.dataFlow.value
        )
        firstAfterSection.value = newList
        assertEquals(
            doublePrepend + List(totalCount) { index: Int ->
                index
            } + firstAfterSection.value + otherPrependItems,
            presenter.dataFlow.value
        )
        pagingFlow.invalidate()
        assertEquals(
            doublePrepend + firstAfterSection.value + otherPrependItems,
            presenter.dataFlow.value
        )
    }

    @Test
    fun asyncBehaviorPresenterTest() = runTest {
        val totalCount = Random.nextInt(80, 1000)
        val testDataSource = TestDataSource(totalCount)
        val pagingFlow = buildPagingFlow(
            basePagingFlowConfiguration.copy(
                processingDispatcher = Dispatchers.Default
            )
        ) {
            addDataSource(testDataSource)
        }
        val presenter =
            pagingFlow.pagingDataPresenter(invalidateBehavior = InvalidateBehavior.INVALIDATE_IMMEDIATELY)

        pagingFlow.loadNextPageWithResult()
        Dispatchers.Default { delay(30L) }
        assertEquals(
            pageSize,
            presenter.dataFlow.value.size
        )
        pagingFlow.invalidate()
        Dispatchers.Default { delay(30L) }
        assertEquals(
            0,
            presenter.dataFlow.value.size
        )
    }
}