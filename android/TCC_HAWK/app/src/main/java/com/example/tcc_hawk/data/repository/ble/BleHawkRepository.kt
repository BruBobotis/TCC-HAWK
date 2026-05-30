@file:Suppress("MissingPermission")
package com.example.tcc_hawk.data.repository.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.util.Log
import com.example.tcc_hawk.data.model.*
import com.example.tcc_hawk.data.repository.HawkRepository
import kotlinx.coroutines.flow.*
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.Calendar
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import com.example.tcc_hawk.data.ble.HawkBleUuids
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import android.R.string.ok
import android.content.Intent
import com.example.tcc_hawk.data.alarms.AlarmReceiver
import com.example.tcc_hawk.data.mqtt.MqttManager
import com.example.tcc_hawk.data.model.VitalsPayload
import java.time.Instant



class BleHawkRepository(private val context: Context) : HawkRepository {
   init {
        MqttManager.init(context)
   }
    private val appContext = context.applicationContext
    private var lastMqttPublishMillis = 0L
    private val mqttPublishIntervalMillis = 5_000L
    private val _history = MutableStateFlow<List<VitalsReading>>(emptyList())
    fun vitalsHistoryFlow(): StateFlow<List<VitalsReading>> = _history.asStateFlow()

    private var lastHistorySaveMillis = 0L
    companion object {
        private const val TAG = "HAWK_BLE"
        private const val DEVICE_NAME = "HAWK-WATCH"
    }

    private fun getVitalLimits(profile: PatientProfile?): VitalLimits {
        return VitalLimits(
            bpmLow = profile?.bradycardiaLimit ?: 50,
            bpmHigh = profile?.tachycardiaLimit ?: 130,
            spo2Low = profile?.spo2LowLimit ?: 87,
            spo2High = profile?.spo2HighLimit ?: 100
        )
    }
    private fun triggerVitalAlarmOnPhone(
        type: VitalAlertType,
        value: Int,
        limit: Int
    ) {
        val (title, message, alertType) = when (type) {
            VitalAlertType.BPM_LOW -> Triple(
                "Batimento baixo",
                "Frequência cardíaca baixa: $value BPM. Limite mínimo: $limit BPM.",
                "BPM_LOW"
            )

            VitalAlertType.BPM_HIGH -> Triple(
                "Batimento alto",
                "Frequência cardíaca elevada: $value BPM. Limite máximo: $limit BPM.",
                "BPM_HIGH"
            )

            VitalAlertType.SPO2_LOW -> Triple(
                "Oxigenação baixa",
                "Saturação de oxigênio baixa: $value%. Limite mínimo: $limit%.",
                "SPO2_LOW"
            )

            VitalAlertType.SPO2_HIGH -> Triple(
                "Oxigenação alta",
                "Saturação de oxigênio acima do limite: $value%. Limite máximo: $limit%.",
                "SPO2_HIGH"
            )
        }

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.example.tcc_hawk.ACTION_VITAL_ALERT"
            putExtra("type", alertType)
            putExtra("title", title)
            putExtra("message", message)
        }

