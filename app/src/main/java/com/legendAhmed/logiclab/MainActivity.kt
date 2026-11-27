package com.legendAhmed.logiclab
//
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.legendAhmed.logiclab.ui.theme.Blue
import com.legendAhmed.logiclab.ui.theme.Brand
import com.legendAhmed.logiclab.ui.theme.Danger
import com.legendAhmed.logiclab.ui.theme.LampOn
import com.legendAhmed.logiclab.ui.theme.LogicLabTheme
import com.legendAhmed.logiclab.ui.theme.Pal
import com.legendAhmed.logiclab.ui.theme.SegOn
import com.legendAhmed.logiclab.ui.theme.Violet
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.max

/* ============================ Activity ============================ */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = Store(this)
        setContent { App(store) }
    }
}

@Composable
fun App(store: Store) {
    val sysDark = isSystemInDarkTheme()
    var themePref by remember { mutableStateOf(store.darkPref()) }

    // If the user toggles Dark Mode from the phone's system settings while the app is open,
    // reset the manual override so the app immediately follows the phone's new theme.
    LaunchedEffect(sysDark) {
        if (themePref != -1) {
            themePref = -1
        }
    }

    val dark = when (themePref) {
        1 -> true
        0 -> false
        else -> sysDark
    }
    val pal = remember(dark) { Pal(dark) }
    val app = remember { AppState() }
    val circuit = remember { Circuit() }
    val history = remember { History() }

    remember { // first launch: seed the project list with the demos
        if (store.names().isEmpty()) {
            listOf("SR latch" to "SR", "Full adder" to "FULLADD", "Half adder" to "HALF").forEach { (n, k) ->
                val c = Circuit()
                buildDemo(c, k)
                store.save(n, c.serialize())
            }
        }
        true
    }

    val toggleTheme = {
        val nextDark = !dark
        themePref = if (nextDark) 1 else 0
        store.setDark(nextDark)
    }

    LogicLabTheme(dark = dark) {
        Box(Modifier.fillMaxSize().background(pal.bg)) {
            if (app.screen == "home") HomeScreen(app, circuit, history, store, pal, toggleTheme)
            else EditorScreen(app, circuit, history, store, pal, toggleTheme)
        }
    }
}

/* ============================ Small widgets ============================ */
@Composable
fun Pill(text: String, selected: Boolean, pal: Pal, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Text(
        text, fontSize = 13.sp, fontWeight = FontWeight.Bold,
        color = if (selected) Color.White else pal.ink,
        modifier = Modifier.clip(shape)
            .background(if (selected) Brand else Brush.linearGradient(listOf(pal.card, pal.card)))
            .border(1.dp, if (selected) Color.Transparent else pal.low, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

@Composable
fun RoundGlyph(glyph: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(glyph, color = Color.White.copy(alpha = if (enabled) 1f else 0.4f), fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun NameDialog(title: String, initial: String, pal: Pal, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = pal.card,
        title = { Text(title, color = pal.ink) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it.take(32) }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onOk(text.trim()) }) { Text("OK", color = Violet) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = pal.sub) } }
    )
}

/** Label for every element, plus a value for resistors (ohms) and batteries (volts). */
@Composable
fun NodeDialog(n: Node, pal: Pal, onDismiss: () -> Unit, onOk: (String, Float?) -> Unit) {
    var label by remember { mutableStateOf(n.label) }
    var value by remember { mutableStateOf(formatValue(n.value)) }
    val hasValue = n.kind == Kind.RESISTOR || n.kind == Kind.BATTERY
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = pal.card,
        title = { Text(if (hasValue) "Label and value" else "Label", color = pal.ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = label, onValueChange = { label = it.take(16) }, singleLine = true,
                    label = { Text("Label") }
                )
                if (hasValue) {
                    OutlinedTextField(
                        value = value, onValueChange = { value = it.take(10) }, singleLine = true,
                        label = { Text(if (n.kind == Kind.BATTERY) "Volts (for example 5)" else "Ohms (for example 330 or 4.7k)") }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onOk(label.trim(), if (hasValue) parseValue(value) else null) }) { Text("OK", color = Violet) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = pal.sub) } }
    )
}

