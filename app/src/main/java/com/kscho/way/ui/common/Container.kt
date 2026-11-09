package com.kscho.way.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.kscho.way.WayApp
import com.kscho.way.di.AppContainer

@Composable
fun appContainer(): AppContainer = (LocalContext.current.applicationContext as WayApp).container
