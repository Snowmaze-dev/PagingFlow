package ru.snowmaze.pagingflow.params

import kotlinx.coroutines.Job

object ReturnPagingLibraryKeys {

    object DataSetJob: DataKey<Job> {
        override val key = "data_set_job"
    }

    object PagingParamsList: DataKey<List<PagingParams?>> {
        override val key = "paging_params_list"
    }
}