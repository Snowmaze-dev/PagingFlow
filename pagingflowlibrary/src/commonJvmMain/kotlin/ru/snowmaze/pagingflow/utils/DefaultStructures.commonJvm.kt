package ru.snowmaze.pagingflow.utils

import androidx.collection.MutableScatterMap

actual fun <K, V> platformMapOf() = MutableScatterMap<K, V>().asMutableMap()