package com.legendAhmed.logiclab
//
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*

import androidx.compose.runtime.*

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.max

/* ============================ Theme ============================ */
private val Violet = Color(0xFF7C3AED)
private val Blue = Color(0xFF2563EB)
private val Brand = Brush.linearGradient(listOf(Violet, Blue))
private val LampOn = Color(0xFFFFC83D)
private val SegOn = Color(0xFF8B5CF6)

class Pal(val dark: Boolean) {
    val bg = if (dark) Color(0xFF0E0F1E) else Color(0xFFF4F3FF)
    val card = if (dark) Color(0xFF181A33) else Color.White
    val ink = if (dark) Color(0xFFE8E9FF) else Color(0xFF1B1C3A)
    val sub = if (dark) Color(0xFF9A9FC8) else Color(0xFF6B6F99)
    val low = if (dark) Color(0xFF5A5F85) else Color(0xFFA3A8C8)
    val grid = if (dark) Color(0xFF23264A) else Color(0xFFDAD8F5)
}

/* ============================ Model ============================ */
enum class Kind(val label: String, val ins: Int, val group: String) {
    AND("AND", 2, "Gates"), OR("OR", 2, "Gates"), NAND("NAND", 2, "Gates"),
    NOR("NOR", 2, "Gates"), XOR("XOR", 2, "Gates"), XNOR("XNOR", 2, "Gates"),
    NOT("NOT", 1, "Gates"), BUF("BUF", 1, "Gates"),
    AND3("AND3", 3, "Gates"), OR3("OR3", 3, "Gates"),
    SWITCH("Switch", 0, "Inputs"), BUTTON("Button", 0, "Inputs"), CLOCK("Clock", 0, "Inputs"),
    LAMP("Lamp", 1, "Outputs"), HEX("Hex", 4, "Outputs");

    val isSource: Boolean get() = ins == 0
    val isGate: Boolean get() = group == "Gates"
    val isSink: Boolean get() = this == LAMP || this == HEX
}

class Node(val id: Int, val kind: Kind, x: Float, y: Float, label: String = "") {
    var x by mutableStateOf(x)
    var y by mutableStateOf(y)
    var label by mutableStateOf(label)
    var out by mutableStateOf(false) // sources: their state, gates: computed output
}

data class Wire(val from: Int, val to: Int, val port: Int)

/** All sizes are in "world dp". The canvas scales them by zoom * density. */
object Dim {
    fun w(k: Kind) = when {
        k.isGate -> 76f
        k == Kind.HEX -> 64f
        else -> 52f
    }

    fun h(k: Kind) = when {
        k == Kind.AND3 || k == Kind.OR3 -> 68f
        k.isGate -> 56f
        k == Kind.HEX -> 84f
        else -> 52f
    }

    fun inPort(n: Node, i: Int) = Offset(n.x, n.y + h(n.kind) * (i + 1f) / (n.kind.ins + 1f))
    fun outPort(n: Node) = Offset(n.x + w(n.kind), n.y + h(n.kind) / 2f)
}

class Circuit {
    val nodes = mutableStateListOf<Node>()
    val wires = mutableStateListOf<Wire>()
    private var nextId = 1

    fun add(kind: Kind, x: Float, y: Float, label: String = ""): Node =
        Node(nextId++, kind, x, y, label).also { nodes.add(it) }

    fun byId(id: Int): Node? = nodes.firstOrNull { it.id == id }

    fun remove(n: Node) {
        wires.removeAll { it.from == n.id || it.to == n.id }
        nodes.remove(n)
    }

    fun clearAll() { wires.clear(); nodes.clear(); nextId = 1 }

    fun connect(from: Node, to: Node, port: Int): Boolean {
        if (from.id == to.id || from.kind.isSink || to.kind.isSource || port >= to.kind.ins) return false
        wires.removeAll { it.to == to.id && it.port == port } // one wire per input port
        wires.add(Wire(from.id, to.id, port))
        return true
    }

    fun inputOf(n: Node, port: Int): Boolean {
        val w = wires.firstOrNull { it.to == n.id && it.port == port } ?: return false
        return byId(w.from)?.out ?: false
    }

