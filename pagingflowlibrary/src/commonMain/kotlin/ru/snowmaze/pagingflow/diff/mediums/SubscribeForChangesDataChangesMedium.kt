package ru.snowmaze.pagingflow.diff.mediums

import ru.snowmaze.pagingflow.diff.DataChangedCallback
import kotlin.concurrent.Volatile

abstract class SubscribeForChangesDataChangesMedium<Key : Any, Data : Any, Data1: Any>(
    private val pagingDataChangesMedium: PagingDataChangesMedium<Key, Data1>,
) : DefaultPagingDataChangesMedium<Key, Data>() {

    private var lastCallback: DataChangedCallback<Key, Data1>? = null

    @Volatile
    private var _callbackCount = 0
    val callbackCount get() = _callbackCount

    private fun subscribeForEvents() {
        lastCallback?.let { pagingDataChangesMedium.removeDataChangedCallback(it) }
        val newCallback = getChangesCallback()
        pagingDataChangesMedium.addDataChangedCallback(newCallback)
        lastCallback = newCallback
    }

    private fun unsubscribeFromEvents() {
        lastCallback?.let { pagingDataChangesMedium.removeDataChangedCallback(it) }
        lastCallback = null
    }

    override fun addDataChangedCallback(callback: DataChangedCallback<Key, Data>) {
        super.addDataChangedCallback(callback)
        _callbackCount++
        if (_callbackCount == 1) subscribeForEvents()
    }

    override fun removeDataChangedCallback(callback: DataChangedCallback<Key, Data>): Boolean {
        val removed = super.removeDataChangedCallback(callback)
        if (removed) {
            _callbackCount--
            if (_callbackCount == 0) unsubscribeFromEvents()
        }
        return removed
    }

    abstract fun getChangesCallback(): DataChangedCallback<Key, Data1>
}