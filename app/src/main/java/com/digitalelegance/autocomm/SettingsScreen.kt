package com.digitalelegance.autocomm

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.digitalelegance.autocomm.ble.PanelConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun TopBar(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Fa.ArrowLeft, "Back", Modifier.size(22.dp), tint = Color.White) }
        Text(title, Modifier.weight(1f), fontSize = 26.sp, fontWeight = FontWeight.Black, color = Color.White)
        actions()
    }
}

@Composable
private fun Page(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().background(Bg).padding(16.dp)) {
        TopBar(title, onBack, actions)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            content()
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ---------------- Settings home ----------------

enum class SettingsPage(val title: String, val subtitle: String) {
    PANELS("Panels", "Find, rename, connect or forget the front and rear panels"),
    VOICE("Voice Message Display", "How voice messages look on the panels"),
    STICKERS("Bumper Stickers", "What each panel shows when nothing else is on it"),
    QUICK("Quick Responses", "The 9 buttons on the main screen"),
    SAVED("Saved Messages", "All your saved text, images and animations"),
    STUDIO("Design Studio", "Create text, image and animation messages"),
}

@Composable
fun SettingsHome(onOpen: (SettingsPage) -> Unit, onBack: () -> Unit) {
    val icons: Map<SettingsPage, ImageVector> = mapOf(
        SettingsPage.PANELS to Fa.PowerOff,
        SettingsPage.VOICE to Fa.Microphone,
        SettingsPage.STICKERS to Fa.Shuffle,
        SettingsPage.QUICK to Fa.Pencil,
        SettingsPage.SAVED to Fa.FloppyDisk,
        SettingsPage.STUDIO to Fa.FillDrip,
    )
    Page("Settings", onBack) {
        SettingsPage.entries.forEach { p ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(TileBg)
                    .border(1.dp, Line, RoundedCornerShape(16.dp)).clickable { onOpen(p) }.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icons.getValue(p), null, Modifier.size(28.dp), tint = Color.White)
                Spacer(Modifier.width(20.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(p.subtitle, color = Color.Gray, fontSize = 14.sp)
                }
                Icon(Fa.AngleRight, null, Modifier.size(18.dp), tint = Color.Gray)
            }
        }
    }
}

// ---------------- Panels ----------------

@Composable
fun PanelsPage(vm: AutoCommViewModel, onFind: (String) -> Unit, onRename: (String) -> Unit, onBack: () -> Unit) {
    val front by vm.front.collectAsStateWithLifecycle()
    val rear by vm.rear.collectAsStateWithLifecycle()
    val names by vm.names.collectAsStateWithLifecycle()
    Page("Panels", onBack) {
        listOf("front" to front, "rear" to rear).forEach { (role, conn) ->
            ConnectionCard(names[role] ?: role, role, conn, onFind, onRename, vm)
        }
    }
}

@Composable
private fun ConnectionCard(
    name: String,
    role: String,
    conn: PanelConnection?,
    onFind: (String) -> Unit,
    onRename: (String) -> Unit,
    vm: AutoCommViewModel,
) {
    val st = statusName(conn)
    val info = conn?.info?.collectAsStateWithLifecycle()?.value ?: ""
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(14.dp).background(statusColor(st), CircleShape))
                Spacer(Modifier.width(10.dp))
                Text("$name  (${role.uppercase()})", fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.weight(1f))
                Text(st ?: "NOT SET UP", color = Color.Gray)
            }
            if (info.isNotEmpty()) Text(info, color = Color.Gray, fontSize = 12.sp)
            if (conn != null && conn.width > 0) Text("${conn.width}×${conn.height} LEDs", color = Color.Gray, fontSize = 12.sp)
            val mod = Modifier.fillMaxWidth().height(52.dp)
            OutlinedButton(onClick = { onFind(role) }, mod) { Text("Find", fontSize = 17.sp) }
            OutlinedButton(onClick = { onRename(role) }, mod) { Text("Rename", fontSize = 17.sp) }
            OutlinedButton(onClick = { vm.reconnect(role) }, mod) { Text("Connect", fontSize = 17.sp) }
            OutlinedButton(onClick = { vm.forget(role) }, mod, enabled = conn != null) { Text("Forget", fontSize = 17.sp) }
        }
    }
}

// ---------------- Voice ----------------

