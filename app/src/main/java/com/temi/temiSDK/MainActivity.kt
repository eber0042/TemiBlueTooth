package com.temi.temiSDK

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.temi.temiSDK.ui.theme.GreetMiTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okio.IOException

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GreetMiTheme {
                BluetoothScreen()
            }
        }
    }
}

@SuppressLint("MutableCollectionMutableState")
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun BluetoothScreen() {
    val context = LocalContext.current
    val bluetoothAdapter: BluetoothAdapter =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    val permissionState = rememberPermissionState(Manifest.permission.BLUETOOTH)

    val coarseLocationPermissionState =
        rememberPermissionState(permission = Manifest.permission.ACCESS_COARSE_LOCATION)
    val fineLocationPermissionState =
        rememberPermissionState(permission = Manifest.permission.ACCESS_FINE_LOCATION)

    val pairedDevices = remember { mutableStateListOf<BluetoothDevice>() }

    LaunchedEffect(Unit) {
        while (true) {
            delay(2000)
        }
    }
    // State for tracking discovered devices and scanning state
    val discoveredDevices = remember { mutableStateOf(mutableListOf<BluetoothDevice>()) }
    var isScanning by remember { mutableStateOf(false) }
    var dots by remember { mutableIntStateOf(0) }

    // Manage dots animation for scanning indication
    LaunchedEffect(isScanning) {
        if (isScanning) {
            while (true) {
                delay(500)
                dots = (dots + 1) % 4 // Cycle through 0 to 3
            }
        } else {
            dots = 0 // Reset dots when not discovering
        }
    }

    // Handle discovery timeout (e.g., 12 seconds)
    LaunchedEffect(isScanning) {
        if (isScanning) {
            delay(12000) // Set timeout for scanning
            if (ActivityCompat.checkSelfPermission(
                    context, Manifest.permission.BLUETOOTH_SCAN
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                bluetoothAdapter.cancelDiscovery()
                isScanning = false
                Log.i("Bluetooth", "Discovery timed out")
            }
        }
    }

    // BroadcastReceiver to handle found Bluetooth devices
    val discoveryReceiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val action: String = intent?.action ?: return
                if (BluetoothDevice.ACTION_FOUND == action) {
                    val device: BluetoothDevice? =
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    device?.let {
                        if (!discoveredDevices.value.any { it.address == device.address }) {
                            discoveredDevices.value.add(device)
                        }
                    }
                }
            }
        }
    }

    // Register and unregister the BroadcastReceiver
    DisposableEffect(Unit) {
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        context.registerReceiver(discoveryReceiver, filter)

        onDispose {
            context.unregisterReceiver(discoveryReceiver)
            if (isScanning) {
                bluetoothAdapter.cancelDiscovery()
            }
        }
    }

    // Handle updating pairedDevices
    LaunchedEffect(pairedDevices) {

    }


    // Pair with selected device
    fun pairWithDevice(device: BluetoothDevice, pairedDevices: MutableList<BluetoothDevice>) {
        // Initiate pairing
        val isBonded = device.createBond()
        Log.i("DATA!", "Pairing with device: ${device.name}, Bonded: $isBonded")

        if (isBonded) {
            // Check if the device is already in pairedDevices before adding
            if (!pairedDevices.any { it.address == device.address }) {
                pairedDevices.add(device)
                Log.i("DATA!", "Successfully paired with device: ${device.name}")
            } else {
                Log.i("DATA!", "Device already paired: ${device.name}")
            }
        } else {
            Log.e("DATA!", "Failed to pair with device: ${device.name}")
        }
    }

    /*
        // Connect to device and send data
    fun connectAndSendData(device: BluetoothDevice, data: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val uuid = device.uuids.firstOrNull()?.uuid ?: return@launch
                val socket = device.createRfcommSocketToServiceRecord(uuid)
                socket.connect()

                val outputStream = socket.outputStream
                outputStream.write(data.toByteArray())
                outputStream.flush()
                outputStream.close()
                socket.close()
                Log.i("Request!", "Data sent to ${device.name}")
            } catch (e: IOException) {
                Log.e("Error!", "Failed to connect/send data: ${e.message}")
            }
        }
    }
     */


    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Need these permission to be able to do a scan
            if (!coarseLocationPermissionState.status.isGranted || !fineLocationPermissionState.status.isGranted || !permissionState.status.isGranted) {
                Text("Permissions denied.")
                Button(onClick = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package", context.packageName, null)
                    intent.data = uri
                    context.startActivity(intent)
                }) {
                    Text("Go to Settings")
                }
            } else {
                // Start/Stop Bluetooth scanning
                Button(onClick = {
                    if (isScanning) {
                        bluetoothAdapter.cancelDiscovery()
                        isScanning = false
                    } else {
                        bluetoothAdapter.startDiscovery()
                        isScanning = true
                    }
                }) {
                    Text(if (isScanning) "Stop Scanning" else "Start Scanning")
                }

                // Current paired devices
                if (pairedDevices.isNotEmpty()) {
                    Text("Paired Devices:")
                    pairedDevices.forEach { device ->
                        Text(device.name ?: "Unnamed device")
                    }
                } else {
                    Text("No paired devices found.")
                }

                // Display scanning progress
                if (isScanning) {
                    Text("Scanning${".".repeat(dots)}")
                } else if (discoveredDevices.value.isNotEmpty()) {
                    Text("Discovered Devices:")

                    LazyColumn {
                        items(discoveredDevices.value) { device ->
                            // Each device is displayed as a clickable row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth() // Ensure the row takes the full width
                                    .clickable {
                                        pairWithDevice(device, pairedDevices) // Initiate pairing
                                        // Uncomment below to send data after pairing
                                        // connectAndSendData(device, "Hello from Bluetooth!") // Send data
                                    }
                                    .padding(vertical = 8.dp) // Add vertical padding for better spacing
                            ) {
                                // Display device name and address
                                Text(
                                    text = "${device.name} || ${device.address}",
                                    modifier = Modifier
                                        .weight(1f) // Allow text to take up remaining space
                                        .padding(end = 16.dp) // Reduced end padding for better spacing
                                )
                                // Display pairing action
                                Text(
                                    text = "Pair",
                                    color = Color.Blue,
                                    modifier = Modifier
                                        .padding(start = 16.dp) // Adjust padding as needed
                                        .align(Alignment.CenterVertically) // Center align text vertically
                                )
                            }
                        }
                    }
                } else {
                    Text("No devices discovered.")
                }
            }
        }
    }
}

