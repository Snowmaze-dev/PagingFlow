package ru.snowmaze.pagingflow.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.collectAsLazyPagingItems
import ru.snowmaze.pagingflow.sample.ui.theme.TestComposeAppTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TestComposeAppTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    App(innerPadding)
                }
            }
        }
    }
}

@Composable
fun PagingList(innerPadding: PaddingValues) {
    val lazyListState = rememberLazyListState()
    val model = viewModel<TestAndroidxPagingViewModel>()
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