@Composable
fun VoicePage(vm: AutoCommViewModel, onBack: () -> Unit) {
    val voice by vm.voiceStyle.collectAsStateWithLifecycle()
    val (w, h) = remember { vm.canvasSize() }
    Page("Voice Message Display", onBack) {
        Text("Used for every voice-to-panel message.", color = Color.Gray)
        StyleEditor(voice, "VOICE MESSAGE 🎤", w, h, { vm.setVoiceStyle(it) })
    }
}

// ---------------- Bumper stickers ----------------

@Composable
fun StickersPage(
    vm: AutoCommViewModel,
    onEditItem: (role: String, index: Int, msg: Message) -> Unit,
    onNew: (role: String) -> Unit,
    onBack: () -> Unit,
) {
    val stickers by vm.stickers.collectAsStateWithLifecycle()
    val names by vm.names.collectAsStateWithLifecycle()
    val messages by vm.messages.collectAsStateWithLifecycle()
    val (w, h) = remember { vm.canvasSize() }
    var picking by remember { mutableStateOf<Pair<String, Int?>?>(null) } // role, index to replace (null = add)

    Page("Bumper Stickers", onBack) {
        Text("Each panel loops through its list when nothing else is showing.", color = Color.Gray)
        listOf("front", "rear").forEach { role ->
            val items = stickers[role].orEmpty()
            var frames by remember(role) { mutableStateOf<Frames?>(null) }
            LaunchedEffect(items, messages, w, h) {
                frames = withContext(Dispatchers.Default) { vm.stickerFrames(items, w, h) }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(names[role] ?: role, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.weight(1f))
                        Text(if (items.isEmpty()) "Off" else "${items.size} in loop", color = Color.Gray)
                    }
                    LedPreview(frames, w, h)
                    items.forEachIndexed { i, item ->
                        val m = messages.firstOrNull { it.id == item.id }
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TileBg)
                                .border(1.dp, Line, RoundedCornerShape(12.dp)).padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${i + 1}", Modifier.width(28.dp), color = Color.Gray, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                MessageFace(m, Modifier.weight(1f).height(48.dp), fontSize = 15)
                                Text(m?.kind?.label ?: "", color = Color.Gray, fontSize = 13.sp)
                            }
                            Row(Modifier.padding(start = 28.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FaButton(Fa.AngleUp, "Move up", enabled = i > 0, size = 44) { vm.moveStickerItem(role, i, i - 1) }
                                FaButton(Fa.Pencil, "Edit", enabled = m != null, size = 44) { m?.let { onEditItem(role, i, it) } }
                                FaButton(Fa.Rotate, "Switch", size = 44) { picking = role to i }
                                FaButton(Fa.TrashCan, "Remove", size = 44) { vm.removeStickerItem(role, i) }
                                FaButton(Fa.AngleDown, "Move down", enabled = i < items.lastIndex, size = 44) { vm.moveStickerItem(role, i, i + 1) }
                                Spacer(Modifier.weight(1f))
                                if (items.size > 1) {
                                    FaButton(Fa.Minus, "Shorter", enabled = item.seconds > 1, size = 36) { vm.setStickerSeconds(role, i, item.seconds - 1) }
                                    Text("${item.seconds}s", Modifier.width(40.dp), color = Color.White, textAlign = TextAlign.Center)
                                    FaButton(Fa.Plus, "Longer", enabled = item.seconds < 120, size = 36) { vm.setStickerSeconds(role, i, item.seconds + 1) }
                                }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { picking = role to null }, Modifier.weight(1f).height(52.dp)) {
                            Icon(Fa.Plus, null, Modifier.size(16.dp)); Spacer(Modifier.width(8.dp)); Text("Add saved")
                        }
                        OutlinedButton(onClick = { onNew(role) }, Modifier.weight(1f).height(52.dp)) {
                            Icon(Fa.Pencil, null, Modifier.size(16.dp)); Spacer(Modifier.width(8.dp)); Text("Create new")
                        }
                        OutlinedButton(onClick = { vm.clearSticker(role) }, Modifier.weight(1f).height(52.dp), enabled = items.isNotEmpty()) {
                            Icon(Fa.CircleXmark, null, Modifier.size(16.dp)); Spacer(Modifier.width(8.dp)); Text("Clear")
                        }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { vm.syncStickers() }, Modifier.weight(1f).height(56.dp)) {
                Icon(Fa.Shuffle, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Sync (front → rear)")
            }
            OutlinedButton(onClick = { vm.clearStickers() }, Modifier.weight(1f).height(56.dp)) {
                Icon(Fa.CircleXmark, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Clear both")
            }
        }
    }

    picking?.let { (role, index) ->
        MessagePicker(messages, onDismiss = { picking = null }) { m ->
            if (index == null) vm.addStickerItem(role, m.id) else vm.replaceStickerItem(role, index, m.id)
            picking = null
        }
    }
}

// ---------------- Quick responses ----------------

@Composable
fun QuickPage(vm: AutoCommViewModel, onEdit: (Int, Message) -> Unit, onBack: () -> Unit) {
    val quick by vm.quick.collectAsStateWithLifecycle()
    val messages by vm.messages.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }
    var switching by remember { mutableStateOf<Int?>(null) }

    Page("Quick Responses", onBack, actions = {
        FaButton(Fa.Pencil, "Edit", selected = editing) { editing = !editing }
    }) {
        quick.forEachIndexed { i, id ->
            val m = messages.firstOrNull { it.id == id }
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TileBg).border(1.dp, Line, RoundedCornerShape(12.dp)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}", Modifier.width(28.dp), color = Color.Gray, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    TargetPill(m?.target)
                    Spacer(Modifier.width(14.dp))
                    Text(
                        m?.name ?: "Empty", Modifier.weight(1f), fontSize = 19.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = if (m == null) Color.Gray else if (m.isText) rgb(m.style.color) else Color.White, fontWeight = FontWeight.Bold
                    )
                    if (m != null) Text(m.kind.label, color = Color.Gray, fontSize = 13.sp)
                }
                if (editing) Row(Modifier.padding(start = 42.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FaButton(Fa.AngleUp, "Move up", enabled = i > 0) { vm.moveQuick(i, i - 1) }
                    FaButton(Fa.Pencil, "Edit", enabled = m != null) { m?.let { onEdit(i, it) } }
                    FaButton(Fa.Rotate, "Switch") { switching = i }
                    FaButton(Fa.TrashCan, "Remove", enabled = m != null) { vm.removeQuick(i) }
                    FaButton(Fa.AngleDown, "Move down", enabled = i < QUICK_SLOTS - 1) { vm.moveQuick(i, i + 1) }
                }
            }
        }
    }

    switching?.let { slot ->
        MessagePicker(messages, onDismiss = { switching = null }) { vm.setQuick(slot, it.id); switching = null }
    }
}

