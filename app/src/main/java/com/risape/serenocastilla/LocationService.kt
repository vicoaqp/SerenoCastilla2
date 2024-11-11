package com.risape.serenocastilla

import com.google.android.gms.location.FusedLocationProviderClient
import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint


class LocationService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback

    // Canal de notificación
    private val CHANNEL_ID = "location_channel"  // ID único del canal

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Configuración del LocationRequest para obtener la ubicación
        locationRequest = LocationRequest.create().apply {
            interval = 2000  // Intervalo de 2 segundos
            fastestInterval = 1000  // Intervalo más rápido de 1 segundo
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        // Configuración del callback de la ubicación
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val location = locationResult.lastLocation
                if (location != null) {
                    val geoPoint = GeoPoint(location.latitude, location.longitude)
                    val timestamp = Timestamp.now()
                    sendLocationToFirestore(geoPoint, timestamp)
                }
            }
        }

        // Crear el canal de notificación solo si estamos en Android 8.0 o superior
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelName = "Location Service Channel"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val notificationChannel = NotificationChannel(CHANNEL_ID, channelName, importance)
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(notificationChannel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Crear la notificación
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Ubicación en transmisión")
            .setContentText("Transmitiendo ubicación...")
            .setSmallIcon(R.drawable.ic_location)  // Asegúrate de tener un ícono para la notificación
            .build()

        // Iniciar el servicio en primer plano con la notificación
        startForeground(1, notification)

        // Iniciar la actualización de la ubicación
        startLocationUpdates()

        return START_STICKY
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            stopSelf()
            return
        }
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)
    }

    private fun sendLocationToFirestore(geoPoint: GeoPoint, timestamp: Timestamp) {
        val data = hashMapOf(
            "location" to geoPoint,
            "timestamp" to timestamp
        )

        FirebaseFirestore.getInstance().collection("sereno").document("sereno1")
            .set(data)
            .addOnSuccessListener { /* Ubicación enviada o actualizada con éxito */ }
            .addOnFailureListener { e -> e.printStackTrace() }
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}