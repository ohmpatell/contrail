package com.contrail.contrail.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        LiveAircraftEntity::class,
        TrailPointEntity::class,
        RouteEntity::class,
        PhotoEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class ContrailDatabase : RoomDatabase() {
    abstract fun liveAircraftDao(): LiveAircraftDao
    abstract fun trailDao(): TrailDao
    abstract fun routeDao(): RouteDao
    abstract fun photoDao(): PhotoDao
}
