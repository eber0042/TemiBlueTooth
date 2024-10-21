package com.temi.temiSDK

import android.Manifest
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.temi.temiSDK.ui.theme.GreetMiTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GreetMiTheme {
                Greeting(
                )
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun Greeting() {
    val context = LocalContext.current
    val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    val bluetoothAdapter = bluetoothManager.adapter
    val enableBtLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Log.i("Result!", "Bluetooth enabled")
        } else {
            Log.i("Result!", "Bluetooth enabling failed")
        }
    }

    val permissionState = rememberPermissionState(permission = Manifest.permission.BLUETOOTH_CONNECT)
    val coarseLocationPermissionState = rememberPermissionState(permission = Manifest.permission.ACCESS_COARSE_LOCATION)
    val fineLocationPermissionState = rememberPermissionState(permission = Manifest.permission.ACCESS_FINE_LOCATION)
    val pairedDevices = remember { mutableStateOf(setOf<BluetoothDevice>()) }
    val discoveredDevices = remember { mutableStateOf(mutableListOf<BluetoothDevice>()) }

    var dots by remember { mutableStateOf(0) }
    var isDiscovering by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Handle dots animation for "Discovering devices..."
    LaunchedEffect(isDiscovering) {
        if (isDiscovering) {
            while (true) {
                delay(500)
                dots = (dots + 1) % 4 // Cycle through 0 to 3
            }
        } else {
            dots = 0 // Reset dots when not discovering
        }
    }

    // Automatically stop discovery after a timeout
    LaunchedEffect(isDiscovering) {
        if (isDiscovering) {
            delay(12000) // Set timeout (e.g., 12 seconds)
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_SCAN
                ) != PackageManager.PERMISSION_GRANTED
            ) {
            }
            bluetoothAdapter.cancelDiscovery()
            isDiscovering = false
            Log.i("Request!", "Discovery timed out")
        }
    }

    LaunchedEffect(Unit) {
        when {
            !permissionState.status.isGranted -> {
                if (permissionState.status.shouldShowRationale) {
                    Log.i("Request!", "Showing rationale for Bluetooth permission")
                } else {
                    Log.i("Request!", "Bluetooth permission denied with 'Don't ask again'")
                }
            }
            !coarseLocationPermissionState.status.isGranted -> {
                if (coarseLocationPermissionState.status.shouldShowRationale) {
                    Log.i("Request!", "Showing rationale for Coarse Location permission")
                } else {
                    Log.i("Request!", "Coarse Location permission denied with 'Don't ask again'")
                }
            }
            !fineLocationPermissionState.status.isGranted -> {
                if (fineLocationPermissionState.status.shouldShowRationale) {
                    Log.i("Request!", "Showing rationale for Fine Location permission")
                } else {
                    Log.i("Request!", "Fine Location permission denied with 'Don't ask again'")
                }
            }
            bluetoothAdapter == null -> {
                Log.e("Request!", "Bluetooth is not supported on this device.")
            }
            !bluetoothAdapter.isEnabled -> {
                Log.i("Request!", "Sent activate Bluetooth request")
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                enableBtLauncher.launch(enableBtIntent)
            }
            else -> {
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

                    pairedDevices.value = bluetoothAdapter.bondedDevices

                    if (!isDiscovering) {
                        Log.i("Request!", "Starting Discovery")
                        val successDiscovery = bluetoothAdapter.startDiscovery()
                        isDiscovering = successDiscovery
                        Log.i("Request!", "Start Discovery Successful: $successDiscovery")

                        if (!successDiscovery) {
                            Log.e("Request!", "Failed to start discovery")
                        }
                    } else {
                        Log.i("Request!", "Discovery is already in progress")
                    }
                }
            }
        }
    }

    val discoveryReceiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val action: String = intent?.action ?: return
                if (BluetoothDevice.ACTION_FOUND == action) {
                    val device: BluetoothDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE) ?: return
                    // Prevent duplicates by checking the address
                    if (!discoveredDevices.value.any { it.address == device.address }) {
                        discoveredDevices.value.add(device)
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        context.registerReceiver(discoveryReceiver, filter)
        onDispose {
            context.unregisterReceiver(discoveryReceiver)
            if (isDiscovering) {
                bluetoothAdapter.cancelDiscovery()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 50.dp, bottom = 50.dp), // Add padding to top and bottom
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                when {
                    permissionState.status.isGranted &&
                            coarseLocationPermissionState.status.isGranted &&
                            fineLocationPermissionState.status.isGranted -> {

                        Text("Bluetooth permission granted!")
                        if (pairedDevices.value.isNotEmpty()) {
                            Text("Paired Devices:")
                            pairedDevices.value.forEach { device ->
                                Text(device.name ?: "Unnamed device")
                            }
                        } else {
                            Text("No paired devices found.")
                        }

                        if (isDiscovering) {
                            Text("Discovering devices${".".repeat(dots)}") // Dynamic text with dots
                        } else if (discoveredDevices.value.isNotEmpty()) {
                            Text("Discovered Devices:")
                            discoveredDevices.value.forEach { device ->
                                Text((device.name + " || "+  device.address))
                            }
                        } else {
                            Text("No devices discovered.")
                        }

                        // Button to stop discovery
                        if (isDiscovering) {
                            Button(onClick = {
                                bluetoothAdapter.cancelDiscovery()
                                isDiscovering = false
                                Log.i("Request!", "Discovery cancelled")
                            }) {
                                Text("Stop Discovery")
                            }
                        }
                    }
                    else -> {
                        Text("Permissions denied.")
                        Button(onClick = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            val uri = Uri.fromParts("package", context.packageName, null)
                            intent.data = uri
                            context.startActivity(intent)
                        }) {
                            Text("Go to Settings")
                        }
                    }
                }
            }
        }
    }
}









