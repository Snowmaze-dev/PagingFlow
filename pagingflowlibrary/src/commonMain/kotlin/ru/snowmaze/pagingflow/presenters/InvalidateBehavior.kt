package ru.snowmaze.pagingflow.presenters

enum class InvalidateBehavior {

    /**
     * Clears list immediately when invalidate happens
     */
    INVALIDATE_IMMEDIATELY,

    /**
     * Waits until first page after invalidate is added and sends empty list and then adds that page
     */
    SEND_EMPTY_LIST_BEFORE_NEXT_VALUE_SET,

    /**
     * Waits until first page after invalidate is added and replaces old list with list from that page
     */
    WAIT_FOR_NEW_LIST,
}