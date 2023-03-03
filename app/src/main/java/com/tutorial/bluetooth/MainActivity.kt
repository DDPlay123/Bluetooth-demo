package com.tutorial.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView.LayoutManager
import com.tutorial.bluetooth.adapter.ScanDeviceAdapter
import com.tutorial.bluetooth.databinding.ActivityMainBinding
import com.tutorial.bluetooth.utils.Contracts.PERMISSION_BLUETOOTH_CONNECT
import com.tutorial.bluetooth.utils.Contracts.PERMISSION_COARSE_LOCATION
import com.tutorial.bluetooth.utils.Contracts.PERMISSION_CODE
import com.tutorial.bluetooth.utils.Contracts.PERMISSION_FINE_LOCATION
import com.tutorial.bluetooth.utils.Method
import com.tutorial.bluetooth.utils.displayShortToast
import com.tutorial.bluetooth.utils.requestLocationPermission

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    // 參數
    private lateinit var scanDeviceAdapter: ScanDeviceAdapter

    // 藍芽
    private var bluetooth: BluetoothAdapter? = null
    private var pairedDevices: Set<BluetoothDevice>? = null

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
        binding = ActivityMainBinding.inflate(LayoutInflater.from(this))
        setContentView(binding.root)

        initRv( /*初始化RecyclerView*/ )
        initBluetooth( /*初始化藍芽*/ )
        setListener( /*設定按鈕監聽器*/ )
    }

    /**
     * 執行函數
     */
    private fun initRv() {
        scanDeviceAdapter = ScanDeviceAdapter()
        binding.rvList.run {
            layoutManager = LinearLayoutManager(this@MainActivity, LinearLayoutManager.VERTICAL, false)
            adapter = scanDeviceAdapter
            scanDeviceAdapter
        }.apply {
            onItemClickCallback = { position, item ->

            }
        }
    }

    private fun initBluetooth() {
        bluetooth = BluetoothAdapter.getDefaultAdapter()
        if (bluetooth == null) {
            Toast.makeText(this@MainActivity, getString(R.string.toast_not_found_bluetooth_drive), Toast.LENGTH_SHORT).show()
            return
        }

        if (bluetooth?.isEnabled == false)
            launchBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))

        bluetooth?.let { it ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                Method.requestPermission(this@MainActivity, PERMISSION_BLUETOOTH_CONNECT)
        }
    }

    private fun setListener() {
        binding.run {
            btnScan.setOnClickListener {
                if (!requestLocationPermission()) return@setOnClickListener
            }

            btnChat.setOnClickListener {
                if (!requestLocationPermission()) return@setOnClickListener
            }

            btnBLE.setOnClickListener {
                if (!requestLocationPermission()) return@setOnClickListener
            }
        }
    }
}