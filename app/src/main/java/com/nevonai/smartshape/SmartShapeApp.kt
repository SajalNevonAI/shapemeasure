package com.nevonai.smartshape

import android.app.Application
import com.nevonai.smartshape.data.ScanRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class SmartShapeApp : Application() {

    @Inject lateinit var scans: ScanRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appScope.launch { scans.purgeDeleted() }
    }
}
