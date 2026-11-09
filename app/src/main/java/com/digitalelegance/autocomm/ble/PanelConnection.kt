package com.digitalelegance.autocomm.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

/** One PixelJoy panel (front or rear). Handles connect, handshake, uploads, auto-reconnect. */
@SuppressLint("MissingPermission")
@Suppress("DEPRECATION")
class PanelConnection(
    private val context: Context,
    val address: String,
    val role: String,
) {
    enum class Status { DISCONNECTED, CONNECTING, HANDSHAKE, READY, BUSY, ERROR }

    private val tag = "AutoComm/$role"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _status = MutableStateFlow(Status.DISCONNECTED)
    val status: StateFlow<Status> = _status.asStateFlow()

    private val _info = MutableStateFlow("")
    val info: StateFlow<String> = _info.asStateFlow()

    var width = 96
        private set
    var height = 16
        private set

    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private val notifications = Channel<ByteArray>(Channel.UNLIMITED)
    private val opMutex = Mutex()
    @Volatile private var pending: CompletableDeferred<Int>? = null
    @Volatile private var wantConnected = false

    fun connect() {
        wantConnected = true
        if (gatt == null) open()
    }

    fun disconnect() {
        wantConnected = false
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        writeChar = null
        _status.value = Status.DISCONNECTED
    }

    private fun open() {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return
        _status.value = Status.CONNECTING
        _info.value = "Connecting…"
        gatt = adapter.getRemoteDevice(address)
            .connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            Log.d(tag, "state=$newState status=$status")
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                scope.launch { setup(g) }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED || status != BluetoothGatt.GATT_SUCCESS) {
                g.close()
                if (gatt == g) gatt = null
                writeChar = null
                pending?.complete(-1)
                _status.value = Status.DISCONNECTED
                _info.value = "Disconnected"
                if (wantConnected) scope.launch {
                    delay(3000)
                    if (wantConnected && gatt == null) open()
                }
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            Log.d(tag, "mtu=$mtu")
            pending?.complete(status)
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            pending?.complete(status)
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            pending?.complete(status)
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            pending?.complete(status)
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            val v = c.value ?: return
            notifications.trySend(v.copyOf())
        }
    }

    /** Runs one GATT operation and waits for its callback. 0 = success, -2 = busy, -3 = timeout. */
    private suspend fun op(timeoutMs: Long = 5000, start: () -> Boolean): Int {
        val d = CompletableDeferred<Int>()
        pending = d
        if (!start()) {
            pending = null
            return -2
        }
        val r = withTimeoutOrNull(timeoutMs) { d.await() } ?: -3
        pending = null
        return r
    }

    private suspend fun setup(g: BluetoothGatt) {
        _status.value = Status.HANDSHAKE
        _info.value = "Discovering…"
        op { g.requestMtu(517) }
        if (op(10_000) { g.discoverServices() } != BluetoothGatt.GATT_SUCCESS) return fail("Service discovery failed")

        // Service 0000E0xx, write 0000A0xx, notify 0000F0xx (xx = model id, e.g. AD for P0AD)
        val svc = g.services.firstOrNull { s ->
            val u = s.uuid.toString().lowercase()
            u.startsWith("0000e0") && u.endsWith(BASE_SUFFIX)
        } ?: return fail("PixelJoy service not found")
        val model = svc.uuid.toString().substring(6, 8)
        val wc = svc.getCharacteristic(uuid16("a0$model")) ?: return fail("Write char missing")
        val nc = svc.getCharacteristic(uuid16("f0$model")) ?: return fail("Notify char missing")
        writeChar = wc

        g.setCharacteristicNotification(nc, true)
        val cccd = nc.getDescriptor(CCCD) ?: return fail("CCCD missing")
        cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        op { g.writeDescriptor(cccd) }

        _info.value = "Handshake…"
        drain()
        if (!write(PixelJoyProtocol.handshake1())) return fail("Handshake write failed")
        val r1 = awaitResponse(0x00, 3000)
        if (r1 == null || r1.size < 10) return fail("No handshake reply")
        if (!write(PixelJoyProtocol.handshake2(r1[8], r1[9]))) return fail("Handshake write failed")
        val r2 = awaitResponse(0x01, 3000) ?: return fail("Handshake rejected")
        if (r2.size > 8 && r2[8].toInt() != 0) return fail("Handshake rejected")

        write(PixelJoyProtocol.queryStatus())
        awaitResponse(0x10, 2000)?.let { s ->
            if (s.size > 5) {
                width = s[4].toInt() and 0xFF
                height = s[5].toInt() and 0xFF
            }
        }
        _info.value = "P0${model.uppercase()} · ${width}x$height"
        _status.value = Status.READY
    }

    private fun fail(msg: String) {
        Log.w(tag, msg)
        _info.value = msg
        _status.value = Status.ERROR
        gatt?.disconnect()
    }

    private suspend fun write(bytes: ByteArray): Boolean {
        val g = gatt ?: return false
        val c = writeChar ?: return false
        repeat(10) {
            c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            c.value = bytes
            when (op(2000) { g.writeCharacteristic(c) }) {
                BluetoothGatt.GATT_SUCCESS -> return true
                -2 -> delay(15) // stack busy, retry
                else -> return false
            }
        }
        return false
    }

    private suspend fun awaitResponse(cmd: Int, timeoutMs: Long): ByteArray? =
        withTimeoutOrNull(timeoutMs) {
            var p: ByteArray
            do { p = notifications.receive() } while (!PixelJoyProtocol.isResponse(p, cmd))
            p
        }

    private fun drain() {
        while (notifications.tryReceive().isSuccess) { /* discard */ }
    }

    suspend fun command(bytes: ByteArray): Boolean = opMutex.withLock {
        if (_status.value != Status.READY) return false
        write(bytes)
    }

    suspend fun displayOff() = command(PixelJoyProtocol.displayOff())
    suspend fun setMode(mode: Int) = command(PixelJoyProtocol.setMode(mode))

    /** Upload a panel-sized PNG (gif=false) or GIF89a (gif=true). */
    suspend fun upload(file: ByteArray, gif: Boolean): Boolean = opMutex.withLock {
        if (_status.value != Status.READY) return false
        _status.value = Status.BUSY
        try {
            val name = PixelJoyProtocol.newTransferName()
            val chunks = PixelJoyProtocol.uploadChunks(name, file)
            _info.value = "Uploading ${file.size / 1024} KB…"
            drain()
            if (!write(PixelJoyProtocol.uploadStart(name))) return false
            awaitResponse(0x09, 3000)
            for (c in chunks) if (!write(c)) return false
            val end = if (gif) PixelJoyProtocol.uploadEndGif(name) else PixelJoyProtocol.uploadEndImage(name)
            if (!write(end)) return false
            val ok = awaitResponse(if (gif) 0x0C else 0x0B, 5000) != null
            _info.value = (if (ok) "Sent" else "No confirm") + " · ${width}x$height"
            ok
        } finally {
            if (_status.value == Status.BUSY) _status.value = Status.READY
        }
    }

    companion object {
        private const val BASE_SUFFIX = "-0000-1000-8000-00805f9b34fb"
        private val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private fun uuid16(hex4: String): UUID = UUID.fromString("0000$hex4$BASE_SUFFIX")
    }
}
