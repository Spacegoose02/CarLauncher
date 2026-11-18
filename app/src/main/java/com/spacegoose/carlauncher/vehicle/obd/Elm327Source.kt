package com.spacegoose.carlauncher.vehicle.obd

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class ObdStatus(val message: String) {
    NOT_SET_UP("Pick your OBD-II adapter in settings"),
    NEEDS_PERMISSION("Car data needs Bluetooth permission"),
    BLUETOOTH_OFF("Bluetooth is off"),
    CONNECTING("Connecting to the adapter..."),
    NO_CAR("Adapter found, waiting for the car"),
    CONNECTED("Connected"),
    FAILED("Can't reach the adapter, retrying"),
}

data class PairedDevice(val name: String, val address: String)

/**
 * Reads live engine data from a Bluetooth (classic, SPP) ELM327 OBD-II adapter. It keeps reconnecting while
 * started, because the adapter usually only powers up with the ignition.
 */
class Elm327Source(context: Context) {
    private val appContext = context.applicationContext
    private val adapter: BluetoothAdapter? = appContext.getSystemService(BluetoothManager::class.java)?.adapter
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _reading = MutableStateFlow(ObdReading())
    val reading: StateFlow<ObdReading> = _reading.asStateFlow()
    private val _status = MutableStateFlow(ObdStatus.NOT_SET_UP)
    val status: StateFlow<ObdStatus> = _status.asStateFlow()

    private var job: Job? = null
    private var address: String = ""
    @Volatile private var socket: BluetoothSocket? = null

    val hasAdapter: Boolean get() = adapter != null

    fun hasPermission(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    /** Paired Bluetooth devices, for picking the adapter. Likely adapters ("OBD", "ELM", "Vgate", ...) first. */
    @SuppressLint("MissingPermission")
    fun pairedDevices(): List<PairedDevice> {
        if (adapter == null || !hasPermission()) return emptyList()
        return try {
            adapter.bondedDevices.orEmpty()
                .map { PairedDevice(it.name ?: it.address, it.address) }
                .sortedWith(compareBy({ !looksLikeObd(it.name) }, { it.name.lowercase() }))
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    /** Starts (or switches) the connection to [address]; a blank address stops it. */
    fun start(address: String) {
        if (address == this.address && job?.isActive == true) return
        stop()
        this.address = address
        if (address.isBlank()) {
            _status.value = ObdStatus.NOT_SET_UP
            return
        }
        job = scope.launch { connectLoop(address) }
    }

    fun stop() {
        job?.cancel()
        job = null
        closeSocket()
    }

    private suspend fun CoroutineScope.connectLoop(address: String) {
        var backoff = MIN_RETRY_MS
        while (isActive) {
            when {
                !hasPermission() -> _status.value = ObdStatus.NEEDS_PERMISSION
                adapter == null || !adapter.isEnabled -> _status.value = ObdStatus.BLUETOOTH_OFF
                else -> {
                    _status.value = ObdStatus.CONNECTING
                    try {
                        val s = connect(adapter, address)
                        socket = s
                        backoff = MIN_RETRY_MS
                        talk(s)
                    } catch (e: IOException) {
                        _status.value = ObdStatus.FAILED
                    } catch (e: SecurityException) {
                        _status.value = ObdStatus.NEEDS_PERMISSION
                    } catch (e: IllegalArgumentException) {
                        _status.value = ObdStatus.FAILED // a malformed saved address
                    } finally {
                        closeSocket()
                    }
                }
            }
            _reading.value = ObdReading()
            delay(backoff)
            backoff = (backoff * 2).coerceAtMost(MAX_RETRY_MS)
        }
    }

    @SuppressLint("MissingPermission")
    private fun connect(adapter: BluetoothAdapter, address: String): BluetoothSocket {
        val device = adapter.getRemoteDevice(address)
        try {
            adapter.cancelDiscovery() // discovery slows the connection down a lot
        } catch (e: SecurityException) {
            // Android 12+ wants BLUETOOTH_SCAN for this; carry on without it
        }
        val secure = device.createRfcommSocketToServiceRecord(SPP)
        return try {
            secure.connect()
            secure
        } catch (e: IOException) {
            runCatching { secure.close() }
            // Many cheap adapters only accept an insecure connection.
            device.createInsecureRfcommSocketToServiceRecord(SPP).also { it.connect() }
        }
    }

    /** Sets the adapter up, then polls the car until the connection drops. */
    private suspend fun CoroutineScope.talk(s: BluetoothSocket) {
        ObdProtocol.SETUP.forEach { command(s, it, if (it == "ATZ") RESET_TIMEOUT_MS else TIMEOUT_MS) }
        var reading = ObdReading()
        var round = 0
        while (isActive) {
            // Rpm and speed every round; the slow-changing values in turn, one per round.
            val pids = listOf(ObdPid.RPM, ObdPid.SPEED) + SLOW[round % SLOW.size]
            var answered = false
            for (pid in pids) {
                val response = command(s, pid.command, TIMEOUT_MS)
                if (ObdProtocol.isNoConnection(response)) break
                val next = ObdProtocol.apply(reading, pid, response)
                if (next != reading || ObdProtocol.dataBytes(response, pid) != null) answered = true
                reading = next
            }
            if (round % SLOW.size == 0) {
                ObdProtocol.parseVoltage(command(s, ObdProtocol.VOLTAGE, TIMEOUT_MS))?.let { reading = reading.copy(batteryVolts = it) }
            }
            _reading.value = reading
            _status.value = if (answered) ObdStatus.CONNECTED else ObdStatus.NO_CAR
            round++
            delay(if (answered) POLL_MS else NO_CAR_RETRY_MS)
        }
    }

    /** Sends one command and reads up to the ELM327's ">" prompt. */
    private suspend fun command(s: BluetoothSocket, cmd: String, timeoutMs: Long): String {
        val output = s.outputStream
        val input = s.inputStream
        output.write("$cmd\r".toByteArray(Charsets.US_ASCII))
        output.flush()
        val text = StringBuilder()
        val buffer = ByteArray(256)
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            // available() rather than a blocking read, so a silent adapter can't hang the loop.
            if (input.available() > 0) {
                val n = input.read(buffer)
                if (n < 0) throw IOException("closed")
                text.append(String(buffer, 0, n, Charsets.US_ASCII))
                if ('>' in text) return text.toString()
            } else {
                delay(READ_POLL_MS)
            }
        }
        return text.toString()
    }

    private fun closeSocket() {
        socket?.let { runCatching { it.close() } }
        socket = null
    }

    companion object {
        private val SPP: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private val SLOW = listOf(ObdPid.COOLANT, ObdPid.FUEL, ObdPid.INTAKE, ObdPid.LOAD)
        private const val TIMEOUT_MS = 2_500L
        private const val RESET_TIMEOUT_MS = 5_000L
        private const val READ_POLL_MS = 15L
        private const val POLL_MS = 250L
        private const val NO_CAR_RETRY_MS = 5_000L
        private const val MIN_RETRY_MS = 5_000L
        private const val MAX_RETRY_MS = 60_000L

        fun looksLikeObd(name: String): Boolean =
            listOf("obd", "elm", "vgate", "vlink", "veepeak", "obdlink", "konnwei", "carista", "icar")
                .any { name.contains(it, ignoreCase = true) }
    }
}
