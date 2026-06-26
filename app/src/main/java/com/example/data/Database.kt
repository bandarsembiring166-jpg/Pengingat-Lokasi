package com.example.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedLocationDao {
    @Query("SELECT * FROM saved_locations ORDER BY isFavorite DESC, name ASC")
    fun getAllLocations(): Flow<List<SavedLocation>>

    @Query("SELECT * FROM saved_locations WHERE id = :id LIMIT 1")
    suspend fun getLocationById(id: Int): SavedLocation?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocation(location: SavedLocation): Long

    @Update
    suspend fun updateLocation(location: SavedLocation)

    @Delete
    suspend fun deleteLocation(location: SavedLocation)
}

@Dao
interface OrderLogDao {
    @Query("SELECT * FROM order_logs ORDER BY orderTime DESC")
    fun getAllOrders(): Flow<List<OrderLog>>

    @Query("""
        SELECT 
            o.id as orderId,
            l.id as locationId,
            l.name as locationName,
            l.address as locationAddress,
            l.latitude as latitude,
            l.longitude as longitude,
            o.orderTime as orderTime,
            o.itemsDescription as itemsDescription,
            o.totalAmount as totalAmount,
            o.status as status,
            o.customerName as customerName,
            o.notes as orderNotes,
            l.notes as locationNotes,
            o.syncStatus as syncStatus
        FROM order_logs o
        INNER JOIN saved_locations l ON o.locationId = l.id
        ORDER BY o.orderTime DESC
    """)
    fun getAllOrdersWithLocation(): Flow<List<OrderWithLocation>>

    @Query("""
        SELECT 
            o.id as orderId,
            l.id as locationId,
            l.name as locationName,
            l.address as locationAddress,
            l.latitude as latitude,
            l.longitude as longitude,
            o.orderTime as orderTime,
            o.itemsDescription as itemsDescription,
            o.totalAmount as totalAmount,
            o.status as status,
            o.customerName as customerName,
            o.notes as orderNotes,
            l.notes as locationNotes,
            o.syncStatus as syncStatus
        FROM order_logs o
        INNER JOIN saved_locations l ON o.locationId = l.id
        WHERE o.locationId = :locationId
        ORDER BY o.orderTime DESC
    """)
    fun getOrdersByLocationId(locationId: Int): Flow<List<OrderWithLocation>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrder(order: OrderLog): Long

    @Update
    suspend fun updateOrder(order: OrderLog)

    @Delete
    suspend fun deleteOrder(order: OrderLog)

    @Query("UPDATE order_logs SET syncStatus = 1")
    suspend fun markAllAsSynced()
}

@Database(entities = [SavedLocation::class, OrderLog::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun savedLocationDao(): SavedLocationDao
    abstract fun orderLogDao(): OrderLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "order_locations_db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
