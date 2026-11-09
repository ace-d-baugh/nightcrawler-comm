package com.digitalelegance.autocomm

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.UTurnLeft
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.digitalelegance.autocomm.ble.PanelConnection

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = Bg, surface = Bg)) {
                Surface(Modifier.fillMaxSize(), color = Bg) { AutoCommApp() }
            }
        }
    }
}

private fun requiredPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

private enum class Screen { MAIN, SETTINGS, PANELS, VOICE, STICKERS, QUICK, SAVED, STUDIO }

private class StudioRequest(val msg: Message?, val back: Screen, val onSaved: ((Message) -> Unit)?)

@Composable
fun AutoCommApp(vm: AutoCommViewModel = viewModel()) {
    val ctx = LocalContext.current
    var screen by remember { mutableStateOf(Screen.MAIN) }
    var studio by remember { mutableStateOf(StudioRequest(null, Screen.SETTINGS, null)) }
    var scanning by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf<String?>(null) }
    val names by vm.names.collectAsStateWithLifecycle()

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.all { it }) vm.onPermissionsGranted()
    }
    LaunchedEffect(Unit) {
        val perms = requiredPermissions()
        if (perms.all { ctx.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }) vm.onPermissionsGranted()
        else permLauncher.launch(perms)
    }

    val onFind: (String) -> Unit = { scanning = it; vm.startScan() }
    val onRename: (String) -> Unit = { renaming = it }
    fun openStudio(msg: Message?, back: Screen, onSaved: ((Message) -> Unit)? = null) {
        studio = StudioRequest(msg, back, onSaved); screen = Screen.STUDIO
    }
    val back: () -> Unit = {
        screen = when (screen) {
            Screen.STUDIO -> studio.back
            Screen.SETTINGS -> Screen.MAIN
            else -> Screen.SETTINGS
        }
    }

    BackHandler(enabled = screen != Screen.MAIN, onBack = back)

    when (screen) {
        Screen.MAIN -> MainScreen(vm, onFind, onRename) { screen = Screen.SETTINGS }
        Screen.SETTINGS -> SettingsHome(onOpen = { p ->
            screen = when (p) {
                SettingsPage.PANELS -> Screen.PANELS
                SettingsPage.VOICE -> Screen.VOICE
                SettingsPage.STICKERS -> Screen.STICKERS
                SettingsPage.QUICK -> Screen.QUICK
                SettingsPage.SAVED -> Screen.SAVED
                SettingsPage.STUDIO -> { studio = StudioRequest(null, Screen.SETTINGS, null); Screen.STUDIO }
            }
        }, onBack = back)
        Screen.PANELS -> PanelsPage(vm, onFind, onRename, back)
        Screen.VOICE -> VoicePage(vm, back)
        Screen.STICKERS -> StickersPage(
            vm,
            onEditItem = { role, i, m -> openStudio(m, Screen.STICKERS) { vm.replaceStickerItem(role, i, it.id) } },
            onNew = { role -> openStudio(null, Screen.STICKERS) { vm.addStickerItem(role, it.id) } },
            onBack = back,
        )
        Screen.QUICK -> QuickPage(vm, onEdit = { i, m -> openStudio(m, Screen.QUICK) { vm.setQuick(i, it.id) } }, onBack = back)
        Screen.SAVED -> SavedPage(vm, onEdit = { openStudio(it, Screen.SAVED) }, onNew = { openStudio(null, Screen.SAVED) }, onBack = back)
        Screen.STUDIO -> key(studio) { StudioScreen(vm, studio.msg, studio.onSaved, onBack = back) }
    }

    scanning?.let { role -> ScanDialog(vm, role) { scanning = null; vm.stopScan() } }
    renaming?.let { role ->
        RenameDialog(names[role] ?: role, onSave = { vm.rename(role, it); renaming = null }, onDismiss = { renaming = null })
    }
}

