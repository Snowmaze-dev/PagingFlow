package ru.snowmaze.pagingflow.samples

import android.os.Bundle
import android.util.Log
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
                    List(innerPadding)
                }
            }
        }
    }
}

@Composable
fun List(innerPadding: PaddingValues) {
    val model = viewModel<TestViewModel>()
    val scope = rememberCoroutineScope()
    val pagingTrigger = remember {
        PagingTrigger(
            pagingFlow = { model.pagingFlow },
            prefetchDistance = PREFETCH_DISTANCE,
            itemCount = { model.pagingDataPresenter.dataFlow.value.size }
        )
    }
    val lazyListState = rememberLazyListState()
    val items by model.pagingDataPresenter.dataFlow.collectAsState()
    Log.d("MyActivity", "items $items")
    LazyColumn(
        modifier = Modifier.fillMaxWidth(), state = lazyListState, contentPadding = innerPadding
    ) {
        itemsIndexed(items) { index, item ->
            if (item != null) TestItem(item)
//                            pagingTrigger.onItemVisible(index)
            pagingTrigger.launchedHandleLazyListState(lazyListState)
        }
    }
}

@Composable
fun PagingList(innerPadding: PaddingValues) {
    val lazyListState = rememberLazyListState()
    val model = androidx.lifecycle.viewmodel.compose.viewModel<TestPagingViewModel>()
    val items = model.flow.collectAsLazyPagingItems()
    Log.d("MyActivity", "items $items")
    items.loadState.prepend
    LazyColumn(
        state = lazyListState, contentPadding = innerPadding
    ) {
        Log.d("MyActivity", "items ${items.itemSnapshotList}")
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