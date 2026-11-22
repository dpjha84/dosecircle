package com.dosecircle.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dosecircle.app.AppContainer
import com.dosecircle.app.DoseCircleApp

@Composable
fun rememberContainer(): AppContainer = (LocalContext.current.applicationContext as DoseCircleApp).container

/** Builds a ViewModel that needs the app container. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(crossinline build: (AppContainer) -> VM): VM {
    val container = rememberContainer()
    return viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = build(container) as T
        },
    )
}

fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