    /**
     * Simulation: recompute every gate in passes until no output changes.
     * Because values persist between passes, feedback circuits (latches) hold their state.
     */
    fun settle() {
        repeat(100) {
            var changed = false
            for (n in nodes) {
                if (n.kind.isSource) continue
                val a = inputOf(n, 0)
                val b = inputOf(n, 1)
                val c = inputOf(n, 2)
                val v = when (n.kind) {
                    Kind.AND -> a && b
                    Kind.OR -> a || b
                    Kind.NAND -> !(a && b)
                    Kind.NOR -> !(a || b)
                    Kind.XOR -> a != b
                    Kind.XNOR -> a == b
                    Kind.NOT -> !a
                    Kind.BUF -> a
                    Kind.AND3 -> a && b && c
                    Kind.OR3 -> a || b || c
                    Kind.LAMP -> a
                    else -> n.out // HEX shows its inputs directly, sources keep their state
                }
                if (v != n.out) { n.out = v; changed = true }
            }
            if (!changed) return
        }
    }

    fun hexValue(n: Node): Int =
        (if (inputOf(n, 0)) 1 else 0) or (if (inputOf(n, 1)) 2 else 0) or
                (if (inputOf(n, 2)) 4 else 0) or (if (inputOf(n, 3)) 8 else 0)

    fun serialize(): String = buildString {
        nodes.forEach {
            val lab = it.label.replace("|", " ").replace("\n", " ")
            appendLine("N|${it.id}|${it.kind.name}|${it.x}|${it.y}|${if (it.out && it.kind.isSource) 1 else 0}|$lab")
        }
        wires.forEach { appendLine("W|${it.from}|${it.to}|${it.port}") }
    }

    fun load(text: String) {
        clearAll()
        var maxId = 0
        text.lines().forEach { line ->
            val p = line.split("|")
            when (p.firstOrNull()) {
                "N" -> if (p.size >= 6) {
                    val k = runCatching { Kind.valueOf(p[2]) }.getOrNull() ?: return@forEach
                    val id = p[1].toIntOrNull() ?: return@forEach
                    val n = Node(id, k, p[3].toFloatOrNull() ?: 0f, p[4].toFloatOrNull() ?: 0f, p.getOrElse(6) { "" })
                    if (k.isSource) n.out = p[5] == "1"
                    nodes.add(n)
                    maxId = max(maxId, id)
                }
                "W" -> if (p.size >= 4) {
                    val f = p[1].toIntOrNull(); val t = p[2].toIntOrNull(); val pt = p[3].toIntOrNull()
                    if (f != null && t != null && pt != null) wires.add(Wire(f, t, pt))
                }
            }
        }
        nextId = maxId + 1
        settle()
    }
}

/** Undo / redo using text snapshots of the circuit. */
class History {
    private val undoStack = ArrayDeque<String>()
    private val redoStack = ArrayDeque<String>()
    var canUndo by mutableStateOf(false)
    var canRedo by mutableStateOf(false)

    private fun sync() { canUndo = undoStack.isNotEmpty(); canRedo = redoStack.isNotEmpty() }

    fun push(before: String) {
        undoStack.addLast(before)
        if (undoStack.size > 60) undoStack.removeFirst()
        redoStack.clear(); sync()
    }

    fun undo(current: String): String? {
        if (undoStack.isEmpty()) return null
        redoStack.addLast(current)
        return undoStack.removeLast().also { sync() }
    }

    fun redo(current: String): String? {
        if (redoStack.isEmpty()) return null
        undoStack.addLast(current)
        return redoStack.removeLast().also { sync() }
    }

    fun clear() { undoStack.clear(); redoStack.clear(); sync() }
}

class Store(ctx: Context) {
    private val sp = ctx.getSharedPreferences("logic_lab", Context.MODE_PRIVATE)
    fun names(): List<String> = (sp.getString("names", "") ?: "").split("\n").filter { it.isNotBlank() }
    fun read(name: String): String = sp.getString("p_$name", "") ?: ""
    fun save(name: String, data: String) {
        val list = names().toMutableList()
        if (name !in list) list.add(0, name)
        sp.edit().putString("names", list.joinToString("\n")).putString("p_$name", data).apply()
    }

    fun delete(name: String) {
        val list = names().filter { it != name }
        sp.edit().putString("names", list.joinToString("\n")).remove("p_$name").apply()
    }

    fun darkPref(): Int = sp.getInt("dark", -1)
    fun setDark(v: Boolean) { sp.edit().putInt("dark", if (v) 1 else 0).apply() }
}