//@SuppressLint("MutableCollectionMutableState")
//@OptIn(ExperimentalPermissionsApi::class)
//@Composable
//fun BluetoothScreen() {
//    val context = LocalContext.current
//    val bluetoothAdapter: BluetoothAdapter =
//        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
//    val permissionState = rememberPermissionState(Manifest.permission.BLUETOOTH)
//
//    // State for tracking discovered devices
//    var devices by remember { mutableStateOf(listOf<BluetoothDevice>()) }
//    var isScanning by remember { mutableStateOf(false) }
//
//    var dots by remember { mutableIntStateOf(0) }
//    val discoveredDevices = remember { mutableStateOf(mutableListOf<BluetoothDevice>()) }
//
//    LaunchedEffect(isScanning) {
//        if (isScanning) {
//            while (true) {
//                delay(500)
//                dots = (dots + 1) % 4 // Cycle through 0 to 3
//            }
//        } else {
//            dots = 0 // Reset dots when not discovering
//        }
//    }
//
//    LaunchedEffect(isScanning) {
//        if (isScanning) {
//            delay(12000) // Set timeout (e.g., 12 seconds)
//            if (ActivityCompat.checkSelfPermission(
//                    context,
//                    Manifest.permission.BLUETOOTH_SCAN
//                ) != PackageManager.PERMISSION_GRANTED
//            ) {
//            }
//            bluetoothAdapter.cancelDiscovery()
//            isScanning = false
//            Log.i("Request!", "Discovery timed out")
//        }
//    }
//
//    LaunchedEffect(Unit) {
//        while (true) {
//            Log.i("DATA!", bluetoothAdapter.isDiscovering.toString())
//            delay(2000)
//        }
//    }
//    /*
//    // BroadcastReceiver to handle found Bluetooth devices
//    val receiver = rememberUpdatedState(object : BroadcastReceiver() {
//        override fun onReceive(context: Context, intent: Intent?) {
//            when (intent?.action) {
//                BluetoothDevice.ACTION_FOUND -> {
//                    val device: BluetoothDevice? =
//                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
//                    if (ActivityCompat.checkSelfPermission(
//                            context,
//                            Manifest.permission.BLUETOOTH_CONNECT
//                        ) != PackageManager.PERMISSION_GRANTED
//                    ) {
//                        return
//                    }
//                    if (device != null && device.name != null) {
//                        devices = devices + device
//                    }
//                }
//            }
//        }
//    })
//    */
//
//    val discoveryReceiver = remember {
//        object : BroadcastReceiver() {
//            override fun onReceive(context: Context?, intent: Intent?) {
//                val action: String = intent?.action ?: return
//                if (BluetoothDevice.ACTION_FOUND == action) {
//                    val device: BluetoothDevice =
//                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE) ?: return
//                    // Prevent duplicates by checking the address
//                    if (!discoveredDevices.value.any { it.address == device.address }) {
//                        discoveredDevices.value.add(device)
//                    }
//                }
//            }
//        }
//    }
//
//    DisposableEffect(Unit) {
//        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
//        context.registerReceiver(discoveryReceiver, filter)
//
//        onDispose {
//            context.unregisterReceiver(discoveryReceiver)
//            if (isScanning) {
//                bluetoothAdapter.cancelDiscovery()
//            }
//        }
//    }
//
//    Box(
//        modifier = Modifier.fillMaxSize(),
//        contentAlignment = Alignment.Center
//    ) {
//        Column(modifier = Modifier.padding(16.dp)) {
//            // Button to request permission
//            if (!permissionState.status.isGranted) {
//                Button(onClick = { permissionState.launchPermissionRequest() }) {
//                    Text("Request Bluetooth Permission")
//                }
//            } else {
//                // Start/Stop scanning for Bluetooth devices
//                Button(onClick = {
//                    if (isScanning) {
//                        bluetoothAdapter?.cancelDiscovery()
//                        isScanning = false
//                    } else {
//                        bluetoothAdapter?.startDiscovery()
//                        isScanning = true
//                    }
//                }) {
//                    Text(if (isScanning) "Stop Scanning" else "Start Scanning")
//                }
//
//                if (isScanning) {
//                    Text("Scanning${".".repeat(dots)}")
//                } else {
//                    // Display the list of discovered devices
//                    Text("Discovered Devices:")
//                    discoveredDevices.value.forEach { device ->
//                        Text((device.name + " || " + device.address))
//                    }
////                    LazyColumn(modifier = Modifier.padding(top = 16.dp)) {
////                        items(devices.size) { index ->
////                            val device = devices[index]
////                            Text(text = "${device.name} - ${device.address}")
////                        }
//                }
//            }
//        }
//    }
//}

