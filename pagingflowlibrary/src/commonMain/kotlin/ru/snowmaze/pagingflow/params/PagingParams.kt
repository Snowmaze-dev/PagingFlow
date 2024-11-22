package ru.snowmaze.pagingflow.params

/**
 * Paging params where you can specify typesafe params for paging sources
 * @see PagingLibraryParamsKeys
 * @see DataKey
 */
open class PagingParams internal constructor(
    internalMap: Map<String, Any?>,
    reuseMap: Boolean = true
) {

    companion object {

        val EMPTY = PagingParams(emptyMap())

        operator fun invoke(
            capacity: Int = 5,
            builder: MutablePagingParams.() -> Unit
        ) = MutablePagingParams(capacity).apply(builder)
    }

    constructor(pagingParams: PagingParams) : this(pagingParams.map, reuseMap = false)

    internal open val map = if (reuseMap) internalMap
    else LinkedHashMap(internalMap)

    val entries get() = map.entries
    val keys get() = map.keys
    val size get() = map.size
    val values get() = map.values

    operator fun <T> get(key: DataKey<T>): T = getOrNull(key)
        ?: throw IllegalArgumentException("You should specify ${key.key} key for that operation.")

    fun <T> getOrNull(key: DataKey<T>): T? {
        return map[key.key] as? T
    }

    fun isEmpty() = map.isEmpty()

    fun containsValue(value: Any) = map.containsValue(value)

    fun containsKey(key: DataKey<*>) = map.containsKey(key.key)

    fun getStringKeysMap() = map.mapKeys { it.key }

    override fun toString(): String {
        return getStringKeysMap().toString()
    }
}

class MutablePagingParams internal constructor(
    map: Map<String, Any?>,
    reuseMap: Boolean = true
) : PagingParams(map, reuseMap) {

    constructor(): this(LinkedHashMap())

    constructor(capacity: Int) : this(LinkedHashMap(capacity))

    constructor(pagingParams: PagingParams) : this(
        pagingParams.map, reuseMap = false
    )

    private val internalMap get() = map as MutableMap<String, Any?>

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
) = MutablePagingParams(buildMap(pairs.size) {
    putAll(pairs = pairs.map { it.first.key to it.second })
})

fun PagingParams.contains(key: DataKey<*>) = containsKey(key)

fun <T> MutablePagingParams.poll(key: DataKey<T>): T? = remove(key)

fun <T> MutablePagingParams.pollNotNull(key: DataKey<T>) = requireNotNull(poll(key))

fun <T> MutablePagingParams?.getOrElse(key: DataKey<T>, orElse: () -> T): T {
    return this?.getOrNull(key) ?: orElse()
}

fun PagingParams.toMutableParams() = MutablePagingParams(this)