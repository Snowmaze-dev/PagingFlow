package ru.snowmaze.samples.pagingflow

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import ru.snowmaze.pagingflow.PaginationDirection
import ru.snowmaze.pagingflow.loadNextPage
import ru.snowmaze.pagingflow.loadNextPageAndAwaitDataSet
import ru.snowmaze.pagingflow.loadNextPageWithResult
import ru.snowmaze.pagingflow.presenters.BasicPresenterConfiguration
import ru.snowmaze.pagingflow.presenters.dataFlow
import ru.snowmaze.pagingflow.presenters.statePresenter
import ru.snowmaze.pagingflow.result.LoadNextPageResult
import ru.snowmaze.pagingflow.samples.TestItem
import ru.snowmaze.pagingflow.samples.TestViewModel
import ru.snowmaze.pagingflow.samples.TestViewModel.Companion.EXAMPLE_LOAD_SIZE
import ru.snowmaze.pagingflow.samples.TestViewModel.Companion.REMOVE_PAGE_OFFSET
import kotlin.test.Test

class PagingTest {

    @Test
    fun testPaging(): Unit = runBlocking {
//        val upPreloader = listOf(TestItem.Loader(false))
        val upPreloader = listOf<TestItem>()
        val model = TestViewModel()
        val presenter = model.pagingEventsMedium.statePresenter(
            sharingStarted = SharingStarted.WhileSubscribed(),
            configuration = BasicPresenterConfiguration(
                unsubscribeDelayWhenNoSubscribers = 0
            )
        )
        var subscriber = launch { presenter.latestDataFlow.collect() }

        presenter.dataFlow.firstEqualsWithTimeout(
            getExpectedList(itemsCount = EXAMPLE_LOAD_SIZE) + TestItem.Loader(
                true
            )
        )

        repeat(3) {
            model.pagingFlow.loadNextPageAndAwaitDataSet()
        }

        presenter.dataFlow.firstEqualsWithTimeout(
            upPreloader +
                    getExpectedList(EXAMPLE_LOAD_SIZE) +
                    TestItem.Loader(true)
        )

        model.pagingFlow.loadNextPageAndAwaitDataSet(PaginationDirection.UP)
        val expectedItems = (0 until 150).map { "Item $it" }
        val expected = expectedItems.map {
            TestItem.Item(it)
        } + TestItem.Loader(true)
        presenter.dataFlow.firstEqualsWithTimeout(expected)
        subscriber.cancel()

        val directPresenter = model.pagingFlow.statePresenter()
        directPresenter.dataFlow.firstEqualsWithTimeout(expectedItems)

        subscriber = launch {
            presenter.latestDataFlow.collect()
        }

        delay(20)
        presenter.dataFlow.firstEqualsWithTimeout(expected)

        model.pagingFlow.loadNextPageAndAwaitDataSet()
        presenter.dataFlow.firstEqualsWithTimeout(
            upPreloader +
                    getExpectedList(EXAMPLE_LOAD_SIZE) +
                    TestItem.Loader(true)
        )

        model.pagingFlow.loadNextPageAndAwaitDataSet()
        presenter.dataFlow.firstEqualsWithTimeout(
            upPreloader +
                    getExpectedList(EXAMPLE_LOAD_SIZE * 2) +
                    TestItem.Loader(true)
        )

        model.pagingFlow.loadNextPageAndAwaitDataSet()
        presenter.dataFlow.firstEqualsWithTimeout(
            upPreloader +
                    getExpectedList(
                        EXAMPLE_LOAD_SIZE * 2,
                        itemsOffset = EXAMPLE_LOAD_SIZE
                    ) + TestItem.Loader(true)
        )

        model.pagingFlow.loadNextPageAndAwaitDataSet(PaginationDirection.UP)

        presenter.dataFlow.firstEqualsWithTimeout(
            upPreloader +
                    getExpectedList(EXAMPLE_LOAD_SIZE * 2) +
                    TestItem.Loader(true)
        )

        while (
            model.pagingFlow.loadNextPageWithResult(
                PaginationDirection.UP
            ) is LoadNextPageResult.Success
        ) {
        }

        repeat(10) {
            repeat(5) {
                model.pagingFlow.loadNextPage(
                    PaginationDirection.DOWN
                )
            }
            repeat(5) {
                model.pagingFlow.loadNextPage(
                    PaginationDirection.UP
                )
            }
        }
        var job: Job? = null
        repeat(4) {
            job = launch {
                model.pagingFlow.loadNextPageAndAwaitDataSet(
                    PaginationDirection.DOWN
                )
            }
        }
        job?.join()

        presenter.dataFlow.firstEqualsWithTimeout(
            upPreloader +
                    getExpectedList(EXAMPLE_LOAD_SIZE * 4) +
                    TestItem.Loader(true)
        )

        subscriber.cancel()
    }

    private fun getExpectedList(
        nullsCount: Int = 0,
        itemsCount: Int = EXAMPLE_LOAD_SIZE * REMOVE_PAGE_OFFSET,
        itemsOffset: Int = 0
    ) = arrayOfNulls<TestItem>(nullsCount).asList() +
            (nullsCount until (nullsCount + itemsCount + itemsOffset)).map {
                TestItem.Item("Item $it")
            }
}