package com.digitalelegance.autocomm

import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private enum class Tool { DRAW, ERASE, FILL, PICK }

/**
 * Design Studio. [initial] is a message being edited (or null for new);
 * [onSaved] runs after a save (e.g. to set a bumper sticker or quick-response slot).
 */
@Composable
fun StudioScreen(vm: AutoCommViewModel, initial: Message?, onSaved: ((Message) -> Unit)?, onBack: () -> Unit) {
    var tab by remember { mutableIntStateOf(initial?.kind?.ordinal ?: 0) }
    val (w, h) = remember { vm.canvasSize() }

    Column(Modifier.fillMaxSize().background(Bg).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TopBar("Design Studio", onBack)
        TabRow(selectedTabIndex = tab, containerColor = Bg) {
            MsgKind.entries.forEachIndexed { i, k ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(k.label, fontSize = 18.sp) })
            }
        }
        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> TextStudio(vm, w, h, initial?.takeIf { it.kind == MsgKind.TEXT }, onSaved)
                1 -> PixelStudio(vm, w, h, false, initial?.takeIf { it.kind == MsgKind.IMAGE }, onSaved)
                else -> PixelStudio(vm, w, h, true, initial?.takeIf { it.kind == MsgKind.ANIMATION }, onSaved)
            }
        }
    }
}

// ---------------- Text ----------------

@Composable
private fun TextStudio(vm: AutoCommViewModel, w: Int, h: Int, initial: Message?, onSaved: ((Message) -> Unit)?) {
    var text by remember { mutableStateOf(initial?.text ?: "HELLO 👋") }
    var style by remember { mutableStateOf(initial?.style ?: TextStyle(effect = Effect.LASER)) }
    var target by remember { mutableStateOf(initial?.target ?: Target.BOTH) }
    var id by remember { mutableStateOf(initial?.id) }
    var notified by remember { mutableStateOf(initial?.id) }
    var note by remember { mutableStateOf("") }

    fun build() = Message(id ?: newId(), text.trim().take(40), MsgKind.TEXT, target, text, style)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        StyleEditor(
            style, text, w, h, { style = it },
            showBrightness = false,
            top = { DisplayRow(target) { target = it } },
            middle = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = text, onValueChange = { text = it }, modifier = Modifier.weight(1f),
                        label = { Text("Text") }, singleLine = true
                    )
                    FaButton(Fa.PaperPlane, "Send", enabled = text.isNotBlank(), size = 56) {
                        vm.sendStudio(build()); note = "Sent"
                    }
                    FaButton(Fa.FloppyDisk, "Save", enabled = text.isNotBlank(), size = 56) {
                        val m = vm.saveMessage(build())
                        id = m.id
                        if (m.id != notified) { notified = m.id; onSaved?.invoke(m) }
                        note = "Saved to Saved Messages"
                    }
                }
                if (note.isNotEmpty()) Text(note, color = Green)
            },
        )
        Spacer(Modifier.height(24.dp))
    }
}

// ---------------- Image / Animation ----------------