class AppState {
    var screen by mutableStateOf("home")
    var name by mutableStateOf("")
    var tab by mutableStateOf(0)
}

/* ============================ Demos & lessons ============================ */
fun buildDemo(c: Circuit, key: String) {
    c.clearAll()
    when (key) {
        "HALF" -> {
            val a = c.add(Kind.SWITCH, 10f, 30f, "A"); val b = c.add(Kind.SWITCH, 10f, 130f, "B")
            val x = c.add(Kind.XOR, 120f, 20f); val n = c.add(Kind.AND, 120f, 120f)
            val s = c.add(Kind.LAMP, 250f, 22f, "Sum"); val co = c.add(Kind.LAMP, 250f, 122f, "Carry")
            c.connect(a, x, 0); c.connect(b, x, 1); c.connect(a, n, 0); c.connect(b, n, 1)
            c.connect(x, s, 0); c.connect(n, co, 0)
        }
        "FULL" -> {
            val a = c.add(Kind.SWITCH, 8f, 10f, "A"); val b = c.add(Kind.SWITCH, 8f, 100f, "B")
            val ci = c.add(Kind.SWITCH, 8f, 190f, "Cin")
            val x1 = c.add(Kind.XOR, 100f, 20f); val a1 = c.add(Kind.AND, 100f, 110f)
            val x2 = c.add(Kind.XOR, 210f, 60f); val a2 = c.add(Kind.AND, 210f, 160f)
            val o = c.add(Kind.OR, 320f, 130f)
            val s = c.add(Kind.LAMP, 312f, 55f, "Sum"); val co = c.add(Kind.LAMP, 420f, 132f, "Cout")
            c.connect(a, x1, 0); c.connect(b, x1, 1); c.connect(a, a1, 0); c.connect(b, a1, 1)
            c.connect(x1, x2, 0); c.connect(ci, x2, 1); c.connect(x1, a2, 0); c.connect(ci, a2, 1)
            c.connect(a1, o, 0); c.connect(a2, o, 1); c.connect(x2, s, 0); c.connect(o, co, 0)
        }
        "SR" -> {
            val r = c.add(Kind.SWITCH, 10f, 30f, "R"); val s = c.add(Kind.SWITCH, 10f, 170f, "S")
            val n1 = c.add(Kind.NOR, 130f, 40f); val n2 = c.add(Kind.NOR, 130f, 160f)
            val q = c.add(Kind.LAMP, 260f, 42f, "Q"); val qb = c.add(Kind.LAMP, 260f, 162f, "Q'")
            c.connect(r, n1, 0); c.connect(n2, n1, 1); c.connect(s, n2, 0); c.connect(n1, n2, 1)
            c.connect(n1, q, 0); c.connect(n2, qb, 0)
        }
        "NOT", "BUF" -> {
            val a = c.add(Kind.SWITCH, 10f, 60f, "A")
            val g = c.add(Kind.valueOf(key), 120f, 58f); val l = c.add(Kind.LAMP, 250f, 60f, "Y")
            c.connect(a, g, 0); c.connect(g, l, 0)
        }
        else -> {
            val k = Kind.valueOf(key)
            val a = c.add(Kind.SWITCH, 10f, 30f, "A"); val b = c.add(Kind.SWITCH, 10f, 130f, "B")
            val g = c.add(k, 120f, 70f); val l = c.add(Kind.LAMP, 250f, 72f, "Y")
            c.connect(a, g, 0); c.connect(b, g, 1); c.connect(g, l, 0)
        }
    }
    c.settle()
}

class Lesson(val name: String, val idea: String, val table: String, val key: String)

