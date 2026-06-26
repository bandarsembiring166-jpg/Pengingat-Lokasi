package com.example.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.security.CryptoManager
import java.io.Serializable

@Entity(tableName = "saved_locations")
data class SavedLocation(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val notes: String = "",
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) : Serializable

@Entity(
    tableName = "order_logs",
    foreignKeys = [
        ForeignKey(
            entity = SavedLocation::class,
            parentColumns = ["id"],
            childColumns = ["locationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["locationId"])]
)
data class OrderLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val locationId: Int,
    val orderTime: Long = System.currentTimeMillis(),
    val itemsDescription: String,
    val totalAmount: Double = 0.0,
    val status: String = "SELESAI", // SELESAI, BATAL, PROSES
    val customerName: String = "",
    val notes: String = "",
    val syncStatus: Int = 0 // 0 = local, 1 = synced
) : Serializable

data class OrderWithLocation(
    val orderId: Int,
    val locationId: Int,
    val locationName: String,
    val locationAddress: String,
    val latitude: Double,
    val longitude: Double,
    val orderTime: Long,
    val itemsDescription: String,
    val totalAmount: Double,
    val status: String,
    val customerName: String,
    val orderNotes: String,
    val locationNotes: String,
    val syncStatus: Int
) : Serializable

fun SavedLocation.encrypt(): SavedLocation = copy(
    name = CryptoManager.encrypt(name),
    address = CryptoManager.encrypt(address),
    notes = CryptoManager.encrypt(notes)
)

fun SavedLocation.decrypt(): SavedLocation = copy(
    name = CryptoManager.decrypt(name),
    address = CryptoManager.decrypt(address),
    notes = CryptoManager.decrypt(notes)
)

fun OrderLog.encrypt(): OrderLog = copy(
    itemsDescription = CryptoManager.encrypt(itemsDescription),
    customerName = CryptoManager.encrypt(customerName),
    notes = CryptoManager.encrypt(notes)
)

fun OrderLog.decrypt(): OrderLog = copy(
    itemsDescription = CryptoManager.decrypt(itemsDescription),
    customerName = CryptoManager.decrypt(customerName),
    notes = CryptoManager.decrypt(notes)
)

fun OrderWithLocation.decrypt(): OrderWithLocation = copy(
    locationName = CryptoManager.decrypt(locationName),
    locationAddress = CryptoManager.decrypt(locationAddress),
    itemsDescription = CryptoManager.decrypt(itemsDescription),
    customerName = CryptoManager.decrypt(customerName),
    orderNotes = CryptoManager.decrypt(orderNotes),
    locationNotes = CryptoManager.decrypt(locationNotes)
)
