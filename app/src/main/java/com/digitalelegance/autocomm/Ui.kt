package com.digitalelegance.autocomm

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.digitalelegance.autocomm.ble.PanelConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.roundToInt

val Bg = Color(0xFF0B0B0B)
val TileBg = Color(0xFF1C1C1C)
val Line = Color(0xFF3A3A3A)
val Green = Color(0xFF00E676)
val Yellow = Color(0xFFFFC400)
val Red = Color(0xFFFF5252)
val MicRed = Color(0xFFB00020)
const val OPAQUE = 0xFF000000.toInt()

fun rgb(c: Int) = Color(OPAQUE or c)

val PALETTE = listOf(
    0xFF0000, 0xFF4000, 0xFF8000, 0xFFB000, 0xFFFF00, 0x80FF00, 0x00FF40, 0x00FFB0,
    0x00FFFF, 0x00C8FF, 0x0080FF, 0x0000FF, 0x8000FF, 0xFF00FF, 0xFF40FF, 0xFF0080,
    0xFFFFFF, 0xC0C0C0, 0x808080, 0x404040, 0xFFC0A0, 0x804000, 0x006000, 0x000000,
)

@Composable
fun statusName(conn: PanelConnection?): String? {
    if (conn == null) return null
    val s by conn.status.collectAsStateWithLifecycle()
    return s.name
}

fun statusColor(name: String?): Color = when {
    name == null -> Red
    name == "READY" || name == "BUSY" -> Green
    name.contains("ERROR") || name.contains("DISCONNECT") || name == "IDLE" -> Red
    else -> Yellow
}

/** Plays [frames] as a dot-matrix LED panel. */
@Composable
fun LedPreview(frames: Frames?, cols: Int, rows: Int, modifier: Modifier = Modifier) {
    val c = frames?.w ?: cols
    val r = frames?.h ?: rows
    var idx by remember(frames) { mutableIntStateOf(0) }
    LaunchedEffect(frames) {
        if (frames == null || frames.count <= 1) return@LaunchedEffect
        while (true) {
            delay(frames.delays.getOrElse(idx) { 100 }.toLong())
            idx = (idx + 1) % frames.count
        }
    }
    val dim = Color(0xFF1E1E1E)
    Canvas(
        modifier
            .fillMaxWidth()
            .aspectRatio(c / r.toFloat())
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black)
            .border(1.dp, Line, RoundedCornerShape(8.dp))
            .padding(6.dp)
    ) {
        val cell = min(size.width / c, size.height / r)
        val x0 = (size.width - cell * c) / 2f
        val y0 = (size.height - cell * r) / 2f
        val rad = cell * 0.4f
        val px = frames?.pixels?.getOrNull(idx)
        for (y in 0 until r) for (x in 0 until c) {
            val v = px?.getOrNull(y * c + x) ?: 0
            drawCircle(if (v ushr 24 != 0) Color(v) else dim, rad, Offset(x0 + x * cell + cell / 2, y0 + y * cell + cell / 2))
        }
    }
}

/** Renders a text style preview (async). */
@Composable
fun StylePreview(text: String, style: TextStyle, w: Int, h: Int, modifier: Modifier = Modifier) {
    var frames by remember { mutableStateOf<Frames?>(null) }
    LaunchedEffect(text, style, w, h) {
        frames = withContext(Dispatchers.Default) { TextFx.render(text, style, w, h) }
    }
    LedPreview(frames, w, h, modifier)
}

/** Crisp pixel image for thumbnails. */
@Composable
fun PixelImage(px: IntArray, w: Int, h: Int, modifier: Modifier = Modifier) {
    val bmp = remember(px, w, h) { Media.thumbnail(px, w, h).asImageBitmap() }
    Image(bmp, null, modifier, contentScale = ContentScale.Fit, filterQuality = FilterQuality.None)
}

@Composable
fun SectionTitle(text: String) {
    Text(text, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 8.dp))
}

val FrontRed = Color(0xFFE53935)
val BothPurple = Color(0xFF9C27B0)
val RearBlue = Color(0xFF1E88E5)

fun targetColor(t: Target) = when (t) { Target.FRONT -> FrontRed; Target.BOTH -> BothPurple; Target.REAR -> RearBlue }

