package ru.snowmaze.pagingflow.presenters

import kotlinx.coroutines.flow.StateFlow

interface PagingDataPresenter<Key : Any, Data : Any> {

    val dataFlow: StateFlow<List<Data?>>
}

val PagingDataPresenter<*, *>.itemCount get() = dataFlow.value.size