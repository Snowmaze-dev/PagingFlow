package ru.snowmaze.pagingflow.params

/**
 * Paging params where you can specify typesafe params for data sources
 * @see PagingLibraryKeys
 * @see DataKey
 */
class PagingParams(map: Map<String, Any?> = emptyMap()) {

    companion object {

        val EMPTY = PagingParams()

        fun Map<DataKey<*>, Any>.pagingParams() = PagingParams(mapKeys { it.key.key })
    }

    constructor(pagingParams: PagingParams) : this(pagingParams.internalMap)

    constructor(builder: PagingParams.() -> Unit) : this(
        PagingParams(emptyMap<String, Any>()).apply(builder)
    )

    private val internalMap = LinkedHashMap(map)

    val entries get() = internalMap.entries
    val keys get() = internalMap.keys
    val size get() = internalMap.size
    val values get() = internalMap.values

    fun put(pagingParams: PagingParams) {
        internalMap.putAll(pagingParams.internalMap)
    }

    operator fun <T> get(key: DataKey<T>): T = getOrNull(key)
        ?: throw IllegalArgumentException("You should specify ${key.key} key for that operation.")

    fun <T> getOrNull(key: DataKey<T>): T? {
        return internalMap[key.key] as? T
    }

    fun clear() = internalMap.clear()

    fun isEmpty() = internalMap.isEmpty()

    fun <T> remove(key: DataKey<T>) = internalMap.remove(key.key) as? T

    fun <T> put(key: DataKey<T>, value: T?) = internalMap.put(key.key, value)

    fun containsValue(value: Any) = internalMap.containsValue(value)

    fun containsKey(key: DataKey<*>) = internalMap.containsKey(key.key)

    fun getStringKeysMap() = internalMap.mapKeys { it.key }

    override fun toString(): String {
        return getStringKeysMap().toString()
    }
}

fun <T : Any> PagingParams(pair: Pair<DataKey<T>, T>) = PagingParams {
    put(pair.first, pair.second)
}

fun PagingParams(vararg pairs: Pair<DataKey<out Any?>, Any?>) = PagingParams(buildMap(pairs.size) {
    putAll(pairs = pairs.map { it.first.key to it.second })
})

fun PagingParams.contains(key: DataKey<*>) = containsKey(key)

fun <T> PagingParams.poll(key: DataKey<T>): T? = remove(key)

fun <T> PagingParams.pollNotNull(key: DataKey<T>) = requireNotNull(poll(key))

fun <T> PagingParams?.getOrElse(key: DataKey<T>, orElse: () -> T): T {
    return this?.getOrNull(key) ?: orElse()
}