@Composable
fun TargetSelector(selected: Target, onSelect: (Target) -> Unit, modifier: Modifier = Modifier, height: Int = 56) {
    Row(modifier.fillMaxWidth().height(height.dp).clip(CircleShape).border(2.dp, Color.Gray, CircleShape)) {
        listOf(Target.FRONT, Target.BOTH, Target.REAR).forEachIndexed { i, t ->
            if (i > 0) Box(Modifier.fillMaxHeight().width(2.dp).background(Color.Gray))
            val sel = selected == t
            Box(
                Modifier.weight(1f).fillMaxHeight().background(if (sel) targetColor(t) else Color.Transparent).clickable { onSelect(t) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    t.name.lowercase().replaceFirstChar { it.uppercase() },
                    color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/** "Display" label + Front/Both/Rear selector, as in the studio sketches. */
@Composable
fun DisplayRow(selected: Target, onSelect: (Target) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Display", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        TargetSelector(selected, onSelect, Modifier.weight(1f), height = 44)
    }
}

/** Same-size pill: red left half = front only, blue right half = rear only, purple = both. */
@Composable
fun TargetPill(target: Target?, modifier: Modifier = Modifier) {
    Canvas(modifier.size(52.dp, 22.dp)) {
        val r = androidx.compose.ui.geometry.CornerRadius(size.height / 2)
        val half = size.width / 2
        when (target) {
            Target.BOTH -> drawRoundRect(BothPurple, cornerRadius = r)
            Target.FRONT -> clipRect(right = half) { drawRoundRect(FrontRed, cornerRadius = r) }
            Target.REAR -> clipRect(left = half) { drawRoundRect(RearBlue, cornerRadius = r) }
            null -> Unit
        }
        drawRoundRect(Color.Gray, cornerRadius = r, style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
    }
}

/** Small dot in the panel color: red = front, blue = rear, purple = both. */
@Composable
fun TargetDot(target: Target, modifier: Modifier = Modifier, size: Int = 12) {
    Box(modifier.size(size.dp).background(targetColor(target), CircleShape))
}

@Composable
fun <T> DropdownField(label: String, options: List<T>, selected: T, name: (T) -> String, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, fontSize = 17.sp, modifier = Modifier.width(120.dp))
        Box(Modifier.weight(1f)) {
            OutlinedButton(onClick = { open = true }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                Text(name(selected), Modifier.weight(1f), color = Color.White)
                Icon(Fa.AngleDown, null, Modifier.size(16.dp), tint = Color.White)
            }
            DropdownMenu(open, { open = false }) {
                options.forEach { o -> DropdownMenuItem(text = { Text(name(o)) }, onClick = { onSelect(o); open = false }) }
            }
        }
    }
}

@Composable
fun FaButton(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, enabled: Boolean = true, selected: Boolean = false, size: Int = 48, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier.size(size.dp).clip(shape)
            .background(if (selected) Color.White else TileBg)
            .border(1.dp, if (selected) Color.White else Line, shape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, desc, Modifier.size((size * 0.45f).dp), tint = when { !enabled -> Color(0xFF4A4A4A); selected -> Color.Black; else -> Color.White })
    }
}

@Composable
fun ColorSwatch(label: String, color: Int, onChange: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { open = true }.padding(4.dp)) {
        Box(Modifier.size(32.dp).background(rgb(color), CircleShape).border(2.dp, Color.White, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(label, color = Color.White)
    }
    if (open) ColorPickerDialog(color, onPick = { onChange(it); open = false }, onDismiss = { open = false })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPickerDialog(initial: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val start = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(OPAQUE or initial, it) } }
    var hue by remember { mutableFloatStateOf(start[0]) }
    var sat by remember { mutableFloatStateOf(start[1]) }
    var value by remember { mutableFloatStateOf(start[2]) }
    val cur = android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value)) and 0xFFFFFF
    var hex by remember(cur) { mutableStateOf("%06X".format(cur)) }
    fun set(c: Int) {
        val f = FloatArray(3); android.graphics.Color.colorToHSV(OPAQUE or c, f)
        hue = f[0]; sat = f[1]; value = f[2]
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pick a color") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PALETTE.forEach { c ->
                        Box(
                            Modifier.size(36.dp).background(rgb(c), CircleShape)
                                .border(if (c == cur) 3.dp else 1.dp, if (c == cur) Color.White else Line, CircleShape)
                                .clickable { set(c) }
                        )
                    }
                }
                LabeledSlider("Hue", hue, 0f..360f, { "${it.roundToInt()}°" }) { hue = it }
                LabeledSlider("Saturation", sat, 0f..1f, { "${(it * 100).roundToInt()}%" }) { sat = it }
                LabeledSlider("Value", value, 0f..1f, { "${(it * 100).roundToInt()}%" }) { value = it }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp).background(rgb(cur), RoundedCornerShape(8.dp)).border(1.dp, Color.White, RoundedCornerShape(8.dp)))
                    Spacer(Modifier.width(12.dp))
                    OutlinedTextField(
                        value = hex,
                        onValueChange = { v ->
                            hex = v.uppercase().filter { it in "0123456789ABCDEF" }.take(6)
                            if (hex.length == 6) set(hex.toInt(16))
                        },
                        singleLine = true,
                        label = { Text("Hex") },
                        modifier = Modifier.width(140.dp)
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(cur) }) { Text("PICK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL") } },
    )
}

