package ru.snowmaze.pagingflow.samples

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import ru.snowmaze.pagingflow.utils.PagingTrigger

@Composable
inline fun <reified VM : dev.icerock.moko.mvvm.viewmodel.ViewModel> viewModel(
    viewModelStoreOwner: ViewModelStoreOwner = checkNotNull(LocalViewModelStoreOwner.current) {
        "No ViewModelStoreOwner was provided via LocalViewModelStoreOwner"
    },
    key: String? = null,
    factory: ViewModelProvider.Factory? = null,
    extras: CreationExtras = if (viewModelStoreOwner is HasDefaultViewModelProviderFactory) {
        viewModelStoreOwner.defaultViewModelCreationExtras
    } else {
        CreationExtras.Empty
    }
): VM = androidx.lifecycle.viewmodel.compose.viewModel(
    viewModelStoreOwner = viewModelStoreOwner,
    key = key,
    factory = factory,
    extras = extras
)

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