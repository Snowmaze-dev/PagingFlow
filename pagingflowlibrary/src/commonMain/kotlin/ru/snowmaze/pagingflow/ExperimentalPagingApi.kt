package ru.snowmaze.pagingflow

@Retention(value = AnnotationRetention.BINARY)
@RequiresOptIn(
    level = RequiresOptIn.Level.WARNING,
    message = "This is a experimental API and it can have critical bugs."
)
annotation class ExperimentalPagingApi