        context.sendBroadcast(intent)
    }

    private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
    private val adapter: BluetoothAdapter? get() = bluetoothManager.adapter
    private val scanner: BluetoothLeScanner? get() = adapter?.bluetoothLeScanner
    private var gatt: BluetoothGatt? = null
    private var statusChar: BluetoothGattCharacteristic? = null
    private var syncChar: BluetoothGattCharacteristic? = null
    private var cmdChar: BluetoothGattCharacteristic? = null
    private var lastFallState = false
    private var lastFallAlertMillis = 0L
    private var lastBpmLowAlertMillis = 0L
    private var lastBpmHighAlertMillis = 0L
    private var lastSpo2LowAlertMillis = 0L
    private var lastSpo2HighAlertMillis = 0L

    private val vitalAlertCooldownMillis = 60_000L
    private val _vitals = MutableStateFlow(
        VitalsReading(
            bpm = 0,
            steps = 0,
            isMoving = false,
            battery = 0,
            ax = null,
            ay = null,
            az = null,
            fallDetected = false
        )
    )
    private val _alerts = MutableStateFlow<List<AlertEvent>>(emptyList())
    private val _reminders = MutableStateFlow<List<Reminder>>(emptyList())
    private val _caregivers = MutableStateFlow<List<Caregiver>>(emptyList())

    override fun vitalsFlow(): Flow<VitalsReading> = _vitals
    override fun alertsFlow(): Flow<List<AlertEvent>> = _alerts
    override fun remindersFlow(): Flow<List<Reminder>> = _reminders
    override fun caregiversFlow(): Flow<List<Caregiver>> = _caregivers

    // -------------------- Scan / Connect --------------------

    @SuppressLint("MissingPermission")
    fun startScan() {
        try {
            val sc = scanner ?: run {
                Log.e(TAG, "Scanner null (bluetooth desligado?)")
                return
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    Log.e("BLE", "Sem permissão BLUETOOTH_SCAN")
                    return
                }
            }
            Log.d(TAG, "startScan()... adapterEnabled=${adapter?.isEnabled}")

            // filtro por SERVICE é mais confiável que filtro por nome
            val filters = emptyList<ScanFilter>()

            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            sc.startScan(filters, settings, scanCallback)

            // segurança: para scan depois de 10s (evita scan infinito)
            // (opcional) se quiser depois eu mando com Handler
        } catch (e: SecurityException) {
            Log.e(TAG, "Sem permissão BLUETOOTH_SCAN: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    private fun triggerFallAlarmOnPhone() {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.example.tcc_hawk.ACTION_FALL_ALERT"
            putExtra("type", "FALL")
            putExtra("title", "Queda detectada")
            putExtra("message", "Possível queda detectada pelo relógio")
        }

        context.sendBroadcast(intent)
    }


    @SuppressLint("MissingPermission")
    fun sendSync(datetime: String) {
        val characteristic = gatt
            ?.getService(HawkBleUuids.SERVICE)
            ?.getCharacteristic(HawkBleUuids.SYNC)

        characteristic?.let {
            it.value = datetime.toByteArray()
            gatt?.writeCharacteristic(it)
            Log.d(TAG, "SYNC enviado: $datetime")
        }
    }

    @SuppressLint("MissingPermission")
    fun sendPhoneTimeNow() {
        val g = gatt ?: run { Log.e(TAG, "sendPhoneTimeNow: gatt null"); return }
        val c = syncChar ?: run { Log.e(TAG, "sendPhoneTimeNow: syncChar null"); return }

        // ✅ Formato recomendado pro ESP: "YYYY-MM-DD HH:MM"
        val cal = java.util.Calendar.getInstance()
// Formato pro ESP: "YYYY-MM-DD HH:MM:SS"
        val payload = String.format(
            "%04d-%02d-%02d %02d:%02d:%02d",
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH),
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE),
            cal.get(Calendar.SECOND)
        )

        c.value = payload.toByteArray(Charsets.UTF_8)
        c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        val ok = g.writeCharacteristic(c)
        Log.d(TAG, "TX SYNC='$payload' ok=$ok")
    }

    @SuppressLint("MissingPermission")
    override fun sendAlert(type: String, duration: Int, message: String) {

        val payload = "ALERT|$type|$duration|$message"

        val characteristic = gatt
            ?.getService(HawkBleUuids.SERVICE)
            ?.getCharacteristic(HawkBleUuids.CMD)

        characteristic?.let {
            it.value = payload.toByteArray()
            gatt?.writeCharacteristic(it)
            Log.d(TAG, "ALERT enviado: $payload")
        }
    }

    override fun sendRawCommand(payload: String) {

        val characteristic = gatt
            ?.getService(HawkBleUuids.SERVICE)
            ?.getCharacteristic(HawkBleUuids.CMD)

        characteristic?.let {
            it.value = payload.toByteArray()
            gatt?.writeCharacteristic(it)
            Log.d(TAG, "CMD enviado: $payload")
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        Log.d(TAG, "disconnect()")
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        statusChar = null
        syncChar = null
        cmdChar = null
    }
    @Volatile private var connecting = false
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            Log.d("BLE_SCAN", "Encontrado: ${device.name} | ${device.address}")

            if (device.name != null && device.name.contains("HAWK", true)) {
                scanner?.stopScan(this)
                connect(device)
            }
            if (!connecting && device.name == HawkBleUuids.DEVICE_NAME) {
                connecting = true
                scanner?.stopScan(this)
                connect(device)
            } else connecting = false
        }
    }

    @SuppressLint("MissingPermission")
    private fun connect(device: BluetoothDevice) {
        Log.d(TAG, "connectGatt(${device.address})")
        gatt?.close()
        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    // -------------------- GATT --------------------

    private fun hasConnectPermission(context: Context): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= 31) {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.BLUETOOTH_CONNECT
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else true
    }

    private val gattCallback = object : BluetoothGattCallback() {

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            Log.d(TAG, "onConnectionStateChange status=$status newState=$newState")
            if (!hasConnectPermission(appContext)) {
                Log.e(TAG, "Sem BLUETOOTH_CONNECT -> não dá pra requestMtu/discoverServices")
                return
            }
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "Connected -> requestMtu(185)")
                val ok = g.requestMtu(185)
                if (!ok) {
                    Log.w(TAG, "requestMtu retornou false -> discoverServices() direto")
                    g.discoverServices()
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(TAG, "Disconnected")
                statusChar = null
                syncChar = null
                cmdChar = null
                gatt = null
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            Log.d(TAG, "onMtuChanged mtu=$mtu status=$status -> discoverServices()")
            g.discoverServices()
        }
        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            Log.d(TAG, "onServicesDiscovered status=$status")
            Log.d("BLE_DISC", "Services discovered: ${g.services.size}")
            g.services.forEach { s ->
                Log.d("BLE_DISC", "Service=${s.uuid} chars=${s.characteristics.size}")
                s.characteristics.forEach { c ->
                    Log.d("BLE_DISC", "  Char=${c.uuid} props=${c.properties}")
                    c.descriptors.forEach { d ->
                        Log.d("BLE_DISC", "    Desc=${d.uuid}")
                    }
                }

            }
            Log.d(TAG, "STATUS=${statusChar?.uuid} props=${statusChar?.properties}")
            Log.d(TAG, "SYNC=${syncChar?.uuid} props=${syncChar?.properties}")
            Log.d(TAG, "CMD=${cmdChar?.uuid} props=${cmdChar?.properties}")
            // dentro do onServicesDiscovered(...)

            val hawkService = g.services.firstOrNull { it.uuid == HawkBleUuids.SERVICE }
                ?: run {
                    Log.e("BLE_DISC", "Hawk service NOT found. Expected=${HawkBleUuids.SERVICE}")
                    return
                }

            statusChar = hawkService.getCharacteristic(HawkBleUuids.STATUS)
            syncChar   = hawkService.getCharacteristic(HawkBleUuids.SYNC)
            cmdChar    = hawkService.getCharacteristic(HawkBleUuids.CMD)

            Log.d("BLE_DISC", "FOUND status=${statusChar?.uuid} props=${statusChar?.properties}")
            Log.d("BLE_DISC", "FOUND sync=${syncChar?.uuid} props=${syncChar?.properties}")
            Log.d("BLE_DISC", "FOUND cmd=${cmdChar?.uuid} props=${cmdChar?.properties}")
            // ✅ habilita notify no STATUS
            statusChar?.let { enableNotify(g, it) }
            // Depois de encontrar e setar syncChar/statusChar/cmdChar:
            Log.d(TAG, "GATT pronto. Enviando SYNC inicial (com segundos)...")
        }
        @SuppressLint("MissingPermission")
        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            val txt = characteristic.value?.toString(Charsets.UTF_8)
            Log.d(TAG, "onCharacteristicWrite uuid=${characteristic.uuid} status=$status value=$txt")
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            val value = characteristic.value ?: return
            onNotify(characteristic, value)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            onNotify(characteristic, value)
        }

        private fun onNotify(ch: BluetoothGattCharacteristic, value: ByteArray) {
            if (ch.uuid == HawkBleUuids.STATUS) {
                Log.d("BLE_NOTIFY", "STATUS notify bytes=${value.size}")
                handleStatus(value)
            } else {
                Log.d("BLE_NOTIFY", "Other notify uuid=${ch.uuid} bytes=${value.size}")
            }
        }
        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            Log.d("BLE_CCCD", "onDescriptorWrite status=$status uuid=${descriptor.uuid}")

            if (status == BluetoothGatt.GATT_SUCCESS &&
                descriptor.uuid == HawkBleUuids.CCCD
            ) {
                // Só agora manda o SYNC
                val payload = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                    .format(java.util.Date())
                sendPhoneTimeNow() // <-- agora ela passa a ser usada
                val ch = syncChar
                if (ch != null) {
                    ch.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    ch.value = payload.toByteArray(Charsets.UTF_8)
                    val ok = gatt.writeCharacteristic(ch)
                    Log.d("BLE_SYNC", "SYNC após CCCD='$payload' ok=$ok")
                }

            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableNotify(gatt: BluetoothGatt, ch: BluetoothGattCharacteristic) {
        val cccdUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        val desc = ch.getDescriptor(cccdUuid)
        if (desc == null) {
            Log.e("BLE_CCCD", "CCCD NOT found for ${ch.uuid}")
            return
        }

        val okSet = gatt.setCharacteristicNotification(ch, true)
        Log.d("BLE_CCCD", "setCharacteristicNotification(${ch.uuid}) ok=$okSet")

        val okWrite = if (Build.VERSION.SDK_INT >= 33) {
            gatt.writeDescriptor(desc, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        } else {
            desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(desc)
        }
        Log.d("BLE_CCCD", "writeDescriptor(${desc.uuid}) ok=$okWrite")
    }

    // -------------------- Writes --------------------

    @SuppressLint("MissingPermission")
    fun sendTimeSyncNow() {
        val ch = syncChar ?: run {
            Log.e(TAG, "SYNC char null")
            return
        }
        syncChar?.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        val ok = gatt?.writeCharacteristic(syncChar)
        Log.d("BLE_SYNC", "writeCharacteristic(SYNC) ok=$ok")
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val payload = fmt.format(Date())
        Log.d("BLE_SYNC", "Sending SYNC payload='$payload' to ${syncChar?.uuid}")
        Log.d(TAG, "SYNC write: $payload")
        ch.value = payload.toByteArray(Charset.forName("UTF-8"))
        gatt?.writeCharacteristic(ch)
        Log.d("BLE_SYNC", "writeCharacteristic(sync) returned=$ok")
    }

    @SuppressLint("MissingPermission")
    fun sendCmd(cmd: String) {
        val ch = cmdChar ?: run {
            Log.e(TAG, "CMD char null")
            return
        }
        Log.d(TAG, "CMD write: $cmd")
        ch.value = cmd.toByteArray(Charset.forName("UTF-8"))
        gatt?.writeCharacteristic(ch)
    }

    private fun checkVitalAlerts(
        bpm: Int,
        spo2: Int,
        profile: PatientProfile?
    ) {
        val now = System.currentTimeMillis()
        val limits = getVitalLimits(profile)

        if (bpm > 0 && bpm < limits.bpmLow && now - lastBpmLowAlertMillis > vitalAlertCooldownMillis) {
            lastBpmLowAlertMillis = now
            triggerVitalAlarmOnPhone(
                type = VitalAlertType.BPM_LOW,
                value = bpm,
                limit = limits.bpmLow
            )
        }

        if (bpm > 0 && bpm > limits.bpmHigh && now - lastBpmHighAlertMillis > vitalAlertCooldownMillis) {
            lastBpmHighAlertMillis = now
            triggerVitalAlarmOnPhone(
                type = VitalAlertType.BPM_HIGH,
                value = bpm,
                limit = limits.bpmHigh
            )
        }

        if (spo2 > 0 && spo2 < limits.spo2Low && now - lastSpo2LowAlertMillis > vitalAlertCooldownMillis) {
            lastSpo2LowAlertMillis = now
            triggerVitalAlarmOnPhone(
                type = VitalAlertType.SPO2_LOW,
                value = spo2,
                limit = limits.spo2Low
            )
        }

        if (spo2 > 0 && spo2 > limits.spo2High && now - lastSpo2HighAlertMillis > vitalAlertCooldownMillis) {
            lastSpo2HighAlertMillis = now
            triggerVitalAlarmOnPhone(
                type = VitalAlertType.SPO2_HIGH,
                value = spo2,
                limit = limits.spo2High
            )
        }
    }


    // -------------------- STATUS parse --------------------

    private fun handleStatus(bytes: ByteArray) {
        val txt = bytes.toString(Charsets.UTF_8).trim()
        if (txt.isEmpty()) return

        Log.d("BLE_RAW", txt)

        // Parse flexível: aceita "K:V" ou "K=V" e separa por ';'
        val map = txt.split(';')
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { token ->
                val idx = token.indexOf('=').takeIf { it > 0 }
                    ?: token.indexOf(':').takeIf { it > 0 }
                    ?: return@mapNotNull null

                val key = token.substring(0, idx).trim().uppercase()
                val value = token.substring(idx + 1).trim()
                if (key.isEmpty()) null else key to value
            }
            .toMap()
        Log.d("BLE_PARSE", "mapKeys=${map.keys} map=$map")
        // se não veio nenhum campo que a gente usa, ignora
        val hasUseful =
            map.containsKey("BPM") || map.containsKey("SPO2") || map.containsKey("P") || map.containsKey("B") ||
                    map.containsKey("Q") || map.containsKey("X") || map.containsKey("Y") || map.containsKey("Z") ||
                    map.containsKey("MPU") || map.containsKey("F") || map.containsKey("MAX")

        if (!hasUseful) {
            Log.w("BLE_PARSE", "Payload sem campos úteis, ignorando. txt='$txt'")
            return
        }
        val prev = _vitals.value

        // Helpers locais (evitam duplicação)
        fun intOrPrev(key: String, prevValue: Int) = map[key]?.toIntOrNull() ?: prevValue
        fun bool01(key: String) = (map[key]?.toIntOrNull() ?: 0) == 1
        fun floatOrNull(key: String) = map[key]?.replace(",", ".")?.toFloatOrNull()

        val nextBpm = intOrPrev("BPM", prev.bpm)
        val nextSpo2 = intOrPrev("SPO2", prev.spo2)
        val nextSteps = intOrPrev("P", prev.steps)
        val nextBattery = intOrPrev("B", prev.battery)

        val nextHour = map["H"] ?: prev.watchHour
        val nextDate = map["D"] ?: prev.watchDate

        val nextAx = floatOrNull("X") ?: prev.ax
        val nextAy = floatOrNull("Y") ?: prev.ay
        val nextAz = floatOrNull("Z") ?: prev.az

        val nextFall = bool01("Q") || prev.fallDetected
        val nextMpuOk = bool01("MPU")
        val nextFinger = bool01("F")

        // Movimento: prioriza acelerômetro se disponível, senão steps
        val nextIsMoving = if (nextAx != null && nextAy != null && nextAz != null) {
            val mag = kotlin.math.sqrt(nextAx * nextAx + nextAy * nextAy + nextAz * nextAz)
            mag > 1.12f
        } else {
            nextSteps > prev.steps
        }

        // Atualiza UMA vez só
        _vitals.value = prev.copy(
            watchHour = nextHour,
            watchDate = nextDate,
            bpm = nextBpm,
            spo2 = nextSpo2,
            steps = nextSteps,
            battery = nextBattery,
            ax = nextAx,
            ay = nextAy,
            az = nextAz,
            fallDetected = nextFall,
            isMoving = nextIsMoving,
            mpuOk = nextMpuOk,
            fingerDetected = nextFinger,
            timestampMillis = System.currentTimeMillis()
        )
        val now = System.currentTimeMillis()

        val shouldPublishMqtt =
            now - lastMqttPublishMillis >= mqttPublishIntervalMillis || nextFall

        if (shouldPublishMqtt) {
            lastMqttPublishMillis = now

            val currentVitals = _vitals.value

            val mqttPayload = VitalsPayload(
                passos = currentVitals.steps,
                bpm = currentVitals.bpm,
                spo2 = currentVitals.spo2.toFloat(),
                quedasTotal = currentVitals.fallsCount,
                quedaAtual = currentVitals.fallDetected,
                timestamp = Instant.now().toString()
            )

            MqttManager.publishVitals(mqttPayload)
        }

        checkVitalAlerts(
            bpm = nextBpm,
            spo2 = nextSpo2,
            profile = null
        )

        // Evento de queda (gera 1x)
        if (bool01("Q")) {
            val alreadyHasFall = _alerts.value.firstOrNull()?.type == AlertType.FALL
            if (!alreadyHasFall) {
                val ev = AlertEvent(
                    type = AlertType.FALL,
                    title = "Queda detectada",
                    subtitle = "Relógio reportou Q=1",
                    severity = Severity.HIGH,
                    read = false
                )
                _alerts.value = listOf(ev) + _alerts.value
            }
        }

        if (now - lastHistorySaveMillis >= 60_000L) {
            lastHistorySaveMillis = now
            _history.update { oldList ->
                (oldList + _vitals.value).takeLast(24 * 60 * 7)
                // ~7 dias guardando 1 ponto por minuto
            }
        }

        val shouldTriggerFallAlert =
            bool01("Q") &&
                    !lastFallState &&
                    now - lastFallAlertMillis > 30_000L

        lastFallState = bool01("Q")

        if (shouldTriggerFallAlert) {
            lastFallAlertMillis = now
            triggerFallAlarmOnPhone()
        }


        Log.d(TAG, "Parsed: H=$nextHour D=$nextDate BPM=$nextBpm SPO2=$nextSpo2 P=$nextSteps B=$nextBattery Q=${bool01("Q")} MPU=$nextMpuOk F=$nextFinger")
    }

    // -------------------- HawkRepository (stubs por enquanto) --------------------

    override suspend fun markAlertRead(id: String) {
        _alerts.value = _alerts.value.map { if (it.id == id) it.copy(read = true) else it }
    }

    override suspend fun addReminder(reminder: Reminder) {
        _reminders.value = listOf(reminder) + _reminders.value
    }

    override suspend fun updateReminder(reminder: Reminder) {
        _reminders.value = _reminders.value.map { if (it.id == reminder.id) reminder else it }
    }

    override suspend fun deleteReminder(id: String) {
        _reminders.value = _reminders.value.filterNot { it.id == id }
    }

    override suspend fun addCaregiver(c: Caregiver) {
        _caregivers.value = _caregivers.value + c
    }

    override suspend fun deleteCaregiver(id: String) {
        _caregivers.value = _caregivers.value.filterNot { it.id == id }
    }

    override suspend fun setPrincipalCaregiver(id: String) {
        val sorted = _caregivers.value.sortedBy { it.priority }
        val updated = sorted.map { cg ->
            when {
                cg.id == id -> cg.copy(priority = 1)
                else -> cg.copy(priority = cg.priority + 1)
            }
        }.sortedBy { it.priority }.mapIndexed { idx, cg -> cg.copy(priority = idx + 1) }
        _caregivers.value = updated
    }
}