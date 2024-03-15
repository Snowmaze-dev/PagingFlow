package ru.snowmaze.pagingflow.params

import ru.snowmaze.pagingflow.PagingFlow
import ru.snowmaze.pagingflow.sources.ConcatDataSource
import ru.snowmaze.pagingflow.sources.DataSource

object PagingLibraryKeys {

    /**
     * Invalidates all data except new page in [ConcatDataSource]
     */
    object InvalidateData : DataKey<Boolean> {

        override val key = "invalidate_data"
    }

    /**
     * Awaits until first data event is received from [DataSource.load] flow
     * You can supply this param to [PagingFlow.loadNextPageWithResult] params
     * You can specify await time also
     */
    object AwaitFirstDataSet : DataKey<Long?> {
        override val key = "await_data_load"
    }
}