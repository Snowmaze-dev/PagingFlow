package ru.snowmaze.pagingflow.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.snowmaze.pagingflow.presenters.StatePagingDataPresenter
import ru.snowmaze.pagingflow.presenters.data
import ru.snowmaze.pagingflow.sample.TestViewModel.Companion.PREFETCH_DISTANCE
import ru.snowmaze.pagingflow.utils.PagingTrigger

@Composable
fun PagingList(
    model: TestViewModel,
    innerPadding: PaddingValues,
    pagingDataPresenter: StatePagingDataPresenter<Int, TestItem>
) {
    val scope = rememberCoroutineScope()
    val pagingTrigger = remember {
        PagingTrigger(
            pagingFlow = { model.pagingFlow },
            prefetchDownDistance = PREFETCH_DISTANCE,
            itemCount = { pagingDataPresenter.data.size },
            currentStartIndex = { pagingDataPresenter.startIndex },
            coroutineScope = scope
        )
    }
    val lazyListState = rememberLazyListState()
    val items by pagingDataPresenter.latestDataFlow.collectAsStateWithLifecycle()
    LazyColumn(
        modifier = Modifier.fillMaxWidth(), state = lazyListState, contentPadding = innerPadding
    ) {
        itemsIndexed(items.data, key = { index, item ->
            item?.let { item.javaClass.simpleName.hashCode() + item.hashCode() }
                ?: (Int.MAX_VALUE - index)
        }) { index, item ->
            when (item) {
                null, is TestItem.Item -> TestItem(item)
                is TestItem.Loader -> LoaderItem(item)
            }
            pagingTrigger.launchedHandleLazyListState(lazyListState)
        }
    }
}

@Composable
fun LoaderItem(item: TestItem.Loader) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            modifier = Modifier.padding(vertical = 16.dp),
        )
    }
}

@Composable
fun TestItem(item: TestItem.Item?) {
    Text(
        modifier = Modifier.padding(top = 4.dp, start = 8.dp, end = 8.dp, bottom = 20.dp).let() {
            if (item == null) it
                .size(128.dp, 24.dp)
                .background(Color.Gray, RoundedCornerShape(8.dp))
            else it
        },
        text = item?.text ?: ""
    )
}