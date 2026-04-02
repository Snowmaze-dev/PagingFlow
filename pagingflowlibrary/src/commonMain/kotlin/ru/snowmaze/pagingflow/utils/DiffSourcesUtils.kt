package ru.snowmaze.pagingflow.utils

import dev.andrewbailey.diff.differenceOf
import ru.snowmaze.pagingflow.PagingFlow
import ru.snowmaze.pagingflow.source.PagingSource

suspend fun PagingFlow<*, *>.setPagingSourcesWithDiff(sources: List<PagingSource<*, *>>) =
    setPagingSources(sources as List<Nothing>) { oldList, newList ->
        val difference = differenceOf(oldList, newList)
        buildList {
            fun remove(index: Int, count: Int) {
                add(DiffOperation.Remove(index, count))
            }

            fun addOperation(index: Int, items: List<PagingSource<*, *>>) {
                add(DiffOperation.Add(index, items.size, items))
            }

            fun move(fromIndex: Int, toIndex: Int) {
                add(
                    DiffOperation.Move(
                        fromIndex,
                        if (toIndex > fromIndex) toIndex - 1 else toIndex
                    )
                )
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
                        move(operation.fromIndex, operation.toIndex)
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