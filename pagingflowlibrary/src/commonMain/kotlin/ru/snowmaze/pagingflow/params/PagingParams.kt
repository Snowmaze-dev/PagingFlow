package ru.snowmaze.pagingflow.params

import androidx.collection.MutableScatterMap
import androidx.collection.ScatterMap
import androidx.collection.emptyScatterMap

/**
 * Paging params where you can specify typesafe params for paging sources
 * @see PagingLibraryParamsKeys
 * @see DataKey
 */
open class PagingParams internal constructor(
    internalMap: ScatterMap<String, Any?>,
    reuseMap: Boolean = true
) {

    companion object {

        val EMPTY = PagingParams(emptyScatterMap())

        operator fun invoke(
            capacity: Int = 5,
            builder: (MutablePagingParams.() -> Unit)? = null
        ): MutablePagingParams {
            val params = MutablePagingParams(capacity)
            if (builder != null) params.builder()
            return params
        }
    }

    constructor(pagingParams: PagingParams) : this(pagingParams.map, reuseMap = false)

    internal open val map = if (reuseMap) internalMap
    else MutableScatterMap<String, Any?>(internalMap.size).apply {
        putAll(internalMap)
    }

    private val mapInterface = map.asMap()

    val entries get() = mapInterface.entries
    val keys get() = mapInterface.keys
    val size get() = map.size
    val values get() = mapInterface.values

    operator fun <T> get(key: DataKey<T>): T = getOrNull(key)
        ?: throw IllegalArgumentException("You should specify ${key.key} key for that operation.")

    fun <T> getOrNull(key: DataKey<T>): T? {
        return map[key.key] as? T
    }

    fun isEmpty() = map.isEmpty()

    fun containsValue(value: Any) = map.containsValue(value)

    fun containsKey(key: DataKey<*>) = map.containsKey(key.key)

    fun getStringKeysMap() = mapInterface.mapKeys { it.key }

    override fun toString(): String {
        return getStringKeysMap().toString()
    }
}

class MutablePagingParams(
    map: ScatterMap<String, Any?>,
    reuseMap: Boolean = true
) : PagingParams(map, reuseMap) {

    companion object {
        fun noCapacity() = MutablePagingParams(0)
    }

    constructor(): this(MutableScatterMap())

    constructor(capacity: Int) : this(MutableScatterMap(capacity))

    constructor(pagingParams: PagingParams) : this(
        pagingParams.map, reuseMap = false
    )

    private val internalMap get() = map as MutableScatterMap<String, Any?>

    fun put(pagingParams: PagingParams) {
        internalMap.putAll(pagingParams.map)
    }

    fun clear() = internalMap.clear()

    fun <T> remove(key: DataKey<T>) = internalMap.remove(key.key) as? T

    fun <T> put(key: DataKey<T>, value: T?) = internalMap.put(key.key, value)

    fun asReadOnly(): PagingParams = PagingParams(internalMap)
}

fun <T : Any> pagingParamsOf(pair: Pair<DataKey<T>, T>) = PagingParams(1) {
    put(pair.first, pair.second)
}

fun pagingParamsOf(
    vararg pairs: Pair<DataKey<out Any?>, Any?>
) = MutablePagingParams(MutableScatterMap<String, Any?>(pairs.size).apply {
    putAll(pairs = pairs.map { it.first.key to it.second })
})

fun PagingParams.contains(key: DataKey<*>) = containsKey(key)

fun <T> MutablePagingParams.poll(key: DataKey<T>): T? = remove(key)

fun <T> MutablePagingParams.pollNotNull(key: DataKey<T>) = requireNotNull(poll(key))

fun <T> MutablePagingParams?.getOrElse(key: DataKey<T>, orElse: () -> T): T {
    return this?.getOrNull(key) ?: orElse()
}

fun PagingParams.toMutableParams() = MutablePagingParams(this)