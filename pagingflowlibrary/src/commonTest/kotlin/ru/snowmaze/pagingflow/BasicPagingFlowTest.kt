package ru.snowmaze.pagingflow

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class BasicPagingFlowTest {

    val loadSize = Random.nextInt(5, 30)

    private val basePagingFlowConfiguration = PagingFlowConfiguration(
        defaultParams = LoadParams(loadSize, 0),
        removePagesOffset = null,
        mainDispatcher = Dispatchers.Unconfined,
        processingDispatcher = Dispatchers.Unconfined
    )

    @Test
    fun testFlow() = runBlocking(Dispatchers.Default.limitedParallelism(1)) {
        val otherFlow = flow {
            emit(6)
        }
        val flow = flow<Int> {
            emit(5)
            delay(500)
            emitAll(otherFlow)
        }
        var lastTime = Clock.System.now().toEpochMilliseconds()
        flow.collect {
            val timeDiff = Clock.System.now().toEpochMilliseconds() - lastTime
            println("timeDiff:$timeDiff flowValue:$it")
            lastTime = Clock.System.now().toEpochMilliseconds()
        }
    }

    @Test
    fun basePaginationUseCaseTest() = runBlocking {
        val totalCount = Random.nextInt(80, 1000)
        val testDataSource = TestDataSource(totalCount)
        val pagingFlow = buildPagingFlow(basePagingFlowConfiguration) {
            addDataSource(testDataSource)
        }

        pagingFlow.testLoadEverything(listOf(testDataSource), loadSize)
        invalidateAndCheckLoadingRight(pagingFlow, testDataSource)
    }

    @Test
    fun baseThreeSourcesPaginationUseCaseTest() = runBlocking {
        val firstTestDataSource = TestDataSource(Random.nextInt(80, 500))
        val secondTestDataSource = TestDataSource(Random.nextInt(80, 500))
        val thirdTestDataSource = TestDataSource(Random.nextInt(80, 500))
        val pagingFlow = buildPagingFlow(basePagingFlowConfiguration) {
            addDataSource(firstTestDataSource)
            addDataSource(secondTestDataSource)
            addDataSource(thirdTestDataSource)
        }

        pagingFlow.testLoadEverything(
            listOf(firstTestDataSource, secondTestDataSource, thirdTestDataSource),
            loadSize
        )
        invalidateAndCheckLoadingRight(pagingFlow, firstTestDataSource)
    }

    private suspend fun invalidateAndCheckLoadingRight(
        pagingFlow: PagingFlow<Int, String, DefaultPagingStatus>,
        firstSource: TestDataSource
    ) {
        pagingFlow.invalidate()
        val resultAfterValidate = pagingFlow.loadNextPageWithResult()
        assertEquals(true, resultAfterValidate.asSuccess().hasNext)
        assertEquals(firstSource.getItems(loadSize), pagingFlow.dataFlow.value)
    }
}