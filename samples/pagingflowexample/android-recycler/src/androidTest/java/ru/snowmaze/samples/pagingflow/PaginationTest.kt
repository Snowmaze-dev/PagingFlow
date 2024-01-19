package ru.snowmaze.samples.pagingflow

import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.IdlingPolicies
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import ru.snowmaze.pagingflow.samples.MainActivity
import ru.snowmaze.pagingflow.samples.TestViewModel.Companion.EXAMPLE_LOAD_SIZE
import ru.snowmaze.pagingflow.samples.TestViewModel.Companion.PREFETCH_DISTANCE
import ru.snowmaze.pagingflow.samples.TestViewModel.Companion.REMOVE_PAGE_OFFSET
import java.util.concurrent.TimeUnit

class PaginationTest {

    companion object {
        const val TEST_TOTAL_COUNT = 300
    }

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Before
    fun init() {
        activityRule.scenario.onActivity {
            it.viewModel.totalItemsCount = TEST_TOTAL_COUNT
        }
    }

    @Test
    fun testPagination(): Unit = runBlocking {
        val removeOffset = EXAMPLE_LOAD_SIZE * REMOVE_PAGE_OFFSET
        IdlingPolicies.setIdlingResourceTimeout(10, TimeUnit.SECONDS);
        val recycler = onView(withId(R.id.main_recycler_view))
        for (index in 0 until TEST_TOTAL_COUNT) {
            onView(withText("Item $index")).check(matches(isDisplayed()))
            recycler.perform(
                RecyclerViewActions.scrollToPosition<RecyclerView.ViewHolder>(
                    index + 1
                )
            )
            awaitFrame()
        }
    }

    fun calculateRelativeIndex(
        absoluteIndex: Int,
        removeCountElements: Int,
        removeOffset: Int,
        prefetchDistance: Int,
        totalItemsCount: Int
    ): Int {

        if (absoluteIndex < removeCountElements) return absoluteIndex

        val lastPaginationIndex = totalItemsCount - prefetchDistance
        if (absoluteIndex >= lastPaginationIndex) {
            return removeOffset - prefetchDistance + (absoluteIndex - lastPaginationIndex)
        }

        if (absoluteIndex < removeOffset - prefetchDistance) return absoluteIndex

        val deleteOperations =
            (absoluteIndex - (removeOffset - prefetchDistance)) / removeCountElements + 1

        return absoluteIndex - deleteOperations * removeCountElements
    }
}