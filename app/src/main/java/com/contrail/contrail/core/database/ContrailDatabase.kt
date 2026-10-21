package com.contrail.contrail.core.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import com.contrail.contrail.core.data.repository.SightingDao
import com.contrail.contrail.core.data.repository.SightingEntity

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
        SightingEntity::class,
        TripEntity::class,
        SegmentEntity::class,
        ConnectionPrefEntity::class,
        LoungeEntity::class,
        LoungeVisitEntity::class,
    ],
    version = 3,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],
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
    abstract fun sightingDao(): SightingDao
    abstract fun tripDao(): TripDao
    abstract fun loungeDao(): LoungeDao
}
