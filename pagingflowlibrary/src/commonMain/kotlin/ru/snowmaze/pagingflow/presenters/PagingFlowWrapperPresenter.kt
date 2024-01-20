package ru.snowmaze.pagingflow.presenters

import kotlinx.coroutines.flow.MutableStateFlow
import ru.snowmaze.pagingflow.PagingFlow
import ru.snowmaze.pagingflow.diff.DataChangedCallback

class PagingFlowWrapperPresenter<Key : Any, Data : Any>(pagingFlow: PagingFlow<Key, Data, *>) :
    PagingDataPresenter<Key, Data>() {

    override val dataFlow = MutableStateFlow(listOf<Data?>())

    init {
        pagingFlow.addDataChangedCallback(object : DataChangedCallback<Key, Data> {
            override fun onPageAdded(key: Key?, pageIndex: Int, items: List<Data>) {
                callDataChangedCallbacks { onPageAdded(key, pageIndex, items) }
            }

            override fun onPageChanged(key: Key?, pageIndex: Int, items: List<Data?>) {
                callDataChangedCallbacks { onPageChanged(key, pageIndex, items) }
            }

            override fun onPageRemoved(key: Key?, pageIndex: Int) {
                callDataChangedCallbacks { onPageRemoved(key, pageIndex) }
            }

            override fun onInvalidate() {
                callDataChangedCallbacks { onInvalidate() }
            }
        })
    }
}

fun <Key : Any, Data : Any> PagingFlow<Key, Data, *>.pagingFlowWrapperPresenter(
) = PagingFlowWrapperPresenter(pagingFlow = this)