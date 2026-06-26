package com.example.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat

class NotificationHelper(private val context: Context) {
    private val channelId = "location_order_reminders"
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Pengingat Lokasi Order"
            val descriptionText = "Notifikasi saat Anda berada dekat lokasi order"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun sendLocationReminderNotification(id: Int, locationName: String, distanceMeter: Int, notes: String) {
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_map) // Uses standard platform map icon
            .setContentTitle("Dekat Lokasi Order: $locationName")
            .setContentText("Jarak: ~${distanceMeter}m. Catatan: $notes")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        notificationManager.notify(id, builder.build())
    }
}
