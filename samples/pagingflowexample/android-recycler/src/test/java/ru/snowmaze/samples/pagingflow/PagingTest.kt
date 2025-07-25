package ru.snowmaze.samples.pagingflow

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Test
import ru.snowmaze.pagingflow.PaginationDirection
import ru.snowmaze.pagingflow.loadNextPageAndAwaitDataSet
import ru.snowmaze.pagingflow.presenters.BasicPresenterConfiguration
import ru.snowmaze.pagingflow.presenters.data
import ru.snowmaze.pagingflow.presenters.dataFlow
import ru.snowmaze.pagingflow.presenters.statePresenter
import ru.snowmaze.pagingflow.samples.TestItem
import ru.snowmaze.pagingflow.samples.TestViewModel
import ru.snowmaze.pagingflow.samples.TestViewModel.Companion.EXAMPLE_LOAD_SIZE

class PagingTest {

    @Test
    fun testPaging(): Unit = runBlocking {
        val model = TestViewModel()
        val presenter = model.pagingEventsMedium.statePresenter(
            sharingStarted = SharingStarted.WhileSubscribed(),
            configuration = BasicPresenterConfiguration(
                unsubscribeDelayWhenNoSubscribers = 0
            )
        )
        var subscriber = launch {
            presenter.latestDataFlow.collect()
        }
        repeat(3) {
            model.pagingFlow.loadNextPageAndAwaitDataSet()
        }

        withTimeout(500L) {
            val expected = (0 until EXAMPLE_LOAD_SIZE).map { null } +
                    (EXAMPLE_LOAD_SIZE until EXAMPLE_LOAD_SIZE * 4).map {
                        TestItem.Item("Item $it")
                    } + TestItem.Loader(true)
            presenter.dataFlow.first {
                it == expected
            }
        }
        model.pagingFlow.loadNextPageAndAwaitDataSet(PaginationDirection.UP)
        val expectedItems = (0 until 150).map { "Item $it" }
        val expected = expectedItems.map {
            TestItem.Item(it)
        } + TestItem.Loader(true)
        if (withTimeoutOrNull(500L) {
                presenter.dataFlow.first { it == expected }
            } == null) {
            println("expected $expected")
            println("actual ${presenter.data}")
            throw IllegalStateException()
        }
        subscriber.cancel()
        val directPresenter = model.pagingFlow.statePresenter()

        if (withTimeoutOrNull(500L) {
                directPresenter.dataFlow.first { it == expectedItems }
            } == null) {
            println("expected $expectedItems")
            println("actual ${directPresenter.data}")
            throw IllegalStateException()
        }

        subscriber = launch {
            presenter.latestDataFlow.collect()
        }

        delay(100)
        if (withTimeoutOrNull(500L) {
                presenter.dataFlow.first { it == expected }
            } == null) {
            println("expected $expected")
            println("actual ${presenter.data}")
            throw IllegalStateException()
        }

        subscriber.cancel()
    }
}