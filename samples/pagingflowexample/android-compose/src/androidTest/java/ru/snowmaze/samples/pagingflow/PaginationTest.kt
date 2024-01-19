package ru.snowmaze.samples.pagingflow

import androidx.test.ext.junit.rules.ActivityScenarioRule
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import ru.snowmaze.pagingflow.samples.MainActivity

class PaginationTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun testPagination(): Unit = runBlocking {
//        val removeOffset = EXAMPLE_LOAD_SIZE * REMOVE_PAGE_OFFSET
//        IdlingPolicies.setIdlingResourceTimeout(10, TimeUnit.SECONDS);
//        val recycler = onView(withId(R.id.main_recycler_view))
//        for (index in 0 until TOTAL_ITEMS_COUNT) {
//            onView(withText("Item $index")).check(matches(isDisplayed()))
//            val relativeIndex = calculateRelativeIndex(
//                absoluteIndex = index,
//                removeCountElements = EXAMPLE_LOAD_SIZE,
//                removeOffset = removeOffset,
//                prefetchDistance = PRELOAD_DISTANCE,
//                totalItemsCount = TOTAL_ITEMS_COUNT,
//            ) + 1
//            recycler.perform(
//                RecyclerViewActions.scrollToPosition<RecyclerView.ViewHolder>(
//                    relativeIndex
//                )
//            )
//            awaitFrame()
//        }
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