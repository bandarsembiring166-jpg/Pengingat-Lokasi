package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import android.util.Base64
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.OrderLog
import com.example.data.SavedLocation
import com.example.data.OrderWithLocation
import com.example.viewmodel.OrderViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import coil.compose.AsyncImage
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import kotlin.math.sqrt
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp(
    viewModel: OrderViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // State collections
    val locations by viewModel.locations.collectAsStateWithLifecycle()
    val orders by viewModel.orders.collectAsStateWithLifecycle()
    val currentLocation by viewModel.currentLocation.collectAsStateWithLifecycle()
    val isSimulationEnabled by viewModel.isSimulationEnabled.collectAsStateWithLifecycle()
    val simulatedLat by viewModel.simulatedLat.collectAsStateWithLifecycle()
    val simulatedLng by viewModel.simulatedLng.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()

    // Secure user profile states (fully encrypted in SharedPreferences)
    val isLoggedIn by viewModel.isLoggedIn.collectAsStateWithLifecycle()
    val userDisplayName by viewModel.userDisplayName.collectAsStateWithLifecycle()
    val userEmail by viewModel.userEmail.collectAsStateWithLifecycle()
    val userPhotoUrl by viewModel.userPhotoUrl.collectAsStateWithLifecycle()

    // Google Sign-In Configuration
    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .build()
    }
    val googleSignInClient = remember { GoogleSignIn.getClient(context, gso) }

    val googleSignInLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            if (account != null) {
                viewModel.handleGoogleSignInResult(account)
            } else {
                scope.launch {
                    snackbarHostState.showSnackbar("Gagal mendapatkan akun Google")
                }
            }
        } catch (e: ApiException) {
            e.printStackTrace()
            scope.launch {
                val errorCode = e.statusCode
                snackbarHostState.showSnackbar("Gagal login Google (Error $errorCode). Silakan gunakan simulasi login aman.")
            }
        }
    }

    var locationPermissionGranted by remember { mutableStateOf(viewModel.hasLocationPermission()) }

    // Foreground Permission Launcher (Strictly enforces while-in-use ACCESS_FINE_LOCATION and ACCESS_COARSE_LOCATION)
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true
        locationPermissionGranted = fineGranted || coarseGranted
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        locationPermissionGranted = viewModel.hasLocationPermission()
    }

    // Navigation and tab states
    var currentTab by remember { mutableStateOf(0) } // 0 = Beranda, 1 = Lokasi, 2 = Sync

    // Selection/Dialog states
    var selectedLocationForHistory by remember { mutableStateOf<SavedLocation?>(null) }
    var locationToAddOrder by remember { mutableStateOf<SavedLocation?>(null) }
    var showAddLocationDialog by remember { mutableStateOf(false) }
    var locationToEdit by remember { mutableStateOf<SavedLocation?>(null) }
    var orderToEdit by remember { mutableStateOf<OrderWithLocation?>(null) }

    // Listen for UI events
    LaunchedEffect(key1 = true) {
        viewModel.uiEvent.collectLatest { event ->
            when (event) {
                is OrderViewModel.UiEvent.ShowSuccess -> {
                    snackbarHostState.showSnackbar(
                        message = event.message,
                        withDismissAction = true,
                        duration = SnackbarDuration.Short
                    )
                }
                is OrderViewModel.UiEvent.ShowError -> {
                    snackbarHostState.showSnackbar(
                        message = event.message,
                        withDismissAction = true,
                        duration = SnackbarDuration.Long
                    )
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(30.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Waktu Order Lokasi",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.simulateCloudSync() },
                        modifier = Modifier.testTag("sync_shortcut_button")
                    ) {
                        Icon(
                            imageVector = if (syncStatus == OrderViewModel.SyncStatus.SYNCING) Icons.Default.Refresh else Icons.Default.CloudSync,
                            contentDescription = "Sync Cloud",
                            tint = when (syncStatus) {
                                OrderViewModel.SyncStatus.SYNCING -> MaterialTheme.colorScheme.secondary
                                OrderViewModel.SyncStatus.SUCCESS -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
            ) {
                NavigationBarItem(
                    selected = currentTab == 0,
                    onClick = { currentTab = 0 },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Beranda") },
                    label = { Text("Beranda") },
                    modifier = Modifier.testTag("nav_home")
                )
                NavigationBarItem(
                    selected = currentTab == 1,
                    onClick = { currentTab = 1 },
                    icon = { Icon(Icons.Default.Place, contentDescription = "Lokasi") },
                    label = { Text("Lokasi") },
                    modifier = Modifier.testTag("nav_locations")
                )
                NavigationBarItem(
                    selected = currentTab == 2,
                    onClick = { currentTab = 2 },
                    icon = { Icon(Icons.Default.Sync, contentDescription = "Sinkronisasi") },
                    label = { Text("Sinkronisasi") },
                    modifier = Modifier.testTag("nav_sync")
                )
                NavigationBarItem(
                    selected = currentTab == 3,
                    onClick = { currentTab = 3 },
                    icon = { Icon(Icons.Default.Person, contentDescription = "Profil") },
                    label = { Text("Profil") },
                    modifier = Modifier.testTag("nav_profile")
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            when (currentTab) {
                0 -> DashboardScreen(
                    viewModel = viewModel,
                    locations = locations,
                    orders = orders,
                    currentLocation = currentLocation,
                    isSimulationEnabled = isSimulationEnabled,
                    simulatedLat = simulatedLat,
                    simulatedLng = simulatedLng,
                    locationPermissionGranted = locationPermissionGranted,
                    onRequestPermission = {
                        permissionLauncher.launch(
                            arrayOf(
                                android.Manifest.permission.ACCESS_FINE_LOCATION,
                                android.Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    },
                    onNavigateToLocations = { currentTab = 1 },
                    onAddOrderClick = { locationToAddOrder = it },
                    onViewHistoryClick = { selectedLocationForHistory = it },
                    onFlyToLocation = { loc ->
                        viewModel.toggleSimulation(true)
                        viewModel.updateSimulatedCoordinates(loc.latitude, loc.longitude)
                    }
                )
                1 -> LocationsScreen(
                    viewModel = viewModel,
                    locations = locations,
                    orders = orders,
                    onAddLocationClick = { showAddLocationDialog = true },
                    onAddOrderClick = { locationToAddOrder = it },
                    onViewHistoryClick = { selectedLocationForHistory = it },
                    onEditLocationClick = { locationToEdit = it },
                    onFlyToLocation = { loc ->
                        viewModel.toggleSimulation(true)
                        viewModel.updateSimulatedCoordinates(loc.latitude, loc.longitude)
                    }
                )
                2 -> SyncScreen(
                    viewModel = viewModel,
                    syncStatus = syncStatus
                )
                3 -> ProfileScreen(
                    viewModel = viewModel,
                    isLoggedIn = isLoggedIn,
                    displayName = userDisplayName,
                    email = userEmail,
                    photoUrl = userPhotoUrl,
                    onGoogleSignInClick = {
                        val signInIntent = googleSignInClient.signInIntent
                        googleSignInLauncher.launch(signInIntent)
                    }
                )
            }

            // --- DIALOGS & SHEET SIMULATIONS ---

            // 1. History Order Dialog per Location
            selectedLocationForHistory?.let { loc ->
                LocationHistoryDialog(
                    location = loc,
                    orders = orders.filter { it.locationId == loc.id },
                    onDismiss = { selectedLocationForHistory = null },
                    onEditOrder = { orderToEdit = it },
                    onDeleteOrder = { order -> viewModel.deleteOrder(OrderLog(id = order.orderId, locationId = order.locationId, itemsDescription = order.itemsDescription)) }
                )
            }

            // 2. Add Location Dialog
            if (showAddLocationDialog) {
                AddLocationDialog(
                    onDismiss = { showAddLocationDialog = false },
                    onSave = { name, address, lat, lng, notes, isFav ->
                        viewModel.insertLocation(name, address, lat, lng, notes, isFav)
                        showAddLocationDialog = false
                    },
                    currentSimulatedLat = simulatedLat,
                    currentSimulatedLng = simulatedLng
                )
            }

            // 3. Edit Location Dialog
            locationToEdit?.let { loc ->
                EditLocationDialog(
                    location = loc,
                    onDismiss = { locationToEdit = null },
                    onSave = { updatedLoc ->
                        viewModel.updateLocation(updatedLoc)
                        locationToEdit = null
                    }
                )
            }

            // 4. Add Order Dialog
            locationToAddOrder?.let { loc ->
                AddOrderDialog(
                    location = loc,
                    onDismiss = { locationToAddOrder = null },
                    onSave = { items, total, customer, notes, status ->
                        viewModel.insertOrder(loc.id, items, total, customer, notes, status)
                        locationToAddOrder = null
                    }
                )
            }

            // 5. Edit Order Dialog
            orderToEdit?.let { orderWithLoc ->
                EditOrderDialog(
                    orderWithLocation = orderWithLoc,
                    onDismiss = { orderToEdit = null },
                    onSave = { updatedOrder ->
                        viewModel.updateOrder(updatedOrder)
                        orderToEdit = null
                        // Refresh history if open
                        if (selectedLocationForHistory?.id == updatedOrder.locationId) {
                            selectedLocationForHistory = locations.find { it.id == updatedOrder.locationId }
                        }
                    }
                )
            }
        }
    }
}

// ==================== SCREEN: DASHBOARD (BERANDA) ====================
@Composable
fun DashboardScreen(
    viewModel: OrderViewModel,
    locations: List<SavedLocation>,
    orders: List<OrderWithLocation>,
    currentLocation: android.location.Location?,
    isSimulationEnabled: Boolean,
    simulatedLat: Double,
    simulatedLng: Double,
    locationPermissionGranted: Boolean,
    onRequestPermission: () -> Unit,
    onNavigateToLocations: () -> Unit,
    onAddOrderClick: (SavedLocation) -> Unit,
    onViewHistoryClick: (SavedLocation) -> Unit,
    onFlyToLocation: (SavedLocation) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Sync & Status Row (Styled to match the Clean Minimalism header status)
        item {
            val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                when (syncStatus) {
                                    OrderViewModel.SyncStatus.SUCCESS -> Color(0xFF10B981)
                                    OrderViewModel.SyncStatus.SYNCING -> Color(0xFF3B82F6)
                                    else -> Color(0xFF94A3B8)
                                }
                            )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when (syncStatus) {
                            OrderViewModel.SyncStatus.SUCCESS -> "TERSINKRONISASI"
                            OrderViewModel.SyncStatus.SYNCING -> "SINKRONISASI..."
                            else -> "BELUM SINKRON"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = { viewModel.simulateCloudSync() },
                    modifier = Modifier.size(36.dp).testTag("sync_header_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = "Sync",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // 2. Clean Minimalist Hero Banner Card
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Halo, Kurir Tangguh! 🛵",
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Kelola jam-jam tersibuk orderan di setiap lokasi agar pengiriman Anda berikutnya lebih efisien dan tepat waktu.",
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        // 3. Proximity alert banner (Extracted exactly from the Clean Minimalism Active Reminder HTML element)
        val nearestNearLocation = locations.find { loc ->
            val results = FloatArray(1)
            android.location.Location.distanceBetween(
                simulatedLat, simulatedLng,
                loc.latitude, loc.longitude,
                results
            )
            results[0].toInt() <= 300
        }

        if (isSimulationEnabled && nearestNearLocation != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onSecondaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = "Active Reminder",
                                tint = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "PENGINGAT AKTIF",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "Ingatkan jam order di ${nearestNearLocation.name}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }
        }

        // 4. Interactive Live GPS Map
        item {
            InteractiveCustomMap(
                locations = locations,
                orders = orders,
                simulatedLat = simulatedLat,
                simulatedLng = simulatedLng,
                onAddOrderClick = onAddOrderClick,
                onViewHistoryClick = onViewHistoryClick,
                onFlyToLocation = onFlyToLocation
            )
        }

        // Summary Indicators (Stats grid)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    title = "Total Tempat",
                    value = locations.size.toString(),
                    icon = Icons.Default.Place,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "Total Orderan",
                    value = orders.size.toString(),
                    icon = Icons.Default.ShoppingBag,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Active Location Proximity Monitoring & Alert Simulator
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isSimulationEnabled) Icons.Default.NotificationsActive else Icons.Default.NotificationsPaused,
                                contentDescription = null,
                                tint = if (isSimulationEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Pengingat Lokasi (Simulator GPS)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                        Switch(
                            checked = isSimulationEnabled,
                            onCheckedChange = { viewModel.toggleSimulation(it) },
                            modifier = Modifier.testTag("switch_simulation")
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Kurir tidak dapat bergerak fisik di browser. Gunakan kontrol di bawah untuk memindahkan lokasi virtual Anda atau klik penanda di peta. Ketika berada <300m dari lokasi order, alarm pengingat berdering!",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )

                    AnimatedVisibility(
                        visible = isSimulationEnabled,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Column(modifier = Modifier.padding(top = 12.dp)) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                            Spacer(modifier = Modifier.height(12.dp))

                            // Lat/Lng display and adjustment slider
                            Text(
                                text = "Koordinat Virtual Anda Saat Ini:",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Lat: ${String.format(Locale.US, "%.5f", simulatedLat)} | Lng: ${String.format(Locale.US, "%.5f", simulatedLng)}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Coordinate sliders for manual drifting
                            Text(
                                text = "Geser untuk Menyesuaikan Latitude (Utara - Selatan)",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("-6.210", fontSize = 10.sp, modifier = Modifier.width(36.dp))
                                Slider(
                                    value = simulatedLat.toFloat(),
                                    onValueChange = { viewModel.updateSimulatedCoordinates(it.toDouble(), simulatedLng) },
                                    valueRange = -6.210f..-6.180f,
                                    modifier = Modifier.weight(1f).testTag("slider_lat")
                                )
                                Text("-6.180", fontSize = 10.sp, modifier = Modifier.width(36.dp))
                            }

                            Text(
                                text = "Geser untuk Menyesuaikan Longitude (Barat - Timur)",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("106.760", fontSize = 10.sp, modifier = Modifier.width(42.dp))
                                Slider(
                                    value = simulatedLng.toFloat(),
                                    onValueChange = { viewModel.updateSimulatedCoordinates(simulatedLat, it.toDouble()) },
                                    valueRange = 106.760f..106.840f,
                                    modifier = Modifier.weight(1f).testTag("slider_lng")
                                )
                                Text("106.840", fontSize = 10.sp, modifier = Modifier.width(42.dp))
                            }

                            // Locations proximity checklist
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Jarak ke Tempat Terdekat:",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            if (locations.isEmpty()) {
                                Text("Belum ada lokasi tersimpan.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                locations.take(3).forEach { loc ->
                                    val results = FloatArray(1)
                                    android.location.Location.distanceBetween(
                                        simulatedLat, simulatedLng,
                                        loc.latitude, loc.longitude,
                                        results
                                    )
                                    val distance = results[0].toInt()
                                    val isNear = distance <= 300

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isNear) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                            )
                                            .padding(8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(loc.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text("Jarak: ${distance}m", fontSize = 11.sp, color = if (isNear) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (isNear) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = "Dekat",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Memicu Alarm", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                            } else {
                                                TextButton(
                                                    onClick = { viewModel.updateSimulatedCoordinates(loc.latitude, loc.longitude) },
                                                    contentPadding = PaddingValues(0.dp),
                                                    modifier = Modifier.height(24.dp)
                                                ) {
                                                    Text("Lompat GPS", fontSize = 10.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = !isSimulationEnabled,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Column(modifier = Modifier.padding(top = 12.dp)) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                            Spacer(modifier = Modifier.height(12.dp))

                            if (locationPermissionGranted) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                                        .padding(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Security verified",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "Akses GPS Aktif & Aman",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Text(
                                            text = "Izin lokasi foreground diberikan. Koordinat GPS asli sedang dilacak secara aman hanya saat aplikasi terbuka.",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            lineHeight = 15.sp
                                        )
                                    }
                                }
                            } else {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f))
                                        .padding(12.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Security,
                                            contentDescription = "Strict Location Control",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = "Izin Lokasi Diperlukan (Aktif Saja)",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Untuk melacak pengiriman nyata, aplikasi memerlukan izin GPS foreground. Kami menerapkan kontrol ketat: data lokasi HANYA diakses saat aplikasi aktif di layar untuk melindungi privasi Anda sepenuhnya.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        lineHeight = 15.sp
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(
                                        onClick = onRequestPermission,
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.MyLocation, null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Aktifkan GPS (Hanya Saat Aktif)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. Riwayat Penanda Order Sebelumnya (History of locations with orders)
        val locationsWithOrders = locations.filter { loc -> orders.any { it.locationId == loc.id } }

        item {
            Text(
                text = "Riwayat Penanda Order Sebelumnya",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        if (locationsWithOrders.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.padding(20.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.PinDrop,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Belum ada penanda order sebelumnya. Tambah order pada suatu lokasi terlebih dahulu.",
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        } else {
            items(locationsWithOrders, key = { "loc_order_${it.id}" }) { loc ->
                val locOrders = orders.filter { it.locationId == loc.id }.sortedByDescending { it.orderTime }
                val latestOrder = locOrders.firstOrNull()

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onViewHistoryClick(loc) }
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = loc.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Badge(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ) {
                                Text(
                                    text = "${locOrders.size} Order",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = loc.address,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (latestOrder != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Order Terakhir (${latestOrder.customerName.ifBlank { "Pelanggan" }})",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    Text(
                                        text = latestOrder.itemsDescription,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = formatRupiah(latestOrder.totalAmount),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    val sdf = remember { SimpleDateFormat("dd MMM, HH:mm", Locale("id", "ID")) }
                                    Text(
                                        text = sdf.format(Date(latestOrder.orderTime)),
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onFlyToLocation(loc) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Icon(Icons.Default.MyLocation, null, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Lompat GPS", fontSize = 10.sp)
                            }
                            OutlinedButton(
                                onClick = { onViewHistoryClick(loc) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Icon(Icons.Default.History, null, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Buka Riwayat", fontSize = 10.sp)
                            }
                            Button(
                                onClick = { onAddOrderClick(loc) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Icon(Icons.Default.Add, null, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Tambah Order", fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }

        // Recent Order Feeds
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Riwayat Jam Order Terbaru",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                TextButton(onClick = onNavigateToLocations) {
                    Text("Lihat Semua", fontSize = 13.sp)
                }
            }
        }

        if (orders.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Belum ada riwayat order tercatat.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        } else {
            items(orders.take(4), key = { "order_feed_${it.orderId}" }) { order ->
                DashboardOrderRow(order = order)
            }
        }
    }
}

@Composable
fun InteractiveCustomMap(
    locations: List<SavedLocation>,
    orders: List<OrderWithLocation>,
    simulatedLat: Double,
    simulatedLng: Double,
    onAddOrderClick: (SavedLocation) -> Unit,
    onViewHistoryClick: (SavedLocation) -> Unit,
    onFlyToLocation: (SavedLocation) -> Unit,
    modifier: Modifier = Modifier
) {
    var zoomLevel by remember { mutableStateOf(1.0f) }
    var selectedLocation by remember { mutableStateOf<SavedLocation?>(null) }

    // Constants for GPS bounds of Jakarta
    val latMin = -6.210
    val latMax = -6.180
    val lngMin = 106.760
    val lngMax = 106.840

    // Pulse animation for simulated user position
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseAlpha"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(280.dp),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 1. The Canvas Map
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(locations, zoomLevel) {
                        detectTapGestures { offset ->
                            val centerX = size.width / 2f
                            val centerY = size.height / 2f

                            var clickedLoc: SavedLocation? = null
                            var minDistance = Float.MAX_VALUE
                            val clickThreshold = 24.dp.toPx()

                            locations.forEach { loc ->
                                val rawX = (size.width * (loc.longitude - lngMin) / (lngMax - lngMin)).toFloat()
                                val rawY = (size.height * (latMax - loc.latitude) / (latMax - latMin)).toFloat()

                                // Adjust for zoom level centering
                                val pinX = (rawX - centerX) * zoomLevel + centerX
                                val pinY = (rawY - centerY) * zoomLevel + centerY

                                val dx = offset.x - pinX
                                val dy = offset.y - pinY
                                val dist = sqrt((dx * dx + dy * dy).toDouble()).toFloat()

                                if (dist < clickThreshold && dist < minDistance) {
                                    minDistance = dist
                                    clickedLoc = loc
                                }
                            }
                            selectedLocation = clickedLoc
                        }
                    }
            ) {
                val width = size.width
                val height = size.height
                val centerX = width / 2f
                val centerY = height / 2f

                // Map canvas transform scale
                // Draw Map Background Grid
                drawRect(color = Color(0xFFF1F5F9)) // Slate 100 for clean light theme map look

                // Grid Lines
                val latRange = latMax - latMin
                val lngRange = lngMax - lngMin

                // Draw vertical grid lines (longitude)
                for (i in 0..8) {
                    val rawX = width * i / 8f
                    val x = (rawX - centerX) * zoomLevel + centerX

                    if (x in 0f..width) {
                        drawLine(
                            color = Color.LightGray.copy(alpha = 0.5f),
                            start = Offset(x, 0f),
                            end = Offset(x, height),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                }

                // Draw horizontal grid lines (latitude)
                for (i in 0..6) {
                    val rawY = height * i / 6f
                    val y = (rawY - centerY) * zoomLevel + centerY

                    if (y in 0f..height) {
                        drawLine(
                            color = Color.LightGray.copy(alpha = 0.5f),
                            start = Offset(0f, y),
                            end = Offset(width, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                }

                // Draw stylized background Jakarta streets/roads for real map feeling
                val roadColor = Color.White
                val roadStroke = 4.dp.toPx() * zoomLevel

                // Street 1: Jl. Jenderal Sudirman (diagonal-vertical)
                val road1Path = Path().apply {
                    val x1 = (width * 0.5f - centerX) * zoomLevel + centerX
                    val y1 = (0f - centerY) * zoomLevel + centerY
                    val x2 = (width * 0.55f - centerX) * zoomLevel + centerX
                    val y2 = (height * 1f - centerY) * zoomLevel + centerY
                    moveTo(x1, y1)
                    lineTo(x2, y2)
                }
                drawPath(path = road1Path, color = roadColor, style = Stroke(roadStroke))

                // Street 2: Jl. Gatot Subroto (horizontal curved)
                val road2Path = Path().apply {
                    val x1 = (0f - centerX) * zoomLevel + centerX
                    val y1 = (height * 0.6f - centerY) * zoomLevel + centerY
                    val cx = (width * 0.5f - centerX) * zoomLevel + centerX
                    val cy = (height * 0.55f - centerY) * zoomLevel + centerY
                    val x2 = (width * 1f - centerX) * zoomLevel + centerX
                    val y2 = (height * 0.65f - centerY) * zoomLevel + centerY
                    moveTo(x1, y1)
                    quadraticTo(cx, cy, x2, y2)
                }
                drawPath(path = road2Path, color = roadColor, style = Stroke(roadStroke))

                // Street 3: Tol Dalam Kota / Lingkar Luar (Outer Ring Road)
                val road3Path = Path().apply {
                    val x1 = (width * 0.1f - centerX) * zoomLevel + centerX
                    val y1 = (height * 1f - centerY) * zoomLevel + centerY
                    val cx = (width * 0.15f - centerX) * zoomLevel + centerX
                    val cy = (height * 0.2f - centerY) * zoomLevel + centerY
                    val x2 = (width * 0.9f - centerX) * zoomLevel + centerX
                    val y2 = (0f - centerY) * zoomLevel + centerY
                    moveTo(x1, y1)
                    quadraticTo(cx, cy, x2, y2)
                }
                drawPath(path = road3Path, color = roadColor.copy(alpha = 0.8f), style = Stroke(roadStroke * 1.2f))

                // Draw River (Kali Ciliwung) in translucent blue-green
                val riverPath = Path().apply {
                    val x1 = (width * 0.35f - centerX) * zoomLevel + centerX
                    val y1 = (height * 1f - centerY) * zoomLevel + centerY
                    val cx1 = (width * 0.3f - centerX) * zoomLevel + centerX
                    val cy1 = (height * 0.5f - centerY) * zoomLevel + centerY
                    val cx2 = (width * 0.45f - centerX) * zoomLevel + centerX
                    val cy2 = (height * 0.3f - centerY) * zoomLevel + centerY
                    val x2 = (width * 0.4f - centerX) * zoomLevel + centerX
                    val y2 = (0f - centerY) * zoomLevel + centerY
                    moveTo(x1, y1)
                    cubicTo(cx1, cy1, cx2, cy2, x2, y2)
                }
                drawPath(path = riverPath, color = Color(0xFF93C5FD).copy(alpha = 0.4f), style = Stroke(8.dp.toPx() * zoomLevel))

                // 2. Draw Current User / Virtual GPS Position
                val gpsRawX = width * (simulatedLng - lngMin) / (lngMax - lngMin)
                val gpsRawY = height * (latMax - simulatedLat) / (latMax - latMin)
                val gpsX = ((gpsRawX - centerX) * zoomLevel + centerX).toFloat()
                val gpsY = ((gpsRawY - centerY) * zoomLevel + centerY).toFloat()

                // Pulsing accuracy halo
                drawCircle(
                    color = Color(0xFF3B82F6).copy(alpha = 0.2f * pulseAlpha),
                    radius = 32.dp.toPx() * pulseScale * zoomLevel,
                    center = Offset(gpsX, gpsY)
                )

                // User dot shadow
                drawCircle(
                    color = Color.Black.copy(alpha = 0.15f),
                    radius = 8.dp.toPx() * zoomLevel,
                    center = Offset(gpsX, gpsY + 2.dp.toPx())
                )

                // Inner primary dot
                drawCircle(
                    color = Color(0xFF3B82F6),
                    radius = 7.dp.toPx() * zoomLevel,
                    center = Offset(gpsX, gpsY)
                )

                // White outline
                drawCircle(
                    color = Color.White,
                    radius = 7.dp.toPx() * zoomLevel,
                    style = Stroke(2.dp.toPx()),
                    center = Offset(gpsX, gpsY)
                )

                // 3. Draw Saved Locations pins
                locations.forEach { loc ->
                    val pinRawX = width * (loc.longitude - lngMin) / (lngMax - lngMin)
                    val pinRawY = height * (latMax - loc.latitude) / (latMax - latMin)
                    val pinX = ((pinRawX - centerX) * zoomLevel + centerX).toFloat()
                    val pinY = ((pinRawY - centerY) * zoomLevel + centerY).toFloat()

                    val isSelected = selectedLocation?.id == loc.id
                    val ordersCount = orders.count { it.locationId == loc.id }
                    val hasOrders = ordersCount > 0

                    val pinColor = when {
                        isSelected -> Color(0xFFEF4444) // Vibrant Red for selection
                        hasOrders -> Color(0xFF10B981) // Emerald Green for locations with active orders
                        loc.isFavorite -> Color(0xFFF59E0B) // Amber for favorites
                        else -> Color(0xFF6366F1) // Indigo for normal locations
                    }

                    // Render Pin shadow
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.2f),
                        radius = (if (isSelected) 7.dp.toPx() else 5.dp.toPx()) * zoomLevel,
                        center = Offset(pinX, pinY + (if (isSelected) 6.dp.toPx() else 4.dp.toPx()))
                    )

                    // Draw outer pin circle
                    drawCircle(
                        color = pinColor,
                        radius = (if (isSelected) 12.dp.toPx() else 8.dp.toPx()) * zoomLevel,
                        center = Offset(pinX, pinY)
                    )

                    // Draw white border
                    drawCircle(
                        color = Color.White,
                        radius = (if (isSelected) 12.dp.toPx() else 8.dp.toPx()) * zoomLevel,
                        style = Stroke(1.5.dp.toPx() * zoomLevel),
                        center = Offset(pinX, pinY)
                    )

                    // Draw center dot/indicator
                    val centerDotRadius = (if (isSelected) 4.dp.toPx() else 3.dp.toPx()) * zoomLevel
                    drawCircle(
                        color = Color.White,
                        radius = centerDotRadius,
                        center = Offset(pinX, pinY)
                    )
                }
            }

            // Overlay 1: Title of Map in top-left
            Box(
                modifier = Modifier
                    .padding(12.dp)
                    .align(Alignment.TopStart)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.9f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Navigation,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "PETA KURIR AKTIF",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Overlay 2: Zoom Controls in bottom-right
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilledIconButton(
                    onClick = { if (zoomLevel < 3.0f) zoomLevel += 0.5f },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.White,
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.size(36.dp),
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Zoom In", modifier = Modifier.size(18.dp))
                }
                FilledIconButton(
                    onClick = { if (zoomLevel > 1.0f) zoomLevel -= 0.5f },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.White,
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.size(36.dp),
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Zoom Out", modifier = Modifier.size(18.dp))
                }
                FilledIconButton(
                    onClick = { zoomLevel = 1.0f },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.White,
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.size(36.dp),
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.CenterFocusStrong, contentDescription = "Reset Zoom", modifier = Modifier.size(16.dp))
                }
            }
        }
    }

    // Interactive details card when a pin is selected on the map
    AnimatedVisibility(
        visible = selectedLocation != null,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        selectedLocation?.let { loc ->
            val distance = FloatArray(1).apply {
                android.location.Location.distanceBetween(
                    simulatedLat, simulatedLng,
                    loc.latitude, loc.longitude,
                    this
                )
            }[0].toInt()

            val locOrders = orders.filter { it.locationId == loc.id }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (loc.isFavorite) Icons.Default.Star else Icons.Default.Place,
                                contentDescription = null,
                                tint = if (loc.isFavorite) Color(0xFFF59E0B) else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = loc.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        IconButton(
                            onClick = { selectedLocation = null },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Tutup", modifier = Modifier.size(16.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = loc.address,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "📍 Jarak: ${distance}m",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (distance <= 300) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "📦 Order: ${locOrders.size} kali",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (loc.notes.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Memo: ${loc.notes}",
                            style = MaterialTheme.typography.bodySmall,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onFlyToLocation(loc) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Lompat GPS", fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = { onViewHistoryClick(loc) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Riwayat", fontSize = 11.sp)
                        }
                        Button(
                            onClick = { onAddOrderClick(loc) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Order Baru", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(title, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(value, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
fun DashboardOrderRow(order: OrderWithLocation) {
    val formatSdf = remember { SimpleDateFormat("HH:mm - dd MMM", Locale("in", "ID")) }
    val formattedTime = formatSdf.format(Date(order.orderTime))

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        when (order.status) {
                            "SELESAI" -> MaterialTheme.colorScheme.primaryContainer
                            "BATAL" -> MaterialTheme.colorScheme.tertiaryContainer
                            else -> MaterialTheme.colorScheme.secondaryContainer
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (order.status) {
                        "SELESAI" -> Icons.Default.CheckCircle
                        "BATAL" -> Icons.Default.Cancel
                        else -> Icons.Default.Schedule
                    },
                    contentDescription = null,
                    tint = when (order.status) {
                        "SELESAI" -> MaterialTheme.colorScheme.onPrimaryContainer
                        "BATAL" -> MaterialTheme.colorScheme.onTertiaryContainer
                        else -> MaterialTheme.colorScheme.onSecondaryContainer
                    },
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = order.locationName,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = order.itemsDescription,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formattedTime,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (order.totalAmount > 0) formatRupiah(order.totalAmount) else "Tanpa Biaya",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}


// ==================== SCREEN: LOKASI & CATATAN ORDER ====================
@Composable
fun LocationsScreen(
    viewModel: OrderViewModel,
    locations: List<SavedLocation>,
    orders: List<OrderWithLocation>,
    onAddLocationClick: () -> Unit,
    onAddOrderClick: (SavedLocation) -> Unit,
    onViewHistoryClick: (SavedLocation) -> Unit,
    onEditLocationClick: (SavedLocation) -> Unit,
    onFlyToLocation: (SavedLocation) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredLocations = locations.filter {
        it.name.contains(searchQuery, ignoreCase = true) ||
                it.address.contains(searchQuery, ignoreCase = true)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Daftar Tempat Order", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Text("Tandai lokasi dan simpan riwayat pengirimannya", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(
                    onClick = onAddLocationClick,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("add_location_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Tambah", fontSize = 13.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Cari lokasi atau alamat...", fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().testTag("search_bar"),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (filteredLocations.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Explore, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (searchQuery.isEmpty()) "Belum ada lokasi tersimpan." else "Pencarian tidak ditemukan.",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(filteredLocations, key = { it.id }) { loc ->
                        LocationCard(
                            location = loc,
                            orderCount = orders.count { it.locationId == loc.id },
                            onAddOrder = { onAddOrderClick(loc) },
                            onViewHistory = { onViewHistoryClick(loc) },
                            onEdit = { onEditLocationClick(loc) },
                            onDelete = { viewModel.deleteLocation(loc) },
                            onToggleFav = { viewModel.updateLocation(loc.copy(isFavorite = !loc.isFavorite)) },
                            onFlyTo = { onFlyToLocation(loc) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LocationCard(
    location: SavedLocation,
    orderCount: Int,
    onAddOrder: () -> Unit,
    onViewHistory: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleFav: () -> Unit,
    onFlyTo: () -> Unit
) {
    var expandedMenu by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Title & Favorite button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = location.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (location.isFavorite) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Favorit",
                                tint = Color(0xFFFFB300),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = location.address,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row {
                    IconButton(onClick = onToggleFav, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = if (location.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "Toggle Favorite",
                            tint = if (location.isFavorite) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Box {
                        IconButton(onClick = { expandedMenu = true }, modifier = Modifier.size(32.dp)) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Menu",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = expandedMenu,
                            onDismissRequest = { expandedMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Edit Lokasi") },
                                onClick = {
                                    expandedMenu = false
                                    onEdit()
                                },
                                leadingIcon = { Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp)) }
                            )
                            DropdownMenuItem(
                                text = { Text("Hapus Lokasi", color = Color.Red) },
                                onClick = {
                                    expandedMenu = false
                                    onDelete()
                                },
                                leadingIcon = { Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp), tint = Color.Red) }
                            )
                        }
                    }
                }
            }

            if (location.notes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .padding(10.dp)
                ) {
                    Text(
                        text = "Catatan: ${location.notes}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(12.dp))

            // Stats & Actions bottom row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Total orders logged here badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "$orderCount Order",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                // Interactive quick triggers
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Fly Simulated GPS here
                    OutlinedButton(
                        onClick = onFlyTo,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.GpsFixed, null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("GPS", fontSize = 11.sp)
                    }

                    // Log Order Button
                    Button(
                        onClick = onAddOrder,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.height(32.dp).testTag("log_order_btn")
                    ) {
                        Icon(Icons.Default.HistoryEdu, null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Catat", fontSize = 11.sp)
                    }

                    // History Details Button
                    Button(
                        onClick = onViewHistory,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.height(32.dp).testTag("view_history_btn")
                    ) {
                        Icon(Icons.Default.History, null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Riwayat", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}


// ==================== SCREEN: SINKRONISASI DATA ====================
@Composable
fun SyncScreen(
    viewModel: OrderViewModel,
    syncStatus: OrderViewModel.SyncStatus
) {
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalContext.current.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val context = LocalContext.current

    var inputSyncCode by remember { mutableStateOf("") }
    var exportedCode by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text("Sinkronisasi Antar Perangkat", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.onBackground)
                Text("Simpan dan bagikan riwayat catatan jam order Anda", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Card 1: Cloud Sync Simulation
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudSync,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Sinkronisasi Cloud Terpadu", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Unggah semua catatan lokal Anda ke cloud secara aman. Ini akan mensinkronisasikan jam sibuk dan penanda lokasi Anda ke database pusat.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (syncStatus == OrderViewModel.SyncStatus.SYNCING) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Menghubungkan & Sinkronisasi data...", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    } else {
                        Button(
                            onClick = { viewModel.simulateCloudSync() },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().testTag("sync_cloud_button")
                        ) {
                            Text("Mulai Sinkronisasi Sekarang", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    if (syncStatus == OrderViewModel.SyncStatus.SUCCESS) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Database ter-sinkronisasi penuh!", color = Color(0xFF10B981), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // Card 2: Manual Device Sync (JSON Export Code)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Share, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Ekspor Kode Sync (Kirim ke HP Lain)", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Gunakan Kode Sync ini untuk menyalin seluruh catatan lokasi dan riwayat orderan Anda ke perangkat android rekan Anda yang lain tanpa internet.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            scope.launch {
                                val json = viewModel.getExportPayload()
                                exportedCode = Base64.encodeToString(json.toByteArray(Charsets.UTF_8), Base64.DEFAULT)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("generate_sync_btn")
                    ) {
                        Text("Generate Kode Sinkronisasi", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }

                    if (exportedCode.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = exportedCode,
                            onValueChange = {},
                            readOnly = true,
                            maxLines = 4,
                            shape = RoundedCornerShape(12.dp),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 10.sp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.colors(focusedContainerColor = MaterialTheme.colorScheme.background)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = {
                                val clip = ClipData.newPlainText("Order Sync Code", exportedCode)
                                clipboardManager.setPrimaryClip(clip)
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().testTag("copy_sync_btn")
                        ) {
                            Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Salin Kode Ke Clipboard", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // Card 3: Manual Device Sync (JSON Import Code)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Download, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Impor Kode Sync (Terima Data)", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Tempelkan Kode Sinkronisasi dari perangkat pengirim di bawah ini. Sistem akan menggabungkan database lokal dengan data yang diimpor secara otomatis tanpa merusak data lama.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = inputSyncCode,
                        onValueChange = { inputSyncCode = it },
                        placeholder = { Text("Tempel Kode Sync di sini...", fontSize = 12.sp) },
                        maxLines = 5,
                        shape = RoundedCornerShape(12.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp),
                        modifier = Modifier.fillMaxWidth().testTag("input_sync_field"),
                        colors = TextFieldDefaults.colors(focusedContainerColor = MaterialTheme.colorScheme.background)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            if (inputSyncCode.trim().isNotEmpty()) {
                                try {
                                    val decodedBytes = Base64.decode(inputSyncCode.trim(), Base64.DEFAULT)
                                    val decodedJson = String(decodedBytes, Charsets.UTF_8)
                                    viewModel.importBackup(decodedJson)
                                    inputSyncCode = ""
                                } catch (e: Exception) {
                                    // Event logged in viewmodel
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("import_sync_btn")
                    ) {
                        Text("Proses Impor & Gabungkan Data", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}


// ==================== DIALOG: HISTORY ORDER PER LOKASI ====================
@Composable
fun LocationHistoryDialog(
    location: SavedLocation,
    orders: List<OrderWithLocation>,
    onDismiss: () -> Unit,
    onEditOrder: (OrderWithLocation) -> Unit,
    onDeleteOrder: (OrderWithLocation) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
                .padding(vertical = 16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Header details
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Text(
                        text = "Riwayat: ${location.name}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                    )
                }

                Text(
                    text = location.address,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp, bottom = 12.dp)
                )

                HorizontalDivider()

                if (orders.isEmpty()) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.HistoryToggleOff, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Belum ada riwayat order tercatat di tempat ini.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 12.dp)
                    ) {
                        items(orders) { order ->
                            HistoryItemRow(
                                order = order,
                                onEdit = { onEditOrder(order) },
                                onDelete = { onDeleteOrder(order) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryItemRow(
    order: OrderWithLocation,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val formatFull = remember { SimpleDateFormat("HH:mm | dd MMMM yyyy", Locale("in", "ID")) }
    val formattedDate = formatFull.format(Date(order.orderTime))

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Customer & Time
                Column {
                    Text(
                        text = order.customerName.ifEmpty { "Pelanggan Anonim" },
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = formattedDate,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Status pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            when (order.status) {
                                "SELESAI" -> Color(0xFF10B981).copy(alpha = 0.2f)
                                "BATAL" -> Color(0xFFEF4444).copy(alpha = 0.2f)
                                else -> Color(0xFFF59E0B).copy(alpha = 0.2f)
                            }
                        )
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = order.status,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (order.status) {
                            "SELESAI" -> Color(0xFF10B981)
                            "BATAL" -> Color(0xFFEF4444)
                            else -> Color(0xFFF59E0B)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Order details
            Text(
                text = "Order: ${order.itemsDescription}",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (order.orderNotes.isNotEmpty()) {
                Text(
                    text = "Ket: ${order.orderNotes}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (order.totalAmount > 0) formatRupiah(order.totalAmount) else "Tanpa Biaya",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.secondary
                )

                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Edit, "Edit", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Delete, "Hapus", modifier = Modifier.size(16.dp), tint = Color.Red)
                    }
                }
            }
        }
    }
}


// ==================== DIALOG: TAMBAH LOKASI BARU ====================
@Composable
fun AddLocationDialog(
    onDismiss: () -> Unit,
    onSave: (name: String, address: String, lat: Double, lng: Double, notes: String, isFav: Boolean) -> Unit,
    currentSimulatedLat: Double,
    currentSimulatedLng: Double
) {
    var name by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var latText by remember { mutableStateOf(String.format(Locale.US, "%.5f", currentSimulatedLat)) }
    var lngText by remember { mutableStateOf(String.format(Locale.US, "%.5f", currentSimulatedLng)) }
    var notes by remember { mutableStateOf("") }
    var isFavorite by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth().padding(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Tambah Tempat Baru",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nama Tempat (cth: Warung Bu Joko)") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("add_loc_name")
                )

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Alamat (cth: Gg. Sejahtera 4)") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("add_loc_address")
                )

                // Coordinates Row
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = latText,
                        onValueChange = { latText = it },
                        label = { Text("Latitude") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).testTag("add_loc_lat")
                    )
                    OutlinedTextField(
                        value = lngText,
                        onValueChange = { lngText = it },
                        label = { Text("Longitude") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).testTag("add_loc_lng")
                    )
                }

                // Grab simulated/current button
                OutlinedButton(
                    onClick = {
                        latText = String.format(Locale.US, "%.5f", currentSimulatedLat)
                        lngText = String.format(Locale.US, "%.5f", currentSimulatedLng)
                    },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.GpsFixed, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Salin dari Koordinat GPS Sekarang", fontSize = 12.sp)
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Catatan Lokasi (cth: Samping Gapura)") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("add_loc_notes")
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { isFavorite = !isFavorite }
                ) {
                    Checkbox(checked = isFavorite, onCheckedChange = { isFavorite = it })
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Sukai & Tandai sebagai Favorit", fontSize = 13.sp)
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Batal")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (name.isNotEmpty() && address.isNotEmpty()) {
                                val parsedLat = latText.toDoubleOrNull() ?: currentSimulatedLat
                                val parsedLng = lngText.toDoubleOrNull() ?: currentSimulatedLng
                                onSave(name, address, parsedLat, parsedLng, notes, isFavorite)
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("save_location_btn")
                    ) {
                        Text("Simpan")
                    }
                }
            }
        }
    }
}


// ==================== DIALOG: EDIT LOKASI ====================
@Composable
fun EditLocationDialog(
    location: SavedLocation,
    onDismiss: () -> Unit,
    onSave: (SavedLocation) -> Unit
) {
    var name by remember { mutableStateOf(location.name) }
    var address by remember { mutableStateOf(location.address) }
    var latText by remember { mutableStateOf(location.latitude.toString()) }
    var lngText by remember { mutableStateOf(location.longitude.toString()) }
    var notes by remember { mutableStateOf(location.notes) }
    var isFavorite by remember { mutableStateOf(location.isFavorite) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth().padding(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Edit Tempat Lokasi",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nama Tempat") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Alamat") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = latText,
                        onValueChange = { latText = it },
                        label = { Text("Latitude") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = lngText,
                        onValueChange = { lngText = it },
                        label = { Text("Longitude") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Catatan Lokasi") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { isFavorite = !isFavorite }
                ) {
                    Checkbox(checked = isFavorite, onCheckedChange = { isFavorite = it })
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Favorit", fontSize = 13.sp)
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Batal")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (name.isNotEmpty() && address.isNotEmpty()) {
                                onSave(
                                    location.copy(
                                        name = name,
                                        address = address,
                                        latitude = latText.toDoubleOrNull() ?: location.latitude,
                                        longitude = lngText.toDoubleOrNull() ?: location.longitude,
                                        notes = notes,
                                        isFavorite = isFavorite
                                    )
                                )
                            }
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Perbarui")
                    }
                }
            }
        }
    }
}


// ==================== DIALOG: CATAT ORDERAN BARU ====================
@Composable
fun AddOrderDialog(
    location: SavedLocation,
    onDismiss: () -> Unit,
    onSave: (items: String, total: Double, customer: String, notes: String, status: String) -> Unit
) {
    var customer by remember { mutableStateOf("") }
    var items by remember { mutableStateOf("") }
    var totalText by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("SELESAI") } // SELESAI, PROSES, BATAL

    val statusOptions = listOf("SELESAI", "PROSES", "BATAL")

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth().padding(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Catat Order di ${location.name}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                OutlinedTextField(
                    value = customer,
                    onValueChange = { customer = it },
                    label = { Text("Nama Pelanggan (cth: Pak Toni)") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("add_order_customer")
                )

                OutlinedTextField(
                    value = items,
                    onValueChange = { items = it },
                    label = { Text("Menu / Barang Orderan (cth: Nasi Goreng)") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("add_order_items")
                )

                OutlinedTextField(
                    value = totalText,
                    onValueChange = { totalText = it },
                    label = { Text("Total Biaya (Rupiah, cth: 35000)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("add_order_total")
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Catatan Tambahan (cth: Kurang uang pas)") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("add_order_notes")
                )

                // Status selection
                Text("Status Pesanan:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    statusOptions.forEach { option ->
                        val isSelected = status == option
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) {
                                        when (option) {
                                            "SELESAI" -> Color(0xFF10B981)
                                            "BATAL" -> Color(0xFFEF4444)
                                            else -> Color(0xFFF59E0B)
                                        }
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    }
                                )
                                .clickable { status = option }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = option,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Batal")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (items.isNotEmpty()) {
                                val total = totalText.toDoubleOrNull() ?: 0.0
                                onSave(items, total, customer, notes, status)
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("save_order_btn")
                    ) {
                        Text("Log Order")
                    }
                }
            }
        }
    }
}


// ==================== DIALOG: EDIT ORDERAN ====================
@Composable
fun EditOrderDialog(
    orderWithLocation: OrderWithLocation,
    onDismiss: () -> Unit,
    onSave: (OrderLog) -> Unit
) {
    var customer by remember { mutableStateOf(orderWithLocation.customerName) }
    var items by remember { mutableStateOf(orderWithLocation.itemsDescription) }
    var totalText by remember { mutableStateOf(orderWithLocation.totalAmount.toInt().toString()) }
    var notes by remember { mutableStateOf(orderWithLocation.orderNotes) }
    var status by remember { mutableStateOf(orderWithLocation.status) }

    val statusOptions = listOf("SELESAI", "PROSES", "BATAL")

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth().padding(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Edit Log Orderan",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )

                OutlinedTextField(
                    value = customer,
                    onValueChange = { customer = it },
                    label = { Text("Nama Pelanggan") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = items,
                    onValueChange = { items = it },
                    label = { Text("Menu / Barang Orderan") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = totalText,
                    onValueChange = { totalText = it },
                    label = { Text("Total Biaya (Rupiah)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Catatan Tambahan") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Status selection
                Text("Status Pesanan:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    statusOptions.forEach { option ->
                        val isSelected = status == option
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) {
                                        when (option) {
                                            "SELESAI" -> Color(0xFF10B981)
                                            "BATAL" -> Color(0xFFEF4444)
                                            else -> Color(0xFFF59E0B)
                                        }
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    }
                                )
                                .clickable { status = option }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = option,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Batal")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (items.isNotEmpty()) {
                                val total = totalText.toDoubleOrNull() ?: 0.0
                                onSave(
                                    OrderLog(
                                        id = orderWithLocation.orderId,
                                        locationId = orderWithLocation.locationId,
                                        orderTime = orderWithLocation.orderTime,
                                        itemsDescription = items,
                                        totalAmount = total,
                                        status = status,
                                        customerName = customer,
                                        notes = notes,
                                        syncStatus = 0 // Needs sync again as it's modified
                                    )
                                )
                            }
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Perbarui")
                    }
                }
            }
        }
    }
}


// --- Utility functions ---

fun formatRupiah(amount: Double): String {
    return try {
        val format = NumberFormat.getCurrencyInstance(Locale("in", "ID"))
        format.minimumFractionDigits = 0
        format.maximumFractionDigits = 0
        format.format(amount)
    } catch (e: Exception) {
        "Rp " + String.format("%,d", amount.toLong())
    }
}

@Composable
fun ProfileScreen(
    viewModel: OrderViewModel,
    isLoggedIn: Boolean,
    displayName: String?,
    email: String?,
    photoUrl: String?,
    onGoogleSignInClick: () -> Unit
) {
    var isEditing by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf(displayName ?: "") }
    var editEmail by remember { mutableStateOf(email ?: "") }

    LaunchedEffect(displayName, email) {
        if (!isEditing) {
            editName = displayName ?: ""
            editEmail = email ?: ""
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profile_status_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "🔐 Keamanan Akun & Profil",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Semua data identitas Anda dienkripsi penuh di tingkat penyimpanan perangkat menggunakan AES-GCM 128-bit.",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (isLoggedIn) {
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Profile Image with elegant border
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!photoUrl.isNullOrEmpty()) {
                            AsyncImage(
                                model = photoUrl,
                                contentDescription = "Foto Profil",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                        } else {
                            Text(
                                text = (displayName ?: "U").take(1).uppercase(),
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (isEditing) {
                        OutlinedTextField(
                            value = editName,
                            onValueChange = { editName = it },
                            label = { Text("Nama Lengkap") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("edit_name_input"),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = editEmail,
                            onValueChange = { editEmail = it },
                            label = { Text("Email") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("edit_email_input"),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = { isEditing = false },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Batal")
                            }
                            Button(
                                onClick = {
                                    if (editName.isNotBlank() && editEmail.isNotBlank()) {
                                        viewModel.updateProfile(editName, editEmail)
                                        isEditing = false
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Simpan")
                            }
                        }
                    } else {
                        Text(
                            text = displayName ?: "Pengguna Google",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = email ?: "tidak-ada-email@google.com",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = { isEditing = true },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Ubah Profil")
                            }
                            Button(
                                onClick = { viewModel.signOut() },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Keluar Akun")
                            }
                        }
                    }
                }
            }

            // Real-time security visualization card showing decrypted vs encrypted physical storage!
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Visualisasi Enkripsi Fisik di Disk",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Berikut adalah perbandingan data Anda dalam RAM (Plaintext) dibandingkan dengan data terenkripsi di dalam file SharedPreferences (Ciphertext):",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // Field 1: Name
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        ) {
                            Text("Variabel: Display Name", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Plaintext (RAM):", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(displayName ?: "", fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Column(modifier = Modifier.weight(1.5f)) {
                                    Text("Ciphertext (Disk):", fontSize = 10.sp, color = MaterialTheme.colorScheme.error)
                                    Text(
                                        com.example.security.CryptoManager.encrypt(displayName ?: ""),
                                        fontSize = 11.sp,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.error,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Field 2: Email
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        ) {
                            Text("Variabel: Email Address", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Plaintext (RAM):", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(email ?: "", fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Column(modifier = Modifier.weight(1.5f)) {
                                    Text("Ciphertext (Disk):", fontSize = 10.sp, color = MaterialTheme.colorScheme.error)
                                    Text(
                                        com.example.security.CryptoManager.encrypt(email ?: ""),
                                        fontSize = 11.sp,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.error,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = null,
                        modifier = Modifier.size(90.dp),
                        tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Anda Belum Masuk",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Masuk untuk mengakses rincian profil pengiriman dan menyelaraskan dengan aman.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Real Google Sign-In Button
                    Button(
                        onClick = onGoogleSignInClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("google_signin_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.onBackground,
                            contentColor = MaterialTheme.colorScheme.background
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Login,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Masuk dengan Google (API GMS)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Google simulation block for container/sandbox environments
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.BugReport,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Simulasi Masuk Google (Terenkripsi)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Gunakan simulasi ini di lingkungan uji jika API Google Sign-In memerlukan sidik jari SHA-1 yang belum terdaftar. Meniru persis alur enkripsi local storage.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "Akun Google Tersedia:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedButton(
                                onClick = {
                                    viewModel.simulateGoogleSignIn(
                                        "Bandar Sembiring",
                                        "bandarsembiring166@gmail.com",
                                        "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?auto=format&fit=crop&w=150&q=80"
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.VerifiedUser, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Masuk sebagai: bandarsembiring166@gmail.com", fontSize = 11.sp)
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedButton(
                                onClick = {
                                    viewModel.simulateGoogleSignIn(
                                        "Guest Tester",
                                        "tester.aistudio@gmail.com",
                                        null
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.AccountCircle, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Masuk sebagai: tester.aistudio@gmail.com", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
