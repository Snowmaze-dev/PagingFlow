package ru.snowmaze.pagingflow.presenters

import ru.snowmaze.pagingflow.PagingFlow
import ru.snowmaze.pagingflow.diff.PageChangedEvent
import ru.snowmaze.pagingflow.diff.mediums.PagingDataChangesMedium
import ru.snowmaze.pagingflow.diff.mediums.MappingPagingDataMedium
import ru.snowmaze.pagingflow.diff.mediums.ThrottlePagingDataChangesMedium

/**
 * Creates mapping presenter, which maps only changed pages and have throttling mechanism
 * @see pagingDataPresenter for arguments docs
 */
fun <Key : Any, Data : Any, NewData : Any> PagingDataChangesMedium<Key, Data>.mappingDataPresenter(
    invalidateBehavior: InvalidateBehavior =
        InvalidateBehavior.INVALIDATE_AND_SEND_EMPTY_LIST_BEFORE_NEXT_VALUE,
    transform: (PageChangedEvent<Key, Data>) -> List<NewData?>
) = SimpleBuildListPagingPresenter(
    pagingDataChangesMedium = MappingPagingDataMedium(
        pagingDataChangesMedium = this,
        transform = transform
    ),
    invalidateBehavior = invalidateBehavior
)

fun <Key : Any, Data : Any, NewData : Any> PagingFlow<Key, Data, *>.mappingDataPresenter(
    invalidateBehavior: InvalidateBehavior =
        InvalidateBehavior.INVALIDATE_AND_SEND_EMPTY_LIST_BEFORE_NEXT_VALUE,
    throttleDurationMsProvider: () -> Long = { 0 },
    transform: (PageChangedEvent<Key, Data>) -> List<NewData?>
) = ThrottlePagingDataChangesMedium(
    this,
    throttleDurationMsProvider = throttleDurationMsProvider
).mappingDataPresenter(
    invalidateBehavior = invalidateBehavior,
    transform = transform
)

/**
 * Creates simple presenter, which builds list from pages and have throttling mechanism
 * @param invalidateBehavior behavior of invalidate, by default it clears list after new value received, it means that list wouldn't blink when invalidate happens
 * @param throttleDurationMs duration of throttle window
 * @see InvalidateBehavior
 */
fun <Key : Any, Data : Any> PagingDataChangesMedium<Key, Data>.pagingDataPresenter(
    invalidateBehavior: InvalidateBehavior =
        InvalidateBehavior.INVALIDATE_AND_SEND_EMPTY_LIST_BEFORE_NEXT_VALUE
) = SimpleBuildListPagingPresenter(
    pagingDataChangesMedium = this,
    invalidateBehavior = invalidateBehavior
)

fun <Key : Any, Data : Any> PagingFlow<Key, Data, *>.pagingDataPresenter(
    invalidateBehavior: InvalidateBehavior =
        InvalidateBehavior.INVALIDATE_AND_SEND_EMPTY_LIST_BEFORE_NEXT_VALUE,
    throttleDurationMsProvider: () -> Long = { 0 },
) = ThrottlePagingDataChangesMedium(
    this,
    throttleDurationMsProvider = throttleDurationMsProvider,
).pagingDataPresenter(invalidateBehavior)