package ru.snowmaze.pagingflow.sample

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.zacsweers.metro.createGraph
import ru.snowmaze.pagingflow.presenters.statePresenter
import ru.snowmaze.pagingflow.sample.di.AppGraph

@Composable
fun App(paddingValues: PaddingValues) {
    val appGraph = remember { createGraph<AppGraph>() }
    val model = viewModel<TestViewModel>(factory = appGraph.metroViewModelFactory)
    val presenter = remember { model.pagingEventsMedium.statePresenter() }
    PagingList(model,paddingValues, presenter)
}