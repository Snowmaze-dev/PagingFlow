package ru.snowmaze.pagingflow.params

import ru.snowmaze.pagingflow.sources.ConcatDataSource

object DefaultKeys {

    /**
     * Invalidates all data except new page in [ConcatDataSource]
     */
    object InvalidateData : DataKey<Boolean> {
        override val key = "invalidate_data"
    }
}