@Composable
private fun PixelStudio(vm: AutoCommViewModel, w: Int, h: Int, animation: Boolean, initial: Message?, onSaved: ((Message) -> Unit)?) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val messages by vm.messages.collectAsStateWithLifecycle()
    val kind = if (animation) MsgKind.ANIMATION else MsgKind.IMAGE

    val start = remember { initial?.toFrames()?.fitTo(w, h) }
    val frames = remember { mutableStateListOf<IntArray>().apply { addAll(start?.pixels?.map { it.copyOf() } ?: listOf(IntArray(w * h))) } }
    val delays = remember { mutableStateListOf<Int>().apply { addAll(start?.delays ?: listOf(120)) } }
    var cur by remember { mutableIntStateOf(0) }
    var tool by remember { mutableStateOf(Tool.DRAW) }
    var color by remember { mutableIntStateOf(0x00FF40) }
    var picking by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf(initial?.name ?: if (animation) "My Animation" else "My Image") }
    var target by remember { mutableStateOf(initial?.target ?: Target.BOTH) }
    var id by remember { mutableStateOf(initial?.id) }
    var notified by remember { mutableStateOf(initial?.id) }
    var style by remember { mutableStateOf(initial?.style ?: TextStyle(effect = Effect.HOLD)) }
    var playing by remember { mutableStateOf(false) }
    var ghost by remember { mutableStateOf(true) }
    var media by remember { mutableStateOf<SourceMedia?>(null) }
    var trimming by remember { mutableStateOf<SourceMedia?>(null) }
    var openMenu by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    val undo = remember { mutableStateListOf<Pair<Int, IntArray>>() }
    val redo = remember { mutableStateListOf<Pair<Int, IntArray>>() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) scope.launch {
            note = "Loading…"
            val m = withContext(Dispatchers.IO) { Media.load(ctx, uri, animation) }
            note = when {
                m == null -> "Couldn't open that file"
                m.truncated -> "Only the first ${Media.MAX_DECODE_FRAMES} frames of that GIF were loaded"
                else -> ""
            }
            if (m != null) { if (m.frames.size > MAX_ANIM_FRAMES) trimming = m else media = m }
        }
    }

    fun pushUndo() {
        undo.add(cur to frames[cur].copyOf())
        while (undo.size > 50) undo.removeAt(0)
        redo.clear()
    }

    fun resetHistory() { undo.clear(); redo.clear() }

    fun drawAt(x: Int, y: Int, startStroke: Boolean) {
        if (cur !in frames.indices) return
        val px = frames[cur]
        val i = y * w + x
        val c = OPAQUE or color
        when (tool) {
            Tool.DRAW -> { if (startStroke) pushUndo(); if (px[i] != c) frames[cur] = px.copyOf().also { it[i] = c } }
            Tool.ERASE -> { if (startStroke) pushUndo(); if (px[i] != 0) frames[cur] = px.copyOf().also { it[i] = 0 } }
            Tool.FILL -> if (startStroke) { pushUndo(); frames[cur] = floodFill(px, w, h, x, y, c) }
            Tool.PICK -> if (startStroke && px[i] ushr 24 != 0) { color = px[i] and 0xFFFFFF; tool = Tool.DRAW }
        }
    }

    fun currentFrames() = Frames(w, h, frames.toList(), delays.toList())

    fun build() = Message(id ?: newId(), name.ifBlank { kind.label }, kind, target, style = style, w = w, h = h,
        frames = frames.map { it.copyOf() }, delays = delays.toList())

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Name + send + save
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Name") }, modifier = Modifier.weight(1f))
            FaButton(Fa.PaperPlane, "Send", size = 56) { vm.sendStudio(build()); note = "Sent" }
            FaButton(Fa.FloppyDisk, "Save", size = 56) {
                val m = vm.saveMessage(build())
                id = m.id
                if (m.id != notified) { notified = m.id; onSaved?.invoke(m) }
                note = "Saved \"${m.name}\" to Saved Messages"
            }
        }
        if (note.isNotEmpty()) Text(note, color = Green)

        DisplayRow(target) { target = it }

        // LED panel: draw directly on it
        if (playing) {
            val preview = remember(frames.toList(), delays.toList(), style) {
                if (animation) currentFrames() else TextFx.renderImage(frames[0], w, h, style)
            }
            LedPreview(preview, w, h)
        }
        else PixelEditor(
            frames.getOrElse(cur) { IntArray(w * h) }, w, h,
            ghost = if (animation && ghost && cur > 0) frames[cur - 1] else null,
            onDraw = ::drawAt
        )

        // Tools
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FaButton(Fa.Pencil, "Draw", selected = tool == Tool.DRAW) { tool = Tool.DRAW }
            FaButton(Fa.Eraser, "Erase", selected = tool == Tool.ERASE) { tool = Tool.ERASE }
            FaButton(Fa.FillDrip, "Fill", selected = tool == Tool.FILL) { tool = Tool.FILL }
            FaButton(Fa.EyeDropper, "Pick color", selected = tool == Tool.PICK) { tool = Tool.PICK }
            FaButton(Fa.Undo, "Undo", enabled = undo.isNotEmpty()) {
                val (i, px) = undo.removeAt(undo.lastIndex)
                if (i in frames.indices) { redo.add(i to frames[i].copyOf()); frames[i] = px; cur = i }
            }
            FaButton(Fa.Redo, "Redo", enabled = redo.isNotEmpty()) {
                val (i, px) = redo.removeAt(redo.lastIndex)
                if (i in frames.indices) { undo.add(i to frames[i].copyOf()); frames[i] = px; cur = i }
            }
            FaButton(Fa.CircleXmark, "Clear") { pushUndo(); frames[cur] = IntArray(w * h) }
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(rgb(color)).border(3.dp, Color.White, CircleShape)
                    .clickable { picking = true }
            )
        }

        // File / playback
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTextButton(Fa.Upload, "Upload") { picker.launch("image/*") }
            Box {
                IconTextButton(Fa.FolderOpen, "Open") { openMenu = true }
                DropdownMenu(openMenu, { openMenu = false }) {
                    val mine = messages.filter { it.kind == kind }
                    if (mine.isEmpty()) DropdownMenuItem(text = { Text("Nothing saved yet") }, onClick = { openMenu = false })
                    mine.forEach { m ->
                        DropdownMenuItem(text = { Text(m.name) }, onClick = {
                            openMenu = false
                            val f = m.toFrames()?.fitTo(w, h) ?: return@DropdownMenuItem
                            frames.clear(); frames.addAll(f.pixels.map { it.copyOf() })
                            delays.clear(); delays.addAll(f.delays)
                            cur = 0; name = m.name; target = m.target; id = m.id; resetHistory()
                        })
                    }
                }
            }
            IconTextButton(if (playing) Fa.Stop else Fa.Play, if (playing) "Stop" else "Play") { playing = !playing }
            if (animation) IconTextButton(Fa.Ghost, "Ghost", selected = ghost) { ghost = !ghost }
        }

        if (!animation) {
            DropdownField("Animation", TextFx.imageEffects, style.effect, { it.label }) { style = style.copy(effect = it) }
            LabeledSlider("Speed", style.speed.toFloat(), 1f..10f, { "${it.roundToInt()}" }, steps = 8) {
                style = style.copy(speed = it.roundToInt())
            }
        }

        if (animation) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Frame ${cur + 1} of ${frames.size}", color = Color.White, fontWeight = FontWeight.Bold)
                Text("Speed", color = Color.Gray)
                Slider(
                    value = delays.getOrElse(cur) { 120 }.toFloat(),
                    onValueChange = { if (cur in delays.indices) delays[cur] = (it / 10f).roundToInt() * 10 },
                    valueRange = 40f..1000f, steps = 95, modifier = Modifier.weight(1f)
                )
                Text("${delays.getOrElse(cur) { 120 }} ms", color = Color.Gray, modifier = Modifier.width(64.dp))
            }
            frames.forEachIndexed { i, px ->
                val sel = i == cur
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${i + 1}", Modifier.width(28.dp), color = if (sel) Color.White else Color.Gray, fontWeight = FontWeight.Bold)
                    PixelImage(
                        px, w, h,
                        Modifier.weight(1f).aspectRatio(w / h.toFloat())
                            .border(if (sel) 4.dp else 1.dp, if (sel) Color.White else Line)
                            .clickable { cur = i; playing = false }
                    )
                    val full = frames.size >= MAX_ANIM_FRAMES
                    FaButton(Fa.Plus, "Add frame below", enabled = !full, size = 44) {
                        frames.add(i + 1, IntArray(w * h)); delays.add(i + 1, delays[i]); cur = i + 1; resetHistory()
                    }
                    FaButton(Fa.Copy, "Duplicate below", enabled = !full, size = 44) {
                        frames.add(i + 1, frames[i].copyOf()); delays.add(i + 1, delays[i]); cur = i + 1; resetHistory()
                    }
                    FaButton(Fa.TrashCan, "Delete frame", enabled = frames.size > 1, size = 44) {
                        frames.removeAt(i); delays.removeAt(i); cur = min(cur, frames.lastIndex); resetHistory()
                    }
                    FaButton(Fa.AngleUp, "Move up", enabled = i > 0, size = 44) {
                        frames.add(i - 1, frames.removeAt(i)); delays.add(i - 1, delays.removeAt(i)); cur = i - 1; resetHistory()
                    }
                    FaButton(Fa.AngleDown, "Move down", enabled = i < frames.lastIndex, size = 44) {
                        frames.add(i + 1, frames.removeAt(i)); delays.add(i + 1, delays.removeAt(i)); cur = i + 1; resetHistory()
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (picking) ColorPickerDialog(color, onPick = { color = it; picking = false; if (tool != Tool.FILL) tool = Tool.DRAW }, onDismiss = { picking = false })

    trimming?.let { m ->
        TrimDialog(m, onDismiss = { trimming = null }) { from, to ->
            trimming = null
            media = SourceMedia(m.frames.subList(from, to + 1), m.delays.subList(from, to + 1))
        }
    }

    media?.let { m ->
        FramingDialog(m, w, h, onDismiss = { media = null }) { framed ->
            resetHistory()
            val n = if (animation) MAX_ANIM_FRAMES else 1
            val list = framed.take(n)
            frames.clear(); frames.addAll(list)
            delays.clear()
            delays.addAll(List(list.size) { i ->
                if (animation && m.frames.size > 1) ((m.delays.getOrElse(i) { 120 } / 10f).roundToInt() * 10).coerceIn(40, 1000) else 120
            })
            cur = 0
            media = null
        }
    }
}

@Composable
private fun IconTextButton(icon: ImageVector, label: String, selected: Boolean = false, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        colors = if (selected) ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = Color.Black)
        else ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
    ) {
        Icon(icon, null, Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

/** Tap / drag directly on the LED dot grid to draw. */
@Composable
private fun PixelEditor(px: IntArray, w: Int, h: Int, ghost: IntArray?, onDraw: (Int, Int, Boolean) -> Unit) {
    val draw by rememberUpdatedState(onDraw)
    Canvas(
        Modifier
            .fillMaxWidth()
            .aspectRatio(w / h.toFloat())
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black)
            .border(1.dp, Line, RoundedCornerShape(8.dp))
            .padding(6.dp)
            .pointerInput(w, h) {
                fun cellAt(o: Offset): Pair<Int, Int>? {
                    val cell = min(size.width / w.toFloat(), size.height / h.toFloat())
                    val x0 = (size.width - cell * w) / 2f
                    val y0 = (size.height - cell * h) / 2f
                    val x = ((o.x - x0) / cell).toInt()
                    val y = ((o.y - y0) / cell).toInt()
                    return if (o.x >= x0 && o.y >= y0 && x in 0 until w && y in 0 until h) x to y else null
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    cellAt(down.position)?.let { (x, y) -> draw(x, y, true) }
                    down.consume()
                    var last = cellAt(down.position)
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) break
                        val c = cellAt(ch.position)
                        if (c != null && c != last) { draw(c.first, c.second, false); last = c }
                        ch.consume()
                    }
                }
            }
    ) {
        val cell = min(size.width / w, size.height / h)
        val x0 = (size.width - cell * w) / 2f
        val y0 = (size.height - cell * h) / 2f
        val rad = cell * 0.42f
        val off = Color(0xFF1E1E1E)
        for (y in 0 until h) for (x in 0 until w) {
            val v = px[y * w + x]
            val col = when {
                v ushr 24 != 0 -> Color(v)
                ghost != null && ghost[y * w + x] ushr 24 != 0 -> Color(ghost[y * w + x]).copy(alpha = 0.3f)
                else -> off
            }
            drawCircle(col, rad, Offset(x0 + x * cell + cell / 2, y0 + y * cell + cell / 2))
        }
    }
}

