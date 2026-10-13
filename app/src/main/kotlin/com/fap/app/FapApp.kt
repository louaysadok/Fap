package com.fap.app

import android.app.Application
import com.fap.app.di.Graph

public class FapApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
    }
}
