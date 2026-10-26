package com.mindware.reels.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// Schemas are exported to app/schemas; every version bump needs a Migration (no destructive fallback).
@Database(
    entities = [ReelSessionEntity::class, ReelViewEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun reelDao(): ReelDao

    companion object {
        val MIGRATIONS: Array<androidx.room.migration.Migration> = emptyArray()

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "reels.db")
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