private fun floodFill(src: IntArray, w: Int, h: Int, x: Int, y: Int, c: Int): IntArray {
    val out = src.copyOf()
    val from = src[y * w + x]
    if (from == c) return out
    val stack = ArrayDeque<Int>()
    stack.addLast(y * w + x)
    while (stack.isNotEmpty()) {
        val i = stack.removeLast()
        if (out[i] != from) continue
        out[i] = c
        val px = i % w; val py = i / w
        if (px > 0) stack.addLast(i - 1)
        if (px < w - 1) stack.addLast(i + 1)
        if (py > 0) stack.addLast(i - w)
        if (py < h - 1) stack.addLast(i + w)
    }
    return out
}


/** GIFs longer than 40 frames: preview, play/pause, and pick a start/end (snaps to ≤ 40 frames). */
@Composable
private fun TrimDialog(media: SourceMedia, onDismiss: () -> Unit, onContinue: (Int, Int) -> Unit) {
    val n = media.frames.size
    var inIdx by remember { mutableIntStateOf(0) }
    var outIdx by remember { mutableIntStateOf(min(n, MAX_ANIM_FRAMES) - 1) }
    var shown by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(false) }

    fun timeAt(i: Int): String = "%.2fs".format(media.delays.take(i).sum() / 1000f)

    LaunchedEffect(playing, inIdx, outIdx) {
        if (!playing) return@LaunchedEffect
        if (shown !in inIdx..outIdx) shown = inIdx
        while (true) {
            kotlinx.coroutines.delay(media.delays.getOrElse(shown) { 100 }.toLong())
            shown = if (shown >= outIdx) inIdx else shown + 1
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Bg) {
            Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                TopBar("Trim Animation", onDismiss)
                Text("This GIF has $n frames, but the panel supports up to $MAX_ANIM_FRAMES. Choose which part to import — the selection snaps to $MAX_ANIM_FRAMES frames automatically.", color = Color.Gray)
                val bmp = media.frames[shown.coerceIn(0, n - 1)]
                Box(
                    Modifier.fillMaxWidth().heightIn(max = 360.dp).aspectRatio(bmp.width / bmp.height.toFloat())
                        .clip(RoundedCornerShape(8.dp)).background(Color.Black).border(1.dp, Line, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.Image(
                        bmp.asImageBitmap(), null, Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FaButton(if (playing) Fa.Stop else Fa.Play, if (playing) "Pause" else "Play", selected = playing, size = 56) { playing = !playing }
                    Text("Frame ${shown + 1} of $n", color = Color.White, fontWeight = FontWeight.Bold)
                }
                LabeledSlider("Start", inIdx.toFloat(), 0f..(n - 1).toFloat(), { "Frame ${it.roundToInt() + 1} · ${timeAt(it.roundToInt())}" }) {
                    val i = it.roundToInt()
                    inIdx = i
                    if (outIdx < i) outIdx = i
                    if (outIdx - i + 1 > MAX_ANIM_FRAMES) outIdx = i + MAX_ANIM_FRAMES - 1
                    playing = false; shown = i
                }
                LabeledSlider("End", outIdx.toFloat(), 0f..(n - 1).toFloat(), { "Frame ${it.roundToInt() + 1} · ${timeAt(it.roundToInt() + 1)}" }) {
                    val o = it.roundToInt()
                    outIdx = o
                    if (inIdx > o) inIdx = o
                    if (o - inIdx + 1 > MAX_ANIM_FRAMES) inIdx = o - MAX_ANIM_FRAMES + 1
                    playing = false; shown = o
                }
                Text("Selected ${outIdx - inIdx + 1} of $n frames · ${timeAt(inIdx)} – ${timeAt(outIdx + 1)}", color = Green)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(onClick = { playing = false; onContinue(inIdx, outIdx) }) { Text("Continue") }
                }
            }
        }
    }
}

