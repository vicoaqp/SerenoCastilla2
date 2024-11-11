package com.risape.serenocastilla

import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import android.Manifest
import android.content.Intent
import android.location.Location
import androidx.core.content.ContextCompat

import com.google.android.gms.location.*



class MainActivity : AppCompatActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private var isTrackingLocation = false
    private lateinit var btnToggleLocation: Button
    private var lastLocation: GeoPoint? = null

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1
        private const val MIN_DISTANCE_CHANGE_METERS = 5 // Mínimo cambio en metros para actualizar la ubicación
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnToggleLocation = findViewById(R.id.btnToggleLocation)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Configuración del LocationRequest con intervalos más cortos y prioridad alta
        locationRequest = LocationRequest.create().apply {
            interval = 2000 // Intervalo de 2 segundos
            fastestInterval = 1000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        // Configuración del callback para obtener ubicación en tiempo real
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val location = locationResult.lastLocation ?: return
                val geoPoint = GeoPoint(location.latitude, location.longitude)
                val timestamp = Timestamp.now()

                // Enviar la ubicación a Firestore sólo si hay cambio significativo en la ubicación
                if (shouldUpdateLocation(geoPoint)) {
                    sendLocationToFirestore(geoPoint, timestamp)
                    lastLocation = geoPoint
                }
            }
        }

        btnToggleLocation.setOnClickListener {
            if (!isTrackingLocation) {
                startLocationUpdates()
                btnToggleLocation.text = "Detener Envío de Ubicación"
            } else {
                stopLocationUpdates()
                btnToggleLocation.text = "Iniciar Envío de Ubicación"
            }
            isTrackingLocation = !isTrackingLocation
        }
    }

    private fun startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
            return
        }
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, mainLooper)
        Snackbar.make(btnToggleLocation, "Enviando ubicación a Firestore...", Snackbar.LENGTH_LONG).show()
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        Snackbar.make(btnToggleLocation, "Envío de ubicación detenido", Snackbar.LENGTH_LONG).show()
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

    private fun shouldUpdateLocation(newLocation: GeoPoint): Boolean {
        // Comprobar si hay un cambio significativo en la ubicación
        lastLocation?.let {
            val results = FloatArray(1)
            Location.distanceBetween(
                it.latitude, it.longitude,
                newLocation.latitude, newLocation.longitude,
                results
            )
            return results[0] >= MIN_DISTANCE_CHANGE_METERS
        }
        return true // Si es la primera ubicación, actualizar sin comprobación
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates()
            } else {
                Snackbar.make(btnToggleLocation, "Permiso de ubicación denegado", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (isTrackingLocation) {
            stopLocationUpdates()
        }
    }

    override fun onResume() {
        super.onResume()
        if (isTrackingLocation) {
            startLocationUpdates()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isTrackingLocation) {
            stopLocationUpdates()
        }
    }
}

