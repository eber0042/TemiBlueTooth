package com.temi.temiSDK

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.temi.temiSDK.ui.theme.GreetMiTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import java.util.UUID

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

class BleManager(private val bluetoothGatt: BluetoothGatt, context: Context) {

    private val context = context

    companion object {
        private val BATTERY_SERVICE_UUID = UUID.fromString("0000180F-0000-1000-8000-00805f9b34fb")
        private val BATTERY_LEVEL_UUID = UUID.fromString("00002A19-0000-1000-8000-00805f9b34fb")
    }

    // Read the battery level
    fun readBatteryLevel() {
        val batteryService: BluetoothGattService? = bluetoothGatt.getService(BATTERY_SERVICE_UUID)
        val batteryLevelCharacteristic: BluetoothGattCharacteristic? = batteryService?.getCharacteristic(BATTERY_LEVEL_UUID)

        if (batteryLevelCharacteristic != null) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return
            }
            bluetoothGatt.readCharacteristic(batteryLevelCharacteristic)
        } else {
            // Handle the case where the battery level characteristic is not found
            handleCharacteristicNotFound()
        }
    }

    // Callback to handle read results
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (characteristic.uuid == BATTERY_LEVEL_UUID) {
                    val batteryLevel = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0)
                    handleBatteryLevelRead(batteryLevel)
                }
            } else {
                // Handle read error
                handleReadError(status)
            }
        }
    }

    private fun handleBatteryLevelRead(level: Int) {
        // Process the battery level here
        Log.i("Bluetooth!", "Battery Level: $level%")
    }

    private fun handleCharacteristicNotFound() {
        // Logic for handling the case when the characteristic is not found
        println("Battery Level Characteristic not found")
    }

    private fun handleReadError(status: Int) {
        // Logic for handling read error
        println("Failed to read characteristic. Status: $status")
    }
}