/**
 * Position & Fit: drag to move, pinch / slider to zoom (10–400 %, 100 % = fills the frame),
 * twist / slider / ±90° to rotate. Only what's inside the frame is kept; applies to every frame.
 */
@Composable
private fun FramingDialog(media: SourceMedia, w: Int, h: Int, onDismiss: () -> Unit, onApply: (List<IntArray>) -> Unit) {
    val src = media.frames.first()
    val sw = src.width.toFloat()
    val sh = src.height.toFloat()
    val cover = max(w / sw, h / sh)
    var zoomPct by remember { mutableFloatStateOf(100f) }
    var rot by remember { mutableFloatStateOf(0f) }
    var cx by remember { mutableFloatStateOf(w / 2f) }
    var cy by remember { mutableFloatStateOf(h / 2f) }
    var box by remember { mutableStateOf(IntSize.Zero) }

    fun norm(d: Float): Float { var x = (d + 180f) % 360f; if (x < 0) x += 360f; return x - 180f }
    fun reset() { zoomPct = 100f; rot = 0f; cx = w / 2f; cy = h / 2f }
    fun zoom() = cover * zoomPct / 100f

    // frame geometry in screen px: (viewScale, left, top)
    fun geom(): Triple<Float, Float, Float> {
        val bw = box.width.toFloat(); val bh = box.height.toFloat()
        val fw = min(bw * 0.8f, bh * 0.8f * w / h)
        val vs = fw / w
        return Triple(vs, (bw - fw) / 2f, (bh - h * vs) / 2f)
    }

    val preview = remember(zoomPct, rot, cx, cy) {
        Frames(w, h, listOf(Media.frame(src, w, h, cx, cy, zoom(), rot)), listOf(100))
    }
    val paint = remember { Paint(Paint.FILTER_BITMAP_FLAG) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Bg) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TopBar("Position & Fit", onDismiss)
                Text(
                    if (media.frames.size > 1) "Drag, zoom and rotate apply to all ${media.frames.size} frames together."
                    else "Drag the image to move it. It doesn't have to fill the frame — zoom out to leave empty space.",
                    color = Color.Gray
                )
                Box(
                    Modifier.fillMaxWidth().weight(1f).clipToBounds().background(Color(0xFF06070A))
                        .onSizeChanged { box = it }
                        .pointerInput(Unit) {
                            detectTransformGestures { centroid, pan, gZoom, gRot ->
                                val (vs, fl, ft) = geom()
                                if (vs <= 0f) return@detectTransformGestures
                                val px = (centroid.x - fl) / vs
                                val py = (centroid.y - ft) / vs
                                val nz = (zoomPct * gZoom).coerceIn(10f, 400f)
                                val z = nz / zoomPct
                                cx = px + (cx - px) * z + pan.x / vs
                                cy = py + (cy - py) * z + pan.y / vs
                                zoomPct = nz
                                rot = norm(rot + gRot)
                            }
                        }
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        val (vs, fl, ft) = geom()
                        if (vs <= 0f) return@Canvas
                        val m = Matrix().apply {
                            setTranslate(-sw / 2f, -sh / 2f)
                            postScale(zoom() * vs, zoom() * vs)
                            postRotate(rot)
                            postTranslate(fl + cx * vs, ft + cy * vs)
                        }
                        drawContext.canvas.nativeCanvas.drawBitmap(src, m, paint)
                        val fw = w * vs; val fh = h * vs
                        val shade = Color(0xFF06070A).copy(alpha = 0.72f)
                        drawRect(shade, Offset.Zero, Size(size.width, ft))
                        drawRect(shade, Offset(0f, ft + fh), Size(size.width, size.height - ft - fh))
                        drawRect(shade, Offset(0f, ft), Size(fl, fh))
                        drawRect(shade, Offset(fl + fw, ft), Size(size.width - fl - fw, fh))
                        drawRect(Color(0xFF3FA9FF), Offset(fl, ft), Size(fw, fh), style = Stroke(3f))
                    }
                }
                LabeledSlider("Zoom", zoomPct, 10f..400f, { "${it.roundToInt()}%" }) { zoomPct = it.roundToInt().toFloat() }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FaButton(Fa.Undo, "Rotate left 90°", size = 44) { rot = norm(rot - 90f) }
                    Box(Modifier.weight(1f)) {
                        LabeledSlider("Rotate", rot, -180f..180f, { "${it.roundToInt()}°" }) { rot = it.roundToInt().toFloat() }
                    }
                    FaButton(Fa.Redo, "Rotate right 90°", size = 44) { rot = norm(rot + 90f) }
                }
                Text("Panel preview", color = Color.Gray)
                LedPreview(preview, w, h)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { reset() }) { Text("Reset") }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(onClick = { onApply(media.frames.map { Media.frame(it, w, h, cx, cy, zoom(), rot) }) }) {
                        Text(if (media.frames.size > 1) "Apply to ${media.frames.size} Frames" else "Apply")
                    }
                }
            }
        }
    }
}
