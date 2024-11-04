package ru.snowmaze.pagingflow.utils

// scary performance thing, don't watch
internal fun <T, R> Iterable<T>.flattenWithSize(): List<R> {
    val result = ArrayList<R>(sumOf { if (it is Collection<*>) it.size else 1 })
    for (element in this) {
        if (element is Collection<*>) result.addAll(element as Collection<R>)
        else result.add(element as R)
    }
    return result
}