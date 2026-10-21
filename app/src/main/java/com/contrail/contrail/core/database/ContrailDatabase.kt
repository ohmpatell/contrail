package com.contrail.contrail.core.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        LiveAircraftEntity::class,
        TrailPointEntity::class,
        RouteEntity::class,
        PhotoEntity::class,
        AirportEntity::class,
        RunwayEntity::class,
        FrequencyEntity::class,
        AirportUserEntity::class,
        WatchItemEntity::class,
        NewsItemEntity::class,
        CachedResponseEntity::class,
    ],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class ContrailDatabase : RoomDatabase() {
    abstract fun liveAircraftDao(): LiveAircraftDao
    abstract fun trailDao(): TrailDao
    abstract fun routeDao(): RouteDao
    abstract fun photoDao(): PhotoDao
    abstract fun airportDao(): AirportDao
    abstract fun watchDao(): WatchDao
    abstract fun newsDao(): NewsDao
    abstract fun cacheDao(): CacheDao
}