//*******************************ARCHIVED CODE
//@OptIn(ExperimentalPermissionsApi::class)
//@Composable
//fun Greeting() {
//    val context = LocalContext.current
//    val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
//    val bluetoothAdapter = bluetoothManager.adapter
//    val enableBtLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
//        if (result.resultCode == Activity.RESULT_OK) {
//            Log.i("Result!", "Bluetooth enabled")
//        } else {
//            Log.i("Result!", "Bluetooth enabling failed")
//        }
//    }
//
//    val permissionState = rememberPermissionState(permission = Manifest.permission.BLUETOOTH_CONNECT)
//    val coarseLocationPermissionState = rememberPermissionState(permission = Manifest.permission.ACCESS_COARSE_LOCATION)
//    val fineLocationPermissionState = rememberPermissionState(permission = Manifest.permission.ACCESS_FINE_LOCATION)
//    val pairedDevices = remember { mutableStateOf(setOf<BluetoothDevice>()) }
//    val discoveredDevices = remember { mutableStateOf(mutableListOf<BluetoothDevice>()) }
//
//    var dots by remember { mutableStateOf(0) }
//    var isDiscovering by remember { mutableStateOf(false) }
//    val coroutineScope = rememberCoroutineScope()
//
//    // Handle dots animation for "Discovering devices..."
//    LaunchedEffect(isDiscovering) {
//        if (isDiscovering) {
//            while (true) {
//                delay(500)
//                dots = (dots + 1) % 4 // Cycle through 0 to 3
//            }
//        } else {
//            dots = 0 // Reset dots when not discovering
//        }
//    }
//
//    // Automatically stop discovery after a timeout
//    LaunchedEffect(isDiscovering) {
//        if (isDiscovering) {
//            delay(12000) // Set timeout (e.g., 12 seconds)
//            if (ActivityCompat.checkSelfPermission(
//                    context,
//                    Manifest.permission.BLUETOOTH_SCAN
//                ) != PackageManager.PERMISSION_GRANTED
//            ) {
//            }
//            bluetoothAdapter.cancelDiscovery()
//            isDiscovering = false
//            Log.i("Request!", "Discovery timed out")
//        }
//    }
//
//    LaunchedEffect(Unit) {
//        when {
//            !permissionState.status.isGranted -> {
//                if (permissionState.status.shouldShowRationale) {
//                    Log.i("Request!", "Showing rationale for Bluetooth permission")
//                } else {
//                    Log.i("Request!", "Bluetooth permission denied with 'Don't ask again'")
//                }
//            }
//            !coarseLocationPermissionState.status.isGranted -> {
//                if (coarseLocationPermissionState.status.shouldShowRationale) {
//                    Log.i("Request!", "Showing rationale for Coarse Location permission")
//                } else {
//                    Log.i("Request!", "Coarse Location permission denied with 'Don't ask again'")
//                }
//            }
//            !fineLocationPermissionState.status.isGranted -> {
//                if (fineLocationPermissionState.status.shouldShowRationale) {
//                    Log.i("Request!", "Showing rationale for Fine Location permission")
//                } else {
//                    Log.i("Request!", "Fine Location permission denied with 'Don't ask again'")
//                }
//            }
//            bluetoothAdapter == null -> {
//                Log.e("Request!", "Bluetooth is not supported on this device.")
//            }
//            !bluetoothAdapter.isEnabled -> {
//                Log.i("Request!", "Sent activate Bluetooth request")
//                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
//                enableBtLauncher.launch(enableBtIntent)
//            }
//            else -> {
//                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
//                    ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
//                    ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
//
//                    pairedDevices.value = bluetoothAdapter.bondedDevices
//
//                    if (!isDiscovering) {
//                        Log.i("Request!", "Starting Discovery")
//                        val successDiscovery = bluetoothAdapter.startDiscovery()
//                        isDiscovering = successDiscovery
//                        Log.i("Request!", "Start Discovery Successful: $successDiscovery")
//
//                        if (!successDiscovery) {
//                            Log.e("Request!", "Failed to start discovery")
//                        }
//                    } else {
//                        Log.i("Request!", "Discovery is already in progress")
//                    }
//                }
//            }
//        }
//    }
//
//    val discoveryReceiver = remember {
//        object : BroadcastReceiver() {
//            override fun onReceive(context: Context?, intent: Intent?) {
//                val action: String = intent?.action ?: return
//                if (BluetoothDevice.ACTION_FOUND == action) {
//                    val device: BluetoothDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE) ?: return
//                    // Prevent duplicates by checking the address
//                    if (!discoveredDevices.value.any { it.address == device.address }) {
//                        discoveredDevices.value.add(device)
//                    }
//                }
//            }
//        }
//    }
//
//    DisposableEffect(Unit) {
//        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
//        context.registerReceiver(discoveryReceiver, filter)
//        onDispose {
//            context.unregisterReceiver(discoveryReceiver)
//            if (isDiscovering) {
//                bluetoothAdapter.cancelDiscovery()
//            }
//        }
//    }
//
//    Box(modifier = Modifier.fillMaxSize()) {
//        LazyColumn(
//            modifier = Modifier
//                .fillMaxSize()
//                .padding(top = 50.dp, bottom = 50.dp), // Add padding to top and bottom
//            horizontalAlignment = Alignment.CenterHorizontally
//        ) {
//            item {
//                when {
//                    permissionState.status.isGranted &&
//                            coarseLocationPermissionState.status.isGranted &&
//                            fineLocationPermissionState.status.isGranted -> {
//
//                        Text("Bluetooth permission granted!")
//                        if (pairedDevices.value.isNotEmpty()) {
//                            Text("Paired Devices:")
//                            pairedDevices.value.forEach { device ->
//                                Text(device.name ?: "Unnamed device")
//                            }
//                        } else {
//                            Text("No paired devices found.")
//                        }
//
//                        if (isDiscovering) {
//                            Text("Discovering devices${".".repeat(dots)}") // Dynamic text with dots
//                        } else if (discoveredDevices.value.isNotEmpty()) {
//                            Text("Discovered Devices:")
//                            discoveredDevices.value.forEach { device ->
//                                Text((device.name + " || "+  device.address))
//                            }
//                        } else {
//                            Text("No devices discovered.")
//                        }
//
//                        // Button to stop discovery
//                        if (isDiscovering) {
//                            Button(onClick = {
//                                bluetoothAdapter.cancelDiscovery()
//                                isDiscovering = false
//                                Log.i("Request!", "Discovery cancelled")
//                            }) {
//                                Text("Stop Discovery")
//                            }
//                        }
//                    }
//                    else -> {
//                        Text("Permissions denied.")
//                        Button(onClick = {
//                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
//                            val uri = Uri.fromParts("package", context.packageName, null)
//                            intent.data = uri
//                            context.startActivity(intent)
//                        }) {
//                            Text("Go to Settings")
//                        }
//                    }
//                }
//            }
//        }
//    }
//}

