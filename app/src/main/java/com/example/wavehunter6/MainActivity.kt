package com.example.wavehunter6

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.telephony.CellIdentityLte
import android.telephony.CellIdentityNr
import android.telephony.CellInfo
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.TelephonyManager
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity(), LocationListener {

    private lateinit var telephonyManager: TelephonyManager
    private lateinit var locationManager: LocationManager
    private lateinit var handler: Handler
    private lateinit var runnable: Runnable

    private lateinit var tvNetworkInfo: TextView
    private lateinit var etInterval: EditText
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button

    private var isTracking = false
    private var interval = 5000L // Default 5 seconds
    private var latitude = 0.0
    private var longitude = 0.0

    private val requiredPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.READ_PHONE_STATE
    )

    private val storagePermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf() // Android 13+では不要
    } else {
        arrayOf(
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            setupTracking()
        } else {
            Toast.makeText(this, "必要な権限が許可されていません", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvNetworkInfo = findViewById(R.id.tv_network_info)
        etInterval = findViewById(R.id.et_interval)
        btnStart = findViewById(R.id.btn_start)
        btnStop = findViewById(R.id.btn_stop)

        etInterval.setText((interval / 1000).toString())

        btnStart.setOnClickListener {
            val inputInterval = etInterval.text.toString().toLongOrNull()
            if (inputInterval != null && inputInterval > 0) {
                interval = inputInterval * 1000
                startTracking()
            } else {
                Toast.makeText(this, "有効な間隔を入力してください", Toast.LENGTH_SHORT).show()
            }
        }

        btnStop.setOnClickListener {
            stopTracking()
        }

        checkPermissions()
    }

    private fun checkPermissions() {
        val permissionsToRequest = ArrayList<String>()

        for (permission in requiredPermissions + storagePermissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            setupTracking()
        }
    }

    private fun setupTracking() {
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        handler = Handler(Looper.getMainLooper())
        runnable = Runnable {
            collectAndSaveNetworkInfo()
            if (isTracking) {
                handler.postDelayed(runnable, interval)
            }
        }

        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                // 複数のプロバイダーから位置情報を取得
                try {
                    // GPSプロバイダー
                    locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        1000,
                        0f,
                        this
                    )
                    Log.d("MainActivity", "GPS位置情報の更新をリクエスト")
                } catch (e: Exception) {
                    Log.e("MainActivity", "GPS位置情報の更新リクエストに失敗: ${e.message}")
                }

                try {
                    // ネットワークプロバイダー
                    locationManager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        1000,
                        0f,
                        this
                    )
                    Log.d("MainActivity", "ネットワーク位置情報の更新をリクエスト")
                } catch (e: Exception) {
                    Log.e("MainActivity", "ネットワーク位置情報の更新リクエストに失敗: ${e.message}")
                }

                // 最新の既知の位置を取得
                try {
                    val lastLocationGps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    val lastLocationNetwork = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

                    // 最新の位置を使用
                    if (lastLocationGps != null) {
                        latitude = lastLocationGps.latitude
                        longitude = lastLocationGps.longitude
                        Log.d("MainActivity", "GPS最新位置: $latitude, $longitude")
                    } else if (lastLocationNetwork != null) {
                        latitude = lastLocationNetwork.latitude
                        longitude = lastLocationNetwork.longitude
                        Log.d("MainActivity", "ネットワーク最新位置: $latitude, $longitude")
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "最新位置の取得に失敗: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error setting up location updates: ${e.message}")
        }
    }

    private fun startTracking() {
        if (!isTracking) {
            isTracking = true
            handler.post(runnable)
            btnStart.isEnabled = false
            btnStop.isEnabled = true
            Toast.makeText(this, "トラッキングを開始しました", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopTracking() {
        if (isTracking) {
            isTracking = false
            handler.removeCallbacks(runnable)
            btnStart.isEnabled = true
            btnStop.isEnabled = false
            Toast.makeText(this, "トラッキングを停止しました", Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("MissingPermission")
    private fun collectAndSaveNetworkInfo() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        try {
            // 最新の位置情報を試して取得
            try {
                val lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                if (lastKnownLocation != null) {
                    latitude = lastKnownLocation.latitude
                    longitude = lastKnownLocation.longitude
                    Log.d("MainActivity", "最新の位置: $latitude, $longitude")
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "位置情報の取得に失敗: ${e.message}")
            }

            val cellInfoList = telephonyManager.allCellInfo
            Log.d("CellInfoDebug", "cellInfoList取得: ${cellInfoList?.size ?: "null"}")

            if (cellInfoList != null && cellInfoList.isNotEmpty()) {
                val networkInfo = StringBuilder()

                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

                // セル情報のカウント用
                var cellCount = 0

                // すべてのセルを処理
                for (cellInfo in cellInfoList) {
                    Log.d("CellInfoDebug", "セル処理: $cellInfo")
                    // 各セルの情報を保存するためのStringBuilder
                    val cellData = StringBuilder()
                    val csvLine = StringBuilder()

                    var processed = false

                    when (cellInfo) {
                        is CellInfoLte -> {
                            processCellInfoLte(cellInfo, cellData, csvLine, timestamp)
                            processed = true
                        }

                        is CellInfoNr -> {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                processCellInfoNr(cellInfo, cellData, csvLine, timestamp)
                                processed = true
                            }
                        }

                        // 追加のセルタイプ
                        is android.telephony.CellInfoWcdma -> {
                            processCellInfoWcdma(cellInfo, cellData, csvLine, timestamp)
                            processed = true
                        }

                        is android.telephony.CellInfoGsm -> {
                            processCellInfoGsm(cellInfo, cellData, csvLine, timestamp)
                            processed = true
                        }

                        is android.telephony.CellInfoCdma -> {
                            processCellInfoCdma(cellInfo, cellData, csvLine, timestamp)
                            processed = true
                        }
                    }

                    if (processed) {
                        networkInfo.append("--- セル #${++cellCount} ---\n")
                        networkInfo.append(cellData)
                        networkInfo.append("\n")

                        saveToCSV(csvLine.toString())
                    }
                }

                if (cellCount > 0) {
                    tvNetworkInfo.text = networkInfo.toString()
                    Log.d("CellInfoDebug", "表示セル数: $cellCount")
                } else {
                    Log.d("CellInfoDebug", "処理可能なセルが見つかりませんでした")
                    tvNetworkInfo.text = "セル情報を処理できません"
                    useServiceStateAsFallback()
                }
            } else {
                Log.d("CellInfoDebug", "セル情報が取得できませんでした")
                tvNetworkInfo.text = "セル情報が取得できません"
                useServiceStateAsFallback()
            }
        } catch (e: Exception) {
            Log.e("CellInfoDebug", "Error collecting network info: ${e.message}", e)
            tvNetworkInfo.text = "エラー: ${e.message}"
            useServiceStateAsFallback()
        }
    }

    private fun processCellInfoWcdma(cellInfo: android.telephony.CellInfoWcdma, cellData: StringBuilder, csvLine: StringBuilder, timestamp: String) {
        val cellIdentity = cellInfo.cellIdentity
        val mcc = cellIdentity.mcc
        val mnc = cellIdentity.mnc
        val lac = cellIdentity.lac
        val cid = cellIdentity.cid
        val psc = cellIdentity.psc

        // 信号強度の取得
        val signalStrength = try {
            "${cellInfo.cellSignalStrength.dbm} dBm"
        } catch (e: Exception) {
            "不明"
        }

        val registrationStatus = if (cellInfo.isRegistered) "登録済み" else "未登録"

        cellData.append("ネットワークタイプ: WCDMA ($registrationStatus)\n")
        cellData.append("PLMN: $mcc-$mnc\n")
        cellData.append("LAC: $lac\n")
        cellData.append("CID: $cid\n")
        cellData.append("PSC: $psc\n")
        cellData.append("信号強度: $signalStrength\n")
        cellData.append("緯度: $latitude\n")
        cellData.append("経度: $longitude\n")

        val registeredFlag = if (cellInfo.isRegistered) "1" else "0"
        csvLine.append("$timestamp,WCDMA,Unknown,$mcc-$mnc,$cid,Unknown,$psc,$lac,$latitude,$longitude,$registeredFlag,$signalStrength")
    }

    private fun processCellInfoGsm(cellInfo: android.telephony.CellInfoGsm, cellData: StringBuilder, csvLine: StringBuilder, timestamp: String) {
        val cellIdentity = cellInfo.cellIdentity
        val mcc = cellIdentity.mcc
        val mnc = cellIdentity.mnc
        val lac = cellIdentity.lac
        val cid = cellIdentity.cid
        val arfcn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) cellIdentity.arfcn else 0
        val bsic = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) cellIdentity.bsic else 0

        // 信号強度の取得
        val signalStrength = try {
            "${cellInfo.cellSignalStrength.dbm} dBm"
        } catch (e: Exception) {
            "不明"
        }

        val registrationStatus = if (cellInfo.isRegistered) "登録済み" else "未登録"

        cellData.append("ネットワークタイプ: GSM ($registrationStatus)\n")
        cellData.append("PLMN: $mcc-$mnc\n")
        cellData.append("LAC: $lac\n")
        cellData.append("CID: $cid\n")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            cellData.append("ARFCN: $arfcn\n")
            cellData.append("BSIC: $bsic\n")
        }
        cellData.append("信号強度: $signalStrength\n")
        cellData.append("緯度: $latitude\n")
        cellData.append("経度: $longitude\n")

        val registeredFlag = if (cellInfo.isRegistered) "1" else "0"
        csvLine.append("$timestamp,GSM,Unknown,$mcc-$mnc,$cid,$arfcn,$bsic,$lac,$latitude,$longitude,$registeredFlag,$signalStrength")
    }

    private fun processCellInfoCdma(cellInfo: android.telephony.CellInfoCdma, cellData: StringBuilder, csvLine: StringBuilder, timestamp: String) {
        val cellIdentity = cellInfo.cellIdentity
        val networkId = cellIdentity.networkId
        val systemId = cellIdentity.systemId
        val baseStationId = cellIdentity.basestationId

        // 信号強度の取得
        val signalStrength = try {
            "${cellInfo.cellSignalStrength.dbm} dBm"
        } catch (e: Exception) {
            "不明"
        }

        val registrationStatus = if (cellInfo.isRegistered) "登録済み" else "未登録"

        cellData.append("ネットワークタイプ: CDMA ($registrationStatus)\n")
        cellData.append("Network ID: $networkId\n")
        cellData.append("System ID: $systemId\n")
        cellData.append("Base Station ID: $baseStationId\n")
        cellData.append("信号強度: $signalStrength\n")
        cellData.append("緯度: $latitude\n")
        cellData.append("経度: $longitude\n")

        val registeredFlag = if (cellInfo.isRegistered) "1" else "0"
        csvLine.append("$timestamp,CDMA,Unknown,$networkId-$systemId,$baseStationId,Unknown,Unknown,Unknown,$latitude,$longitude,$registeredFlag,$signalStrength")
    }

    private fun processCellInfoLte(cellInfo: CellInfoLte, cellData: StringBuilder, csvLine: StringBuilder, timestamp: String) {
        val cellIdentity = cellInfo.cellIdentity as CellIdentityLte
        val band = getLteBand(cellIdentity.earfcn)
        val plmn = "${cellIdentity.mcc}-${cellIdentity.mnc}"
        val cellId = cellIdentity.ci
        val arfcn = cellIdentity.earfcn
        val pci = cellIdentity.pci
        val tac = cellIdentity.tac

        // 信号強度の取得
        val signalStrength = try {
            "${cellInfo.cellSignalStrength.dbm} dBm"
        } catch (e: Exception) {
            "不明"
        }

        val registrationStatus = if (cellInfo.isRegistered) "登録済み" else "未登録"

        cellData.append("ネットワークタイプ: LTE ($registrationStatus)\n")
        cellData.append("Band: $band\n")
        cellData.append("PLMN: $plmn\n")
        cellData.append("CellID: $cellId\n")
        cellData.append("EARFCN: $arfcn\n")
        cellData.append("PCI: $pci\n")
        cellData.append("TAC: $tac\n")
        cellData.append("信号強度: $signalStrength\n")
        cellData.append("緯度: $latitude\n")
        cellData.append("経度: $longitude\n")

        val registeredFlag = if (cellInfo.isRegistered) "1" else "0"
        csvLine.append("$timestamp,LTE,$band,$plmn,$cellId,$arfcn,$pci,$tac,$latitude,$longitude,$registeredFlag,$signalStrength")
    }

    private fun processCellInfoNr(cellInfo: CellInfoNr, cellData: StringBuilder, csvLine: StringBuilder, timestamp: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val cellIdentity = cellInfo.cellIdentity as CellIdentityNr
            val nrarfcn = cellIdentity.nrarfcn
            val band = getNrBand(nrarfcn)
            val pci = cellIdentity.pci
            val tac = cellIdentity.tac

            // 信号強度の取得
            val signalStrength = try {
                "${cellInfo.cellSignalStrength.dbm} dBm"
            } catch (e: Exception) {
                "不明"
            }

            // MCC-MNCの取得
            val mccMnc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                "${cellIdentity.mccString ?: "?"}-${cellIdentity.mncString ?: "?"}"
            } else {
                "?-?"
            }

            // CellIDの取得
            val cellId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                cellIdentity.nci
            } else {
                0
            }

            val registrationStatus = if (cellInfo.isRegistered) "登録済み" else "未登録"

            cellData.append("ネットワークタイプ: 5G NR ($registrationStatus)\n")
            cellData.append("Band: $band\n")
            cellData.append("PLMN: $mccMnc\n")
            cellData.append("CellID: $cellId\n")
            cellData.append("NRARFCN: $nrarfcn\n")
            cellData.append("PCI: $pci\n")
            cellData.append("TAC: $tac\n")
            cellData.append("信号強度: $signalStrength\n")
            cellData.append("緯度: $latitude\n")
            cellData.append("経度: $longitude\n")

            val registeredFlag = if (cellInfo.isRegistered) "1" else "0"
            csvLine.append("$timestamp,NR,$band,$mccMnc,$cellId,$nrarfcn,$pci,$tac,$latitude,$longitude,$registeredFlag,$signalStrength")
        }
    }

    private fun useServiceStateAsFallback() {
        try {
            val serviceState = telephonyManager.serviceState
            if (serviceState != null) {
                val networkInfo = StringBuilder()
                val csvLine = StringBuilder()
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

                // ServiceStateから基本情報を取得
                val operatorNumeric = telephonyManager.networkOperator
                val mcc = if (operatorNumeric.length >= 3) operatorNumeric.substring(0, 3) else "?"
                val mnc = if (operatorNumeric.length > 3) operatorNumeric.substring(3) else "?"
                val networkType = getNetworkTypeString(telephonyManager.dataNetworkType)

                networkInfo.append("--- 基本ネットワーク情報 ---\n")
                networkInfo.append("ネットワークタイプ: $networkType\n")
                networkInfo.append("PLMN: $mcc-$mnc\n")
                networkInfo.append("オペレーター: ${telephonyManager.networkOperatorName}\n")
                networkInfo.append("緯度: $latitude\n")
                networkInfo.append("経度: $longitude\n")

                csvLine.append("$timestamp,$networkType,Unknown,$mcc-$mnc,Unknown,Unknown,Unknown,Unknown,$latitude,$longitude,Unknown,Unknown")

                tvNetworkInfo.text = networkInfo.toString()
                saveToCSV(csvLine.toString())
            }
        } catch (e: Exception) {
            Log.e("CellInfoDebug", "ServiceState取得エラー: ${e.message}", e)
        }
    }

    private fun saveToCSV(data: String) {
        try {
            val baseDir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                getExternalFilesDir(null)
            } else {
                Environment.getExternalStorageDirectory()
            }

            val fileName = "network_info_${SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())}.csv"
            val file = File(baseDir, fileName)

            val isNewFile = !file.exists()

            FileOutputStream(file, true).use { fos ->
                OutputStreamWriter(fos).use { osw ->
                    if (isNewFile) {
                        // CSVヘッダーを追加
                        osw.append("Timestamp,NetworkType,Band,PLMN,CellID,ARFCN,PCI,TAC,Latitude,Longitude,Registered,SignalStrength\n")
                    }
                    osw.append(data).append("\n")
                }
            }

            Log.d("MainActivity", "Saved to ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error saving to CSV: ${e.message}")
        }
    }

    private fun getNetworkTypeString(type: Int): String {
        return when (type) {
            TelephonyManager.NETWORK_TYPE_GPRS -> "GPRS"
            TelephonyManager.NETWORK_TYPE_EDGE -> "EDGE"
            TelephonyManager.NETWORK_TYPE_UMTS -> "UMTS"
            TelephonyManager.NETWORK_TYPE_HSDPA -> "HSDPA"
            TelephonyManager.NETWORK_TYPE_HSUPA -> "HSUPA"
            TelephonyManager.NETWORK_TYPE_HSPA -> "HSPA"
            TelephonyManager.NETWORK_TYPE_CDMA -> "CDMA"
            TelephonyManager.NETWORK_TYPE_EVDO_0 -> "EVDO_0"
            TelephonyManager.NETWORK_TYPE_EVDO_A -> "EVDO_A"
            TelephonyManager.NETWORK_TYPE_EVDO_B -> "EVDO_B"
            TelephonyManager.NETWORK_TYPE_1xRTT -> "1xRTT"
            TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
            TelephonyManager.NETWORK_TYPE_NR -> "5G NR"
            else -> "Unknown"
        }
    }

    // LTEバンドを取得する関数
    private fun getLteBand(earfcn: Int): Int {
        return when {
            // Band 1
            earfcn in 0..599 -> 1
            // Band 2
            earfcn in 600..1199 -> 2
            // Band 3
            earfcn in 1200..1949 -> 3
            // Band 4
            earfcn in 1950..2399 -> 4
            // Band 5
            earfcn in 2400..2649 -> 5
            // Band 6
            earfcn in 2650..2749 -> 6
            // Band 7
            earfcn in 2750..3449 -> 7
            // Band 8
            earfcn in 3450..3799 -> 8
            // Band 9
            earfcn in 3800..4149 -> 9
            // Band 10
            earfcn in 4150..4749 -> 10
            // Band 11
            earfcn in 4750..4999 -> 11
            // Band 12
            earfcn in 5000..5179 -> 12
            // Band 13
            earfcn in 5180..5279 -> 13
            // Band 14
            earfcn in 5280..5379 -> 14
            // Band 17
            earfcn in 5730..5849 -> 17
            // Band 18
            earfcn in 5850..5999 -> 18
            // Band 19
            earfcn in 6000..6149 -> 19
            // Band 20
            earfcn in 6150..6449 -> 20
            // Band 21
            earfcn in 6450..6599 -> 21
            // Band 22
            earfcn in 6600..7399 -> 22
            // Band 23
            earfcn in 7500..7699 -> 23
            // Band 24
            earfcn in 7700..8039 -> 24
            // Band 25
            earfcn in 8040..8689 -> 25
            // Band 26
            earfcn in 8690..9039 -> 26
            // Band 27
            earfcn in 9040..9209 -> 27
            // Band 28
            earfcn in 9210..9659 -> 28
            // Band 29
            earfcn in 9660..9769 -> 29
            // Band 30
            earfcn in 9770..9869 -> 30
            // Band 31
            earfcn in 9870..9919 -> 31
            // Band 32
            earfcn in 9920..10359 -> 32
            // Band 33
            earfcn in 36000..36199 -> 33
            // Band 34
            earfcn in 36200..36349 -> 34
            // Band 35
            earfcn in 36350..36949 -> 35
            // Band 36
            earfcn in 36950..37549 -> 36
            // Band 37
            earfcn in 37550..37749 -> 37
            // Band 38
            earfcn in 37750..38249 -> 38
            // Band 39
            earfcn in 38250..38649 -> 39
            // Band 40
            earfcn in 38650..39649 -> 40
            // Band 41
            earfcn in 39650..41589 -> 41
            // Band 42
            earfcn in 41590..43589 -> 42
            // Band 43
            earfcn in 43590..45589 -> 43
            // Band 44
            earfcn in 45590..46589 -> 44
            // Band 45
            earfcn in 46590..46789 -> 45
            // Band 46
            earfcn in 46790..54539 -> 46
            // Band 47
            earfcn in 54540..55239 -> 47
            // Band 48
            earfcn in 55240..56739 -> 48
            // Band 49
            earfcn in 56740..58239 -> 49
            // Band 50
            earfcn in 58240..59089 -> 50
            // Band 51
            earfcn in 59090..59139 -> 51
            // Band 52
            earfcn in 59140..60139 -> 52
            // Band 65
            earfcn in 65536..66435 -> 65
            // Band 66
            earfcn in 66436..67335 -> 66
            // Band 68
            earfcn in 67536..67835 -> 68
            // Band 70
            earfcn in 68336..68535 -> 70
            // Band 71
            earfcn in 68586..68935 -> 71
            // Band 72
            earfcn in 68936..68985 -> 72
            // Band 73
            earfcn in 68986..69035 -> 73
            // Band 74
            earfcn in 69036..69465 -> 74
            // Band 75
            earfcn in 69466..70315 -> 75
            // Band 76
            earfcn in 70316..70365 -> 76
            // Band 85
            earfcn in 70366..70545 -> 85
            // Band 87
            earfcn in 70546..70595 -> 87
            // Band 88
            earfcn in 70596..70645 -> 88
            else -> 0 // 不明なバンド
        }
    }

    // 5G NRバンドを取得する関数
    private fun getNrBand(nrarfcn: Int): Int {
        return when {
            // n1
            nrarfcn in 422000..434000 -> 1
            // n2
            nrarfcn in 386000..398000 -> 2
            // n3
            nrarfcn in 361000..376000 -> 3
            // n5
            nrarfcn in 173800..178800 -> 5
            // n7
            nrarfcn in 524000..538000 -> 7
            // n8
            nrarfcn in 185000..192000 -> 8
            // n12
            nrarfcn in 145800..149200 -> 12
            // n20
            nrarfcn in 158200..164200 -> 20
            // n25
            nrarfcn in 386000..399000 -> 25
            // n28
            nrarfcn in 151600..160600 -> 28
            // n34
            nrarfcn in 402000..405000 -> 34
            // n38
            nrarfcn in 514000..524000 -> 38
            // n39
            nrarfcn in 376000..384000 -> 39
            // n40
            nrarfcn in 460000..480000 -> 40
            // n41
            nrarfcn in 499200..537999 -> 41
            // n50
            nrarfcn in 286400..303400 -> 50
            // n51
            nrarfcn in 285400..286400 -> 51
            // n66
            nrarfcn in 422000..440000 -> 66
            // n70
            nrarfcn in 399000..404000 -> 70
            // n71
            nrarfcn in 123400..130400 -> 71
            // n74
            nrarfcn in 295000..303600 -> 74
            // n75
            nrarfcn in 286400..303400 -> 75
            // n76
            nrarfcn in 285400..286400 -> 76
            // n77
            nrarfcn in 620000..680000 -> 77
            // n78
            nrarfcn in 620000..653333 -> 78
            // n79
            nrarfcn in 693334..733333 -> 79
            // n80
            nrarfcn in 342000..357000 -> 80
            // n81
            nrarfcn in 176000..183000 -> 81
            // n82
            nrarfcn in 166000..172000 -> 82
            // n83
            nrarfcn in 140000..143333 -> 83
            // n84
            nrarfcn in 384000..396000 -> 84
            // n86
            nrarfcn in 342000..356000 -> 86
            // n89
            nrarfcn in 164800..171600 -> 89
            // n90
            nrarfcn in 156000..160000 -> 90
            // n91
            nrarfcn in 384000..394000 -> 91
            // n92
            nrarfcn in 376000..383000 -> 92
            // n93
            nrarfcn in 370000..376000 -> 93
            // n94
            nrarfcn in 366000..370000 -> 94
            // n95
            nrarfcn in 145000..149000 -> 95
            // n96
            nrarfcn in 140000..144000 -> 96
            // n97
            nrarfcn in 131000..138000 -> 97
            // n98
            nrarfcn in 127500..130500 -> 98
            // n99
            nrarfcn in 122000..124000 -> 99
            // n100
            nrarfcn in 119000..122000 -> 100
            // n257 (28GHz)
            nrarfcn in 2054166..2104165 -> 257
            // n258 (26GHz)
            nrarfcn in 2016667..2070832 -> 258
            // n259 (41GHz)
            nrarfcn in 2270832..2337499 -> 259
            // n260 (39GHz)
            nrarfcn in 2229166..2279165 -> 260
            // n261 (28GHz)
            nrarfcn in 2070833..2104165 -> 261
            else -> 0 // 不明なバンド
        }
    }

    override fun onLocationChanged(location: Location) {
        latitude = location.latitude
        longitude = location.longitude
        Log.d("MainActivity", "Location: $latitude, $longitude")
    }

    override fun onDestroy() {
        super.onDestroy()
        stopTracking()
        try {
            locationManager.removeUpdates(this)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error removing location updates: ${e.message}")
        }
    }
}