@Composable
private fun MainScreen(vm: AutoCommViewModel, onFind: (String) -> Unit, onRename: (String) -> Unit, onSettings: () -> Unit) {
    val ctx = LocalContext.current
    val front by vm.front.collectAsStateWithLifecycle()
    val rear by vm.rear.collectAsStateWithLifecycle()
    val log by vm.log.collectAsStateWithLifecycle()
    val quick by vm.quick.collectAsStateWithLifecycle()
    val messages by vm.messages.collectAsStateWithLifecycle()
    val active by vm.active.collectAsStateWithLifecycle()
    val power by vm.power.collectAsStateWithLifecycle()
    val shown by vm.shown.collectAsStateWithLifecycle()
    val names by vm.names.collectAsStateWithLifecycle()

    var target by remember { mutableStateOf(Target.BOTH) }
    var showVoice by remember { mutableStateOf(false) }

    val micPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) showVoice = true
    }

    Column(
        Modifier.fillMaxSize().background(Bg).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PanelPill(names["front"] ?: "Front", front, dotFirst = false, { onFind("front") }, { onRename("front") }, { vm.reconnect("front") }, { vm.forget("front") })
            Text(
                "AutoComm", Modifier.weight(1f), textAlign = TextAlign.Center,
                fontSize = 26.sp, fontWeight = FontWeight.Black, color = Color.White
            )
            PanelPill(names["rear"] ?: "Rear", rear, dotFirst = true, { onFind("rear") }, { onRename("rear") }, { vm.reconnect("rear") }, { vm.forget("rear") })
        }

        val (dw, dh) = remember(front, rear) { vm.canvasSize() }
        val frontFrames = if (power) shown["front"]?.frames else null
        val rearFrames = if (power) shown["rear"]?.frames else null
        when (target) {
            Target.FRONT -> LedPreview(frontFrames, dw, dh)
            Target.REAR -> LedPreview(rearFrames, dw, dh)
            Target.BOTH -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LedPreview(frontFrames, dw, dh, Modifier.weight(1f))
                LedPreview(rearFrames, dw, dh, Modifier.weight(1f))
            }
        }

        TargetSelector(target, { target = it })

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val dirIcons = listOf(Fa.ArrowLeft, Icons.Filled.UTurnLeft, Fa.ArrowRight)
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                vm.directions.forEachIndexed { i, m ->
                    val id = m.id
                    val t = if (i == 1) Target.BOTH else target
                    Tile(active == id, rgb(m.style.color), true, { vm.toggle(id, m, t) }, Modifier.weight(1f)) {
                        Icon(dirIcons[i], null, Modifier.size(if (i == 1) 56.dp else 44.dp), tint = if (active == id) rgb(m.style.color) else Color.White)
                    }
                }
            }
            quick.chunked(3).forEachIndexed { r, row ->
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEachIndexed { c, qid ->
                        val idx = r * 3 + c
                        val key = "q$idx"
                        val m = messages.firstOrNull { it.id == qid }
                        val accent = if (m == null || !m.isText) Color.White else rgb(m.style.color)
                        Tile(active == key, accent, m != null, { vm.toggleQuick(idx) }, Modifier.weight(1f)) {
                            MessageFace(m, Modifier.fillMaxSize().padding(6.dp))
                            if (m != null) TargetDot(m.target, Modifier.align(Alignment.TopEnd).padding(8.dp))
                        }
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().height(120.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            RoundIcon(
                Fa.PowerOff, "Power",
                tint = if (power) Color.White else Color(0xFF5A5A5A), glow = power,
                size = 80.dp, onClick = { vm.togglePower() }
            )
            Box(
                Modifier
                    .size(112.dp)
                    .shadow(12.dp, CircleShape, ambientColor = MicRed, spotColor = MicRed)
                    .clip(CircleShape)
                    .background(MicRed)
                    .border(3.dp, Color.White.copy(alpha = 0.85f), CircleShape)
                    .clickable {
                        if (ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) showVoice = true
                        else micPerm.launch(Manifest.permission.RECORD_AUDIO)
                    },
                contentAlignment = Alignment.Center
            ) { Icon(Fa.Microphone, "Microphone", Modifier.size(52.dp), tint = Color.White) }
            RoundIcon(Fa.Gear, "Settings", tint = Color.White, glow = false, size = 80.dp, onClick = onSettings)
        }
    }

    if (showVoice) VoiceDialog(
        onSend = { vm.sendVoice(it, target); showVoice = false },
        onDismiss = { showVoice = false }
    )
}

