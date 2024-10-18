package ru.snowmaze.pagingflow.params

/**
 * Key for data in [PagingParams]
 */
interface DataKey<T> {

    companion object {

        /**
         * Creates default key with specified [key]
         */
        inline operator fun <T> invoke(key: String) = DefaultKey<T>(key)
    }

    val key: String
}

class DefaultKey<T>(override val key: String) : DataKey<T>