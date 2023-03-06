package com.tutorial.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.tutorial.bluetooth.adapter.ScanDeviceAdapter
import com.tutorial.bluetooth.data.BLEDevice
import com.tutorial.bluetooth.databinding.ActivityMainBinding
import com.tutorial.bluetooth.utils.Contracts.PERMISSION_BLUETOOTH_CONNECT
import com.tutorial.bluetooth.utils.Contracts.PERMISSION_BLUETOOTH_SCAN
import com.tutorial.bluetooth.utils.Contracts.PERMISSION_COARSE_LOCATION
import com.tutorial.bluetooth.utils.Contracts.PERMISSION_CODE
import com.tutorial.bluetooth.utils.Contracts.PERMISSION_FINE_LOCATION
import com.tutorial.bluetooth.utils.Method
import com.tutorial.bluetooth.utils.Method.parcelable
import com.tutorial.bluetooth.utils.displayShortToast
import com.tutorial.bluetooth.utils.requestBluetoothPermission
import com.tutorial.bluetooth.utils.requestLocationPermission
import kotlinx.coroutines.*
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*
import kotlin.collections.ArrayList

class MainActivity : AppCompatActivity() {
    companion object {
        private const val DEVICE_SELECTED = "android.bluetooth.devicepicker.action.DEVICE_SELECTED"
        private const val BLE_LAUNCH = "android.bluetooth.devicepicker.action.LAUNCH"
    }

    private val binding: ActivityMainBinding by lazy { ActivityMainBinding.inflate(layoutInflater) }

    // 參數
    private var pairDeviceList: MutableList<BLEDevice> = ArrayList()
    private lateinit var pairDeviceAdapter: ScanDeviceAdapter

    // 藍芽
    private val bluetoothAdapter: BluetoothAdapter by lazy {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }
    private var pairedDevices: Set<BluetoothDevice>? = null
    private var pairedDevice: BluetoothDevice? = null

    private var isConnectOther = true

    private var socket: BluetoothSocket? = null
    private var output: OutputStream? = null
    private var input: InputStream? = null

