package ru.snowmaze.pagingflow.presenters

import ru.snowmaze.pagingflow.PagingFlow
import ru.snowmaze.pagingflow.diff.PageChangedEvent
import ru.snowmaze.pagingflow.diff.mediums.PagingDataChangesMedium
import ru.snowmaze.pagingflow.diff.mediums.MappingPagingDataMedium
import ru.snowmaze.pagingflow.diff.mediums.ThrottlePagingDataChangesMedium

/**
 * Creates mapping presenter, which maps only changed pages and have throttling mechanism
 * @see pagingDataPresenter for arguments docs on arguments
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

/**
 * @param throttleDurationMsProvider provider of duration of throttle window
 * @param shouldThrottleAddPagesEvents defines whether should throttle add pages events or not
 * @param transform mapping lambda
 */
fun <Key : Any, Data : Any, NewData : Any> PagingFlow<Key, Data, *>.mappingDataPresenter(
    invalidateBehavior: InvalidateBehavior =
        InvalidateBehavior.INVALIDATE_AND_SEND_EMPTY_LIST_BEFORE_NEXT_VALUE,
    throttleDurationMsProvider: () -> Long = { 0 },
    shouldThrottleAddPagesEvents: Boolean = false,
    transform: (PageChangedEvent<Key, Data>) -> List<NewData?>
) = ThrottlePagingDataChangesMedium(
    this,
    throttleDurationMsProvider = throttleDurationMsProvider,
    shouldThrottleAddPagesEvents = shouldThrottleAddPagesEvents
).mappingDataPresenter(
    invalidateBehavior = invalidateBehavior,
    transform = transform
)

/**
 * Creates simple presenter, which builds list from pages
 * @param invalidateBehavior behavior of invalidate, by default it clears list after new value received, it means that list wouldn't blink when invalidate happens
 * @see InvalidateBehavior
 */
fun <Key : Any, Data : Any> PagingDataChangesMedium<Key, Data>.pagingDataPresenter(
    invalidateBehavior: InvalidateBehavior =
        InvalidateBehavior.INVALIDATE_AND_SEND_EMPTY_LIST_BEFORE_NEXT_VALUE
) = SimpleBuildListPagingPresenter(
    pagingDataChangesMedium = this,
    invalidateBehavior = invalidateBehavior
)

/**
 * @param throttleDurationMsProvider provider of duration of throttle window
 * @param shouldThrottleAddPagesEvents defines whether should throttle add pages events or not
 */
fun <Key : Any, Data : Any> PagingFlow<Key, Data, *>.pagingDataPresenter(
    invalidateBehavior: InvalidateBehavior =
        InvalidateBehavior.INVALIDATE_AND_SEND_EMPTY_LIST_BEFORE_NEXT_VALUE,
    throttleDurationMsProvider: () -> Long = { 0 },
    shouldThrottleAddPagesEvents: Boolean = false
) = ThrottlePagingDataChangesMedium(
    this,
    throttleDurationMsProvider = throttleDurationMsProvider,
    shouldThrottleAddPagesEvents = shouldThrottleAddPagesEvents
).pagingDataPresenter(invalidateBehavior)