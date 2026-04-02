package ru.snowmaze.pagingflow.sample

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = { exitApplication() },
        content = { App(PaddingValues.Zero) }
    )
}