@Composable
fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, fmt: (Float) -> String, steps: Int = 0, onChange: (Float) -> Unit) {
    Column {
        Row {
            Text(label, color = Color.White, modifier = Modifier.weight(1f))
            Text(fmt(value), color = Color.Gray)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range, steps = steps)
    }
}

/** Text-style editor with live preview. [middle] is placed under the color pickers (e.g. the text field). */
@Composable
fun StyleEditor(
    style: TextStyle, sample: String, w: Int, h: Int, onChange: (TextStyle) -> Unit,
    showBrightness: Boolean = true,
    top: (@Composable () -> Unit)? = null,
    middle: (@Composable () -> Unit)? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        top?.invoke()
        StylePreview(sample.ifBlank { "Sample 😀" }, style, w, h)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            ColorSwatch("Text", style.color) { onChange(style.copy(color = it)) }
            ColorSwatch("Background", style.bg) { onChange(style.copy(bg = it)) }
            ColorSwatch("Border", style.borderColor) { onChange(style.copy(borderColor = it)) }
        }
        middle?.invoke()
        DropdownField("Animation", Effect.entries, style.effect, { it.label }) { onChange(style.copy(effect = it)) }
        DropdownField("Font", Font.entries, style.font, { it.label }) { onChange(style.copy(font = it)) }
        DropdownField("Border", BorderStyle.entries, style.border, { it.label }) { onChange(style.copy(border = it)) }
        // Auto, then 8–18 px
        val pos = if (style.fontSize <= 0) 0f else (style.fontSize - 7).coerceIn(1, 11).toFloat()
        LabeledSlider("Font Size", pos, 0f..11f, { if (it < 0.5f) "Auto" else "${it.roundToInt() + 7} px" }, steps = 10) {
            val p = it.roundToInt()
            onChange(style.copy(fontSize = if (p == 0) 0 else p + 7))
        }
        if (TextFx.canWrap(style.fontSize, h)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Word wrap", color = Color.White, fontSize = 17.sp)
                    Text("Fit more words on one screen before scrolling", color = Color.Gray, fontSize = 13.sp)
                }
                Switch(checked = style.wrap, onCheckedChange = { onChange(style.copy(wrap = it)) })
            }
        }
        LabeledSlider("Speed", style.speed.toFloat(), 1f..10f, { "${it.roundToInt()}" }, steps = 8) {
            onChange(style.copy(speed = it.roundToInt()))
        }
        if (showBrightness) LabeledSlider("Brightness", style.brightness.toFloat(), 5f..100f, { "${it.roundToInt()}%" }) {
            onChange(style.copy(brightness = it.roundToInt()))
        }
    }
}

/** Live panel preview of any message. */
@Composable
fun MessagePreview(vm: AutoCommViewModel, m: Message?, w: Int, h: Int, modifier: Modifier = Modifier) {
    var frames by remember(m) { mutableStateOf<Frames?>(null) }
    LaunchedEffect(m, w, h) {
        frames = if (m == null) null else withContext(Dispatchers.Default) { vm.framesFor(m, w, h) }
    }
    LedPreview(frames, w, h, modifier)
}

/** Small visual summary of a message (thumbnail for images/animations, colored label for text). */
@Composable
fun MessageFace(m: Message?, modifier: Modifier = Modifier, fontSize: Int = 18) {
    Box(modifier, contentAlignment = Alignment.Center) {
        when {
            m == null -> Text("?", color = Line, fontSize = fontSize.sp, fontWeight = FontWeight.Bold)
            !m.isText && m.frames.isNotEmpty() -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PixelImage(m.frames.first(), m.w, m.h, Modifier.fillMaxWidth(0.9f).aspectRatio(m.w / m.h.toFloat()))
                Text(m.name, color = Color.Gray, fontSize = 12.sp, maxLines = 1)
            }
            else -> Text(
                m.text, color = rgb(m.style.color), fontSize = fontSize.sp, fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 3,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}
