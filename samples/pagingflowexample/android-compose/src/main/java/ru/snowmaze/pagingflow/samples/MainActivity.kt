package ru.snowmaze.pagingflow.samples

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.paging.compose.collectAsLazyPagingItems
import ru.snowmaze.pagingflow.presenters.PagingDataPresenter
import ru.snowmaze.pagingflow.presenters.data
import ru.snowmaze.pagingflow.presenters.dataFlow
import ru.snowmaze.pagingflow.presenters.pagingDataPresenter
import ru.snowmaze.pagingflow.utils.PagingTrigger
import ru.snowmaze.pagingflow.samples.TestViewModel.Companion.PREFETCH_DISTANCE
import ru.snowmaze.pagingflow.samples.ui.theme.TestComposeAppTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TestComposeAppTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val model = viewModel<TestViewModel>()
                    val presenter = remember { model.pagingFlow.pagingDataPresenter() }
                    List(innerPadding, presenter)
                }
            }
        }
    }
}

@Composable
fun List(innerPadding: PaddingValues, pagingDataPresenter: PagingDataPresenter<Int, String>) {
    val model = viewModel<TestViewModel>()
    val scope = rememberCoroutineScope()
    val pagingTrigger = remember {
        PagingTrigger(
            pagingFlow = { model.pagingFlow },
            prefetchDownDistance = PREFETCH_DISTANCE,
            itemCount = { pagingDataPresenter.data.size },
            coroutineScope = scope
        )
    }
    val lazyListState = rememberLazyListState()
    val items by pagingDataPresenter.dataFlow.collectAsState(emptyList())
    LazyColumn(
        modifier = Modifier.fillMaxWidth(), state = lazyListState, contentPadding = innerPadding
    ) {
        itemsIndexed(items) { index, item ->
            if (item != null) TestItem(item)
            pagingTrigger.launchedHandleLazyListState(lazyListState)
        }
    }
}

@Composable
fun PagingList(innerPadding: PaddingValues) {
    val lazyListState = rememberLazyListState()
    val model = androidx.lifecycle.viewmodel.compose.viewModel<TestPagingViewModel>()
    val items = model.flow.collectAsLazyPagingItems()
    LazyColumn(
        state = lazyListState, contentPadding = innerPadding
    ) {
        items(items.itemCount) { index ->
            val item = items[index] ?: return@items

            TestItem(item)
        }
    }
}

@Composable
fun TestItem(item: String) {
    Text(
        modifier = Modifier.padding(top = 4.dp, start = 8.dp, end = 8.dp, bottom = 20.dp),
        text = item
    )
}