//@OptIn(ExperimentalPermissionsApi::class)
//@Composable
//fun Greeting() {
//    val context = LocalContext.current
//    val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
//    val bluetoothAdapter = bluetoothManager.adapter
//    val enableBtLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
//        if (result.resultCode == Activity.RESULT_OK) {
//            Log.i("Result!", "Bluetooth enabled")
//        } else {
//            Log.i("Result!", "Bluetooth enabling failed")
//        }
//    }
//
//    val permissionState = rememberPermissionState(permission = Manifest.permission.BLUETOOTH_CONNECT)
//    val coarseLocationPermissionState = rememberPermissionState(permission = Manifest.permission.ACCESS_COARSE_LOCATION)
//    val fineLocationPermissionState = rememberPermissionState(permission = Manifest.permission.ACCESS_FINE_LOCATION)
//    val pairedDevices = remember { mutableStateOf(setOf<BluetoothDevice>()) }
//    val discoveredDevices = remember { mutableStateOf(mutableListOf<BluetoothDevice>()) }
//    var isDiscovering by remember { mutableStateOf(false) }
//    var dots by remember { mutableStateOf(0) }
//
//    // Handle dots animation for "Discovering devices..."
//    LaunchedEffect(isDiscovering) {
//        if (isDiscovering) {
//            while (isDiscovering) {
//                delay(500)
//                dots = (dots + 1) % 4
//            }
//        } else {
//            dots = 0
//        }
//    }
//
//    // Automatically stop discovery after a timeout
//    LaunchedEffect(isDiscovering) {
//        if (isDiscovering) {
//            delay(12000) // Timeout for discovery
//            if (ActivityCompat.checkSelfPermission(
//                    context,
//                    Manifest.permission.BLUETOOTH_SCAN
//                ) != PackageManager.PERMISSION_GRANTED
//            ) {
//            }
//            bluetoothAdapter.cancelDiscovery()
//            isDiscovering = false
//            Log.i("Request!", "Discovery timed out")
//        }
//    }
//
//    // Request necessary permissions and start discovery
//    LaunchedEffect(Unit) {
//        when {
//            !permissionState.status.isGranted -> {
//                permissionState.launchPermissionRequest()
//            }
//            !coarseLocationPermissionState.status.isGranted -> {
//                coarseLocationPermissionState.launchPermissionRequest()
//            }
//            !fineLocationPermissionState.status.isGranted -> {
//                fineLocationPermissionState.launchPermissionRequest()
//            }
//            bluetoothAdapter == null -> {
//                Log.e("Request!", "Bluetooth is not supported on this device.")
//            }
//            !bluetoothAdapter.isEnabled -> {
//                Log.i("Request!", "Sent activate Bluetooth request")
//                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
//                enableBtLauncher.launch(enableBtIntent)
//            }
//            else -> {
//                if (!isDiscovering) {
//                    pairedDevices.value = bluetoothAdapter.bondedDevices
//                    val successDiscovery = bluetoothAdapter.startDiscovery()
//                    isDiscovering = successDiscovery
//                    Log.i("Request!", "Start Discovery Successful: $successDiscovery")
//                }
//            }
//        }
//    }
//
//    // BroadcastReceiver for discovered devices
//    val discoveryReceiver = remember {
//        object : BroadcastReceiver() {
//            override fun onReceive(context: Context?, intent: Intent?) {
//                val action: String = intent?.action ?: return
//                if (BluetoothDevice.ACTION_FOUND == action) {
//                    val device: BluetoothDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE) ?: return
//                    if (!discoveredDevices.value.any { it.address == device.address }) {
//                        discoveredDevices.value.add(device)
//                    }
//                }
//            }
//        }
//    }
//
//    DisposableEffect(Unit) {
//        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
//        context.registerReceiver(discoveryReceiver, filter)
//        onDispose {
//            context.unregisterReceiver(discoveryReceiver)
//            if (isDiscovering) {
//                bluetoothAdapter.cancelDiscovery()
//            }
//        }
//    }
//
//    // Pair with selected device
//    fun pairWithDevice(device: BluetoothDevice) {
//        device.createBond() // Initiates pairing
//        Log.i("Request!", "Pairing with device: ${device.name}")
//    }
//
//    // Connect to device and send data
//    fun connectAndSendData(device: BluetoothDevice, data: String) {
//        CoroutineScope(Dispatchers.IO).launch {
//            try {
//                val uuid = device.uuids.firstOrNull()?.uuid ?: return@launch
//                val socket = device.createRfcommSocketToServiceRecord(uuid)
//                socket.connect()
//
//                val outputStream = socket.outputStream
//                outputStream.write(data.toByteArray())
//                outputStream.flush()
//                outputStream.close()
//                socket.close()
//                Log.i("Request!", "Data sent to ${device.name}")
//            } catch (e: IOException) {
//                Log.e("Error!", "Failed to connect/send data: ${e.message}")
//            }
//        }
//    }
//
//    // UI Components
//    Box(modifier = Modifier.fillMaxSize()) {
//        LazyColumn(
//            modifier = Modifier.fillMaxSize().padding(top = 50.dp, bottom = 50.dp),
//            horizontalAlignment = Alignment.CenterHorizontally
//        ) {
//            item {
//                when {
//                    permissionState.status.isGranted &&
//                            coarseLocationPermissionState.status.isGranted &&
//                            fineLocationPermissionState.status.isGranted -> {
//
//                        if (pairedDevices.value.isNotEmpty()) {
//                            Text("Paired Devices:")
//                            pairedDevices.value.forEach { device ->
//                                Text(device.name ?: "Unnamed device")
//                            }
//                        } else {
//                            Text("No paired devices found.")
//                        }
//
//                        if (isDiscovering) {
//                            Text("Discovering devices${".".repeat(dots)}")
//                        } else if (discoveredDevices.value.isNotEmpty()) {
//                            Text("Discovered Devices:")
//                            discoveredDevices.value.forEach { device ->
//                                Row(
//                                    modifier = Modifier
//                                        .fillMaxWidth()
//                                        .clickable {
//                                            pairWithDevice(device) // Initiate pairing
//                                            connectAndSendData(device, "Hello from Bluetooth!") // Send data
//                                        }
//                                ) {
//                                    Text(
//                                        text = "${device.name} || ${device.address}",
//                                        modifier = Modifier.weight(1f)
//                                    )
//                                    Text(
//                                        text = "Pair & Send",
//                                        color = Color.Blue,
//                                        modifier = Modifier.padding(start = 4.dp) // Adjust padding as needed
//                                    )
//                                }
//                            }
//                        } else {
//                            Text("No devices discovered.")
//                        }
//
//                        if (isDiscovering) {
//                            Button(onClick = {
//                                bluetoothAdapter.cancelDiscovery()
//                                isDiscovering = false
//                                Log.i("Request!", "Discovery cancelled")
//                            }) {
//                                Text("Stop Discovery")
//                            }
//                        }
//                    }
//                    else -> {
//                        Text("Permissions denied.")
//                        Button(onClick = {
//                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
//                            val uri = Uri.fromParts("package", context.packageName, null)
//                            intent.data = uri
//                            context.startActivity(intent)
//                        }) {
//                            Text("Go to Settings")
//                        }
//                    }
//                }
//            }
//        }
//    }
//}


