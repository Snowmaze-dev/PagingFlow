package ru.snowmaze.pagingflow

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import ru.snowmaze.pagingflow.diff.specificPagingSourceMedium
import ru.snowmaze.pagingflow.presenters.BasicPresenterConfiguration
import ru.snowmaze.pagingflow.presenters.data
import ru.snowmaze.pagingflow.presenters.dataFlow
import ru.snowmaze.pagingflow.presenters.statePresenter
import ru.snowmaze.pagingflow.source.TestPagingSource
import kotlin.test.Test
import kotlin.test.assertEquals

class SpecificPagingSourceMediumTest {

    private val pageSize = 20
    private val removePagesOffset = 2
    private val totalCount = 10000

    private val basePagingFlowConfiguration = PagingFlowConfiguration(
        defaultParams = LoadParams(pageSize, 0),
        processingDispatcher = Dispatchers.Default,
        maxItemsConfiguration = MaxItemsConfiguration(
            maxItemsCount = removePagesOffset * pageSize, maxDroppedPagesItemsCount = null
        )
    )

    @Test
    fun baseTest() = runTestOnDispatchersDefault {
        val testPagingSource = TestPagingSource(totalCount)
        val pagingFlow = buildPagingFlow(basePagingFlowConfiguration) {
            addDownPagingSource(TestPagingSource(40))
            addDownPagingSource(testPagingSource)
        }
        val presenterForFirst = pagingFlow.specificPagingSourceMedium(0).statePresenter(
            configuration = BasicPresenterConfiguration(presenterFlow = ::MutableSharedFlow),
            sharingStarted = SharingStarted.Eagerly
        )
        val presenterForSecond = pagingFlow.specificPagingSourceMedium(1).statePresenter(
            configuration = BasicPresenterConfiguration(presenterFlow = ::MutableSharedFlow),
            sharingStarted = SharingStarted.Eagerly
        )
        pagingFlow.loadNextPageAndAwaitDataSet()
        presenterForSecond.dataFlow.firstEqualsWithTimeout(emptyList())
        assertEquals(testPagingSource.getItems(20), presenterForFirst.data)
        pagingFlow.loadNextPageAndAwaitDataSet()
        presenterForFirst.dataFlow.firstEqualsWithTimeout(testPagingSource.getItems(40))
        presenterForSecond.dataFlow.firstEqualsWithTimeout(emptyList())
        pagingFlow.loadNextPageAndAwaitDataSet()
        presenterForFirst.dataFlow.firstEqualsWithTimeout(
            testPagingSource.getItems(40).takeLast(20)
        )
        presenterForSecond.dataFlow.firstEqualsWithTimeout(testPagingSource.getItems(20))
    }
}