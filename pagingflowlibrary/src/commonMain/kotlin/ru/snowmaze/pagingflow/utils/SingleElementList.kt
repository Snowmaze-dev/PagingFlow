package ru.snowmaze.pagingflow.utils

internal class SingleElementList<T : Any> : List<T>, Iterator<T> {

    var element: T? = null
    private var iterated = false

    override val size = 1

    override fun contains(element: T) = this.element === element

    override fun containsAll(
        elements: Collection<T>
    ) = if (elements.size == 1) contains(elements.first())
    else if (elements.isEmpty()) true
    else false

    override fun get(index: Int) = element!!

    override fun indexOf(element: T) = if (element === this.element) 0 else -1

    override fun isEmpty() = false

    override fun iterator(): Iterator<T> {
        iterated = false
        return this
    }

    override fun lastIndexOf(element: T) = if (this.element === element) 0 else -1

    override fun listIterator() = listOf(element!!).listIterator()

    override fun listIterator(index: Int) = listOf(element!!).listIterator(index)

    override fun subList(fromIndex: Int, toIndex: Int) =
        listOf(element!!).subList(fromIndex, toIndex)

    override fun hasNext() = !iterated

    override fun next() = if (iterated) throw IllegalStateException() else {
        iterated = true
        element!!
    }
}