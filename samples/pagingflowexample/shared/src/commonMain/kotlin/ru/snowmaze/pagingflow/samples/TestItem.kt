package ru.snowmaze.pagingflow.samples

sealed class TestItem {

    data class Item(val text: String): TestItem()

    data class Loader(val isDown: Boolean): TestItem()
}