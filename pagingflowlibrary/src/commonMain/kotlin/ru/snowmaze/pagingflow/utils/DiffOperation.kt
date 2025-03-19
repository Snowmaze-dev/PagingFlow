package ru.snowmaze.pagingflow.utils

sealed class DiffOperation<T> {

    data class Add<T>(
        val index: Int,
        val count: Int,
        val items: List<T>? = null
    ) : DiffOperation<T>()

    data class Remove<T>(
        val index: Int,
        val count: Int
    ) : DiffOperation<T>()

    data class Move<T>(
        val fromIndex: Int,
        val toIndex: Int
    ) : DiffOperation<T>()
}