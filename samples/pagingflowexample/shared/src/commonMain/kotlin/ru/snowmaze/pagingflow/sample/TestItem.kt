package ru.snowmaze.pagingflow.sample

sealed class TestItem {

    data class Item(val text: String): TestItem()

    data class Loader(val isDown: Boolean): TestItem()
}