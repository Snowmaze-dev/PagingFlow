package ru.snowmaze.pagingflow.presenters

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class BasicStatePagingDataPresenter<Key : Any, Data : Any>(
    private val presenter: PagingDataPresenter<Key, Data>,
    coroutineScope: CoroutineScope
) : StatePagingDataPresenter<Key, Data> {

    override val latestDataFlow: StateFlow<LatestData<Data>> = presenter.latestDataFlow.stateIn(
        scope = coroutineScope,
        started = SharingStarted.Eagerly,
        initialValue = LatestData(emptyList())
    )

    override val startIndex: Int get() = presenter.startIndex
}