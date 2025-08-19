package ru.snowmaze.pagingflow.utils

import androidx.collection.MutableScatterMap
import dev.andrewbailey.diff.differenceOf
import ru.snowmaze.pagingflow.PagingFlow
import ru.snowmaze.pagingflow.source.PagingSource

suspend fun PagingFlow<*, *>.setPagingSourcesWithDiff(sources: List<PagingSource<*, *>>) =
    setPagingSources(sources as List<Nothing>) { oldList, newList ->
        val difference = differenceOf(oldList, newList)
        buildList {
            val added = MutableScatterMap<Int, Int>()
            fun remove(index: Int, count: Int) {
                add(DiffOperation.Remove(index, count))
            }

            fun addOperation(index: Int, items: List<PagingSource<*, *>>) {
                added[index] = (added[index] ?: 0) + 1
                add(DiffOperation.Add(index, items.size, items))
            }

            fun move(fromIndex: Int, toIndex: Int) {
                add(DiffOperation.Move(fromIndex, calculateRelativeIndex(added, toIndex)))
            }
            for (operation in difference.operations) {
                when (operation) {
                    is dev.andrewbailey.diff.DiffOperation.Remove -> {
                        remove(operation.index, 1)
                    }

                    is dev.andrewbailey.diff.DiffOperation.Add -> {
                        addOperation(operation.index, listOf(operation.item))
                    }

                    is dev.andrewbailey.diff.DiffOperation.Move -> {
                        move(operation.fromIndex, operation.toIndex - 1) // TODO perhaps bug here
                    }

                    is dev.andrewbailey.diff.DiffOperation.RemoveRange -> {
                        remove(operation.startIndex, operation.endIndex - operation.startIndex)
                    }

                    is dev.andrewbailey.diff.DiffOperation.AddAll -> {
                        addOperation(operation.index, operation.items)
                    }

                    is dev.andrewbailey.diff.DiffOperation.MoveRange -> {
                        val oldIndex = operation.fromIndex
                        val newIndex = operation.toIndex
                        val count = operation.itemCount
                        when {
                            newIndex < oldIndex -> {
                                (0 until count).forEach { item ->
                                    move(oldIndex + item, newIndex + item)
                                }
                            }

                            newIndex > oldIndex -> {
                                repeat(count) {
                                    move(oldIndex, newIndex)
                                }
                            }
                        }
                    }
                }
            }
        } as List<Nothing>
    }

private inline fun calculateRelativeIndex(added: MutableScatterMap<Int, Int>, index: Int): Int {
    var sum = 0
    added.forEach { key, value ->
        if (index >= key) sum =+ value
    }
    return index + sum
}