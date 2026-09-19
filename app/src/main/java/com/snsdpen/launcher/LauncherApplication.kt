package com.snsdpen.launcher

import android.app.Application
import android.content.Context
import com.snsdpen.launcher.data.LauncherStore

class LauncherApplication : Application() {
    val store: LauncherStore by lazy { LauncherStore(this) }
}

val Context.launcherStore: LauncherStore
    get() = (applicationContext as LauncherApplication).store
