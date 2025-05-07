package com.example.cellinfoapp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telephony.CellIdentityLte
import android.telephony.CellIdentityNr
import android.telephony.CellInfo
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.TelephonyManager
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity(), LocationListener {

    private val PERMISSION_REQUEST_CODE = 100
    private lateinit var textViewCellInfo: TextView
    private lateinit var buttonStartStop: Button
    private lateinit var spinnerInterval: Spinner
    
    private lateinit var locationManager: LocationManager
    private var currentLatitude = 0.0
    private var currentLongitude = 0.0
    
    private val handler = Handler(Looper.getMainLooper())
    private var runnable: Runnable? = null
    private var isScanning = false
    private var scanInterval = 5000L // デフォルト：5秒
    
    private val intervalOptions = listOf(
        Pair("5秒", 5000L),
        Pair("10秒", 10000L),
        Pair("30秒", 30000L),
        Pair("1分", 60000L),
        Pair("5分", 300000L)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // UIコンポーネントの初期化
        textViewCellInfo = findViewById(R.id.textViewCellInfo)
        buttonStartStop = findViewById(R.id.buttonStartStop)
        spinnerInterval = findViewById(R.id.spinnerInterval)
        
        // ロケーションマネージャの初期化
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        
        // スキャンボタンのクリックリスナー設定
        buttonStartStop.setOnClickListener {
            if (isScanning) {
                stopScanning()
            } else {
                if (checkPermissions()) {
                    startScanning()
                } else {
                    requestPermissions()
                }
            }
        }
        
        // スピナーの設定
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            intervalOptions.map { it.first }
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerInterval.adapter = adapter
        
        spinnerInterval.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                scanInterval = intervalOptions[position].second
                if (isScanning) {
                    // 実行中の場合は一度停止して新しい間隔で再開
                    stopScanning()
                    startScanning()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // 何もしない
            }
        }
    }

    // 権限チェック
    private fun checkPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
    }

    // 権限リクエスト
    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.READ_PHONE_STATE
            ),
            PERMISSION_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                startScanning()
            } else {
                Toast.makeText(this, "権限が必要です", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // スキャン開始
    private fun startScanning() {
        isScanning = true
        buttonStartStop.text = "スキャン停止"
        
        // 位置情報の取得を開始
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 5f, this)
        }
        
        // 定期的な基地局情報スキャンを開始
        runnable = object : Runnable {
            override fun run() {
                scanCellInfo()
                handler.postDelayed(this, scanInterval)
            }
        }
        runnable?.let { handler.post(it) }
    }

    // スキャン停止
    private fun stopScanning() {
        isScanning = false
        buttonStartStop.text = "スキャン開始"
        
        // 位置情報の取得を停止
        locationManager.removeUpdates(this)
        
        // 定期スキャンを停止
        runnable?.let { handler.removeCallbacks(it) }
        runnable = null
    }

    // 基地局情報のスキャン
    private fun scanCellInfo() {
        val telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val cellInfoList = telephonyManager.allCellInfo ?: return
        
        if (cellInfoList.isEmpty()) {
            textViewCellInfo.text = "セル情報が取得できません"
            return
        }

        // 現在時刻
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val infoBuilder = StringBuilder("スキャン時刻: $timestamp\n\n")
        
        // CSVに書き込むデータリスト
        val csvData = mutableListOf<CellData>()

        for (cellInfo in cellInfoList) {
            when (cellInfo) {
                is CellInfoLte -> {
                    // LTEセル情報の取得
                    val identityLte = cellInfo.cellIdentity as CellIdentityLte
                    
                    val mcc = identityLte.mcc.toString()
                    val mnc = identityLte.mnc.toString()
                    val pci = identityLte.pci
                    val earfcn = identityLte.earfcn
                    val dbm = cellInfo.cellSignalStrength.dbm
                    
                    infoBuilder.append("--- LTE セル情報 ---\n")
                    infoBuilder.append("PLMN: $mcc-$mnc\n")
                    infoBuilder.append("PCI: $pci\n")
                    infoBuilder.append("EARFCN: $earfcn\n")
                    infoBuilder.append("信号強度: $dbm dBm\n\n")
                    
                    // CSVデータに追加
                    csvData.add(
                        CellData(
                            technology = "LTE",
                            plmn = "$mcc-$mnc",
                            pci = pci.toString(),
                            arfcn = earfcn.toString(),
                            signal = dbm.toString(),
                            latitude = currentLatitude.toString(),
                            longitude = currentLongitude.toString(),
                            timestamp = timestamp
                        )
                    )
                }
                is CellInfoNr -> {
                    // 5G NRセル情報の取得 (Android 10以降)
                    val identityNr = cellInfo.cellIdentity as CellIdentityNr
                    
                    val mcc = identityNr.mccString ?: "不明"
                    val mnc = identityNr.mncString ?: "不明"
                    val pci = identityNr.pci
                    val nrarfcn = identityNr.nrarfcn
                    val dbm = cellInfo.cellSignalStrength.dbm
                    
                    infoBuilder.append("--- 5G NR セル情報 ---\n")
                    infoBuilder.append("PLMN: $mcc-$mnc\n")
                    infoBuilder.append("PCI: $pci\n")
                    infoBuilder.append("NRARFCN: $nrarfcn\n")
                    infoBuilder.append("信号強度: $dbm dBm\n\n")
                    
                    // CSVデータに追加
                    csvData.add(
                        CellData(
                            technology = "5G",
                            plmn = "$mcc-$mnc",
                            pci = pci.toString(),
                            arfcn = nrarfcn.toString(),
                            signal = dbm.toString(),
                            latitude = currentLatitude.toString(),
                            longitude = currentLongitude.toString(),
                            timestamp = timestamp
                        )
                    )
                }
            }
        }

        // 画面に表示
        textViewCellInfo.text = infoBuilder.toString()
        
        // CSVファイルに保存
        saveToCsv(csvData)
    }
    
    // CSVファイルに保存
    private fun saveToCsv(cellDataList: List<CellData>) {
        if (cellDataList.isEmpty()) return
        
        try {
            // ディレクトリの作成（存在しない場合）
            val directory = File(getExternalFilesDir(null), "cell_info")
            if (!directory.exists()) {
                directory.mkdirs()
            }
            
            // ファイル名（日付ベース）
            val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
            val fileName = "cell_info_${dateFormat.format(Date())}.csv"
            val file = File(directory, fileName)
            
            // ヘッダー行の有無をチェック
            val needHeader = !file.exists()
            
            // ファイルに追記
            FileWriter(file, true).use { writer ->
                if (needHeader) {
                    writer.append("TIMESTAMP,TECHNOLOGY,PLMN,PCI,ARFCN,SIGNAL_DBM,LATITUDE,LONGITUDE\n")
                }
                
                for (data in cellDataList) {
                    writer.append("${data.timestamp},${data.technology},${data.plmn},${data.pci},${data.arfcn},${data.signal},${data.latitude},${data.longitude}\n")
                }
            }
            
            // トースト通知（オプション）
            // Toast.makeText(this, "データを保存しました", Toast.LENGTH_SHORT).show()
            
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "データの保存に失敗しました: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    // 位置情報リスナーのメソッド
    override fun onLocationChanged(location: Location) {
        currentLatitude = location.latitude
        currentLongitude = location.longitude
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopScanning()
    }
    
    // データクラス
    data class CellData(
        val technology: String,
        val plmn: String,
        val pci: String,
        val arfcn: String,
        val signal: String,
        val latitude: String,
        val longitude: String,
        val timestamp: String
    )
}