//@OptIn(ExperimentalPermissionsApi::class)
//@Composable
//fun Greeting() {
//    val context = LocalContext.current
//    val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
//    val bluetoothAdapter = bluetoothManager.adapter
//    val enableBtLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
//        if (result.resultCode == Activity.RESULT_OK) {
//            Log.i("Result!", "Bluetooth enabled")
//        } else {
//            Log.i("Result!", "Bluetooth enabling failed")
//        }
//    }
//
//    val permissionState = rememberPermissionState(permission = Manifest.permission.BLUETOOTH_CONNECT)
//    val coarseLocationPermissionState = rememberPermissionState(permission = Manifest.permission.ACCESS_COARSE_LOCATION)
//    val fineLocationPermissionState = rememberPermissionState(permission = Manifest.permission.ACCESS_FINE_LOCATION)
//    val pairedDevices = remember { mutableStateOf(setOf<BluetoothDevice>()) }
//    val discoveredDevices = remember { mutableStateOf(mutableListOf<BluetoothDevice>()) }
//    var isDiscovering by remember { mutableStateOf(false) }
//    var dots by remember { mutableStateOf(0) }
//
//    // Handle dots animation for "Discovering devices..."
//    LaunchedEffect(isDiscovering) {
//        if (isDiscovering) {
//            while (isDiscovering) {
//                delay(500)
//                dots = (dots + 1) % 4
//            }
//        } else {
//            dots = 0
//        }
//    }
//
//    // Automatically stop discovery after a timeout
//    LaunchedEffect(isDiscovering) {
//        if (isDiscovering) {
//            delay(12000) // Timeout for discovery
//            if (ActivityCompat.checkSelfPermission(
//                    context,
//                    Manifest.permission.BLUETOOTH_SCAN
//                ) != PackageManager.PERMISSION_GRANTED
//            ) {
//            }
//            bluetoothAdapter.cancelDiscovery()
//            isDiscovering = false
//            Log.i("Request!", "Discovery timed out")
//        }
//    }
//
//    // Request necessary permissions and start discovery
//    LaunchedEffect(Unit) {
//        when {
//            !permissionState.status.isGranted -> {
//                permissionState.launchPermissionRequest()
//            }
//            !coarseLocationPermissionState.status.isGranted -> {
//                coarseLocationPermissionState.launchPermissionRequest()
//            }
//            !fineLocationPermissionState.status.isGranted -> {
//                fineLocationPermissionState.launchPermissionRequest()
//            }
//            bluetoothAdapter == null -> {
//                Log.e("Request!", "Bluetooth is not supported on this device.")
//            }
//            !bluetoothAdapter.isEnabled -> {
//                Log.i("Request!", "Sent activate Bluetooth request")
//                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
//                enableBtLauncher.launch(enableBtIntent)
//            }
//            else -> {
//                if (!isDiscovering) {
//                    pairedDevices.value = bluetoothAdapter.bondedDevices
//                    val successDiscovery = bluetoothAdapter.startDiscovery()
//                    isDiscovering = successDiscovery
//                    Log.i("Request!", "Start Discovery Successful: $successDiscovery")
//                }
//            }
//        }
//    }
//
//    // BroadcastReceiver for discovered devices
//    val discoveryReceiver = remember {
//        object : BroadcastReceiver() {
//            override fun onReceive(context: Context?, intent: Intent?) {
//                val action: String = intent?.action ?: return
//                if (BluetoothDevice.ACTION_FOUND == action) {
//                    val device: BluetoothDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE) ?: return
//                    if (!discoveredDevices.value.any { it.address == device.address }) {
//                        discoveredDevices.value.add(device)
//                    }
//                }
//            }
//        }
//    }
//
//    DisposableEffect(Unit) {
//        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
//        context.registerReceiver(discoveryReceiver, filter)
//        onDispose {
//            context.unregisterReceiver(discoveryReceiver)
//            if (isDiscovering) {
//                bluetoothAdapter.cancelDiscovery()
//            }
//        }
//    }
//
//    // Pair with selected device
//    fun pairWithDevice(device: BluetoothDevice) {
//        device.createBond() // Initiates pairing
//        Log.i("Request!", "Pairing with device: ${device.name}")
//    }
//
//    // UI Components
//    Box(modifier = Modifier.fillMaxSize()) {
//        LazyColumn(
//            modifier = Modifier.fillMaxSize().padding(top = 50.dp, bottom = 50.dp),
//            horizontalAlignment = Alignment.CenterHorizontally
//        ) {
//            item {
//                when {
//                    permissionState.status.isGranted &&
//                            coarseLocationPermissionState.status.isGranted &&
//                            fineLocationPermissionState.status.isGranted -> {
//
//                        if (pairedDevices.value.isNotEmpty()) {
//                            Text("Paired Devices:")
//                            pairedDevices.value.forEach { device ->
//                                Text(device.name ?: "Unnamed device")
//                            }
//                        } else {
//                            Text("No paired devices found.")
//                        }
//
//                        if (isDiscovering) {
//                            Text("Discovering devices${".".repeat(dots)}")
//                        } else if (discoveredDevices.value.isNotEmpty()) {
//                            Text("Discovered Devices:")
//                            discoveredDevices.value.forEach { device ->
//                                Row(
//                                    modifier = Modifier
//                                        .fillMaxWidth()
//                                        .clickable {
//                                            pairWithDevice(device) // Initiate pairing
//                                        }
//                                ) {
//                                    Text(
//                                        text = "${device.name} || ${device.address}",
//                                        modifier = Modifier.weight(1f)
//                                    )
//                                    Text(
//                                        text = "Pair",
//                                        color = Color.Blue,
//                                        modifier = Modifier.padding(start = 4.dp) // Adjust padding as needed
//                                    )
//                                }
//                            }
//                        } else {
//                            Text("No devices discovered.")
//                        }
//
//                        if (isDiscovering) {
//                            Button(onClick = {
//                                bluetoothAdapter.cancelDiscovery()
//                                isDiscovering = false
//                                Log.i("Request!", "Discovery cancelled")
//                            }) {
//                                Text("Stop Discovery")
//                            }
//                        }
//                    }
//                    else -> {
//                        Text("Permissions denied.")
//                        Button(onClick = {
//                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
//                            val uri = Uri.fromParts("package", context.packageName, null)
//                            intent.data = uri
//                            context.startActivity(intent)
//                        }) {
//                            Text("Go to Settings")
//                        }
//                    }
//                }
//            }
//        }
//    }
//}


