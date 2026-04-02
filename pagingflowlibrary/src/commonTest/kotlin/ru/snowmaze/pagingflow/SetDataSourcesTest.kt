package ru.snowmaze.pagingflow

import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import ru.snowmaze.pagingflow.presenters.StatePagingDataPresenter
import ru.snowmaze.pagingflow.presenters.data
import ru.snowmaze.pagingflow.presenters.dataFlow
import ru.snowmaze.pagingflow.presenters.pagingDataPresenter
import ru.snowmaze.pagingflow.presenters.statePresenter
import ru.snowmaze.pagingflow.result.LoadNextPageResult
import ru.snowmaze.pagingflow.source.DefinedItemsTestPagingSource
import ru.snowmaze.pagingflow.utils.setPagingSourcesWithDiff
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals

class SetpagingSourcesTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Test
    fun setpagingSourcesTest() = runTest(testDispatcher) {
        val firstPagingSource = DefinedItemsTestPagingSource("1", listOf(1))
        val secondPagingSource = DefinedItemsTestPagingSource("2", listOf(2))
        val thirdPagingSource = DefinedItemsTestPagingSource("3", listOf(3))
        val fourthPagingSource = DefinedItemsTestPagingSource("4", listOf(4))
        val fifthPagingSource = DefinedItemsTestPagingSource("5", listOf(5))
        val sixthPagingSource = DefinedItemsTestPagingSource("6", listOf(6, 7))
        val seventhPagingSource = DefinedItemsTestPagingSource("7", listOf(8, 9, 10))
        val pagingFlow = buildPagingFlow<Int, Int>(
            PagingFlowConfiguration(LoadParams(3), processingDispatcher = testDispatcher),
        ) {
            addDownPagingSource(firstPagingSource)
            addDownPagingSource(secondPagingSource)
            addDownPagingSource(thirdPagingSource)
            addDownPagingSource(fourthPagingSource)
        }

        val presenter = pagingFlow.pagingDataPresenter().statePresenter(
            sharingStarted = SharingStarted.Eagerly
        )
        while ((pagingFlow.loadNextPageWithResult() as LoadNextPageResult.Success).hasNext) {
        }
        assertContentEquals(
            (firstPagingSource.items + secondPagingSource.items + thirdPagingSource.items + fourthPagingSource.items),
            presenter.data
        )

        pagingFlow.testSetSources(
            presenter,
            listOf(fifthPagingSource, thirdPagingSource)
        )
        pagingFlow.testSetSources(
            presenter,
            listOf(secondPagingSource)
        )

        pagingFlow.testSetSources(
            presenter,
            listOf(thirdPagingSource, fifthPagingSource)
        )
        pagingFlow.testSetSources(
            presenter,
            listOf(fifthPagingSource, secondPagingSource, thirdPagingSource, fourthPagingSource)
        )

        pagingFlow.testSetSources(
            presenter,
            listOf(thirdPagingSource)
        )

        pagingFlow.testSetSources(
            presenter,
            listOf(secondPagingSource, fifthPagingSource, thirdPagingSource, fourthPagingSource)
        )

        pagingFlow.testSetSources(
            presenter,
            listOf(fourthPagingSource, thirdPagingSource, fifthPagingSource, firstPagingSource)
        )
        pagingFlow.testSetSources(
            presenter,
            listOf(secondPagingSource, firstPagingSource, fourthPagingSource, fifthPagingSource)
        )

        pagingFlow.testSetSources(
            presenter,
            listOf(fourthPagingSource, firstPagingSource, thirdPagingSource)
        )

        pagingFlow.testSetSources(
            presenter,
            listOf(fourthPagingSource, firstPagingSource)
        )

        pagingFlow.testSetSources(
            presenter,
            listOf(fourthPagingSource, secondPagingSource, thirdPagingSource, firstPagingSource)
        )

        pagingFlow.testSetSources(
            presenter,
            listOf(fifthPagingSource, secondPagingSource, fourthPagingSource, sixthPagingSource, thirdPagingSource)
        )

        pagingFlow.testSetSources(
            presenter,
            listOf(thirdPagingSource, fifthPagingSource, fourthPagingSource)
        )

        pagingFlow.testSetSources(
            presenter,
            listOf(sixthPagingSource, firstPagingSource, fourthPagingSource, thirdPagingSource, secondPagingSource)
        )

        pagingFlow.testSetSources(
            presenter,
            listOf(sixthPagingSource, secondPagingSource, fourthPagingSource, firstPagingSource, fifthPagingSource)
        )

        val allPagingSources = listOf(
            firstPagingSource,
            secondPagingSource,
            thirdPagingSource,
            fourthPagingSource,
            fifthPagingSource,
            sixthPagingSource,
            seventhPagingSource
        )
        for (i in 0 until 1000) {
            val newPagingSources = allPagingSources.shuffled()
                .slice(0 until Random.nextInt(0, allPagingSources.size))
            pagingFlow.testSetSources(
                presenter,
                newPagingSources
            )
        }
    }

    private var lastSources: List<DefinedItemsTestPagingSource<Int>> = emptyList()

    private suspend fun PagingFlow<Int, Int>.testSetSources(
        presenter: StatePagingDataPresenter<Int, Int>,
        sources: List<DefinedItemsTestPagingSource<Int>>
    ) {
        setPagingSourcesWithDiff(sources)
        do {
            val result = loadNextPageWithResult()
        } while (result is LoadNextPageResult.Success && result.hasNext)

        val sourcesContent = sources.map { it.items }.flatten()
        presenter.dataFlow.firstWithTimeout(message = {
            "expected $sourcesContent but got ${presenter.data} last sources ${lastSources.map { it.items }.flatten()}"
        }) { value ->
            sourcesContent == value
        }
        lastSources = sources
    }
}