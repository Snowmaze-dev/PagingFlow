package ru.snowmaze.pagingflow.sample

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import ru.snowmaze.pagingflow.utils.PagingTrigger

@Composable
fun PagingTrigger.launchedHandleLazyListState(lazyListState: LazyListState) {
    LaunchedEffect(key1 = remember { derivedStateOf { lazyListState.firstVisibleItemIndex } }) {
        handleLazyListState(lazyListState)
    }
}

fun PagingTrigger.handleLazyListState(lazyListState: LazyListState) {
    if (!onItemVisible(lazyListState.firstVisibleItemIndex)) {
        onItemVisible(lazyListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return)
    }
}