    /**
     * 請求權限Callback
     */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            PERMISSION_CODE -> {
                for (result in grantResults)
                    // 如果使用者不同意權限。
                    if (result != PackageManager.PERMISSION_GRANTED) {
                        when {
                            // GPS定位
                            permissions.any { it == PERMISSION_FINE_LOCATION || it == PERMISSION_COARSE_LOCATION } ->
                                displayShortToast(getString(R.string.toast_ask_location_permission))
                            // Bluetooth
                            permissions.any { it == PERMISSION_BLUETOOTH_SCAN || it == PERMISSION_BLUETOOTH_CONNECT } ->
                                displayShortToast(getString(R.string.toast_ask_bluetooth_permission))
                        }
                        return
                    }
            }
        }
    }

    /**
     * 跨Activity傳值
     */
    private val launchBluetooth =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result?.let {
            if (result.resultCode != RESULT_OK)
                Toast.makeText(this@MainActivity, getString(R.string.toast_please_open_bluetooth), Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 開始建立Activity
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        initRv( /*初始化RecyclerView*/ )
        setListener( /*設定按鈕監聽器*/ )
    }

    /**
     * 結束Activity
     */
    override fun onDestroy() {
        unregisterReceiver(receiver)
        socket = null
        output = null
        input = null
        super.onDestroy()
    }

    /**
     * 當系統記憶體不足時，執行GC
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level <= ComponentCallbacks2.TRIM_MEMORY_MODERATE)
            System.gc()
    }

    /**
     * 執行函數
     */
    private fun initRv() {
        pairDeviceAdapter = ScanDeviceAdapter()
        binding.rvListPair.run {
            layoutManager = LinearLayoutManager(this@MainActivity, LinearLayoutManager.VERTICAL, false)
            adapter = pairDeviceAdapter
            pairDeviceAdapter
        }.apply {
            onItemClickCallback = { _, item ->
                Toast.makeText(this@MainActivity, item.name, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setListener() {
        binding.run {
            btnScan.setOnClickListener {
                if (!requestLocationPermission()) return@setOnClickListener
                initBluetooth( /*初始化藍芽*/ )
            }

            btnPair.setOnClickListener {
                Intent(BLE_LAUNCH).apply { startActivity(this) }
            }

            btnSend.setOnClickListener {
                if (socket == null || isConnectOther) {
                    initSocket()
                    return@setOnClickListener
                }
                try {
                    if (edContent.text?.isNotEmpty() == true) {
                        output?.write(edContent.text.toString().toByteArray(Charsets.UTF_8))
                        Toast.makeText(this@MainActivity, "Success", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun initBluetooth() {
        // 確定權限開啟
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED ||
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                    != PackageManager.PERMISSION_GRANTED)) {
            requestBluetoothPermission()
            return
        }
        // 取得藍芽開啟狀態
        if (!bluetoothAdapter.isEnabled)
            launchBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))

        bluetoothAdapter.let {
            // 先找已配對過的
            pairedDevices = it.bondedDevices
            pairedDevices?.forEach { device ->
                pairDeviceList.add(BLEDevice(device.name, device.address))
            }.also { pairDeviceAdapter.setterData(pairDeviceList) }
            it
        }.also {
            binding.btnPair.apply {
                isClickable = true
                isEnabled = true
            }
            val filter = IntentFilter(DEVICE_SELECTED)
            registerReceiver(receiver, filter)
        }
    }

    private fun initSocket() {
        Toast.makeText(this@MainActivity, "Connecting...", Toast.LENGTH_LONG).show()
        // 確定權限開啟
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED ||
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                    != PackageManager.PERMISSION_GRANTED)) {
            requestBluetoothPermission()
            return
        }
        try {
            // Socket 連線
            socket = pairedDevice?.createRfcommSocketToServiceRecord(pairedDevice?.uuids?.get(0)?.uuid) ?: return
            isConnectOther = false
            CoroutineScope(Dispatchers.Default).launch {
                while (socket?.isConnected == false) {
                    try {
                        socket?.connect()
                        if (socket?.isConnected == true) {
                            Method.logE("initSocket", "Connect Success")
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@MainActivity, "Connect Success!", Toast.LENGTH_SHORT).show()
                            }
                            output = socket?.outputStream
                            input = socket?.inputStream
                            startReadingFromSocket( /*開始監聽資料*/ )
                        }
                    } catch (e: IOException) {
                        e.printStackTrace()
                        Method.logE("initSocket", "Connect Error: ${e.message.toString()}")
                    } catch (e: InterruptedException) {
                        e.printStackTrace()
                        Method.logE("initSocket", "Connect Error: ${e.message.toString()}")
                    }
                    delay(5000)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Method.logE("initSocket", "Error: ${e.message.toString()}")
        }
    }

    private fun startReadingFromSocket() {
        CoroutineScope(Dispatchers.Default).launch {
            while (socket?.isConnected == true) {
                try {
                    val data = ByteArray(1024)
                    val length = input?.read(data)
                    if (length != null && length > 0) {
                        val receivedData = data.copyOf(length)
                        withContext(Dispatchers.Main) {
                            binding.tvReceive.text =
                                String.format(getString(R.string.ui_receive_data, receivedData.toString(Charsets.UTF_8)))
                        }
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                    Method.logE("startReadingFromSocket", "Error: ${e.message.toString()}")
                    break
                }
            }
            Method.logE("startReadingFromSocket", "Socket disconnected")
        }
    }

    /**
     * Service
     */
    private val receiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context?, intent: Intent?) {
            pairedDevice = intent?.parcelable(BluetoothDevice.EXTRA_DEVICE)
            binding.tvHC05.text = String.format(getString(R.string.ui_connect_state), pairedDevice?.name)
            try {
                isConnectOther = true
                pairedDevice?.createBond()
                Toast.makeText(this@MainActivity, "Connect ${pairedDevice?.name}", Toast.LENGTH_SHORT).show()
                binding.btnSend.apply {
                    isClickable = true
                    isEnabled = true
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Method.logE("onReceive", "Connect Error: ${e.message.toString()}")
                finish()
            }
        }
    }
}