package com.nevonai.smartshape.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [ScanEntity::class, ScanFts::class], version = 1, exportSchema = true)
abstract class SmartShapeDatabase : RoomDatabase() {
    abstract fun scanDao(): ScanDao
}
