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
import ru.snowmaze.pagingflow.PaginationDirection
import ru.snowmaze.pagingflow.PagingStatus
import ru.snowmaze.pagingflow.diff.DataChangedCallback
import ru.snowmaze.pagingflow.diff.mediums.PagingDataChangesMedium
import ru.snowmaze.pagingflow.errorshandler.DefaultPagingUnhandledErrorsHandler
import ru.snowmaze.pagingflow.internal.DataPagesManager
import ru.snowmaze.pagingflow.internal.PagingSourcesHelper
import ru.snowmaze.pagingflow.internal.PagingSourcesManager
import ru.snowmaze.pagingflow.internal.PageLoader
import ru.snowmaze.pagingflow.params.PagingLibraryParamsKeys
import ru.snowmaze.pagingflow.params.PagingParams
import ru.snowmaze.pagingflow.params.ReturnPagingLibraryKeys
import ru.snowmaze.pagingflow.presenters.InvalidateBehavior
import ru.snowmaze.pagingflow.result.LoadResult
import ru.snowmaze.pagingflow.result.mapParams
import ru.snowmaze.pagingflow.utils.DiffOperation
import ru.snowmaze.pagingflow.utils.fastFirstOrNull
import ru.snowmaze.pagingflow.utils.mapHasNext
import ru.snowmaze.pagingflow.utils.toInfo

class ConcatPagingSource<Key : Any, Data : Any>(
    private val concatDataSourceConfig: PageLoaderConfig<Key>,
) : PagingSource<Key, Data>, PagingDataChangesMedium<Key, Data> {

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

    val pagesCount get() = dataPagesManager.dataPages.count { !it.isNullified }

    val currentPagesCount get() = dataPagesManager.currentPagesCount
    val isLoading
        get() = upPagingStatus.value is PagingStatus.Loading ||
                downPagingStatus.value is PagingStatus.Loading

    override val pagingUnhandledErrorsHandler = DefaultPagingUnhandledErrorsHandler()

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

    override fun addDataChangedCallback(callback: DataChangedCallback<Key, Data>) {
        dataPagesManager.addDataChangedCallback(callback)
    }

    override fun removeDataChangedCallback(callback: DataChangedCallback<Key, Data>): Boolean {
        return dataPagesManager.removeDataChangedCallback(callback)
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

    suspend fun invalidateAndSetPagingSources(pagingSourceList: List<PagingSource<Key, Data>>) {
        loadDataMutex.withLock {
            withContext(concatDataSourceConfig.processingDispatcher) {
                dataPagesManager.invalidate(removeCachedData = true)
                pagingSourcesManager.replacePagingSources(pagingSourceList)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun load(
        loadParams: LoadParams<Key>,
    ) = loadDataMutex.withLock {
        val isPaginationDown = loadParams.paginationDirection == PaginationDirection.DOWN

        val loadSeveralPages = loadParams.pagingParams
            ?.getOrNull(PagingLibraryParamsKeys.LoadSeveralPages)

        if (loadSeveralPages != null) {
            val sourceResultKey = pageLoader.sourceResultKey
            var lastResult: LoadResult<Key, Data>? = null
            var lastResultWithLoad: LoadResult<Key, Data>? = null
            val resultPagingParams = mutableListOf<PagingParams?>()
            val key = pageLoader.pageLoaderResultKey
            val pagingStatus = if (isPaginationDown) pageLoader.downPagingStatus
            else pageLoader.upPagingStatus
            while (true) {
                val currentResult = lastResult?.returnData?.getOrNull(sourceResultKey) ?: lastResult
                val currentPagingParams = loadSeveralPages.getPagingParams(
                    currentResult as? LoadResult<Any, Any>
                ) ?: break
                val defaultPagingParams =
                    concatDataSourceConfig.defaultParamsProvider().pagingParams
                defaultPagingParams?.put(currentPagingParams)
                lastResult = pageLoader.loadData(
                    loadParams = loadParams.copy(
                        pagingParams = defaultPagingParams ?: currentPagingParams
                    ),
                    lastPageIndex = null,
                    shouldReplaceOnConflict = true,
                    shouldSetNewStatus = false
                )
                resultPagingParams += lastResult.returnData?.getOrNull(key)?.returnData
                    ?: lastResult.returnData
                if (lastResult is LoadResult.NothingToLoad &&
                    pagingStatus.value is PagingStatus.Loading || !pagingStatus.value.hasNextPage
                ) break
                lastResultWithLoad = lastResult
                if (lastResult is LoadResult.Failure) continue
                loadSeveralPages.onSuccess?.invoke(
                    lastResult.returnData?.get(sourceResultKey) as LoadResult.Success<Any, Any>
                )
            }
            lastResult ?: throw IllegalArgumentException("Should load at least one pagination.")
            lastResultWithLoad?.returnData?.get(pageLoader.statusKey)?.let {
                (if (isPaginationDown) pageLoader.downPagingStatus
                else pageLoader.upPagingStatus).value = it
            }
            lastResult = lastResult.mapParams(
                lastResult.returnData?.let { PagingParams(it) } ?: PagingParams()
            )
            val returnData = lastResult.returnData
            returnData?.getOrNull(key)
                ?.returnData?.let { PagingParams(it) }?.let {
                    returnData.put(key, returnData[key].copy(returnData = it))
                }
            (returnData?.getOrNull(key)?.returnData ?: returnData)?.put(
                ReturnPagingLibraryKeys.PagingParamsList,
                resultPagingParams
            )
            return@withLock lastResult
        } else pageLoader.loadData(
            loadParams = loadParams,
            lastPageIndex = null,
            shouldReplaceOnConflict = true,
            shouldSetNewStatus = true
        )
    }

    /**
     * Deletes all pages
     * @param removeCachedData should also remove cached data for pages?
     * @param invalidateBehavior see [InvalidateBehavior]
     */
    suspend fun invalidate(
        invalidateBehavior: InvalidateBehavior?,
        removeCachedData: Boolean
    ) = loadDataMutex.withLock {
        withContext(concatDataSourceConfig.processingDispatcher) {
            dataPagesManager.invalidate(
                invalidateBehavior = invalidateBehavior,
                removeCachedData = removeCachedData,
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