@Composable
private fun PanelPill(
    name: String,
    conn: PanelConnection?,
    dotFirst: Boolean,
    onFind: () -> Unit,
    onRename: () -> Unit,
    onConnect: () -> Unit,
    onForget: () -> Unit,
) {
    val c = statusColor(statusName(conn))
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .widthIn(max = 130.dp)
                .clip(CircleShape)
                .background(c.copy(alpha = 0.12f))
                .border(2.dp, c, CircleShape)
                .clickable { menu = true }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val dot = @Composable { Box(Modifier.size(10.dp).background(c, CircleShape)) }
            if (dotFirst) { dot(); Spacer(Modifier.width(6.dp)) }
            Text(name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!dotFirst) { Spacer(Modifier.width(6.dp)); dot() }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Find") }, onClick = { menu = false; onFind() })
            DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; onRename() })
            DropdownMenuItem(text = { Text("Connect") }, onClick = { menu = false; onConnect() })
            DropdownMenuItem(text = { Text("Forget") }, enabled = conn != null, onClick = { menu = false; onForget() })
        }
    }
}

@Composable
private fun Tile(
    active: Boolean,
    color: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .fillMaxHeight()
            .then(if (active) Modifier.shadow(16.dp, shape, ambientColor = color, spotColor = color) else Modifier)
            .clip(shape)
            .background(if (active) color.copy(alpha = 0.22f) else TileBg)
            .border(if (active) 3.dp else 1.dp, if (active) color else Line, shape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
private fun RoundIcon(icon: ImageVector, desc: String, tint: Color, glow: Boolean, size: Dp, onClick: () -> Unit) {
    Box(
        Modifier
            .size(size)
            .drawBehind {
                if (glow) drawCircle(
                    Brush.radialGradient(listOf(Color.White.copy(alpha = 0.45f), Color.Transparent), center, this.size.minDimension / 2f),
                    radius = this.size.minDimension / 2f
                )
            }
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, desc, Modifier.size(size * 0.6f), tint = tint) }
}

@Composable
private fun LabeledPreview(label: String, frames: Frames?, cols: Int, rows: Int, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        LedPreview(frames, cols, rows)
        Text(
            "CURRENT DISPLAY · $label", Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
            color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun VoiceDialog(onSend: (String) -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var text by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var session by remember { mutableIntStateOf(0) }

    DisposableEffect(session) {
        text = ""
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
            status = "Speech recognition not available"
            return@DisposableEffect onDispose { }
        }
        val sr = SpeechRecognizer.createSpeechRecognizer(ctx)
        sr.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { status = "Listening…" }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { status = "Processing…" }
            override fun onError(error: Int) { status = if (text.isBlank()) "Didn't catch that — tap Redo" else "" }
            override fun onResults(results: Bundle?) {
                results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { text = it }
                status = ""
            }
            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    ?.takeIf { it.isNotBlank() }?.let { text = it }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        sr.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        )
        onDispose { sr.destroy() }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = Color(0xFF151515), border = BorderStroke(1.dp, Line)) {
            Column(Modifier.padding(24.dp).widthIn(min = 420.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text(
                    "AutoComm", Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                    fontSize = 30.sp, fontWeight = FontWeight.Black, color = Color.White
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
                    textStyle = MaterialTheme.typography.headlineSmall,
                    placeholder = { Text(status.ifEmpty { "…" }, color = Color.Gray) },
                )
                if (status.isNotEmpty() && text.isNotEmpty()) Text(status, color = Color.Gray, fontSize = 14.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    OutlinedButton(onClick = { session++ }, Modifier.height(64.dp).width(160.dp), shape = CircleShape) {
                        Text("Redo", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { onSend(text) }, Modifier.height(64.dp).width(160.dp), enabled = text.isNotBlank(), shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                    ) { Text("Send", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
private fun RenameDialog(current: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename panel") },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onSave(name) }) { Text("SAVE") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL") } },
    )
}

@Composable
private fun ScanDialog(vm: AutoCommViewModel, role: String, onDismiss: () -> Unit) {
    val found by vm.found.collectAsStateWithLifecycle()
    val scanning by vm.scanning.collectAsStateWithLifecycle()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Find ${role.uppercase()} panel") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (scanning) Text("Scanning… power on one panel at a time.", color = Color.Gray)
                if (found.isEmpty() && !scanning) Text("Nothing found.")
                found.forEach { f ->
                    TextButton(onClick = { vm.assign(role, f.address); onDismiss() }) {
                        Text("${f.name}   ${f.address}   ${f.rssi} dBm")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { vm.startScan() }) { Text("RESCAN") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL") } },
    )
}
