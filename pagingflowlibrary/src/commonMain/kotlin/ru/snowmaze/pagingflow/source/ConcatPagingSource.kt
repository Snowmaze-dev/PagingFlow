package ru.snowmaze.pagingflow.source

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.snowmaze.pagingflow.LoadParams
import ru.snowmaze.pagingflow.PagingStatus
import ru.snowmaze.pagingflow.diff.PagingEventsListener
import ru.snowmaze.pagingflow.diff.mediums.PagingEventsMedium
import ru.snowmaze.pagingflow.errorshandler.DefaultPagingUnhandledErrorsHandler
import ru.snowmaze.pagingflow.errorshandler.PagingUnhandledErrorsHandler
import ru.snowmaze.pagingflow.internal.DataPagesManager
import ru.snowmaze.pagingflow.internal.PageLoader
import ru.snowmaze.pagingflow.internal.PagingSourcesHelper
import ru.snowmaze.pagingflow.internal.PagingSourcesManager
import ru.snowmaze.pagingflow.presenters.InvalidateBehavior
import ru.snowmaze.pagingflow.utils.DiffOperation
import ru.snowmaze.pagingflow.utils.fastFirstOrNull
import ru.snowmaze.pagingflow.utils.mapHasNext
import ru.snowmaze.pagingflow.utils.toInfo

open class ConcatPagingSource<Key : Any, Data : Any>(
    private val concatDataSourceConfig: PageLoaderConfig<Key>,
    override val pagingUnhandledErrorsHandler: PagingUnhandledErrorsHandler<Key, Data> =
        DefaultPagingUnhandledErrorsHandler()
) : PagingSource<Key, Data>, PagingEventsMedium<Key, Data> {

    private val loadDataMutex = Mutex()

    private val pagingSourcesManager = PagingSourcesManager<Key, Data>()

    private val dataPagesManager = DataPagesManager(
        pageLoaderConfig = concatDataSourceConfig,
        setDataMutex = loadDataMutex,
        pagingSourcesManager = pagingSourcesManager
    )

    val firstPageInfo
        get() = dataPagesManager.dataPages.fastFirstOrNull { !it.isNullified }?.toInfo()
    val lastPageInfo get() = dataPagesManager.dataPages.lastOrNull()?.toInfo()
    val pagesInfo get() = dataPagesManager.dataPages.map { it.toInfo() }

    val notNullifiedPagesCount get() = dataPagesManager.dataPages.count { !it.isNullified }

    val pagesCount get() = dataPagesManager.pagesCount

    private val pageLoader = PageLoader(
        pagingSourcesManager = pagingSourcesManager,
        dataPagesManager = dataPagesManager,
        pageLoaderConfig = concatDataSourceConfig,
        pagingUnhandledErrorsHandler = pagingUnhandledErrorsHandler,
        defaultLoadParams = defaultLoadParams
    )
    val concatSourceResultKey = pageLoader.pageLoaderResultKey

    val upPagingStatus = pageLoader.upPagingStatus.asStateFlow()
    val downPagingStatus = pageLoader.downPagingStatus.asStateFlow()

    override val config = dataPagesManager.config

    private val dataSourcesHelper = PagingSourcesHelper(
        pagingSourcesManager = pagingSourcesManager,
        dataPagesManager = dataPagesManager,
        pageLoader = pageLoader,
        loadDataMutex = loadDataMutex
    )

    init {
        val coroutineScope = CoroutineScope(config.processingDispatcher + SupervisorJob())
        config.coroutineScope.launch {
            try {
                awaitCancellation()
            } finally {
                coroutineScope.launch {
                    invalidate(
                        invalidateBehavior = InvalidateBehavior.INVALIDATE_IMMEDIATELY,
                        removeCachedData = true
                    )
                }
            }
        }
    }

    override fun addPagingEventsListener(listener: PagingEventsListener<Key, Data>) {
        dataPagesManager.addPagingEventsListener(listener)
    }

    override fun removePagingEventsListener(listener: PagingEventsListener<Key, Data>): Boolean {
        return dataPagesManager.removePagingEventsListener(listener)
    }

    fun addDownPagingSource(pagingSource: PagingSource<Key, out Data>) {
        pagingSourcesManager.addDownPagingSource(pagingSource as PagingSource<Key, Data>)
        pageLoader.downPagingStatus.value = pageLoader.downPagingStatus.value.mapHasNext(true)
    }

    fun addUpPagingSource(pagingSource: PagingSource<Key, out Data>) {
        pagingSourcesManager.addUpPagingSource(pagingSource as PagingSource<Key, Data>)
        pageLoader.upPagingStatus.value = pageLoader.upPagingStatus.value.mapHasNext(true)
    }

    fun removePagingSource(pagingSource: PagingSource<Key, out Data>) {
        val dataSourceIndex = pagingSourcesManager.getSourceIndex(
            pagingSource as PagingSource<Key, Data>
        )
        if (dataSourceIndex == -1) return
        removePagingSource(dataSourceIndex)
    }

    fun removePagingSource(dataSourceIndex: Int) {
        concatDataSourceConfig.coroutineScope.launch {
            loadDataMutex.withLock {
                dataSourcesHelper.remove(dataSourceIndex)
            }
        }
    }

    suspend fun setPagingSources(
        newPagingSourceList: List<PagingSource<Key, out Data>>,
        diff: (
            oldList: List<PagingSource<Key, out Data>>,
            newList: List<PagingSource<Key, out Data>>
        ) -> List<DiffOperation<PagingSource<Key, out Data>>>
    ) = dataSourcesHelper.setPagingSources(newPagingSourceList, diff)

    suspend fun invalidateAndSetPagingSources(pagingSourceList: List<PagingSource<Key, out Data>>) {
        loadDataMutex.withLock {
            withContext(concatDataSourceConfig.processingDispatcher) {
                dataPagesManager.invalidate(removeCachedData = true)
                pagingSourcesManager.replacePagingSources(
                    pagingSourceList as List<PagingSource<Key, Data>>
                )
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun load(
        loadParams: LoadParams<Key>,
    ) = loadDataMutex.withLock { pageLoader.loadData(loadParams) }

    /**
     * Deletes all pages
     * @param removeCachedData should also remove cached data for pages?
     * @param invalidateBehavior see [InvalidateBehavior]
     */
    suspend fun invalidate(
        invalidateBehavior: InvalidateBehavior?,
        removeCachedData: Boolean = false,
    ) = loadDataMutex.withLock {
        withContext(concatDataSourceConfig.processingDispatcher) {
            dataPagesManager.invalidate(
                removeCachedData = removeCachedData,
                invalidateBehavior = invalidateBehavior,
            )
            pageLoader.downPagingStatus.value = PagingStatus.Initial(
                hasNextPage = pagingSourcesManager.downPagingSources.isNotEmpty()
            )
            pageLoader.upPagingStatus.value = PagingStatus.Initial(
                hasNextPage = pagingSourcesManager.upPagingSources.isNotEmpty()
            )
        }
    }
}