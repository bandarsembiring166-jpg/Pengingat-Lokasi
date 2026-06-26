package com.example.viewmodel

import android.app.Application
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.location.LocationTracker
import com.example.notification.NotificationHelper
import com.example.security.SecurePreferenceManager
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class OrderViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val repository = OrderRepository(database.savedLocationDao(), database.orderLogDao())
    private val locationTracker = LocationTracker(application)
    private val notificationHelper = NotificationHelper(application)
    private val securePrefs = SecurePreferenceManager(application)

    // User profile state flows (securely stored & encrypted)
    private val _isLoggedIn = MutableStateFlow(securePrefs.isLoggedIn)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _userDisplayName = MutableStateFlow(securePrefs.displayName)
    val userDisplayName: StateFlow<String?> = _userDisplayName.asStateFlow()

    private val _userEmail = MutableStateFlow(securePrefs.email)
    val userEmail: StateFlow<String?> = _userEmail.asStateFlow()

    private val _userPhotoUrl = MutableStateFlow(securePrefs.photoUrl)
    val userPhotoUrl: StateFlow<String?> = _userPhotoUrl.asStateFlow()

    // UI state flows
    val locations: StateFlow<List<SavedLocation>> = repository.allLocations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val orders: StateFlow<List<OrderWithLocation>> = repository.allOrdersWithLocation
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Location tracker state
    private val _currentLocation = MutableStateFlow<Location?>(null)
    val currentLocation: StateFlow<Location?> = _currentLocation.asStateFlow()

    // Simulation states
    private val _isSimulationEnabled = MutableStateFlow(true) // Enable simulation by default so user can test location triggers easily!
    val isSimulationEnabled: StateFlow<Boolean> = _isSimulationEnabled.asStateFlow()

    private val _simulatedLat = MutableStateFlow(-6.1950) // Starts close to Kopi Sejahtera Sudirman
    val simulatedLat: StateFlow<Double> = _simulatedLat.asStateFlow()

    private val _simulatedLng = MutableStateFlow(106.8202)
    val simulatedLng: StateFlow<Double> = _simulatedLng.asStateFlow()

    // Status / Messages for UI
    private val _uiEvent = MutableSharedFlow<UiEvent>()
    val uiEvent = _uiEvent.asSharedFlow()

    // Tracking notified locations to avoid spam
    private val notifiedLocationIds = mutableSetOf<Int>()

    // Sync cloud state (simulated)
    private val _syncStatus = MutableStateFlow(SyncStatus.IDLE)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    sealed interface UiEvent {
        data class ShowSuccess(val message: String) : UiEvent
        data class ShowError(val message: String) : UiEvent
    }

    enum class SyncStatus {
        IDLE, SYNCING, SUCCESS, ERROR
    }

    init {
        // Pre-populate with realistic Indonesian coordinates if empty
        viewModelScope.launch {
            locations.first().let {
                if (it.isEmpty()) {
                    prePopulateLocations()
                }
            }
        }

        // Start location monitoring loop
        startLocationMonitoring()
    }

    private suspend fun prePopulateLocations() {
        val defaultLocs = listOf(
            SavedLocation(
                name = "Warung Bu Sum - Kebon Jeruk",
                address = "Jl. Raya Kebon Jeruk No.45, Jakarta Barat",
                latitude = -6.1944,
                longitude = 106.7725,
                notes = "Samping Alfamart, ramai jam makan siang",
                isFavorite = true
            ),
            SavedLocation(
                name = "Kopi Sejahtera - Sudirman",
                address = "Grand Indonesia Mall Lantai 2, Jakarta Pusat",
                latitude = -6.1950,
                longitude = 106.8202,
                notes = "Titik kumpul lobi barat, antrean panjang",
                isFavorite = true
            ),
            SavedLocation(
                name = "Resto Padang Sederhana - Braga",
                address = "Jl. Braga No.12, Bandung",
                latitude = -6.9175,
                longitude = 107.6111,
                notes = "Bungkus cepat, sate padang selalu laris",
                isFavorite = false
            )
        )

        for (loc in defaultLocs) {
            val id = repository.insertLocation(loc)
            // Insert initial order logs for demonstration
            if (loc.name.contains("Warung")) {
                repository.insertOrder(
                    OrderLog(
                        locationId = id.toInt(),
                        itemsDescription = "2 Nasi Ayam Goreng, 1 Es Teh Manis",
                        totalAmount = 35000.0,
                        customerName = "Pak Budi",
                        notes = "Minta sambal hijau dipisah",
                        orderTime = System.currentTimeMillis() - 7200000 // 2 hours ago
                    )
                )
            } else if (loc.name.contains("Kopi")) {
                repository.insertOrder(
                    OrderLog(
                        locationId = id.toInt(),
                        itemsDescription = "5 Es Kopi Susu Aren",
                        totalAmount = 90000.0,
                        customerName = "Mbak Siska",
                        notes = "Less sugar, double shot",
                        orderTime = System.currentTimeMillis() - 3600000 // 1 hour ago
                    )
                )
            }
        }
    }

    private fun startLocationMonitoring() {
        viewModelScope.launch {
            while (true) {
                // Fetch current location
                val loc = if (_isSimulationEnabled.value) {
                    Location("Simulation").apply {
                        latitude = _simulatedLat.value
                        longitude = _simulatedLng.value
                    }
                } else {
                    locationTracker.getCurrentLocation()
                }

                _currentLocation.value = loc

                // Evaluate proximity to saved coordinates
                loc?.let { currentLoc ->
                    val savedLocs = locations.value
                    for (savedLoc in savedLocs) {
                        val results = FloatArray(1)
                        Location.distanceBetween(
                            currentLoc.latitude, currentLoc.longitude,
                            savedLoc.latitude, savedLoc.longitude,
                            results
                        )
                        val distanceMeters = results[0].toInt()

                        // Proximity check: notify if within 300 meters
                        if (distanceMeters <= 300) {
                            if (!notifiedLocationIds.contains(savedLoc.id)) {
                                // Trigger notification
                                notificationHelper.sendLocationReminderNotification(
                                    id = savedLoc.id,
                                    locationName = savedLoc.name,
                                    distanceMeter = distanceMeters,
                                    notes = savedLoc.notes.ifEmpty { "Dekat wilayah target pengiriman!" }
                                )
                                notifiedLocationIds.add(savedLoc.id)
                            }
                        } else {
                            // Reset notified status if user moves away (> 400 meters)
                            if (distanceMeters > 400) {
                                notifiedLocationIds.remove(savedLoc.id)
                            }
                        }
                    }
                }

                delay(5000) // Check every 5 seconds for responsive simulation testing
            }
        }
    }

    // --- Actions ---

    fun insertLocation(name: String, address: String, lat: Double, lng: Double, notes: String, isFavorite: Boolean) {
        viewModelScope.launch {
            val loc = SavedLocation(
                name = name,
                address = address,
                latitude = lat,
                longitude = lng,
                notes = notes,
                isFavorite = isFavorite
            )
            repository.insertLocation(loc)
            _uiEvent.emit(UiEvent.ShowSuccess("Lokasi '$name' berhasil ditambahkan"))
        }
    }

    fun updateLocation(location: SavedLocation) {
        viewModelScope.launch {
            repository.updateLocation(location)
            _uiEvent.emit(UiEvent.ShowSuccess("Lokasi berhasil diperbarui"))
        }
    }

    fun deleteLocation(location: SavedLocation) {
        viewModelScope.launch {
            repository.deleteLocation(location)
            _uiEvent.emit(UiEvent.ShowSuccess("Lokasi '${location.name}' berhasil dihapus"))
        }
    }

    fun insertOrder(locationId: Int, items: String, total: Double, customer: String, notes: String, status: String) {
        viewModelScope.launch {
            val order = OrderLog(
                locationId = locationId,
                itemsDescription = items,
                totalAmount = total,
                customerName = customer,
                notes = notes,
                status = status,
                orderTime = System.currentTimeMillis()
            )
            repository.insertOrder(order)
            _uiEvent.emit(UiEvent.ShowSuccess("Riwayat order berhasil dicatat"))
        }
    }

    fun updateOrder(order: OrderLog) {
        viewModelScope.launch {
            repository.updateOrder(order)
            _uiEvent.emit(UiEvent.ShowSuccess("Riwayat order berhasil diperbarui"))
        }
    }

    fun deleteOrder(order: OrderLog) {
        viewModelScope.launch {
            repository.deleteOrder(order)
            _uiEvent.emit(UiEvent.ShowSuccess("Catatan order berhasil dihapus"))
        }
    }

    // --- Simulation Controls ---
    fun hasLocationPermission(): Boolean {
        return locationTracker.hasLocationPermission()
    }

    fun toggleSimulation(enabled: Boolean) {
        _isSimulationEnabled.value = enabled
    }

    fun updateSimulatedCoordinates(lat: Double, lng: Double) {
        _simulatedLat.value = lat
        _simulatedLng.value = lng
    }

    // --- Sync Operations ---
    fun simulateCloudSync() {
        viewModelScope.launch {
            _syncStatus.value = SyncStatus.SYNCING
            delay(1500) // Elegant visual loading delay
            try {
                repository.markAllAsSynced()
                _syncStatus.value = SyncStatus.SUCCESS
                _uiEvent.emit(UiEvent.ShowSuccess("Sinkronisasi cloud berhasil! Semua data di perangkat Anda sudah sinkron."))
            } catch (e: Exception) {
                _syncStatus.value = SyncStatus.ERROR
                _uiEvent.emit(UiEvent.ShowError("Sinkronisasi gagal: ${e.message}"))
            }
        }
    }

    suspend fun getExportPayload(): String {
        return repository.exportDataToJson()
    }

    fun importBackup(jsonString: String) {
        viewModelScope.launch {
            val result = repository.importDataFromJson(jsonString)
            result.onSuccess { (locs, ords) ->
                _uiEvent.emit(UiEvent.ShowSuccess("Impor Sukses: Terintegrasi $locs lokasi baru & $ords catatan order baru!"))
            }
            result.onFailure { error ->
                _uiEvent.emit(UiEvent.ShowError("Format Kode Sync salah atau korup: ${error.message}"))
            }
        }
    }

    // --- Google Authentication and Secure Profile ---
    fun handleGoogleSignInResult(account: GoogleSignInAccount) {
        securePrefs.isLoggedIn = true
        securePrefs.displayName = account.displayName
        securePrefs.email = account.email
        securePrefs.photoUrl = account.photoUrl?.toString()
        securePrefs.idToken = account.idToken

        _isLoggedIn.value = true
        _userDisplayName.value = account.displayName
        _userEmail.value = account.email
        _userPhotoUrl.value = account.photoUrl?.toString()

        viewModelScope.launch {
            _uiEvent.emit(UiEvent.ShowSuccess("Berhasil masuk menggunakan Google: ${account.displayName}"))
        }
    }

    fun simulateGoogleSignIn(name: String, email: String, photoUrl: String?) {
        securePrefs.isLoggedIn = true
        securePrefs.displayName = name
        securePrefs.email = email
        securePrefs.photoUrl = photoUrl
        securePrefs.idToken = "simulated_google_token_123"

        _isLoggedIn.value = true
        _userDisplayName.value = name
        _userEmail.value = email
        _userPhotoUrl.value = photoUrl

        viewModelScope.launch {
            _uiEvent.emit(UiEvent.ShowSuccess("Simulasi Google Sign-In berhasil untuk: $name"))
        }
    }

    fun signOut() {
        securePrefs.clearProfile()
        _isLoggedIn.value = false
        _userDisplayName.value = null
        _userEmail.value = null
        _userPhotoUrl.value = null

        viewModelScope.launch {
            _uiEvent.emit(UiEvent.ShowSuccess("Berhasil keluar dari akun"))
        }
    }

    fun updateProfile(name: String, email: String) {
        securePrefs.displayName = name
        securePrefs.email = email
        _userDisplayName.value = name
        _userEmail.value = email

        viewModelScope.launch {
            _uiEvent.emit(UiEvent.ShowSuccess("Profil terenkripsi berhasil diperbarui"))
        }
    }
}