val lessons = listOf(
    Lesson("AND", "Output is 1 only when every input is 1.", "A B | Y\n0 0 | 0\n0 1 | 0\n1 0 | 0\n1 1 | 1", "AND"),
    Lesson("OR", "Output is 1 when at least one input is 1.", "A B | Y\n0 0 | 0\n0 1 | 1\n1 0 | 1\n1 1 | 1", "OR"),
    Lesson("NOT", "Flips the input: 0 becomes 1 and 1 becomes 0.", "A | Y\n0 | 1\n1 | 0", "NOT"),
    Lesson("XOR", "Output is 1 when the two inputs are different.", "A B | Y\n0 0 | 0\n0 1 | 1\n1 0 | 1\n1 1 | 0", "XOR"),
    Lesson("NAND", "An AND followed by a NOT. A universal gate.", "A B | Y\n0 0 | 1\n0 1 | 1\n1 0 | 1\n1 1 | 0", "NAND"),
    Lesson("NOR", "An OR followed by a NOT. Also universal.", "A B | Y\n0 0 | 1\n0 1 | 0\n1 0 | 0\n1 1 | 0", "NOR"),
    Lesson("XNOR", "Output is 1 when the two inputs are equal.", "A B | Y\n0 0 | 1\n0 1 | 0\n1 0 | 0\n1 1 | 1", "XNOR"),
    Lesson("Half adder", "Adds two bits. XOR gives the sum, AND gives the carry.", "A B | S C\n0 0 | 0 0\n0 1 | 1 0\n1 0 | 1 0\n1 1 | 0 1", "HALF"),
    Lesson("Full adder", "Adds two bits plus a carry-in, using two half adders and an OR.",
        "A B Ci | S Co\n0 0 0 | 0 0\n0 0 1 | 1 0\n0 1 0 | 1 0\n0 1 1 | 0 1\n1 0 0 | 1 0\n1 0 1 | 0 1\n1 1 0 | 0 1\n1 1 1 | 1 1", "FULL"),
    Lesson("SR latch", "Two cross-coupled NOR gates remember one bit. Pulse S to set Q, pulse R to reset it.",
        "S R | Q\n0 0 | hold\n1 0 | 1\n0 1 | 0\n1 1 | invalid", "SR")
)

/* ============================ Truth table ============================ */
class TTResult(val error: String?, val heads: List<String>, val nIn: Int, val rows: List<List<String>>)