@Composable
fun MessagePicker(messages: List<Message>, onDismiss: () -> Unit, onPick: (Message) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose a saved message") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (messages.isEmpty()) Text("Nothing saved yet.", color = Color.Gray)
                messages.forEach { m ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).border(1.dp, Line, RoundedCornerShape(10.dp))
                            .clickable { onPick(m) }.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TargetPill(m.target)
                        Spacer(Modifier.width(10.dp))
                        MessageFace(m, Modifier.width(140.dp).height(48.dp), fontSize = 14)
                        Spacer(Modifier.width(10.dp))
                        Text(m.kind.label, color = Color.Gray, fontSize = 13.sp)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL") } },
    )
}

// ---------------- Saved messages ----------------

@Composable
fun SavedPage(vm: AutoCommViewModel, onEdit: (Message) -> Unit, onNew: () -> Unit, onBack: () -> Unit) {
    val messages by vm.messages.collectAsStateWithLifecycle()
    var deleting by remember { mutableStateOf<Message?>(null) }
    Page("Saved Messages", onBack, actions = { FaButton(Fa.Plus, "New") { onNew() } }) {
        if (messages.isEmpty()) Text("Nothing saved yet. Tap + to make one.", color = Color.Gray)
        MsgKind.entries.forEach { kind ->
            val list = messages.filter { it.kind == kind }
            if (list.isEmpty()) return@forEach
            SectionTitle(kind.label)
            list.forEach { m ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TileBg).border(1.dp, Line, RoundedCornerShape(12.dp)).padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TargetPill(m.target)
                    MessageFace(m, Modifier.weight(1f).height(64.dp), fontSize = 16)
                    FaButton(Fa.Pencil, "Edit") { onEdit(m) }
                    FaButton(Fa.TrashCan, "Delete") { deleting = m }
                }
            }
        }
    }
    deleting?.let { m ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete \"${m.name}\"?") },
            text = { Text("It will also be removed from Quick Responses and Bumper Stickers.") },
            confirmButton = { TextButton(onClick = { vm.deleteMessage(m.id); deleting = null }) { Text("DELETE", color = Red) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("CANCEL") } },
        )
    }
}