@SuppressLint("MissingPermission")
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun BluetoothScreen() {
    val context = LocalContext.current
    val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    val bluetoothAdapter = bluetoothManager.adapter
    val permissionState = rememberPermissionState(Manifest.permission.BLUETOOTH)
    val fineLocationPermissionState =
        rememberPermissionState(permission = Manifest.permission.ACCESS_FINE_LOCATION)
    val discoveredDevices = remember { mutableStateOf(mutableListOf<BluetoothDevice>()) }
    var isScanning by remember { mutableStateOf(false) }
    var dots by remember { mutableIntStateOf(0) }
    var bluetoothGatt: BluetoothGatt? by remember { mutableStateOf(null) }
    val MY_UUID: UUID = UUID.fromString("00001812-0000-1000-8000-00805f9b34fb")

    // Dots animation for scanning indication
    LaunchedEffect(isScanning) {
        if (isScanning) {
            while (true) {
                delay(500)
                dots = (dots + 1) % 4
            }
        } else {
            dots = 0
        }
    }

    // Handle discovery timeout (e.g., 12 seconds)
    LaunchedEffect(isScanning) {
        if (isScanning) {
            discoveredDevices.value.clear() // Clear previous devices
            delay(12000) // Set timeout for scanning
            if (ActivityCompat.checkSelfPermission(
                    context, Manifest.permission.BLUETOOTH_SCAN
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                bluetoothAdapter.cancelDiscovery()
                isScanning = false
                Log.i("Bluetooth!", "Discovery timed out")
            }
        }
    }

    // BLE scan callback
    val leScanCallback = rememberUpdatedState(newValue = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: android.bluetooth.le.ScanResult) {
            super.onScanResult(callbackType, result)
            val device = result.device
            if (!discoveredDevices.value.any { it.address == device.address }) {
                discoveredDevices.value.add(device)
            }
        }

        override fun onBatchScanResults(results: List<android.bluetooth.le.ScanResult>) {
            super.onBatchScanResults(results)
            results.forEach { result ->
                val device = result.device
                if (!discoveredDevices.value.any { it.address == device.address }) {
                    discoveredDevices.value.add(device)
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.e("Bluetooth!", "Scan failed with error code: $errorCode")
        }
    })

    // Start or stop scanning
    fun toggleScan() {
        if (isScanning) {
            val scanner = bluetoothManager.adapter.bluetoothLeScanner
            scanner.stopScan(leScanCallback.value)
            isScanning = false
        } else {
            val scanner = bluetoothManager.adapter.bluetoothLeScanner
            scanner.startScan(leScanCallback.value)
            isScanning = true
        }
    }

    // Connect to BLE device
    fun connectToDevice(device: BluetoothDevice) {
        bluetoothGatt = device.connectGatt(context, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                super.onConnectionStateChange(gatt, status, newState)
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.i("Bluetooth!", "Connected to GATT server.")
                    // Discover services
                    bluetoothGatt?.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.i("Bluetooth!", "Disconnected from GATT server.")
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                super.onServicesDiscovered(gatt, status)
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.i("Bluetooth!", "Services discovered.")
                    // You can start reading/writing characteristics here
                } else {
                    Log.w("Bluetooth!", "onServicesDiscovered received: $status")
                }
            }
        })
    }

    // Connect to BLE mouse
    fun connectToMouse(device: BluetoothDevice) {
        bluetoothGatt = device.connectGatt(context, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                super.onConnectionStateChange(gatt, status, newState)
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.i("Bluetooth!", "Connected to BLE mouse GATT server.")
                    // Discover services
                    bluetoothGatt?.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.i("Bluetooth!", "Disconnected from BLE mouse GATT server.")
                    bluetoothGatt?.close() // Ensure to close the GATT connection
                    bluetoothGatt = null // Reset the GATT reference
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                super.onServicesDiscovered(gatt, status)
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.i("Bluetooth!", "Mouse services discovered.")

                    // Log all discovered services and their characteristics
                    gatt?.services?.forEach { service ->
                        Log.i("Bluetooth!", "Service UUID: ${service.uuid}")

                        // List characteristics of the service
                        service.characteristics.forEach { characteristic ->
                            Log.i("Bluetooth!", "Characteristic UUID: ${characteristic.uuid}")
                            Log.i("Bluetooth!", "Properties: ${characteristic.properties}")

                            // Optionally read the characteristic here
                            // If it's readable, you can read it
                            if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_READ > 0) {
                                gatt.readCharacteristic(characteristic)
                            }

                            // If it has notifications or indications, you might want to enable them
                            if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY > 0) {
                                gatt.setCharacteristicNotification(characteristic, true)
                                // You might need to configure the descriptor as well for notifications
                                val descriptor = characteristic.getDescriptor(MY_UUID)
                                descriptor?.let {
                                    it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                    gatt.writeDescriptor(it)
                                }
                            }
                        }
                    }
                } else {
                    Log.w("Bluetooth!", "onServicesDiscovered received: $status")
                }
            }

            override fun onCharacteristicRead(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
                super.onCharacteristicRead(gatt, characteristic, status)
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    // Handle the characteristic value
                    Log.i("Bluetooth!", "Characteristic read: ${characteristic?.value?.joinToString()}")
                }
            }
        })
    }

    // UI layout
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (!fineLocationPermissionState.status.isGranted || !permissionState.status.isGranted) {
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
                Button(onClick = {
                    toggleScan()
                }) {
                    Text(if (isScanning) "Stop Scanning${".".repeat(dots)}" else "Start Scanning")
                }

                if (discoveredDevices.value.isNotEmpty()) {
                    Text("Discovered Devices:")
                    LazyColumn {
                        items(discoveredDevices.value) { device ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        connectToMouse(device)
                                        bluetoothGatt?.let { BleManager(it, context) }
                                            ?.readBatteryLevel()
                                    }
                                    .padding(vertical = 8.dp)
                            ) {
                                Text(
                                    text = "${device.name} || ${device.address}",
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 16.dp)
                                )
                                Text(
                                    text = "Connect",
                                    color = Color.Blue,
                                    modifier = Modifier
                                        .padding(start = 16.dp)
                                        .align(Alignment.CenterVertically)
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

// This code allows pairing but not connecting, could be due to connection type
//@SuppressLint("MutableCollectionMutableState")
//@OptIn(ExperimentalPermissionsApi::class)
//@Composable
//fun BluetoothScreen() {
//    val context = LocalContext.current
//    val bluetoothAdapter: BluetoothAdapter =
//        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
//    val permissionState = rememberPermissionState(Manifest.permission.BLUETOOTH)
//    val coarseLocationPermissionState =
//        rememberPermissionState(permission = Manifest.permission.ACCESS_COARSE_LOCATION)
//    val fineLocationPermissionState =
//        rememberPermissionState(permission = Manifest.permission.ACCESS_FINE_LOCATION)
//
//    // Stuff for Bluetooth Classic
//    val pairedDevices = remember { mutableStateListOf<BluetoothDevice>() }
//    val MY_UUID: UUID = UUID.fromString("00001812-0000-1000-8000-00805f9b34fb")
//
//    // State for tracking discovered devices and scanning state
//    val discoveredDevices = remember { mutableStateOf(mutableListOf<BluetoothDevice>()) }
//    var isScanning by remember { mutableStateOf(false) }
//    var dots by remember { mutableIntStateOf(0) }
//
//    // Stuff for Bluetooth LE
//    var bluetoothGatt: BluetoothGatt? by remember { mutableStateOf(null) }
//
//    // Manage dots animation for scanning indication
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
//    // Handle discovery timeout (e.g., 12 seconds)
//    LaunchedEffect(isScanning) {
//        if (isScanning) {
//            discoveredDevices.value.clear() // Clear previous devices
//            delay(12000) // Set timeout for scanning
//            if (ActivityCompat.checkSelfPermission(
//                    context, Manifest.permission.BLUETOOTH_SCAN
//                ) != PackageManager.PERMISSION_GRANTED
//            ) {
//                bluetoothAdapter.cancelDiscovery()
//                isScanning = false
//                Log.i("Bluetooth", "Discovery timed out")
//            }
//        }
//    }
//
//    // BroadcastReceiver to handle found Bluetooth devices
//    val discoveryReceiver = remember {
//        object : BroadcastReceiver() {
//            override fun onReceive(context: Context?, intent: Intent?) {
//                val action: String = intent?.action ?: return
//                if (BluetoothDevice.ACTION_FOUND == action) {
//                    val device: BluetoothDevice? =
//                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
//                    device?.let {
//                        if (!discoveredDevices.value.any { it.address == device.address }) {
//                            discoveredDevices.value.add(device)
//                        }
//                    }
//                }
//            }
//        }
//    }
//
//    // Register and unregister the BroadcastReceiver
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
//    // Pair with selected device
//    fun pairWithDevice(device: BluetoothDevice, pairedDevices: MutableList<BluetoothDevice>) {
//        // Initiate pairing
//        val isBonded = device.createBond()
//        Log.i("DATA!", "Pairing with device: ${device.name}, Bonded: $isBonded")
//
//        if (isBonded) {
//            // Check if the device is already in pairedDevices before adding
//            if (!pairedDevices.any { it.address == device.address }) {
//                pairedDevices.add(device)
//                Log.i("DATA!", "Successfully paired with device: ${device.name}")
//            } else {
//                Log.i("DATA!", "Device already paired: ${device.name}")
//            }
//        } else {
//            Log.e("DATA!", "Failed to pair with device: ${device.name}")
//        }
//    }
//
//    /*
//        // Function to connect to the device after pairing or if already bonded
//    fun connectToDevice(device: BluetoothDevice): BluetoothSocket? {
//        // Here you would initiate a connection, depending on the type of Bluetooth profile you're using (e.g., BluetoothSocket, GATT, etc.)
//        try {
//            val socket = device.createInsecureRfcommSocketToServiceRecord(MY_UUID)
//            Log.i("DATA!", "Test: ${socket.connectionType}")
//            socket.connect() // Connect to the device
//            Log.i("DATA!", "Is connected: ${socket.isConnected}")
//            Log.i("DATA!", "Connected to device: ${device.name}")
//            return socket // Return the connected socket
//        } catch (e: IOException) {
//            Log.e("DATA!", "IOException while connecting to device: ${device.name}, Error: ${e.message}")
//            e.printStackTrace() // This will print the stack trace for more context
//            return null
//        }
//    }
//     */
//
//    // Function to send data
//    fun sendData(socket: BluetoothSocket?, data: String) {
//        if (socket != null && socket.isConnected) {
//            try {
//                val outputStream = socket.outputStream
//                outputStream.write(data.toByteArray())
//                outputStream.flush()
//                Log.i("Request!", "Data sent: $data")
//            } catch (e: IOException) {
//                Log.e("Error!", "Failed to send data: ${e.message}")
//            }
//        } else {
//            Log.e("Error!", "Socket is not connected")
//        }
//    }
//
//    // Handle pairing and connection
//    fun handleDevicePairingAndConnection(
//        device: BluetoothDevice,
//        pairedDevices: MutableList<BluetoothDevice>
//    ) {
//        when (device.bondState) {
//            BluetoothDevice.BOND_BONDED -> {
//                Log.i("DATA!", "Device already paired: ${device.name}, Attempting to connect...")
//                for ( i in 0 until device.uuids.size) {
//                    Log.i("DATA!", "uuids data: " + device.uuids[i].toString())
//                }
//                connectToDevice(device) // Device is already paired, initiate connection
//                // sendData(socket, "Hello from Temi!") // Send data after connecting
//            }
//
//            BluetoothDevice.BOND_NONE -> {
//                Log.i("DATA!", "Device not paired: ${device.name}, Attempting to pair...")
//                pairWithDevice(device, pairedDevices) // Device is not paired, attempt pairing first
//            }
//
//            BluetoothDevice.BOND_BONDING -> {
//                Log.i("DATA!", "Pairing in progress with device: ${device.name}")
//            }
//        }
//    }
//
//    Box(
//        modifier = Modifier.fillMaxSize(),
//        contentAlignment = Alignment.Center
//    ) {
//        Column(modifier = Modifier.padding(16.dp)) {
//            // Need these permissions to be able to do a scan
//            if (!coarseLocationPermissionState.status.isGranted || !fineLocationPermissionState.status.isGranted || !permissionState.status.isGranted) {
//                Text("Permissions denied.")
//                Button(onClick = {
//                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
//                    val uri = Uri.fromParts("package", context.packageName, null)
//                    intent.data = uri
//                    context.startActivity(intent)
//                }) {
//                    Text("Go to Settings")
//                }
//            } else {
//                // Start/Stop Bluetooth scanning
//                Button(onClick = {
//                    if (isScanning) {
//                        bluetoothAdapter.cancelDiscovery()
//                        isScanning = false
//                    } else {
//                        bluetoothAdapter.startDiscovery()
//                        isScanning = true
//                    }
//                }) {
//                    Text(if (isScanning) "Stop Scanning" else "Start Scanning")
//                }
//
//                // Current paired devices
//                if (pairedDevices.isNotEmpty()) {
//                    Text("Paired Devices:")
//                    pairedDevices.forEach { device ->
//                        Text(device.name ?: "Unnamed device")
//                    }
//                } else {
//                    Text("No paired devices found.")
//                }
//
//                // Display scanning progress
//                if (isScanning) {
//                    Text("Scanning${".".repeat(dots)}")
//                } else if (discoveredDevices.value.isNotEmpty()) {
//                    Text("Discovered Devices:")
//
//                    LazyColumn {
//                        items(discoveredDevices.value) { device ->
//                            // Each device is displayed as a clickable row
//                            Row(
//                                modifier = Modifier
//                                    .fillMaxWidth() // Ensure the row takes the full width
//                                    .clickable {
//                                        handleDevicePairingAndConnection(
//                                            device,
//                                            pairedDevices
//                                        ) // Initiate pairing
//                                    }
//                                    .padding(vertical = 8.dp) // Add vertical padding for better spacing
//                            ) {
//                                // Display device name and address
//                                Text(
//                                    text = "${device.name} || ${device.address}",
//                                    modifier = Modifier
//                                        .weight(1f) // Allow text to take up remaining space
//                                        .padding(end = 16.dp) // Reduced end padding for better spacing
//                                )
//                                // Display pairing action
//                                Text(
//                                    text = "Pair",
//                                    color = Color.Blue,
//                                    modifier = Modifier
//                                        .padding(start = 16.dp) // Adjust padding as needed
//                                        .align(Alignment.CenterVertically) // Center align text vertically
//                                )
//                            }
//                        }
//                    }
//                } else {
//                    Text("No devices discovered.")
//                }
//            }
//        }
//    }
//}

//0000110a-0000-1000-8000-00805f9b34fb: This UUID corresponds to the Audio Sink service.
//00001105-0000-1000-8000-00805f9b34fb: This UUID corresponds to the Object Push Profile (OPP) service.
//00001115-0000-1000-8000-00805f9b34fb: This UUID corresponds to the Advanced Audio Distribution Profile (A2DP).
//00001116-0000-1000-8000-00805f9b34fb: This UUID corresponds to the Audio/Video Remote Control Profile (AVRCP).
//0000112d-0000-1000-8000-00805f9b34fb: This UUID is used for Human Interface Device (HID) over GATT.
//0000110e-0000-1000-8000-00805f9b34fb: This UUID corresponds to the Serial Port Profile (SPP).
//0000112f-0000-1000-8000-00805f9b34fb: This UUID is associated with the Headset Profile (HSP).
//00001112-0000-1000-8000-00805f9b34fb: This UUID is for Hands-Free Profile (HFP).
//0000111f-0000-1000-8000-00805f9b34fb: This UUID corresponds to the LE GATT profile.
//00001132-0000-1000-8000-00805f9b34fb: This UUID could be specific to a particular device or vendor service.
//00000000-0000-1000-8000-00805f9b34fb: This UUID is often used as a placeholder or indicates no specific service.
//a82efa21-ae5c-3dde-9bbc-f16da7b16c5a: This appears to be a custom UUID, which might correspond to a specific service created for the device.

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








