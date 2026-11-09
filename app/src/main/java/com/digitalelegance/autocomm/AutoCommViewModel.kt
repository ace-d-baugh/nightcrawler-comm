package com.digitalelegance.autocomm

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.digitalelegance.autocomm.ble.PanelConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class FoundPanel(val name: String, val address: String, val rssi: Int)

/** One entry in a bumper-sticker loop. */
data class StickerItem(val id: String, val seconds: Int = 5)

/** What a panel is (or would be) showing. */
class Shown(val msg: Message, val frames: Frames)

fun newId(): String = UUID.randomUUID().toString()

@SuppressLint("MissingPermission")
class AutoCommViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("autocomm", Context.MODE_PRIVATE)
    private val adapter = app.getSystemService(BluetoothManager::class.java)?.adapter
    private val store = MessageStore(app)

    private val _front = MutableStateFlow<PanelConnection?>(null)
    val front: StateFlow<PanelConnection?> = _front.asStateFlow()
    private val _rear = MutableStateFlow<PanelConnection?>(null)
    val rear: StateFlow<PanelConnection?> = _rear.asStateFlow()

    private val _found = MutableStateFlow<List<FoundPanel>>(emptyList())
    val found: StateFlow<List<FoundPanel>> = _found.asStateFlow()
    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _log = MutableStateFlow("")
    val log: StateFlow<String> = _log.asStateFlow()

    private val amber = TextStyle(color = 0xFFB000, effect = Effect.HOLD)
    val directions = listOf(
        Message("dir0", "LEFT", MsgKind.TEXT, Target.BOTH, "LEFT", amber),
        Message("dir1", "U-TURN", MsgKind.TEXT, Target.BOTH, "U-TURN", amber),
        Message("dir2", "RIGHT", MsgKind.TEXT, Target.BOTH, "RIGHT", amber),
    )

    private val defaultQuick = listOf(
        "THANK YOU" to TextStyle(color = 0x00FF40),
        "SORRY!" to TextStyle(color = 0xFFB000),
        "GO AHEAD" to TextStyle(color = 0x00FF40),
        "BACK OFF" to TextStyle(color = 0xFF0000, effect = Effect.BLINK),
        "NICE CAR!" to TextStyle(color = 0x00C8FF),
        "HAZARD AHEAD" to TextStyle(color = 0xFFB000),
        "LIGHTS ON" to TextStyle(color = 0xFFFFFF),
        "HAVE A MAGICAL DAY ✨" to TextStyle(color = 0xFF40FF, effect = Effect.RAINBOW),
        "HELLO!" to TextStyle(color = 0x00C8FF, effect = Effect.LASER),
    )

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _quick = MutableStateFlow<List<String?>>(List(QUICK_SLOTS) { null })
    val quick: StateFlow<List<String?>> = _quick.asStateFlow()

    private val _stickers = MutableStateFlow<Map<String, List<StickerItem>>>(emptyMap())
    val stickers: StateFlow<Map<String, List<StickerItem>>> = _stickers.asStateFlow()

    private val _voiceStyle = MutableStateFlow(
        prefs.getString("voice_style", null)?.let { runCatching { TextStyle.fromJson(JSONObject(it)) }.getOrNull() }
            ?: TextStyle(color = 0x00FF40)
    )
    val voiceStyle: StateFlow<TextStyle> = _voiceStyle.asStateFlow()

    private val _active = MutableStateFlow<String?>(null)
    val active: StateFlow<String?> = _active.asStateFlow()

    private val _power = MutableStateFlow(true)
    val power: StateFlow<Boolean> = _power.asStateFlow()

    private val _shown = MutableStateFlow<Map<String, Shown>>(emptyMap())
    val shown: StateFlow<Map<String, Shown>> = _shown.asStateFlow()

    private val _names = MutableStateFlow(
        mapOf(
            "front" to (prefs.getString("name_front", null) ?: "Front"),
            "rear" to (prefs.getString("name_rear", null) ?: "Rear"),
        )
    )
    val names: StateFlow<Map<String, String>> = _names.asStateFlow()

    private var started = false
    private var activeTarget: Target? = null
    private val readyJobs = mutableMapOf<String, Job>()
    private val pushJobs = mutableMapOf<String, Job>()

    init {
        migrate()
        _messages.value = store.list()
        _quick.value = runCatching {
            val a = JSONArray(prefs.getString("quick_v3", "[]"))
            (0 until a.length()).map { a.optString(it).ifEmpty { null } }
        }.getOrDefault(emptyList()).let { (it + List(QUICK_SLOTS) { null }).take(QUICK_SLOTS) }
        _stickers.value = listOf("front", "rear").associateWith { role ->
            prefs.getString("stickers_v4_$role", null)?.let { raw ->
                runCatching {
                    val a = JSONArray(raw)
                    (0 until a.length()).map { a.getJSONObject(it) }.map { StickerItem(it.getString("id"), it.optInt("sec", 5)) }
                }.getOrNull()
            } ?: listOfNotNull(prefs.getString("sticker_v3_$role", null)?.let { StickerItem(it) })
        }
    }

    // ---------- persistence / migration ----------

    private fun migrate() {
        if (prefs.getBoolean("v3", false)) return
        val created = ArrayList<Message>()
        store.legacyDesigns().forEach { store.save(it); created += it }
        fun textMsg(label: String, style: TextStyle) =
            Message(newId(), label, MsgKind.TEXT, Target.BOTH, label, style).also { store.save(it); created += it }

        fun fromOld(o: JSONObject?): String? {
            if (o == null) return null
            val did = if (o.has("designId") && !o.isNull("designId")) o.getString("designId") else null
            val label = o.optString("label", "")
            return when {
                did != null && created.any { it.id == did } -> did
                label.isNotBlank() -> textMsg(label, TextStyle.fromJson(o.optJSONObject("style"))).id
                else -> null
            }
        }

        val old = prefs.getString("customs_v2", null)?.let { runCatching { JSONArray(it) }.getOrNull() }
        val slots: List<String?> = old?.let { a -> (0 until a.length()).map { fromOld(a.optJSONObject(it)) } }
            ?: defaultQuick.map { (l, s) -> textMsg(l, s).id }

        var defaultSticker: String? = null
        val edit = prefs.edit()
        listOf("front", "rear").forEach { role ->
            val raw = prefs.getString("sticker_v2_$role", null)
            val id = if (raw != null) fromOld(runCatching { JSONObject(raw) }.getOrNull())
            else defaultSticker ?: textMsg("NIGHTCRAWLER", TextStyle(color = 0x00C8FF)).id.also { defaultSticker = it }
            edit.putString("sticker_v3_$role", id)
        }
        edit.putString("quick_v3", JSONArray().apply { (slots + List(QUICK_SLOTS) { null }).take(QUICK_SLOTS).forEach { put(it ?: "") } }.toString())
        edit.putBoolean("v3", true).apply()
    }

    private fun saveQuick(list: List<String?>) {
        val l = (list + List(QUICK_SLOTS) { null }).take(QUICK_SLOTS)
        prefs.edit().putString("quick_v3", JSONArray().apply { l.forEach { put(it ?: "") } }.toString()).apply()
        _quick.value = l
    }

    fun setQuick(index: Int, id: String?) = saveQuick(_quick.value.toMutableList().also { it[index] = id })

    fun moveQuick(from: Int, to: Int) {
        if (to !in 0 until QUICK_SLOTS) return
        saveQuick(_quick.value.toMutableList().also { val t = it[from]; it[from] = it[to]; it[to] = t })
        _active.value = null
    }

    /** Remove from quick responses (not from saved); everything below moves up. */
    fun removeQuick(index: Int) {
        saveQuick(_quick.value.toMutableList().also { it.removeAt(index); it.add(null) })
        _active.value = null
    }

    private fun saveStickers(role: String, list: List<StickerItem>) {
        prefs.edit().putString("stickers_v4_$role", JSONArray().apply {
            list.forEach { put(JSONObject().put("id", it.id).put("sec", it.seconds)) }
        }.toString()).apply()
        _stickers.update { it + (role to list) }
        val busy = _active.value != null && activeTarget?.let { role in roles(it) } == true
        if (!busy) showSticker(role)
    }

    private fun editStickers(role: String, f: (MutableList<StickerItem>) -> Unit) =
        saveStickers(role, (_stickers.value[role] ?: emptyList()).toMutableList().also(f))

    fun addStickerItem(role: String, id: String) = editStickers(role) { it.add(StickerItem(id)) }
    fun replaceStickerItem(role: String, index: Int, id: String) = editStickers(role) { if (index in it.indices) it[index] = it[index].copy(id = id) }
    fun removeStickerItem(role: String, index: Int) = editStickers(role) { if (index in it.indices) it.removeAt(index) }
    fun moveStickerItem(role: String, from: Int, to: Int) = editStickers(role) {
        if (from in it.indices && to in it.indices) { val t = it[from]; it[from] = it[to]; it[to] = t }
    }
    fun setStickerSeconds(role: String, index: Int, seconds: Int) = editStickers(role) {
        if (index in it.indices) it[index] = it[index].copy(seconds = seconds.coerceIn(1, 120))
    }
    fun clearSticker(role: String) = saveStickers(role, emptyList())
    fun syncStickers() = saveStickers("rear", _stickers.value["front"] ?: emptyList())
    fun clearStickers() { clearSticker("front"); clearSticker("rear") }

    /** All sticker messages for [role], played back to back, each for its time. */
    fun stickerFrames(items: List<StickerItem>, w: Int, h: Int): Frames? {
        val list = items.mapNotNull { i -> message(i.id)?.let { it to i.seconds } }
        if (list.isEmpty()) return null
        if (list.size == 1) return framesFor(list[0].first, w, h)
        val px = ArrayList<IntArray>()
        val d = ArrayList<Int>()
        list.forEach { (m, sec) ->
            val f = framesFor(m, w, h)
            val ms = sec * 1000
            if (f.count == 1) { px += f.pixels[0]; d += ms }
            else {
                val cycle = f.delays.sum().coerceAtLeast(1)
                val reps = (ms / cycle).coerceIn(1, maxOf(1, 400 / f.count))
                repeat(reps) { px.addAll(f.pixels); d.addAll(f.delays) }
            }
        }
        return Frames(w, h, px, d)
    }

    fun setVoiceStyle(s: TextStyle) {
        prefs.edit().putString("voice_style", s.toJson().toString()).apply()
        _voiceStyle.value = s
    }

    fun message(id: String?): Message? = id?.let { i -> _messages.value.firstOrNull { it.id == i } }

    fun saveMessage(m: Message): Message {
        store.save(m)
        _messages.update { l -> if (l.any { it.id == m.id }) l.map { if (it.id == m.id) m else it } else l + m }
        _shown.value.forEach { (role, s) ->
            if (s.msg.id == m.id) show(role, m)
            else if (s.msg.id == "sticker:$role" && _stickers.value[role].orEmpty().any { it.id == m.id }) showSticker(role)
        }
        return m
    }

    fun deleteMessage(id: String) {
        store.delete(id)
        _messages.update { l -> l.filterNot { it.id == id } }
        saveQuick(_quick.value.filterNot { it == id })
        _stickers.value.forEach { (role, list) -> if (list.any { it.id == id }) saveStickers(role, list.filterNot { it.id == id }) }
    }

    /** Canvas size for the studio: connected panel size, else last known. */
    fun canvasSize(): Pair<Int, Int> {
        listOfNotNull(_front.value, _rear.value).firstOrNull { it.width > 0 && it.height > 0 }?.let { return it.width to it.height }
        return prefs.getInt("dims_w", 64) to prefs.getInt("dims_h", 16)
    }

    private fun dims(role: String): Pair<Int, Int> {
        val p = conn(role)
        return if (p != null && p.width > 0 && p.height > 0) p.width to p.height
        else prefs.getInt("dims_w_$role", prefs.getInt("dims_w", 64)) to prefs.getInt("dims_h_$role", prefs.getInt("dims_h", 16))
    }

    // ---------- connections ----------

    fun onPermissionsGranted() {
        if (started) return
        started = true
        prefs.getString("front", null)?.let { attach("front", it) }
        prefs.getString("rear", null)?.let { attach("rear", it) }
        if (_shown.value.isEmpty()) listOf("front", "rear").forEach { showSticker(it) }
    }

    private fun attach(role: String, address: String) {
        val conn = PanelConnection(getApplication(), address, role)
        if (role == "front") {
            _front.value?.disconnect(); _front.value = conn
        } else {
            _rear.value?.disconnect(); _rear.value = conn
        }
        conn.connect()
        readyJobs[role]?.cancel()
        readyJobs[role] = viewModelScope.launch {
            conn.status.first { it == PanelConnection.Status.READY }
            if (conn.width > 0 && conn.height > 0) {
                prefs.edit()
                    .putInt("dims_w", conn.width).putInt("dims_h", conn.height)
                    .putInt("dims_w_$role", conn.width).putInt("dims_h_$role", conn.height)
                    .apply()
            }
            if (!_power.value) return@launch
            val s = _shown.value[role]
            if (s != null && !s.msg.id.startsWith("sticker:")) show(role, s.msg) else showSticker(role)
        }
    }

    private fun conn(role: String) = if (role == "front") _front.value else _rear.value

    fun assign(role: String, address: String) {
        stopScan()
        prefs.edit().putString(role, address).apply()
        attach(role, address)
    }

    fun reconnect(role: String) {
        prefs.getString(role, null)?.let { attach(role, it) } ?: run { _log.value = "$role panel not set up" }
    }

    fun rename(role: String, name: String) {
        val n = name.trim().ifEmpty { role.replaceFirstChar { it.uppercase() } }
        prefs.edit().putString("name_$role", n).apply()
        _names.update { it + (role to n) }
    }

    fun forget(role: String) {
        prefs.edit().remove(role).apply()
        readyJobs.remove(role)?.cancel()
        if (role == "front") { _front.value?.disconnect(); _front.value = null }
        else { _rear.value?.disconnect(); _rear.value = null }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = result.scanRecord?.deviceName ?: result.device.name ?: return
            val item = FoundPanel(name, result.device.address, result.rssi)
            val list = _found.value.filterNot { it.address == item.address } + item
            _found.value = list.sortedWith(compareByDescending<FoundPanel> { it.name.startsWith("P0") }.thenByDescending { it.rssi })
        }
    }

    fun startScan() {
        val scanner = adapter?.bluetoothLeScanner ?: run { _log.value = "Bluetooth is off"; return }
        if (_scanning.value) return
        _found.value = emptyList()
        _scanning.value = true
        scanner.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), scanCallback)
        viewModelScope.launch {
            delay(12_000)
            stopScan()
        }
    }

    fun stopScan() {
        if (!_scanning.value) return
        adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        _scanning.value = false
    }

    // ---------- display ----------

    private fun roles(t: Target): List<String> = when (t) {
        Target.FRONT -> listOf("front")
        Target.REAR -> listOf("rear")
        Target.BOTH -> listOf("front", "rear")
    }

    /** Render a message at a given panel size. */
    fun framesFor(m: Message, w: Int, h: Int): Frames {
        when (m.id) {
            "dir0" -> return TextFx.arrow(TextFx.Arrow.LEFT, m.style, w, h)
            "dir1" -> return TextFx.arrow(TextFx.Arrow.UTURN, m.style, w, h)
            "dir2" -> return TextFx.arrow(TextFx.Arrow.RIGHT, m.style, w, h)
        }
        val f = m.toFrames()?.fitTo(w, h)
        return when {
            m.kind == MsgKind.TEXT || f == null -> TextFx.render(m.text, m.style, w, h)
            m.kind == MsgKind.IMAGE -> TextFx.renderImage(f.pixels[0], w, h, m.style)
            else -> f.brightness(m.style.brightness)
        }
    }

    /** Render [m] for [role], update the preview, and push to the panel if it's ready. */
    private fun show(role: String, m: Message, frames: Frames? = null, produce: ((Int, Int) -> Frames?)? = null) {
        pushJobs[role]?.cancel()
        pushJobs[role] = viewModelScope.launch {
            val (w, h) = dims(role)
            // Always render at this panel's exact size — never rescale (scaling thins 2-px lines).
            val f = frames?.takeIf { it.w == w && it.h == h }
                ?: withContext(Dispatchers.Default) { if (produce != null) produce(w, h) else framesFor(m, w, h) }
                ?: run { _shown.update { it - role }; return@launch }
            _shown.update { it + (role to Shown(m, f)) }
            if (!_power.value) return@launch
            queueUpload(role, f, m.name)
        }
    }

    // ---------- uploads: one at a time per panel, never cut off mid-transfer ----------

    /** "Off" = stay connected but show nothing (an all-black image). The panel's own off command restarts it. */
    private fun blankPanel(role: String) {
        val (w, h) = dims(role)
        queueUpload(role, Frames(w, h, listOf(IntArray(w * h)), listOf(1000)), BLANK)
    }

    private val pendingUpload = mutableMapOf<String, Pair<Frames, String>>()
    private val uploading = mutableSetOf<String>()

    /** Upload [f] to [role]. If an upload is running, the newest request goes next (older ones are dropped). */
    private fun queueUpload(role: String, f: Frames, name: String) {
        pendingUpload[role] = f to name
        if (role in uploading) return
        uploading += role
        viewModelScope.launch {
            try {
                while (true) {
                    val (frames, label) = pendingUpload.remove(role) ?: break
                    if (!_power.value && label != BLANK) continue
                    val p = conn(role)
                    if (p == null) { _log.value = "${_names.value[role]}: not connected (preview only)"; continue }
                    // wait for the panel to be free (up to 20 s)
                    val ready = withTimeoutOrNull(20_000) { p.status.first { it == PanelConnection.Status.READY } } != null
                    if (!ready) { _log.value = "${_names.value[role]}: not ready (${p.status.value.name})"; continue }
                    if (pendingUpload.containsKey(role)) continue // something newer arrived while waiting
                    if (label != BLANK) _log.value = "${_names.value[role]}: sending \"$label\"…"
                    // Preview stays readable; the front panel itself gets the mirror image.
                    val ok = withContext(NonCancellable) {
                        val (bytes, gif) = withContext(Dispatchers.Default) { (if (role == "front") frames.mirrored() else frames).encode() }
                        p.upload(bytes, gif)
                    }
                    _log.value = "${_names.value[role]}: ${if (ok) (if (label == BLANK) "off" else "showing \"$label\"") else "upload failed"}"
                }
            } finally {
                uploading -= role
            }
        }
    }

    private fun showSticker(role: String) {
        val items = _stickers.value[role].orEmpty().filter { message(it.id) != null }
        if (items.isEmpty()) {
            pushJobs[role]?.cancel()
            _shown.update { it - role }
            if (_power.value) blankPanel(role)
            return
        }
        val names = items.mapNotNull { message(it.id)?.name }.joinToString(" · ")
        show(role, Message("sticker:$role", names, MsgKind.ANIMATION)) { w, h -> stickerFrames(items, w, h) }
    }

    fun toggleQuick(index: Int) {
        val m = message(_quick.value.getOrNull(index)) ?: return
        toggle("q$index", m, m.target)
    }

    fun toggle(key: String, m: Message, target: Target) {
        if (_active.value == key) {
            val t = activeTarget ?: target
            _active.value = null
            activeTarget = null
            roles(t).forEach { showSticker(it) }
        } else {
            display(key, m, target)
        }
    }

    /** Show on [target]; any panel not targeted goes back to its bumper sticker. */
    fun display(key: String, m: Message, target: Target, frames: Frames? = null) {
        _active.value = key
        activeTarget = target
        _power.value = true
        roles(target).forEach { show(it, m, frames) }
        (roles(Target.BOTH) - roles(target).toSet()).forEach { role ->
            if (_shown.value[role]?.msg?.id != "sticker:$role") showSticker(role)
        }
    }

    fun sendStudio(m: Message, frames: Frames? = null) = display(STUDIO_ID, m, m.target, frames)

    fun sendVoice(raw: String, target: Target) {
        viewModelScope.launch {
            val text = aiProcess(raw)
            if (text.isNotEmpty()) display(VOICE_ID, Message(VOICE_ID, text, MsgKind.TEXT, target, text, _voiceStyle.value), target)
        }
    }

    // TODO: replace with LLM call (shorten/clean phrasing for the panel).
    private suspend fun aiProcess(raw: String): String = withContext(Dispatchers.Default) {
        raw.trim().replace(Regex("\\s+"), " ").trimEnd('.', ',').uppercase()
    }

    fun togglePower() {
        if (_power.value) {
            _power.value = false
            listOf("front", "rear").forEach { role -> pushJobs[role]?.cancel(); blankPanel(role) }
        } else {
            _power.value = true
            listOf("front", "rear").forEach { role ->
                val s = _shown.value[role]
                if (s != null && !s.msg.id.startsWith("sticker:")) show(role, s.msg) else showSticker(role)
            }
        }
    }

    override fun onCleared() {
        stopScan()
        _front.value?.disconnect()
        _rear.value?.disconnect()
    }

    companion object {
        const val VOICE_ID = "voice"
        const val STUDIO_ID = "studio"
        private const val BLANK = "\u0000off"
    }
}
