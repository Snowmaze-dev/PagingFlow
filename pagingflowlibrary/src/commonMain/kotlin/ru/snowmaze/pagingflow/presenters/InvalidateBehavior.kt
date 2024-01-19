package ru.snowmaze.pagingflow.presenters

enum class InvalidateBehavior {

    /**
     * Clears list when invalidate happens
     */
    INVALIDATE_IMMEDIATELY,

    /**
     * Waits until first page after invalidate is added and sends empty list and then adds that page
     */
    INVALIDATE_AND_SEND_EMPTY_LIST_BEFORE_NEXT_VALUE,

    /**
     * Waits until first page after invalidate is added and replaces old list with list from that page
     */
    INVALIDATE_AND_CLEAR_LIST_BEFORE_NEXT_VALUE_RECEIVED,
}