package ru.snowmaze.pagingflow.utils

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

// scary performance thing, don't watch
internal fun <T, R> List<T>.flattenWithSize(): List<R> {
    val result = ArrayList<R>(fastSumOf { if (it is Collection<*>) it.size else 1 })
    for (element in this) {
        if (element is Collection<*>) result.addAll(element as Collection<R>)
        else result.add(element as R)
    }
    return result
}

@OptIn(ExperimentalContracts::class)
inline fun <T> List<T>.fastForEach(action: (T) -> Unit) {
    contract { callsInPlace(action) }
    for (index in 0..size - 1) {
        action(get(index))
    }
}

@OptIn(ExperimentalContracts::class)
internal inline fun <T> List<T>.fastSumOf(selector: (T) -> Int?): Int {
    contract { callsInPlace(selector) }
    var sum = 0
    fastForEach { element ->
        sum += selector(element) ?: return sum
    }
    return sum
}

@OptIn(ExperimentalContracts::class)
internal inline fun <T> List<T>.fastIndexOfFirst(predicate: (T) -> Boolean): Int {
    contract { callsInPlace(predicate) }
    var index = 0
    fastForEach { item ->
        if (predicate(item)) return index
        index++
    }
    return -1
}

@OptIn(ExperimentalContracts::class)
internal inline fun <T> List<T>.fastIndexOfLast(predicate: (T) -> Boolean): Int {
    contract { callsInPlace(predicate) }
    for (index in (0..size - 1).reversed()) {
        if (predicate(get(index))) return index
    }
    return -1
}

internal inline fun <T> List<T>.fastFirstOrNull(predicate: (T) -> Boolean): T? {
    fastForEach { element -> if (predicate(element)) return element }
    return null
}
