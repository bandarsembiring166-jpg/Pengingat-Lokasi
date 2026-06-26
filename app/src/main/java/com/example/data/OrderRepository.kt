package com.example.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class OrderRepository(
    private val locationDao: SavedLocationDao,
    private val orderDao: OrderLogDao
) {
    val allLocations: Flow<List<SavedLocation>> = locationDao.getAllLocations()
    val allOrdersWithLocation: Flow<List<OrderWithLocation>> = orderDao.getAllOrdersWithLocation()

    fun getOrdersByLocation(locationId: Int): Flow<List<OrderWithLocation>> {
        return orderDao.getOrdersByLocationId(locationId)
    }

    suspend fun insertLocation(location: SavedLocation): Long {
        return locationDao.insertLocation(location)
    }

    suspend fun updateLocation(location: SavedLocation) {
        locationDao.updateLocation(location)
    }

    suspend fun deleteLocation(location: SavedLocation) {
        locationDao.deleteLocation(location)
    }

    suspend fun insertOrder(order: OrderLog): Long {
        return orderDao.insertOrder(order)
    }

    suspend fun updateOrder(order: OrderLog) {
        orderDao.updateOrder(order)
    }

    suspend fun deleteOrder(order: OrderLog) {
        orderDao.deleteOrder(order)
    }

    suspend fun markAllAsSynced() {
        orderDao.markAllAsSynced()
    }

    // Export and Import logic using Moshi
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    // Data structures for JSON Backup
    data class BackupLocation(
        val name: String,
        val address: String,
        val latitude: Double,
        val longitude: Double,
        val notes: String = "",
        val isFavorite: Boolean = false,
        val createdAt: Long
    )

    data class BackupOrder(
        val locationName: String,
        val locationLatitude: Double,
        val locationLongitude: Double,
        val orderTime: Long,
        val itemsDescription: String,
        val totalAmount: Double,
        val status: String,
        val customerName: String,
        val notes: String
    )

    data class BackupPayload(
        val locations: List<BackupLocation>,
        val orders: List<BackupOrder>
    )

    suspend fun exportDataToJson(): String {
        val locationsList = allLocations.first()
        val ordersList = allOrdersWithLocation.first()

        val backupLocations = locationsList.map {
            BackupLocation(it.name, it.address, it.latitude, it.longitude, it.notes, it.isFavorite, it.createdAt)
        }

        val backupOrders = ordersList.map {
            BackupOrder(
                locationName = it.locationName,
                locationLatitude = it.latitude,
                locationLongitude = it.longitude,
                orderTime = it.orderTime,
                itemsDescription = it.itemsDescription,
                totalAmount = it.totalAmount,
                status = it.status,
                customerName = it.customerName,
                notes = it.orderNotes
            )
        }

        val payload = BackupPayload(backupLocations, backupOrders)
        val adapter = moshi.adapter(BackupPayload::class.java)
        return adapter.toJson(payload)
    }

    suspend fun importDataFromJson(jsonString: String): Result<Pair<Int, Int>> {
        return try {
            val adapter = moshi.adapter(BackupPayload::class.java)
            val payload = adapter.fromJson(jsonString) ?: return Result.failure(Exception("Format data JSON tidak valid"))

            var importedLocationsCount = 0
            var importedOrdersCount = 0

            // 1. Restore/Merge locations
            val existingLocations = allLocations.first()
            val locationMap = mutableMapOf<String, Int>() // lowercaseLocationName -> ID

            // Populate map with existing locations
            existingLocations.forEach {
                locationMap[it.name.lowercase().trim()] = it.id
            }

            for (backupLoc in payload.locations) {
                val key = backupLoc.name.lowercase().trim()
                if (!locationMap.containsKey(key)) {
                    val newId = locationDao.insertLocation(
                        SavedLocation(
                            name = backupLoc.name,
                            address = backupLoc.address,
                            latitude = backupLoc.latitude,
                            longitude = backupLoc.longitude,
                            notes = backupLoc.notes,
                            isFavorite = backupLoc.isFavorite,
                            createdAt = backupLoc.createdAt
                        )
                    )
                    locationMap[key] = newId.toInt()
                    importedLocationsCount++
                }
            }

            // 2. Restore/Merge orders
            val existingOrders = allOrdersWithLocation.first()

            for (backupOrder in payload.orders) {
                val locKey = backupOrder.locationName.lowercase().trim()
                val locId = locationMap[locKey] ?: continue // Skip if location was not saved or created

                // Check for duplicate order (same location, same exact timestamp, same customer)
                val duplicate = existingOrders.any {
                    it.locationId == locId && 
                    it.orderTime == backupOrder.orderTime && 
                    it.customerName.lowercase().trim() == backupOrder.customerName.lowercase().trim()
                }

                if (!duplicate) {
                    orderDao.insertOrder(
                        OrderLog(
                            locationId = locId,
                            orderTime = backupOrder.orderTime,
                            itemsDescription = backupOrder.itemsDescription,
                            totalAmount = backupOrder.totalAmount,
                            status = backupOrder.status,
                            customerName = backupOrder.customerName,
                            notes = backupOrder.notes,
                            syncStatus = 1 // Mark as synchronized
                        )
                    )
                    importedOrdersCount++
                }
            }

            Result.success(Pair(importedLocationsCount, importedOrdersCount))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
