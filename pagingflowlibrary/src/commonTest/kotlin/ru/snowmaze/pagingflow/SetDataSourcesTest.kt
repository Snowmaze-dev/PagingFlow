package ru.snowmaze.pagingflow

import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import ru.snowmaze.pagingflow.presenters.PagingDataPresenter
import ru.snowmaze.pagingflow.presenters.pagingDataPresenter
import ru.snowmaze.pagingflow.result.LoadNextPageResult
import ru.snowmaze.pagingflow.utils.setDataSourcesWithDiff
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals

class SetDataSourcesTest {

    @Test
    fun setDataSourcesTest() = runTest {
        val testDispatcher = UnconfinedTestDispatcher()
        val firstDataSource = DefinedItemsTestDataSource("1", listOf(1))
        val secondDataSource = DefinedItemsTestDataSource("2", listOf(2))
        val thirdDataSource = DefinedItemsTestDataSource("3", listOf(3))
        val fourthDataSource = DefinedItemsTestDataSource("4", listOf(4))
        val fifthDataSource = DefinedItemsTestDataSource("5", listOf(5))
        val pagingFlow = buildPagingFlow<Int, Int, DefaultPagingStatus>(
            PagingFlowConfiguration(LoadParams(3), processingDispatcher = testDispatcher),
        ) {
            addDataSource(firstDataSource)
            addDataSource(secondDataSource)
            addDataSource(thirdDataSource)
            addDataSource(fourthDataSource)
        }

        val presenter = pagingFlow.pagingDataPresenter()
        while ((pagingFlow.loadNextPageWithResult() as LoadNextPageResult.Success).hasNext) {
        }
        assertContentEquals(
            (firstDataSource.items + secondDataSource.items + thirdDataSource.items + fourthDataSource.items),
            presenter.dataFlow.value
        )

        pagingFlow.testSetSources(
            presenter,
            listOf(fifthDataSource, thirdDataSource)
        )
        pagingFlow.testSetSources(
            presenter,
            listOf(secondDataSource)
        )

        pagingFlow.testSetSources(
            presenter,
            listOf(thirdDataSource, fifthDataSource)
        )
        pagingFlow.testSetSources(
            presenter,
            listOf(fifthDataSource, secondDataSource, thirdDataSource, fourthDataSource)
        )

        pagingFlow.testSetSources(
            presenter,
            listOf(thirdDataSource)
        )
        pagingFlow.testSetSources(
            presenter,
            listOf(secondDataSource, fifthDataSource, thirdDataSource, fourthDataSource)
        )

        pagingFlow.testSetSources(
            presenter,
            listOf(fourthDataSource, thirdDataSource, fifthDataSource, firstDataSource)
        )
        pagingFlow.testSetSources(
            presenter,
            listOf(secondDataSource, firstDataSource, fourthDataSource, fifthDataSource)
        )

        val allDataSources = listOf(
            firstDataSource,
            secondDataSource,
            thirdDataSource,
            fourthDataSource,
            fifthDataSource
        )
        for (i in 0 until 50) {
            val newDataSources = allDataSources.shuffled()
                .slice(0 until Random.nextInt(0, allDataSources.size))
            pagingFlow.testSetSources(
                presenter,
                newDataSources
            )
        }
    }

    private suspend fun PagingFlow<Int, Int, DefaultPagingStatus>.testSetSources(
        presenter: PagingDataPresenter<Int, Int>,
        sources: List<DefinedItemsTestDataSource<Int>>
    ) {
        setDataSourcesWithDiff(sources).join()
        do {
            val result = loadNextPageWithResult()
        } while (result is LoadNextPageResult.Success && result.hasNext)
        assertContentEquals(sources.map { it.items }.flatten(), presenter.dataFlow.value)
    }
}