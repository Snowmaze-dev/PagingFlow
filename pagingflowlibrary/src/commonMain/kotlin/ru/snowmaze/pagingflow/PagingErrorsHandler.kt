package ru.snowmaze.pagingflow

import ru.snowmaze.pagingflow.result.LoadResult

// TODO при ошибке устанавливать статус который возвращает этот обработчик
// у каждого соурса может быть свой обработчик
abstract class PagingErrorsHandler<PagingStatus: Any> {

    abstract fun handle(exception: Exception): LoadResult.Failure<*, *, PagingStatus>
}