/* ============================ Home ============================ */
@Composable
fun HomeScreen(app: AppState, circuit: Circuit, history: History, store: Store, pal: Pal, onTheme: () -> Unit) {
    var version by remember { mutableStateOf(0) }
    val names = remember(version) { store.names() }
    var chooser by remember { mutableStateOf(false) }
    var nameStep by remember { mutableStateOf(false) }
    var templateKey by remember { mutableStateOf<String?>(null) }
    var pickedMode by remember { mutableStateOf(Mode.NORMAL) }

    fun open(name: String, data: String) {
        circuit.load(data)
        history.clear()
        app.name = name
        app.screen = "editor"
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                    .background(Brand).statusBarsPadding().padding(20.dp, 12.dp, 20.dp, 18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Logic Lab", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        Text("Build and test digital circuits", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                    }
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)).clickable(onClick = onTheme),
                        contentAlignment = Alignment.Center
                    ) { Text(if (pal.dark) "☀" else "☾", color = Color.White, fontSize = 18.sp) }
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Projects", "Learn").forEachIndexed { i, t ->
                        Text(
                            t, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                            color = if (app.tab == i) Violet else Color.White,
                            modifier = Modifier.clip(RoundedCornerShape(50))
                                .background(if (app.tab == i) Color.White else Color.White.copy(alpha = 0.2f))
                                .clickable { app.tab = i }
                                .padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            if (app.tab == 0) {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 110.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(names) { n ->
                        val data = remember(n, version) { store.read(n) }
                        val lines = data.lines()
                        val nodeCount = lines.count { it.startsWith("N|") }
                        val wireCount = lines.count { it.startsWith("W|") }
                        val modeName = if (lines.any { it == "M|FULL" }) "Full" else "Normal"
                        val shape = RoundedCornerShape(20.dp)
                        Row(
                            Modifier.fillMaxWidth().clip(shape).background(pal.card).border(1.dp, pal.grid, shape)
                                .clickable { open(n, data) }.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(Brand), contentAlignment = Alignment.Center) {
                                Text(n.first().uppercase(), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(n, color = pal.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                Text("$nodeCount elements, $wireCount wires, $modeName", color = pal.sub, fontSize = 12.sp)
                            }
                            Text(
                                "Delete", color = pal.sub, fontSize = 12.sp,
                                modifier = Modifier.clickable { store.delete(n); version++ }.padding(8.dp)
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(lessons) { l ->
                        val shape = RoundedCornerShape(20.dp)
                        Column(Modifier.fillMaxWidth().clip(shape).background(pal.card).border(1.dp, pal.grid, shape).padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(l.name, color = pal.ink, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Text(
                                    if (l.mode == Mode.FULL) "FULL" else "NORMAL", color = Violet, fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(l.idea, color = pal.sub, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                            Text(
                                l.table, color = pal.ink, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(top = 10.dp).clip(RoundedCornerShape(12.dp))
                                    .background(pal.bg).padding(10.dp)
                            )
                            Spacer(Modifier.height(10.dp))
                            Pill("Try it", true, pal) {
                                val c = Circuit()
                                buildDemo(c, l.key)
                                open("", c.serialize())
                            }
                        }
                    }
                }
            }
        }

        if (app.tab == 0) {
            Text(
                "+ New project", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(20.dp)
                    .clip(RoundedCornerShape(50)).background(Brand).clickable { chooser = true }
                    .padding(horizontal = 22.dp, vertical = 14.dp)
            )
        }
    }

    BackHandler(enabled = chooser) { chooser = false }
    if (chooser) {
        NewProjectScreen(pal, onBack = { chooser = false }) { key, mode ->
            templateKey = key
            pickedMode = mode
            nameStep = true
        }
    }
    if (nameStep) {
        val tpl = lessons.firstOrNull { it.key == templateKey }
        NameDialog("Project name", tpl?.name ?: "", pal, { nameStep = false }) { n ->
            nameStep = false
            chooser = false
            val name = n.ifBlank { "Untitled ${names.size + 1}" }
            circuit.clearAll()
            circuit.mode1 = pickedMode
            templateKey?.let { buildDemo(circuit, it) }
            history.clear()
            app.name = name
            app.screen = "editor"
            store.save(name, circuit.serialize())
            version++
        }
    }
}

/** Full-screen chooser: pick Normal or Full, then an empty canvas or a ready-made template. */
@Composable
fun NewProjectScreen(pal: Pal, onBack: () -> Unit, onPick: (String?, Mode) -> Unit) {
    var mode by remember { mutableStateOf(Mode.NORMAL) }
    val templates = remember(mode) {
        lessons.filter { it.mode == mode }.sortedBy { if (it.key in listOf("HALF", "FULLADD", "SR")) 0 else 1 }
    }
    Column(Modifier.fillMaxSize().background(pal.bg)) {
        Row(
            Modifier.fillMaxWidth().background(Brand).statusBarsPadding().padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RoundGlyph("‹") { onBack() }
            Text("New project", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        LazyColumn(
            contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Text("Mode", color = pal.sub, fontSize = 13.sp) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Normal", mode == Mode.NORMAL, pal) { mode = Mode.NORMAL }
                    Pill("Full", mode == Mode.FULL, pal) { mode = Mode.FULL }
                }
            }
            item {
                Text(
                    if (mode == Mode.NORMAL) "Gates, inputs and outputs. The VCC and GND pins of the ICs are disabled."
                    else "Adds Battery, GND, resistors, diodes and transistors. ICs must be powered through pins 14 and 7.",
                    color = pal.sub, fontSize = 12.sp
                )
            }
            item { Text("Start from scratch", color = pal.sub, fontSize = 13.sp) }
            item {
                val shape = RoundedCornerShape(20.dp)
                Row(
                    Modifier.fillMaxWidth().clip(shape).background(pal.card).border(1.5.dp, Violet, shape)
                        .clickable { onPick(null, mode) }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(Brand), contentAlignment = Alignment.Center) {
                        Text("+", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("Empty project", color = pal.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text("A blank canvas. Build anything.", color = pal.sub, fontSize = 13.sp)
                    }
                }
            }
            item { Text("Or start from a template", color = pal.sub, fontSize = 13.sp) }
            items(templates) { l ->
                val shape = RoundedCornerShape(20.dp)
                Row(
                    Modifier.fillMaxWidth().clip(shape).background(pal.card).border(1.dp, pal.grid, shape)
                        .clickable { onPick(l.key, mode) }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(Brand), contentAlignment = Alignment.Center) {
                        Text(l.key.take(4), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(l.name, color = pal.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text(l.idea, color = pal.sub, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/* ============================ Editor ============================ */
@Composable
fun EditorScreen(app: AppState, circuit: Circuit, history: History, store: Store, pal: Pal, onTheme: () -> Unit) {
    val ctx = LocalContext.current
    val d = LocalDensity.current.density
    val tm = rememberTextMeasurer()

    var tool by remember { mutableStateOf("move") }        // move | pan | erase | label
    var running by remember { mutableStateOf(true) }
    var showPalette by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Node?>(null) }
    var pan by remember { mutableStateOf(Offset(24f * d, 24f * d)) }
    var zoom by remember { mutableStateOf(1f) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var wireFrom by remember { mutableStateOf<Pair<Node, Int>?>(null) }
    var wireEnd by remember { mutableStateOf(Offset.Zero) }
    var dragNode by remember { mutableStateOf<Node?>(null) }
    var pinching by remember { mutableStateOf(false) }
    var labelTarget by remember { mutableStateOf<Node?>(null) }
    var table by remember { mutableStateOf<TTResult?>(null) }
    var askName by remember { mutableStateOf(false) }
    var fitted by remember { mutableStateOf(false) }

    fun toWorld(p: Offset): Offset = (p - pan) / (zoom * d)
    fun commit() = history.push(circuit.serialize())
    fun autosave() { if (app.name.isNotBlank()) store.save(app.name, circuit.serialize()) }
    fun saveNow() {
        if (app.name.isBlank()) askName = true
        else { autosave(); Toast.makeText(ctx, "Saved", Toast.LENGTH_SHORT).show() }
    }

    fun nodeAt(w: Offset): Node? = circuit.nodes.lastOrNull {
        w.x in it.x..(it.x + Dim.w(it.kind)) && w.y in it.y..(it.y + Dim.h(it.kind))
    }

    fun pinAt(w: Offset): Pair<Node, Int>? {
        var best: Pair<Node, Int>? = null
        var bd = 16f / zoom
        for (n in circuit.nodes) {
            for (i in 0 until Dim.pinCount(n.kind)) {
                if (!circuit.pinEnabled(n, i)) continue
                val dist = (Dim.pin(n, i) - w).getDistance()
                if (dist < bd) { bd = dist; best = n to i }
            }
        }
        return best
    }

    fun wireAt(w: Offset): Wire? = circuit.wires.firstOrNull { wr ->
        val a = circuit.byId(wr.a)
        val b = circuit.byId(wr.b)
        if (a == null || b == null) false
        else {
            val c = bez(Dim.pin(a, wr.ap), Dim.side(a.kind, wr.ap), Dim.pin(b, wr.bp), Dim.side(b.kind, wr.bp))
            (0..30).any { i -> (bezAt(c, i / 30f) - w).getDistance() < 10f / zoom }
        }
    }

    fun finishWire() {
        val from = wireFrom ?: return
        val to = pinAt(wireEnd) ?: return
        val snap = circuit.serialize()
        if (circuit.connect(from.first, from.second, to.first, to.second)) {
            history.push(snap)
            circuit.settle()
        }
    }

    fun zoomBy(f: Float) {
        val nz = (zoom * f).coerceIn(0.25f, 4f)
        val center = Offset(viewSize.width / 2f, viewSize.height / 2f)
        pan = center - (center - pan) * (nz / zoom)
        zoom = nz
    }

    fun fitView() {
        if (circuit.nodes.isEmpty() || viewSize.width == 0) { zoom = 1f; pan = Offset(24f * d, 24f * d); return }
        val minX = circuit.nodes.minOf { it.x }
        val minY = circuit.nodes.minOf { it.y }
        val maxX = circuit.nodes.maxOf { it.x + Dim.w(it.kind) }
        val maxY = circuit.nodes.maxOf { it.y + Dim.h(it.kind) }
        val vw = viewSize.width / d
        val vh = viewSize.height / d
        val bw = max(maxX - minX, 1f)
        val bh = max(maxY - minY, 1f)
        val z = minOf((vw - 48f) / bw, (vh - 96f) / bh).coerceIn(0.25f, 1.5f)
        zoom = z
        pan = Offset(((vw - bw * z) / 2f - minX * z) * d, ((vh - bh * z) / 2f - minY * z) * d)
    }

    fun addNode(k: Kind) {
        commit()
        val c = toWorld(Offset(viewSize.width / 2f, viewSize.height / 2f))
        val j = (circuit.nodes.size % 5) * 14f
        selected = circuit.add(k, c.x - Dim.w(k) / 2f + j, c.y - Dim.h(k) / 2f + j)
        circuit.settle()
        showPalette = false
        tool = "move"
    }

    fun toggleMode() {
        if (circuit.mode1 == Mode.NORMAL) {
            commit(); circuit.setMode(Mode.FULL)
        } else if (circuit.hasFullParts()) {
            Toast.makeText(ctx, "Remove Battery, GND and parts first", Toast.LENGTH_SHORT).show()
        } else {
            commit(); circuit.setMode(Mode.NORMAL)
        }
    }

    LaunchedEffect(viewSize) {
        if (!fitted && viewSize.width > 0) { fitted = true; fitView() }
    }

    LaunchedEffect(running) { // Clock elements toggle while the simulation runs
        if (running) while (true) {
            delay(800)
            val clocks = circuit.nodes.filter { it.kind == Kind.CLOCK }
            if (clocks.isNotEmpty()) { clocks.forEach { it.on = !it.on }; circuit.settle() }
        }
    }

    BackHandler { autosave(); app.screen = "home" }

    Column(Modifier.fillMaxSize()) {
        // top bar
        Row(
            Modifier.fillMaxWidth().background(Brand).statusBarsPadding().padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RoundGlyph("‹") { autosave(); app.screen = "home" }
            Text(
                app.name.ifBlank { "Example" }, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
            )
            RoundGlyph("↶", history.canUndo) {
                history.undo(circuit.serialize())?.let { circuit.load(it); selected = null }
            }
            RoundGlyph("↷", history.canRedo) {
                history.redo(circuit.serialize())?.let { circuit.load(it); selected = null }
            }
            Text(
                "Save", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable { saveNow() }.padding(horizontal = 10.dp, vertical = 8.dp)
            )
            RoundGlyph(if (pal.dark) "☀" else "☾") { onTheme() }
        }

        // tools
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("move" to "Move", "pan" to "Pan", "erase" to "Erase", "label" to "Label").forEach { (k, t) ->
                Pill(t, tool == k, pal) { tool = k }
            }
            Pill(if (running) "Pause" else "Run", !running, pal) { running = !running }
            Pill("Table", false, pal) { table = buildTruthTable(circuit) }
            Pill(if (circuit.mode1 == Mode.FULL) "Full" else "Normal", circuit.mode1 == Mode.FULL, pal) { toggleMode() }
        }
        Text(
            when (tool) {
                "move" -> "Move: drag items. Tap a switch to flip it."
                "pan" -> "Pan: drag from a pin to another pin to draw a wire. Tap an IC pin to set In, Out or NC."
                "erase" -> "Erase: tap an item or a wire to remove it."
                else -> "Label: tap an item to rename it or set its value."
            },
            color = pal.sub, fontSize = 11.sp, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 6.dp)
        )

        // canvas
        Box(Modifier.weight(1f).fillMaxWidth().onSizeChanged { viewSize = it }) {
            Canvas(
                Modifier.fillMaxSize()
                    .clipToBounds() // keeps the circuit from drawing over the top bar
                    .pointerInput(Unit) { // two fingers: pinch to zoom and drag to pan the view
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val ev = awaitPointerEvent()
                                val pressedCount = ev.changes.count { it.pressed }
                                if ( pressedCount >= 2) {
                                    pinching = true
                                    wireFrom = null
                                    dragNode = null
                                    val zc = ev.calculateZoom()
                                    val pc = ev.calculatePan()
                                    val cen = ev.calculateCentroid(useCurrent = true)
                                    if (zc != 1f || pc != Offset.Zero) {
                                        val nz = (zoom * zc).coerceIn(0.25f, 4f)
                                        pan = cen - (cen - pan) * (nz / zoom) + pc
                                        zoom = nz
                                        ev.changes.forEach { if (it.positionChanged()) it.consume() }
                                    }
                                    else if (pressedCount == 0) {
                                        pinching = false
                                    }
                                }
                            } while (ev.changes.any { it.pressed })
                            pinching = false
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = { p ->
                                if (tool == "move") {
                                    val n = nodeAt(toWorld(p))
                                    if (n?.kind == Kind.BUTTON) {
                                        n.on = true; circuit.settle()
                                        tryAwaitRelease()
                                        n.on = false; circuit.settle()
                                    }
                                }
                            },
                            onTap = { p ->
                                val w = toWorld(p)
                                when (tool) {
                                    "erase" -> {
                                        val n = nodeAt(w)
                                        if (n != null) {
                                            commit(); circuit.remove(n); circuit.settle()
                                            if (selected == n) selected = null
                                        } else {
                                            val wr = wireAt(w)
                                            if (wr != null) { commit(); circuit.wires.remove(wr); circuit.settle() }
                                        }
                                    }
                                    "label" -> { nodeAt(w)?.let { labelTarget = it } }
                                    "pan" -> {
                                        val pin = pinAt(w)
                                        if (pin != null) {
                                            val n = pin.first
                                            val i = pin.second
                                            if (n.kind.isIC && i != 6 && i != 13) {
                                                commit()
                                                n.roles[i] = n.roles[i].next()
                                                n.memo.fill(false)
                                                circuit.settle()
                                            }
                                        }
                                    }
                                    else -> {
                                        val n = nodeAt(w)
                                        selected = n
                                        if (n != null && (n.kind == Kind.SWITCH || n.kind == Kind.SPST)) {
                                            n.on = !n.on; circuit.settle()
                                        }
                                    }
                                }
                            }
                        )
                    }
                    .pointerInput(Unit) { // Single-finger drag: move node, draw wire, OR pan the canvas
                        detectDragGestures(
                            onDragStart = { p ->
                                if (pinching) return@detectDragGestures
                                val w = toWorld(p)
                                dragNode = null
                                wireFrom = null
                                if (tool == "move") {
                                    dragNode = nodeAt(w)
                                    selected = dragNode
                                    if (dragNode != null) commit()
                                } else if (tool == "pan") {
                                    val pin = pinAt(w)
                                    if (pin != null) { wireFrom = pin; wireEnd = w }
                                }
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                when {
                                    // Drawing a wire from a pin
                                    wireFrom != null -> {
                                        wireEnd = toWorld(change.position)
                                    }
                                    // Dragging an element in "move" mode
                                    dragNode != null -> {
                                        dragNode?.let {
                                            it.x += amount.x / (zoom * d)
                                            it.y += amount.y / (zoom * d)
                                        }
                                    }
                                    // Dragging on empty space (or in "pan" mode when not touching a pin) -> move screen
                                    else -> {
                                        pan += amount
                                    }
                                }
                            },
                            onDragEnd = { finishWire(); wireFrom = null; dragNode = null },
                            onDragCancel = { wireFrom = null; dragNode = null }
                        )
                    }
            ) {
                @Suppress("UNUSED_VARIABLE")
                val redrawOnSimulation = circuit.tick
                val s = zoom * density
                val step = 24f * s
                if (step > 9f) {
                    var gx = ((pan.x % step) + step) % step
                    while (gx < size.width) {
                        var gy = ((pan.y % step) + step) % step
                        while (gy < size.height) { drawCircle(pal.grid, 1.6f * density, Offset(gx, gy)); gy += step }
                        gx += step
                    }
                }
                withTransform({ translate(pan.x, pan.y); scale(s, s, Offset.Zero) }) {
                    circuit.wires.forEach { w ->
                        val a = circuit.byId(w.a)
                        val b = circuit.byId(w.b)
                        if (a != null && b != null) {
                            drawWire(Dim.pin(a, w.ap), Dim.side(a.kind, w.ap), Dim.pin(b, w.bp), Dim.side(b.kind, w.bp), w.high, pal)
                        }
                    }
                    wireFrom?.let { f -> drawWire(Dim.pin(f.first, f.second), Dim.side(f.first.kind, f.second), wireEnd, 0, false, pal, true) }
                    circuit.nodes.forEach { drawNode(it, circuit, tm, pal, it == selected) }
                }
            }

            // zoom controls
            Row(
                Modifier.align(Alignment.BottomStart).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically
            ) {
                Pill("−", false, pal) { zoomBy(1f / 1.25f) }
                Pill("+", false, pal) { zoomBy(1.25f) }
                Pill("Fit", false, pal) { fitView() }
                Text("${(zoom * 100).toInt()}%", color = pal.sub, fontSize = 12.sp)
            }

            if (!showPalette) {
                Text(
                    "+", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).size(58.dp).clip(CircleShape)
                        .background(Brand).clickable { showPalette = true }.padding(top = 6.dp)
                )
            } else {
                val groups = if (circuit.mode1 == Mode.FULL) listOf("Gates", "Inputs", "Outputs", "Power", "Parts")
                else listOf("Gates", "Inputs", "Outputs")
                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(max = 380.dp)
                        .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).background(pal.card)
                        .verticalScroll(rememberScrollState()).padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Add element", color = pal.ink, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        Text("Close", color = Violet, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { showPalette = false }.padding(6.dp))
                    }
                    groups.forEach { g ->
                        Text(g, color = pal.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Kind.values().filter { it.group == g }.forEach { k ->
                                Pill(if (k.isIC) "${k.label} ${k.chip}" else k.label, false, pal) { addNode(k) }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.navigationBarsPadding())
    }

    labelTarget?.let { n ->
        NodeDialog(n, pal, { labelTarget = null }) { lab, v ->
            commit()
            n.label = lab
            if (v != null) n.value = v
            labelTarget = null
            circuit.settle()
        }
    }
    if (askName) {
        NameDialog("Project name", "", pal, { askName = false }) { t ->
            askName = false
            if (t.isNotBlank()) { app.name = t; autosave(); Toast.makeText(ctx, "Saved", Toast.LENGTH_SHORT).show() }
        }
    }
    table?.let { t -> TruthTableDialog(t, pal) { table = null } }
}

@Composable
fun TruthTableDialog(t: TTResult, pal: Pal, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = pal.card,
        title = { Text("Truth table", color = pal.ink) },
        text = {
            if (t.error != null) Text(t.error, color = pal.sub)
            else Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Brand).padding(vertical = 8.dp)) {
                    t.heads.forEach { h ->
                        Text(h, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                    }
                }
                t.rows.forEach { r ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        r.forEachIndexed { i, v ->
                            Text(
                                v, color = if (i < t.nIn) pal.ink else Violet, fontSize = 15.sp,
                                fontWeight = if (i < t.nIn) FontWeight.Normal else FontWeight.Bold,
                                fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Close", color = Violet) } }
    )
}

/* ============================ Drawing ============================ */
private fun bez(a: Offset, da: Int, b: Offset, db: Int): List<Offset> {
    val dx = max(40f, abs(b.x - a.x) / 2f)
    return listOf(a, Offset(a.x + da * dx, a.y), Offset(b.x + db * dx, b.y), b)
}

private fun bezAt(c: List<Offset>, t: Float): Offset {
    val u = 1f - t
    return c[0] * (u * u * u) + c[1] * (3f * u * u * t) + c[2] * (3f * u * t * t) + c[3] * (t * t * t)
}

private fun DrawScope.drawWire(a: Offset, da: Int, b: Offset, db: Int, on: Boolean, pal: Pal, dashed: Boolean = false) {
    val c = bez(a, da, b, db)
    val path = Path().apply {
        moveTo(c[0].x, c[0].y)
        cubicTo(c[1].x, c[1].y, c[2].x, c[2].y, c[3].x, c[3].y)
    }
    val style = Stroke(
        width = 3.2f, cap = StrokeCap.Round,
        pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(8f, 6f)) else null
    )
    if (on) drawPath(path, Brush.linearGradient(listOf(Violet, Blue), start = a, end = b + Offset(0.5f, 0f)), style = style)
    else drawPath(path, if (dashed) Violet else pal.low, style = style)
}

/** Text is drawn inside the zoom transform, so its size is given in world dp. align: -1 left, 0 center, 1 right. */
private fun DrawScope.drawLabel(
    tm: TextMeasurer, text: String, anchor: Offset, size: Float, color: Color,
    align: Int = 0, bold: Boolean = true
) {
    val style = TextStyle(
        color = color, fontSize = (size / (density * fontScale)).sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
    )
    val r = tm.measure(text, style)
    val x = when (align) {
        -1 -> anchor.x
        1 -> anchor.x - r.size.width
        else -> anchor.x - r.size.width / 2f
    }
    drawText(r, topLeft = Offset(x, anchor.y - r.size.height / 2f))
}

private val SEGMENTS = intArrayOf(63, 6, 91, 79, 102, 109, 125, 7, 127, 111, 119, 124, 57, 94, 121, 113)

private fun DrawScope.drawNode(n: Node, c: Circuit, tm: TextMeasurer, pal: Pal, selected: Boolean) {
    val k = n.kind
    val w = Dim.w(k)
    val h = Dim.h(k)
    val tl = Offset(n.x, n.y)
    val center = Offset(n.x + w / 2f, n.y + h / 2f)
    val border = if (selected) Violet else pal.ink.copy(alpha = 0.7f)
    val bw = if (selected) 3f else 1.6f
    val corner = CornerRadius(12f)
    val full = c.mode1 == Mode.FULL

    when {
        k.isIC -> {
            drawRoundRect(pal.card, tl, Size(w, h), CornerRadius(8f))
            drawRoundRect(border, tl, Size(w, h), CornerRadius(8f), style = Stroke(bw))
            drawArc(pal.bg, 0f, 180f, true, Offset(n.x + w / 2f - 6f, n.y - 6f), Size(12f, 12f))
            drawArc(border, 0f, 180f, false, Offset(n.x + w / 2f - 6f, n.y - 6f), Size(12f, 12f), style = Stroke(1.4f))
            drawLabel(tm, k.label, Offset(center.x, center.y - 8f), 14f, pal.ink)
            drawLabel(tm, k.chip, Offset(center.x, center.y + 8f), 9f, pal.sub, bold = false)
            for (i in 0 until 14) {
                val p = Dim.local(k, i)
                val power = i == 6 || i == 13
                val text = when {
                    i == 6 -> "GND"
                    i == 13 -> "VCC"
                    else -> when (n.roles[i]) {
                        Role.IN -> "In"
                        Role.OUT -> "Out"
                        Role.NC -> "-"
                    }
                }
                val col = when {
                    power && !full -> pal.low.copy(alpha = 0.5f)
                    !power && n.roles[i] == Role.OUT -> Violet
                    else -> pal.sub
                }
                if (i < 7) drawLabel(tm, "${i + 1} $text", Offset(n.x + 6f, n.y + p.y), 8f, col, -1)
                else drawLabel(tm, "$text ${i + 1}", Offset(n.x + w - 6f, n.y + p.y), 8f, col, 1)
            }
        }
        // old
        /*
        k == Kind.LAMP -> {
            if (n.lit) drawCircle(LampOn.copy(alpha = 0.3f), w * 0.75f, center)
            drawCircle(if (n.lit) LampOn else pal.card, w / 2f, center)
            drawCircle(border, w / 2f, center, style = Stroke(bw))
            if (n.burnt) drawLabel(tm, "x", center, 22f, Danger)
            else drawLabel(tm, if (n.lit) "1" else "0", center, 20f, if (n.lit) Color(0xFF1B1C3A) else pal.ink)
            if (full) {
                drawLabel(tm, "+", Offset(n.x + 7f, center.y), 9f, pal.sub, -1)
                drawLabel(tm, "-", Offset(n.x + w - 7f, center.y), 9f, pal.sub, 1)
            }
        }
        */
        //new
        /**/
          k == Kind.LAMP -> {
            if (n.lit) drawCircle(LampOn.copy(alpha = 0.3f), w * 0.75f, center)
            drawCircle(if (n.lit) LampOn else pal.card, w / 2f, center)
            drawCircle(if (n.burnt) Danger else border, w / 2f, center, style = Stroke(if (n.burnt) 2.6f else bw))

            // Always show "1" when lit (even when over-volted)
            drawLabel(tm, if (n.lit) "1" else "0", center, 20f, if (n.lit) Color(0xFF1B1C3A) else pal.ink)

            // Draw warning badge on top-right if voltage/current exceeds standard lamp rating
            if (n.burnt) {
                val wx = n.x + w - 4f
                val wy = n.y + 4f
                val tri = Path().apply {
                    moveTo(wx, wy - 9f)
                    lineTo(wx - 8f, wy + 6f)
                    lineTo(wx + 8f, wy + 6f)
                    close()
                }
                drawPath(tri, Danger)
                drawPath(tri, Color.White, style = Stroke(1.2f))
                drawLabel(tm, "!", Offset(wx, wy + 0.5f), 9f, Color.White)
            }

            if (full) {
                drawLabel(tm, "+", Offset(n.x + 7f, center.y), 9f, pal.sub, -1)
                drawLabel(tm, "-", Offset(n.x + w - 7f, center.y), 9f, pal.sub, 1)
            }
        }
        /**/
        k == Kind.HEX -> {
            drawRoundRect(pal.card, tl, Size(w, h), corner)
            drawRoundRect(border, tl, Size(w, h), corner, style = Stroke(bw))
            val mask = SEGMENTS[n.hexVal and 15]
            val l = n.x + 16f
            val t = n.y + 10f
            val sw = 36f
            val sh = 64f
            val th = 6f
            fun seg(bit: Int, x: Float, y: Float, ww: Float, hh: Float) =
                drawRoundRect(if (mask and bit != 0) SegOn else pal.grid, Offset(x, y), Size(ww, hh), CornerRadius(3f))
            seg(1, l + 4f, t, sw - 8f, th)
            seg(2, l + sw - th, t + 4f, th, sh / 2f - 6f)
            seg(4, l + sw - th, t + sh / 2f + 2f, th, sh / 2f - 6f)
            seg(8, l + 4f, t + sh - th, sw - 8f, th)
            seg(16, l, t + sh / 2f + 2f, th, sh / 2f - 6f)
            seg(32, l, t + 4f, th, sh / 2f - 6f)
            seg(64, l + 4f, t + sh / 2f - th / 2f, sw - 8f, th)
        }
        k.isSource -> {
            if (n.on) drawRoundRect(Brand, tl, Size(w, h), corner) else drawRoundRect(pal.card, tl, Size(w, h), corner)
            drawRoundRect(border, tl, Size(w, h), corner, style = Stroke(bw))
            val fg = if (n.on) Color.White else pal.ink
            drawLabel(tm, if (n.on) "1" else "0", Offset(center.x, center.y - 5f), 22f, fg)
            val tag = when (k) {
                Kind.SWITCH -> "SW"
                Kind.BUTTON -> "BTN"
                else -> "CLK"
            }
            drawLabel(tm, tag, Offset(center.x, n.y + h - 9f), 9f, fg.copy(alpha = 0.75f))
        }
        k == Kind.BATTERY -> {
            drawRoundRect(pal.card, tl, Size(w, h), corner)
            drawRoundRect(border, tl, Size(w, h), corner, style = Stroke(bw))
            drawLabel(tm, formatValue(n.value) + "V", center, 14f, pal.ink)
            drawLabel(tm, "+", Offset(n.x + w - 7f, n.y + 10f), 10f, pal.sub, 1)
            drawLabel(tm, "-", Offset(n.x + 7f, n.y + 10f), 10f, pal.sub, -1)
        }
        k == Kind.GND -> {
            val cx = n.x + w / 2f
            val ink = pal.ink
            drawLine(ink, Offset(cx, n.y), Offset(cx, n.y + 14f), 2.4f)
            drawLine(ink, Offset(cx - 14f, n.y + 14f), Offset(cx + 14f, n.y + 14f), 2.4f)
            drawLine(ink, Offset(cx - 9f, n.y + 22f), Offset(cx + 9f, n.y + 22f), 2.4f)
            drawLine(ink, Offset(cx - 4f, n.y + 30f), Offset(cx + 4f, n.y + 30f), 2.4f)
        }
        k == Kind.RESISTOR -> {
            drawRoundRect(pal.card, tl, Size(w, h), CornerRadius(6f))
            drawRoundRect(border, tl, Size(w, h), CornerRadius(6f), style = Stroke(bw))
            drawLabel(tm, formatValue(n.value) + "Ω", center, 12f, pal.ink)
        }
        k == Kind.DIODE -> {
            val cy = center.y
            val ink = pal.ink
            drawLine(ink, Offset(n.x, cy), Offset(n.x + 22f, cy), 2.4f)
            val tri = Path().apply {
                moveTo(n.x + 22f, cy - 11f); lineTo(n.x + 22f, cy + 11f); lineTo(n.x + 48f, cy); close()
            }
            drawPath(tri, if (n.j1) Violet else pal.card)
            drawPath(tri, border, style = Stroke(bw))
            drawLine(ink, Offset(n.x + 48f, cy - 11f), Offset(n.x + 48f, cy + 11f), 2.4f)
            drawLine(ink, Offset(n.x + 48f, cy), Offset(n.x + w, cy), 2.4f)
        }
        k == Kind.SPST -> {
            if (n.on) drawRoundRect(Brand, tl, Size(w, h), CornerRadius(10f)) else drawRoundRect(pal.card, tl, Size(w, h), CornerRadius(10f))
            drawRoundRect(border, tl, Size(w, h), CornerRadius(10f), style = Stroke(bw))
            drawLabel(tm, if (n.on) "ON" else "OFF", center, 13f, if (n.on) Color.White else pal.ink)
        }
        else -> { // NPN / PNP
            drawRoundRect(pal.card, tl, Size(w, h), corner)
            drawRoundRect(border, tl, Size(w, h), corner, style = Stroke(bw))
            drawLabel(tm, k.label, center, 13f, pal.ink)
            drawLabel(tm, "B", Offset(n.x + 7f, n.y + h / 2f), 9f, pal.sub, -1)
            drawLabel(tm, "C", Offset(n.x + w - 7f, n.y + 10f), 9f, pal.sub, 1)
            drawLabel(tm, "E", Offset(n.x + w - 7f, n.y + h - 10f), 9f, pal.sub, 1)
        }
    }

    // pins
    for (i in 0 until Dim.pinCount(k)) {
        val p = Dim.pin(n, i)
        if (!c.pinEnabled(n, i)) {
            drawCircle(pal.low.copy(alpha = 0.35f), 3f, p)
            continue
        }
        val role = if (k.isIC) n.roles[i] else Role.IN
        val power = k.isIC && (i == 6 || i == 13)
        if (k.isIC && !power && role == Role.NC) {
            drawCircle(pal.card, 5.5f, p)
            drawCircle(pal.low, 4f, p, style = Stroke(1.4f))
            continue
        }
        drawCircle(pal.card, 5.5f, p)
        drawCircle(if (n.hi[i]) Blue else pal.low, 4f, p)
        if (k.isIC && !power && role == Role.OUT) drawCircle(Violet, 6.8f, p, style = Stroke(1.6f))
    }

    if (n.label.isNotBlank()) drawLabel(tm, n.label, Offset(center.x, n.y - 9f), 11f, pal.sub)
}