fun buildTruthTable(c: Circuit): TTResult {
    val ins = c.nodes.filter { it.kind.isSource }
    val outs = c.nodes.filter { it.kind.isSink }
    if (ins.isEmpty() || outs.isEmpty())
        return TTResult("Add at least one input and one output (lamp or display).", emptyList(), 0, emptyList())
    if (ins.size > 6)
        return TTResult("Truth tables support up to 6 inputs. This circuit has ${ins.size}.", emptyList(), 0, emptyList())

    val inNames = ins.mapIndexed { i, n -> n.label.ifBlank { ('A' + i).toString() } }
    val outNames = outs.mapIndexed { i, n -> n.label.ifBlank { "Y${i + 1}" } }
    val saved = ins.map { it.out }
    val rows = ArrayList<List<String>>()
    for (mask in 0 until (1 shl ins.size)) {
        ins.forEachIndexed { i, n -> n.out = ((mask shr (ins.size - 1 - i)) and 1) == 1 }
        c.settle()
        val row = ins.map { if (it.out) "1" else "0" } +
                outs.map { if (it.kind == Kind.HEX) c.hexValue(it).toString(16).uppercase() else if (it.out) "1" else "0" }
        rows.add(row)
    }
    ins.forEachIndexed { i, n -> n.out = saved[i] }
    c.settle()
    return TTResult(null, inNames + outNames, ins.size, rows)
}

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
    var dark by remember { mutableStateOf(store.darkPref().let { if (it < 0) sysDark else it == 1 }) }
    val pal = remember(dark) { Pal(dark) }
    val app = remember { AppState() }
    val circuit = remember { Circuit() }
    val history = remember { History() }

    remember { // first launch: seed the project list with the demos
        if (store.names().isEmpty()) {
            listOf("SR latch" to "SR", "Full adder" to "FULL", "Half adder" to "HALF").forEach { (n, k) ->
                val c = Circuit(); buildDemo(c, k); store.save(n, c.serialize())
            }
        }
        true
    }

    val toggleTheme = { dark = !dark; store.setDark(dark) }

    MaterialTheme(colorScheme = if (dark) darkColorScheme(primary = Violet) else lightColorScheme(primary = Violet)) {
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

/* ============================ Home ============================ */
@Composable
fun HomeScreen(app: AppState, circuit: Circuit, history: History, store: Store, pal: Pal, onTheme: () -> Unit) {
    var version by remember { mutableStateOf(0) }
    val names = remember(version) { store.names() }
    var chooser by remember { mutableStateOf(false) }
    var nameStep by remember { mutableStateOf(false) }
    var templateKey by remember { mutableStateOf<String?>(null) }

    fun open(name: String, data: String) {
        circuit.load(data); history.clear(); app.name = name; app.screen = "editor"
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
                        val nodeCount = data.lines().count { it.startsWith("N|") }
                        val wireCount = data.lines().count { it.startsWith("W|") }
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
                                Text("$nodeCount elements, $wireCount wires", color = pal.sub, fontSize = 12.sp)
                            }
                            Text("Delete", color = pal.sub, fontSize = 12.sp,
                                modifier = Modifier.clickable { store.delete(n); version++ }.padding(8.dp))
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
                            Text(l.name, color = pal.ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text(l.idea, color = pal.sub, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                            Text(
                                l.table, color = pal.ink, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(top = 10.dp).clip(RoundedCornerShape(12.dp))
                                    .background(pal.bg).padding(10.dp)
                            )
                            Spacer(Modifier.height(10.dp))
                            Pill("Try it", true, pal) {
                                val c = Circuit(); buildDemo(c, l.key)
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
        NewProjectScreen(pal, onBack = { chooser = false }) { key -> templateKey = key; nameStep = true }
    }
    if (nameStep) {
        val tpl = lessons.firstOrNull { it.key == templateKey }
        NameDialog("Project name", tpl?.name ?: "", pal, { nameStep = false }) { n ->
            nameStep = false; chooser = false
            val name = n.ifBlank { "Untitled ${names.size + 1}" }
            circuit.clearAll()
            templateKey?.let { buildDemo(circuit, it) }
            history.clear(); app.name = name; app.screen = "editor"
            store.save(name, circuit.serialize()); version++
        }
    }
}

/** Full-screen chooser: an empty canvas, or a ready-made template. */
@Composable
fun NewProjectScreen(pal: Pal, onBack: () -> Unit, onPick: (String?) -> Unit) {
    val templates = remember { lessons.sortedBy { if (it.key in listOf("HALF", "FULL", "SR")) 0 else 1 } }
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
            item { Text("Start from scratch", color = pal.sub, fontSize = 13.sp) }
            item {
                val shape = RoundedCornerShape(20.dp)
                Row(
                    Modifier.fillMaxWidth().clip(shape).background(pal.card).border(1.5.dp, Violet, shape)
                        .clickable { onPick(null) }.padding(16.dp),
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
                        .clickable { onPick(l.key) }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(Brand), contentAlignment = Alignment.Center) {
                        Text(l.key, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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

    var tool by remember { mutableStateOf("move") }          // move | pan | erase | label
    var running by remember { mutableStateOf(true) }
    var showPalette by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Node?>(null) }
    var pan by remember { mutableStateOf(Offset(24f * d, 24f * d)) }
    var zoom by remember { mutableStateOf(1f) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var wireFrom by remember { mutableStateOf<Node?>(null) }
    var wireEnd by remember { mutableStateOf(Offset.Zero) }
    var dragNode by remember { mutableStateOf<Node?>(null) }
    var labelTarget by remember { mutableStateOf<Node?>(null) }
    var table by remember { mutableStateOf<TTResult?>(null) }
    var askName by remember { mutableStateOf(false) }

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

    fun wireAt(w: Offset): Wire? = circuit.wires.firstOrNull { wr ->
        val s = circuit.byId(wr.from)
        val t = circuit.byId(wr.to)
        if (s == null || t == null) false
        else {
            val c = bez(Dim.outPort(s), Dim.inPort(t, wr.port))
            (0..30).any { i -> (bezAt(c, i / 30f) - w).getDistance() < 10f / zoom }
        }
    }

    fun finishWire() {
        val from = wireFrom ?: return
        val hit = 16f / zoom
        for (n in circuit.nodes) {
            for (i in 0 until n.kind.ins) {
                if ((Dim.inPort(n, i) - wireEnd).getDistance() < hit) {
                    commit()
                    circuit.connect(from, n, i)
                    circuit.settle()
                    return
                }
            }
        }
    }

    fun zoomBy(f: Float) {
        val nz = (zoom * f).coerceIn(0.4f, 2.5f)
        val center = Offset(viewSize.width / 2f, viewSize.height / 2f)
        pan = center - (center - pan) * (nz / zoom)
        zoom = nz
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

    LaunchedEffect(running) { // Clock elements toggle while the simulation runs
        if (running) while (true) {
            delay(800)
            val clocks = circuit.nodes.filter { it.kind == Kind.CLOCK }
            if (clocks.isNotEmpty()) { clocks.forEach { it.out = !it.out }; circuit.settle() }
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
            Text("Save", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable { saveNow() }.padding(horizontal = 10.dp, vertical = 8.dp))
            RoundGlyph(if (pal.dark) "☀" else "☾") { onTheme() }
        }

        // tools
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("move" to "Move", "pan" to "Pan", "erase" to "Erase", "label" to "Label").forEach { (k, t) ->
                Pill(t, tool == k, pal) { tool = k }
            }
            Pill(if (running) "Pause" else "Run", !running, pal) { running = !running }
            Pill("Table", false, pal) { table = buildTruthTable(circuit) }
        }

        // canvas
        Box(Modifier.weight(1f).fillMaxWidth().onSizeChanged { viewSize = it }) {
            Canvas(
                Modifier.fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = { p ->
                                val n = nodeAt(toWorld(p))
                                if (tool == "move" && n?.kind == Kind.BUTTON) {
                                    n.out = true; circuit.settle()
                                    tryAwaitRelease()
                                    n.out = false; circuit.settle()
                                }
                            },
                            onTap = { p ->
                                val w = toWorld(p)
                                val n = nodeAt(w)
                                when (tool) {
                                    "erase" -> if (n != null) {
                                        commit(); circuit.remove(n); circuit.settle()
                                        if (selected == n) selected = null
                                    } else wireAt(w)?.let { commit(); circuit.wires.remove(it); circuit.settle() }
                                    "label" -> if (n != null) labelTarget = n
                                    else -> {
                                        selected = n
                                        if (n?.kind == Kind.SWITCH) { n.out = !n.out; circuit.settle() }
                                    }
                                }
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { p ->
                                val w = toWorld(p)
                                dragNode = null; wireFrom = null
                                if (tool == "move") {
                                    val hit = 16f / zoom
                                    val src = circuit.nodes.lastOrNull {
                                        !it.kind.isSink && (Dim.outPort(it) - w).getDistance() < hit
                                    }
                                    if (src != null) { wireFrom = src; wireEnd = w }
                                    else {
                                        dragNode = nodeAt(w); selected = dragNode
                                        if (dragNode != null) commit()
                                    }
                                }
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                if (wireFrom != null) wireEnd = toWorld(change.position)
                                else if (dragNode != null) {
                                    dragNode?.let { it.x += amount.x / (zoom * d); it.y += amount.y / (zoom * d) }
                                } else pan += amount
                            },
                            onDragEnd = { finishWire(); wireFrom = null; dragNode = null },
                            onDragCancel = { wireFrom = null; dragNode = null }
                        )
                    }
            ) {
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
                        val a = circuit.byId(w.from)
                        val b = circuit.byId(w.to)
                        if (a != null && b != null) drawWire(Dim.outPort(a), Dim.inPort(b, w.port), a.out, pal)
                    }
                    wireFrom?.let { drawWire(Dim.outPort(it), wireEnd, false, pal, dashed = true) }
                    circuit.nodes.forEach { drawNode(it, circuit, tm, pal, it == selected) }
                }
            }

            // zoom controls
            Row(
                Modifier.align(Alignment.BottomStart).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically
            ) {
                Pill("−", false, pal) { zoomBy(1f / 1.2f) }
                Pill("+", false, pal) { zoomBy(1.2f) }
                Text("${(zoom * 100).toInt()}%", color = pal.sub, fontSize = 12.sp)
            }

            if (!showPalette) {
                Text(
                    "+", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).size(58.dp).clip(CircleShape)
                        .background(Brand).clickable { showPalette = true }.padding(top = 6.dp)
                )
            } else {
                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).background(pal.card).padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Add element", color = pal.ink, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        Text("Close", color = Violet, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { showPalette = false }.padding(6.dp))
                    }
                    listOf("Gates", "Inputs", "Outputs").forEach { g ->
                        Text(g, color = pal.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Kind.values().filter { it.group == g }.forEach { k -> Pill(k.label, false, pal) { addNode(k) } }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.navigationBarsPadding())
    }

    labelTarget?.let { n ->
        NameDialog("Label", n.label, pal, { labelTarget = null }) { t -> commit(); n.label = t; labelTarget = null }
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
private fun bez(a: Offset, b: Offset): List<Offset> {
    val dx = max(40f, abs(b.x - a.x) / 2f)
    return listOf(a, Offset(a.x + dx, a.y), Offset(b.x - dx, b.y), b)
}

private fun bezAt(c: List<Offset>, t: Float): Offset {
    val u = 1f - t
    return c[0] * (u * u * u) + c[1] * (3f * u * u * t) + c[2] * (3f * u * t * t) + c[3] * (t * t * t)
}

private fun DrawScope.drawWire(a: Offset, b: Offset, on: Boolean, pal: Pal, dashed: Boolean = false) {
    val c = bez(a, b)
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

/** Text is drawn inside the zoom transform, so its size is given in world dp. */
private fun DrawScope.centerText(tm: TextMeasurer, text: String, c: Offset, size: Float, color: Color, bold: Boolean = true) {
    val style = TextStyle(
        color = color, fontSize = (size / (density * fontScale)).sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
    )
    val r = tm.measure(text, style)
    drawText(r, topLeft = c - Offset(r.size.width / 2f, r.size.height / 2f))
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

    when {
        k == Kind.LAMP -> {
            if (n.out) drawCircle(LampOn.copy(alpha = 0.3f), w * 0.75f, center)
            drawCircle(if (n.out) LampOn else pal.card, w / 2f, center)
            drawCircle(border, w / 2f, center, style = Stroke(bw))
            centerText(tm, if (n.out) "1" else "0", center, 20f, if (n.out) Color(0xFF1B1C3A) else pal.ink)
        }
        k == Kind.HEX -> {
            drawRoundRect(pal.card, tl, Size(w, h), corner)
            drawRoundRect(border, tl, Size(w, h), corner, style = Stroke(bw))
            val mask = SEGMENTS[c.hexValue(n)]
            val l = n.x + 16f; val t = n.y + 10f; val sw = 36f; val sh = 64f; val th = 6f
            fun seg(bit: Int, x: Float, y: Float, ww: Float, hh: Float) =
                drawRoundRect(if (mask and bit != 0) SegOn else pal.grid, Offset(x, y), Size(ww, hh), CornerRadius(3f))
            seg(1, l + 4f, t, sw - 8f, th)                           // a
            seg(2, l + sw - th, t + 4f, th, sh / 2f - 6f)            // b
            seg(4, l + sw - th, t + sh / 2f + 2f, th, sh / 2f - 6f)  // c
            seg(8, l + 4f, t + sh - th, sw - 8f, th)                 // d
            seg(16, l, t + sh / 2f + 2f, th, sh / 2f - 6f)           // e
            seg(32, l, t + 4f, th, sh / 2f - 6f)                     // f
            seg(64, l + 4f, t + sh / 2f - th / 2f, sw - 8f, th)      // g
        }
        k.isSource -> {
            if (n.out) drawRoundRect(Brand, tl, Size(w, h), corner) else drawRoundRect(pal.card, tl, Size(w, h), corner)
            drawRoundRect(border, tl, Size(w, h), corner, style = Stroke(bw))
            val fg = if (n.out) Color.White else pal.ink
            centerText(tm, if (n.out) "1" else "0", Offset(center.x, center.y - 5f), 22f, fg)
            centerText(tm, when (k) { Kind.SWITCH -> "SW"; Kind.BUTTON -> "BTN"; else -> "CLK" }, Offset(center.x, n.y + h - 9f), 9f, fg.copy(alpha = 0.75f))
        }
        else -> {
            drawRoundRect(pal.card, tl, Size(w, h), CornerRadius(14f))
            drawRoundRect(border, tl, Size(w, h), CornerRadius(14f), style = Stroke(bw))
            centerText(tm, k.label, center, 16f, pal.ink)
        }
    }

    for (i in 0 until k.ins) {
        val p = Dim.inPort(n, i)
        drawCircle(pal.card, 5.5f, p)
        drawCircle(if (c.inputOf(n, i)) Blue else pal.low, 4f, p)
    }
    if (!k.isSink) {
        val p = Dim.outPort(n)
        drawCircle(pal.card, 5.5f, p)
        drawCircle(if (n.out) Blue else pal.low, 4f, p)
    }
    if (n.label.isNotBlank()) centerText(tm, n.label, Offset(center.x, n.y - 9f), 11f, pal.sub)
}
