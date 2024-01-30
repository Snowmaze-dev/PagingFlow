package ru.snowmaze.pagingflow.params

// TODO заменить тут ключи под капотом на стринг
/**
 * Paging params where you can specify typesafe params for data sources
 * @see DefaultKeys
 * @see DataKey
 */
class PagingParams(map: Map<DataKey<*>, Any> = emptyMap()) {

    private val internalMap = LinkedHashMap(map)

    val entries get() = internalMap.entries
    val keys get() = internalMap.keys
    val size get() = internalMap.size
    val values get() = internalMap.values

    constructor(pagingParams: PagingParams) : this(pagingParams.internalMap)

    constructor(builder: PagingParams.() -> Unit) : this(PagingParams().apply(builder))

    fun put(pagingParams: PagingParams) {
        internalMap.putAll(pagingParams.internalMap)
    }

    operator fun <T> get(key: DataKey<T>): T = getOrNull(key)
        ?: throw IllegalArgumentException("You should specify ${key.key} key for that operation.")

    fun <T> getOrNull(key: DataKey<T>): T? {
        return (internalMap[key] ?: internalMap.firstNotNullOfOrNull { entry ->
            if (entry.key.key == key.key) entry
            else null
        }?.value) as? T
    }

    fun clear() = internalMap.clear()

    fun isEmpty() = internalMap.isEmpty()

    fun <T> remove(key: DataKey<T>) = internalMap.remove(key) as? T

    fun <T> put(key: DataKey<T>, value: T?) = if (value == null) internalMap.remove(key)
    else internalMap.put(key, value)

    fun containsValue(value: Any) = internalMap.containsValue(value)

    fun containsKey(key: DataKey<*>) = internalMap.containsKey(key)

    fun getStringKeysMap() = internalMap.mapKeys { it.key }

    override fun toString(): String {
        return getStringKeysMap().toString()
    }
}

fun PagingParams.contains(key: DataKey<*>) = containsKey(key)

fun <T> PagingParams.poll(key: DataKey<T>): T? = remove(key)

fun <T> PagingParams.pollNotNull(key: DataKey<T>) = requireNotNull(poll(key))

fun <T> PagingParams?.getOrElse(key: DataKey<T>, orElse: () -> T): T {
    return this?.getOrNull(key) ?: orElse()
}