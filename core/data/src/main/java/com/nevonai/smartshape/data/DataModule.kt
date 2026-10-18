package com.nevonai.smartshape.data

import android.content.Context
import androidx.room.Room
import com.nevonai.smartshape.data.db.ScanDao
import com.nevonai.smartshape.data.db.SmartShapeDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): SmartShapeDatabase =
        Room.databaseBuilder(context, SmartShapeDatabase::class.java, "smartshape.db").build()

    @Provides
    fun scanDao(db: SmartShapeDatabase): ScanDao = db.scanDao()
}
