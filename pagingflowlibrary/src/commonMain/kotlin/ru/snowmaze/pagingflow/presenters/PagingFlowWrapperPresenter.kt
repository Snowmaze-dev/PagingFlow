package ru.snowmaze.pagingflow.presenters

import kotlinx.coroutines.flow.MutableStateFlow
import ru.snowmaze.pagingflow.PagingFlow
import ru.snowmaze.pagingflow.diff.DataChangedCallback

/**
 * Wrapper around [PagingFlow] class which can be wrapped in other data presenters like [pagingDataPresenter]
 */
class PagingFlowWrapperPresenter<Key : Any, Data : Any>(pagingFlow: PagingFlow<Key, Data, *>) :
    PagingDataPresenter<Key, Data>() {

    override val dataFlow = MutableStateFlow(emptyList<Data?>())

    init {
        pagingFlow.addDataChangedCallback(createDefaultDataChangedCallback())
    }
}

fun <Key : Any, Data : Any> PagingFlow<Key, Data, *>.pagingFlowWrapperPresenter(
) = PagingFlowWrapperPresenter(pagingFlow = this)