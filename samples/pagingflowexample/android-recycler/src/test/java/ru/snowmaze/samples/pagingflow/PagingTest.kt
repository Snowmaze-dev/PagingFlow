package ru.snowmaze.samples.pagingflow

import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Test
import ru.snowmaze.pagingflow.PaginationDirection
import ru.snowmaze.pagingflow.loadNextPage
import ru.snowmaze.pagingflow.loadNextPageAndAwaitDataSet
import ru.snowmaze.pagingflow.presenters.data
import ru.snowmaze.pagingflow.presenters.dataFlow
import ru.snowmaze.pagingflow.presenters.statePresenter
import ru.snowmaze.pagingflow.samples.TestItem
import ru.snowmaze.pagingflow.samples.TestViewModel

class PagingTest {

    @Test
    fun testPaging(): Unit = runBlocking {
        val model = TestViewModel()
        val presenter = model.pagingEventsMedium.statePresenter(
            sharingStarted = SharingStarted.Eagerly
        )
        model.pagingFlow.loadNextPage()
        model.pagingFlow.loadNextPage()
        model.pagingFlow.loadNextPageAndAwaitDataSet()
        withTimeout(500L) {
            val expected = (0 until 50).map { null } + (50 until 200).map {
                TestItem.Item("Item $it")
            } + TestItem.Loader(true)
            presenter.dataFlow.first {
                it == expected
            }
        }
        model.pagingFlow.loadNextPageAndAwaitDataSet(PaginationDirection.UP)
        val expected = (0 until 150).map {
            TestItem.Item("Item $it")
        } + TestItem.Loader(true)
        if (withTimeoutOrNull(500L) {
            presenter.dataFlow.first { it == expected }
        } == null) {
            println("expected $expected")
            println("actual ${presenter.data}")
            throw IllegalStateException()
        }
    }
}