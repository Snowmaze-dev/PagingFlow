package ru.snowmaze.pagingflow.utils

fun <T> Iterable<Collection<T>>.flattenWithSize(): List<T> {
    val result = ArrayList<T>(sumOf { it.size })
    for (element in this) {
        result.addAll(element)
    }
    return result
}