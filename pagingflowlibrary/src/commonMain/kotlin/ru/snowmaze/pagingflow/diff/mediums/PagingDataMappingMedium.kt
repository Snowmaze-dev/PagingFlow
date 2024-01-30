package ru.snowmaze.pagingflow.diff.mediums

import ru.snowmaze.pagingflow.diff.DataChangedCallback

class PagingDataMappingMedium<Key : Any, Data : Any, NewData : Any>(
    dataChangedCallback: DataChangesMedium<Key, Data>,
    private val transform: (List<Data?>) -> List<NewData?>
) : DefaultDataChangesMedium<Key, NewData>() {

    init {
        dataChangedCallback.addDataChangedCallback(object : DataChangedCallback<Key, Data> {
            override fun onPageAdded(
                key: Key?,
                pageIndex: Int,
                sourceIndex: Int,
                items: List<Data>
            ) {
                val transformResult = transform(items) as List<NewData>
                callDataChangedCallbacks {
                    onPageAdded(key, pageIndex, sourceIndex, transformResult)
                }
            }

            override fun onPageChanged(
                key: Key?,
                pageIndex: Int,
                sourceIndex: Int,
                items: List<Data?>
            ) {
                val transformResult = transform(items)
                callDataChangedCallbacks {
                    onPageChanged(key, pageIndex, sourceIndex, transformResult)
                }
            }

            override fun onPageRemoved(key: Key?, pageIndex: Int, sourceIndex: Int) {
                callDataChangedCallbacks {
                    onPageRemoved(key, pageIndex, sourceIndex)
                }
            }

            override fun onInvalidate() = callDataChangedCallbacks { onInvalidate() }
        })
    }
}