// System with Discovery on it
//@OptIn(ExperimentalPermissionsApi::class)
//@Composable
//fun Greeting() {
//    val context = LocalContext.current
//    val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
//    val bluetoothAdapter = bluetoothManager.adapter
//    val enableBtLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
//        if (result.resultCode == Activity.RESULT_OK) {
//            Log.i("Result!", "Bluetooth enabled")
//        } else {
//            Log.i("Result!", "Bluetooth enabling failed")
//        }
//    }
//
//    val permissionState = rememberPermissionState(permission = Manifest.permission.BLUETOOTH_CONNECT)
//    val coarseLocationPermissionState = rememberPermissionState(permission = Manifest.permission.ACCESS_COARSE_LOCATION)
//    val fineLocationPermissionState = rememberPermissionState(permission = Manifest.permission.ACCESS_FINE_LOCATION)
//    val pairedDevices = remember { mutableStateOf(setOf<BluetoothDevice>()) }
//    val discoveredDevices = remember { mutableStateOf(mutableListOf<BluetoothDevice>()) }
//
//    var dots by remember { mutableStateOf(0) }
//    var isDiscovering by remember { mutableStateOf(false) }
//    val coroutineScope = rememberCoroutineScope()
//
//    // Handle dots animation for "Discovering devices..."
//    LaunchedEffect(isDiscovering) {
//        if (isDiscovering) {
//            while (true) {
//                delay(500)
//                dots = (dots + 1) % 4 // Cycle through 0 to 3
//            }
//        } else {
//            dots = 0 // Reset dots when not discovering
//        }
//    }
//
//    // Automatically stop discovery after a timeout
//    LaunchedEffect(isDiscovering) {
//        if (isDiscovering) {
//            delay(12000) // Set timeout (e.g., 12 seconds)
//            if (ActivityCompat.checkSelfPermission(
//                    context,
//                    Manifest.permission.BLUETOOTH_SCAN
//                ) != PackageManager.PERMISSION_GRANTED
//            ) {
//            }
//            bluetoothAdapter.cancelDiscovery()
//            isDiscovering = false
//            Log.i("Request!", "Discovery timed out")
//        }
//    }
//
//    LaunchedEffect(Unit) {
//        when {
//            !permissionState.status.isGranted -> {
//                if (permissionState.status.shouldShowRationale) {
//                    Log.i("Request!", "Showing rationale for Bluetooth permission")
//                } else {
//                    Log.i("Request!", "Bluetooth permission denied with 'Don't ask again'")
//                }
//            }
//            !coarseLocationPermissionState.status.isGranted -> {
//                if (coarseLocationPermissionState.status.shouldShowRationale) {
//                    Log.i("Request!", "Showing rationale for Coarse Location permission")
//                } else {
//                    Log.i("Request!", "Coarse Location permission denied with 'Don't ask again'")
//                }
//            }
//            !fineLocationPermissionState.status.isGranted -> {
//                if (fineLocationPermissionState.status.shouldShowRationale) {
//                    Log.i("Request!", "Showing rationale for Fine Location permission")
//                } else {
//                    Log.i("Request!", "Fine Location permission denied with 'Don't ask again'")
//                }
//            }
//            bluetoothAdapter == null -> {
//                Log.e("Request!", "Bluetooth is not supported on this device.")
//            }
//            !bluetoothAdapter.isEnabled -> {
//                Log.i("Request!", "Sent activate Bluetooth request")
//                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
//                enableBtLauncher.launch(enableBtIntent)
//            }
//            else -> {
//                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
//                    ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
//                    ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
//
//                    pairedDevices.value = bluetoothAdapter.bondedDevices
//
//                    if (!isDiscovering) {
//                        Log.i("Request!", "Starting Discovery")
//                        val successDiscovery = bluetoothAdapter.startDiscovery()
//                        isDiscovering = successDiscovery
//                        Log.i("Request!", "Start Discovery Successful: $successDiscovery")
//
//                        if (!successDiscovery) {
//                            Log.e("Request!", "Failed to start discovery")
//                        }
//                    } else {
//                        Log.i("Request!", "Discovery is already in progress")
//                    }
//                }
//            }
//        }
//    }
//
//    val discoveryReceiver = remember {
//        object : BroadcastReceiver() {
//            override fun onReceive(context: Context?, intent: Intent?) {
//                val action: String = intent?.action ?: return
//                if (BluetoothDevice.ACTION_FOUND == action) {
//                    val device: BluetoothDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE) ?: return
//                    // Prevent duplicates by checking the address
//                    if (!discoveredDevices.value.any { it.address == device.address }) {
//                        discoveredDevices.value.add(device)
//                    }
//                }
//            }
//        }
//    }
//
//    DisposableEffect(Unit) {
//        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
//        context.registerReceiver(discoveryReceiver, filter)
//        onDispose {
//            context.unregisterReceiver(discoveryReceiver)
//            if (isDiscovering) {
//                bluetoothAdapter.cancelDiscovery()
//            }
//        }
//    }
//
//    Box(modifier = Modifier.fillMaxSize()) {
//        LazyColumn(
//            modifier = Modifier
//                .fillMaxSize()
//                .padding(top = 50.dp, bottom = 50.dp), // Add padding to top and bottom
//            horizontalAlignment = Alignment.CenterHorizontally
//        ) {
//            item {
//                when {
//                    permissionState.status.isGranted &&
//                            coarseLocationPermissionState.status.isGranted &&
//                            fineLocationPermissionState.status.isGranted -> {
//
//                        Text("Bluetooth permission granted!")
//                        if (pairedDevices.value.isNotEmpty()) {
//                            Text("Paired Devices:")
//                            pairedDevices.value.forEach { device ->
//                                Text(device.name ?: "Unnamed device")
//                            }
//                        } else {
//                            Text("No paired devices found.")
//                        }
//
//                        if (isDiscovering) {
//                            Text("Discovering devices${".".repeat(dots)}") // Dynamic text with dots
//                        } else if (discoveredDevices.value.isNotEmpty()) {
//                            Text("Discovered Devices:")
//                            discoveredDevices.value.forEach { device ->
//                                Text(device.name ?: "Unnamed device")
//                            }
//                        } else {
//                            Text("No devices discovered.")
//                        }
//
//                        // Button to stop discovery
//                        if (isDiscovering) {
//                            Button(onClick = {
//                                bluetoothAdapter.cancelDiscovery()
//                                isDiscovering = false
//                                Log.i("Request!", "Discovery cancelled")
//                            }) {
//                                Text("Stop Discovery")
//                            }
//                        }
//                    }
//                    else -> {
//                        Text("Permissions denied.")
//                        Button(onClick = {
//                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
//                            val uri = Uri.fromParts("package", context.packageName, null)
//                            intent.data = uri
//                            context.startActivity(intent)
//                        }) {
//                            Text("Go to Settings")
//                        }
//                    }
//                }
//            }
//        }
//    }
//}








