package ru.snowmaze.pagingflow.params

/**
 * Paging params where you can specify typesafe params for paging sources
 * @see PagingLibraryParamsKeys
 * @see DataKey
 */
abstract class PagingParams internal constructor() {

    companion object {

        val EMPTY: PagingParams = MutablePagingParams(emptyMap())

        fun Map<DataKey<*>, Any>.pagingParams() = MutablePagingParams(mapKeys { it.key.key })

        operator fun invoke() = MutablePagingParams()

        operator fun invoke(map: Map<String, Any?>) = MutablePagingParams(map, reuseMap = false)

        operator fun invoke(pagingParams: PagingParams) = MutablePagingParams(pagingParams)

        operator fun invoke(
            capacity: Int = 5,
            builder: MutablePagingParams.() -> Unit
        ) = MutablePagingParams(capacity).apply(builder)
    }

    abstract val entries: Set<Map.Entry<String, Any?>>
    abstract val keys: Set<String>
    abstract val size: Int
    abstract val values: Collection<Any?>

    operator fun <T> get(key: DataKey<T>): T = getOrNull(key)
        ?: throw IllegalArgumentException("You should specify ${key.key} key for that operation.")

    abstract fun <T> getOrNull(key: DataKey<T>): T?

    abstract fun isEmpty(): Boolean

    abstract fun containsValue(value: Any): Boolean

    abstract fun containsKey(key: DataKey<*>): Boolean

    abstract fun getStringKeysMap(): Map<String, Any?>
}

open class ReadOnlyPagingParams internal constructor(
    internalMap: Map<String, Any?>,
    reuseMap: Boolean = true
): PagingParams() {

    constructor(pagingParams: ReadOnlyPagingParams) : this(pagingParams.map, reuseMap = false)

    internal open val map = if (reuseMap) internalMap
    else LinkedHashMap(internalMap)

    override val entries get() = map.entries
    override val keys get() = map.keys
    override val size get() = map.size
    override val values get() = map.values

    override fun <T> getOrNull(key: DataKey<T>): T? {
        return map[key.key] as? T
    }

    override fun isEmpty() = map.isEmpty()

    override fun containsValue(value: Any) = map.containsValue(value)

    override fun containsKey(key: DataKey<*>) = map.containsKey(key.key)

    override fun getStringKeysMap() = map.mapKeys { it.key }

    override fun toString(): String {
        return getStringKeysMap().toString()
    }
}

class MutablePagingParams internal constructor(
    map: Map<String, Any?>,
    reuseMap: Boolean = true
) : ReadOnlyPagingParams(map, reuseMap) {

    constructor(): this(LinkedHashMap())

    constructor(capacity: Int) : this(LinkedHashMap(capacity))

    constructor(pagingParams: PagingParams) : this(
        (pagingParams as ReadOnlyPagingParams).map, reuseMap = false
    )

    private val internalMap get() = map as MutableMap<String, Any?>

    fun put(pagingParams: PagingParams) {
        internalMap.putAll((pagingParams as ReadOnlyPagingParams).map)
    }

    fun clear() = internalMap.clear()

    fun <T> remove(key: DataKey<T>) = internalMap.remove(key.key) as? T

    fun <T> put(key: DataKey<T>, value: T?) = internalMap.put(key.key, value)

    fun asReadOnly(): PagingParams = ReadOnlyPagingParams(internalMap)
}

fun <T : Any> pagingParamsOf(pair: Pair<DataKey<T>, T>) = PagingParams(1) {
    put(pair.first, pair.second)
}

fun pagingParamsOf(vararg pairs: Pair<DataKey<out Any?>, Any?>) = PagingParams(buildMap(pairs.size) {
    putAll(pairs = pairs.map { it.first.key to it.second })
})

fun PagingParams.contains(key: DataKey<*>) = containsKey(key)

fun <T> MutablePagingParams.poll(key: DataKey<T>): T? = remove(key)

fun <T> MutablePagingParams.pollNotNull(key: DataKey<T>) = requireNotNull(poll(key))

fun <T> MutablePagingParams?.getOrElse(key: DataKey<T>, orElse: () -> T): T {
    return this?.getOrNull(key) ?: orElse()
}

fun PagingParams.toMutableParams() = MutablePagingParams(this)