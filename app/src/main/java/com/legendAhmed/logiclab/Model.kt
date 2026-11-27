package com.legendAhmed.logiclab

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.max

/* ============================================================
 *  Types
 * ============================================================ */

/** NORMAL: gates, inputs, outputs only (VCC / GND pins disabled).
 *  FULL: adds Battery, GND, resistors, diodes, transistors; ICs must be powered. */
enum class Mode { NORMAL, FULL }

/** Role of a signal pin on a gate IC. Tap a pin in the Pan tool to cycle it. */
enum class Role(val ch: Char) {
    IN('I'), OUT('O'), NC('N');

    fun next(): Role = when (this) {
        IN -> OUT
        OUT -> NC
        NC -> IN
    }
}

enum class Kind(val label: String, val group: String, val chip: String = "") {
    // Gates: real 14-pin ICs
    AND("AND", "Gates", "7408"),
    OR("OR", "Gates", "7432"),
    NAND("NAND", "Gates", "7400"),
    NOR("NOR", "Gates", "7402"),
    XOR("XOR", "Gates", "7486"),
    XNOR("XNOR", "Gates", "74266"),
    NOT("NOT", "Gates", "7404"),
    BUF("BUF", "Gates", "7407"),

    // Inputs
    SWITCH("Switch", "Inputs"),
    BUTTON("Button", "Inputs"),
    CLOCK("Clock", "Inputs"),

    // Outputs
    LAMP("Lamp", "Outputs"),
    HEX("Hex", "Outputs"),

    // Full mode only
    BATTERY("Battery", "Power"),
    GND("GND", "Power"),
    RESISTOR("Resistor", "Parts"),
    DIODE("Diode", "Parts"),
    NPN("NPN", "Parts"),
    PNP("PNP", "Parts"),
    SPST("Switch 2p", "Parts");

    val isIC: Boolean get() = group == "Gates"
    val isSource: Boolean get() = this == SWITCH || this == BUTTON || this == CLOCK
    val fullOnly: Boolean get() = group == "Power" || group == "Parts"
}

fun defaultValue(k: Kind): Float = when (k) {
    Kind.BATTERY -> 5f
    Kind.RESISTOR -> 330f
    else -> 0f
}

/** Default pin roles of a gate IC (14 entries; indices 6 and 13 are the power pins). */
fun defaultRoles(k: Kind): List<Role> {
    val sig = if (k == Kind.NOT || k == Kind.BUF) {
        listOf(Role.IN, Role.OUT, Role.IN, Role.OUT, Role.IN, Role.OUT)
    } else {
        listOf(Role.IN, Role.IN, Role.OUT, Role.IN, Role.IN, Role.OUT)
    }
    return sig + Role.NC + sig + Role.NC
}

/** One gate inside an IC: the input pin indices followed by the output pin index. */
class GateUnit(val ins: List<Int>, val out: Int)

/** Scans pins 1..13 in order: every OUT pin closes a gate made of the IN pins before it. */
fun unitsOf(n: Node): List<GateUnit> {
    val res = ArrayList<GateUnit>()
    var cur = ArrayList<Int>()
    for (i in 0 until 14) {
        if (i == 6 || i == 13) continue
        when (n.roles[i]) {
            Role.IN -> cur.add(i)
            Role.OUT -> { res.add(GateUnit(cur, i)); cur = ArrayList() }
            Role.NC -> {}
        }
    }
    return res
}

fun gateFn(k: Kind, ins: List<Boolean>): Boolean {
    if (ins.isEmpty()) return false
    return when (k) {
        Kind.AND -> ins.all { it }
        Kind.OR -> ins.any { it }
        Kind.NAND -> !ins.all { it }
        Kind.NOR -> !ins.any { it }
        Kind.XOR -> ins.count { it } % 2 == 1
        Kind.XNOR -> ins.count { it } % 2 == 0
        Kind.NOT -> !ins.any { it }
        Kind.BUF -> ins.any { it }
        else -> false
    }
}

class Node(val id: Int, val kind: Kind, x: Float, y: Float, label: String = "") {
    var x by mutableStateOf(x)
    var y by mutableStateOf(y)
    var rot by mutableStateOf(0)                           // 0=0°, 1=90°, 2=180°, 3=270°
    var label by mutableStateOf(label)
    var on by mutableStateOf(false)                       // Switch / Button / Clock / 2-pin switch
    var value by mutableStateOf(defaultValue(kind))       // ohms (resistor) or volts (battery)
    val roles = mutableStateListOf<Role>().also { if (kind.isIC) it.addAll(defaultRoles(kind)) }

    // Results written by the simulator (the canvas redraws when Circuit.tick changes)
    var hi = BooleanArray(Dim.pinCount(kind))
    val memo = BooleanArray(14)      // remembered IC outputs (this is what makes latches work)
    var j1 = false                   // diode / LED / base-emitter junction conducting
    var lit = false
    var burnt = false
    var hexVal = 0
}

class Wire(val a: Int, val ap: Int, val b: Int, val bp: Int) {
    var high = false
}

/* ============================================================
 *  Geometry (world units = dp at zoom 1)
 * ============================================================ */
object Dim {
    fun w(k: Kind): Float = when {
        k.isIC -> 100f
        k == Kind.HEX -> 64f
        k == Kind.BATTERY -> 64f
        k == Kind.GND -> 40f
        k == Kind.RESISTOR -> 76f
        k == Kind.DIODE || k == Kind.SPST -> 72f
        k == Kind.NPN || k == Kind.PNP -> 60f
        else -> 52f
    }

    fun h(k: Kind): Float = when {
        k.isIC -> 132f
        k == Kind.HEX -> 84f
        k == Kind.BATTERY -> 44f
        k == Kind.GND -> 40f
        k == Kind.RESISTOR -> 28f
        k == Kind.DIODE || k == Kind.SPST -> 32f
        k == Kind.NPN || k == Kind.PNP -> 68f
        else -> 52f
    }

    fun pinCount(k: Kind): Int = when {
        k.isIC -> 14
        k == Kind.HEX -> 4
        k == Kind.LAMP || k == Kind.BATTERY || k == Kind.RESISTOR || k == Kind.DIODE || k == Kind.SPST -> 2
        k == Kind.NPN || k == Kind.PNP -> 3
        else -> 1
    }
    fun rw(n: Node): Float = if (n.rot % 2 == 1) h(n.kind) else w(n.kind)
    fun rh(n: Node): Float = if (n.rot % 2 == 1) w(n.kind) else h(n.kind)

    /** Base side before rotation: (-1, 0) left, (1, 0) right, (0, -1) top */
    /** -1 pin on the left, +1 on the right, 0 on top (GND). Used for the wire curve direction. */
    private fun baseDir(k: Kind, i: Int): Pair<Int, Int> = when {
        k.isIC -> if (i < 7) (-1 to 0) else (1 to 0)
        k.isSource -> (1 to 0)
        k == Kind.LAMP -> if (i == 0) (-1 to 0) else (1 to 0)
        k == Kind.HEX -> (-1 to 0)
        k == Kind.BATTERY -> if (i == 0) (1 to 0) else (-1 to 0)
        k == Kind.GND -> (0 to -1)
        else -> if (i == 0) (-1 to 0) else (1 to 0)
    }

    /** Returns rotated wire tangent direction (dx, dy) in {-1, 0, 1}. */
    fun dir(n: Node, i: Int): Pair<Int, Int> {
        val (dx, dy) = baseDir(n.kind, i)
        return when (n.rot % 4) {
            1 -> (-dy to dx)
            2 -> (-dx to -dy)
            3 -> (dy to -dx)
            else -> (dx to dy)
        }
    }

    /** DIP-14 layout: pins 1..7 down the left side, pins 8..14 up the right side. */
    fun local(k: Kind, i: Int): Offset {
        val w = w(k)
        val h = h(k)
        return when {
            k.isIC -> if (i < 7) Offset(0f, 18f + i * 16f) else Offset(w, 18f + (13 - i) * 16f)
            k.isSource -> Offset(w, h / 2f)
            k == Kind.LAMP -> if (i == 0) Offset(0f, h / 2f) else Offset(w, h / 2f)
            k == Kind.HEX -> Offset(0f, h * (i + 1) / 5f)
            k == Kind.BATTERY -> if (i == 0) Offset(w, h / 2f) else Offset(0f, h / 2f)
            k == Kind.GND -> Offset(w / 2f, 0f)
            k == Kind.NPN || k == Kind.PNP -> when (i) {
                0 -> Offset(0f, h / 2f)
                1 -> Offset(w, 10f)
                else -> Offset(w, h - 10f)
            }
            else -> if (i == 0) Offset(0f, h / 2f) else Offset(w, h / 2f)
        }
    }

    /** World pin position accounting for 90° clockwise rotation around the node's bounding box. */

    fun pin(n: Node, i: Int): Offset {
        val w = w(n.kind)
        val h = h(n.kind)
        val p = local(n.kind, i)
        val rp = when (n.rot % 4) {
            1 -> Offset(h - p.y, p.x)
            2 -> Offset(w - p.x, h - p.y)
            3 -> Offset(p.y, w - p.x)
            else -> p
        }
        return rp + Offset(n.x, n.y)
    }

    fun pinName(k: Kind, i: Int): String = when {
        k.isIC -> (i + 1).toString()
        k == Kind.LAMP || k == Kind.BATTERY -> if (i == 0) "+" else "-"
        k == Kind.DIODE -> if (i == 0) "A" else "K"
        k == Kind.NPN || k == Kind.PNP -> listOf("B", "C", "E")[i]
        else -> ""
    }
}

/* ============================================================
 *  Circuit + simulation
 * ============================================================ */
private const val NONE = -2

private class Nets {
    private val parent = HashMap<Int, Int>()
    fun find(x: Int): Int {
        var r = x
        while (true) {
            val p = parent[r]
            if (p == null || p == r) return r
            r = p
        }
    }

    fun union(a: Int, b: Int) {
        val ra = find(a)
        val rb = find(b)
        if (ra != rb) parent[ra] = rb
    }
}

private fun solve(a: Array<DoubleArray>, b: DoubleArray): DoubleArray? {
    val n = b.size
    for (i in 0 until n) {
        var p = i
        for (r in i + 1 until n) if (abs(a[r][i]) > abs(a[p][i])) p = r
        if (abs(a[p][i]) < 1e-18) return null
        if (p != i) {
            val t = a[i]; a[i] = a[p]; a[p] = t
            val tb = b[i]; b[i] = b[p]; b[p] = tb
        }
        for (r in i + 1 until n) {
            val f = a[r][i] / a[i][i]
            if (f != 0.0) {
                for (c in i until n) a[r][c] -= f * a[i][c]
                b[r] -= f * b[i]
            }
        }
    }
    val x = DoubleArray(n)
    for (i in n - 1 downTo 0) {
        var s = b[i]
        for (c in i + 1 until n) s -= a[i][c] * x[c]
        x[i] = s / a[i][i]
    }
    return x
}

class Circuit {
    val nodes = mutableStateListOf<Node>()
    val wires = mutableStateListOf<Wire>()
    var mode1 by mutableStateOf(Mode.NORMAL)
    var tick by mutableStateOf(0)
    private var nextId = 1

    private fun key(id: Int, pin: Int) = id * 32 + pin

    fun add(kind: Kind, x: Float, y: Float, label: String = ""): Node =
        Node(nextId++, kind, x, y, label).also { nodes.add(it) }

    fun byId(id: Int): Node? = nodes.firstOrNull { it.id == id }

    fun remove(n: Node) {
        wires.removeAll { it.a == n.id || it.b == n.id }
        nodes.remove(n)
    }

    fun clearAll() { wires.clear(); nodes.clear(); nextId = 1 }

    fun hasFullParts(): Boolean = nodes.any { it.kind.fullOnly }

    /** NORMAL disables VCC/GND pins forever, FULL enables them. */
    fun pinEnabled(n: Node, i: Int): Boolean = when {
        n.kind.fullOnly -> mode1 == Mode.FULL
        n.kind.isIC -> (i != 6 && i != 13) || mode1 == Mode.FULL
        n.kind == Kind.LAMP -> i == 0 || mode1 == Mode.FULL
        else -> true
    }

    fun setMode(m: Mode) {
        mode1 = m
        wires.removeAll { w ->
            val a = byId(w.a)
            val b = byId(w.b)
            a == null || b == null || !pinEnabled(a, w.ap) || !pinEnabled(b, w.bp)
        }
        settle()
    }

    fun connect(a: Node, ap: Int, b: Node, bp: Int): Boolean {
        if (a.id == b.id && ap == bp) return false
        if (!pinEnabled(a, ap) || !pinEnabled(b, bp)) return false
        val dup = wires.any {
            (it.a == a.id && it.ap == ap && it.b == b.id && it.bp == bp) ||
                    (it.a == b.id && it.ap == bp && it.b == a.id && it.bp == ap)
        }
        if (dup) return false
        wires.add(Wire(a.id, ap, b.id, bp))
        return true
    }

    fun settle() {
        if (mode1 == Mode.NORMAL) settleDigital() else settleAnalog()
        tick++
    }

    /* ---------------- NORMAL: logic levels only ---------------- */
    private fun settleDigital() {
        val nets = Nets()
        for (w in wires) nets.union(key(w.a, w.ap), key(w.b, w.bp))
        val ics = nodes.filter { it.kind.isIC }
        val units = ics.associateWith { unitsOf(it) }

        fun highSet(): HashSet<Int> {
            val s = HashSet<Int>()
            for (n in nodes) {
                if (n.kind.isSource && n.on) s.add(nets.find(key(n.id, 0)))
                if (n.kind.isIC) for (u in units[n]!!) if (n.memo[u.out]) s.add(nets.find(key(n.id, u.out)))
            }
            return s
        }

        // Gates are updated one after another, so feedback circuits (latches) settle and hold state.
        for (iter in 0 until 100) {
            var changed = false
            for (n in ics) {
                for (u in units[n]!!) {
                    val high = highSet()
                    val ins = u.ins.map { high.contains(nets.find(key(n.id, it))) }
                    val v = gateFn(n.kind, ins)
                    if (v != n.memo[u.out]) { n.memo[u.out] = v; changed = true }
                }
            }
            if (!changed) break
        }

        val high = highSet()
        for (n in nodes) {
            for (i in n.hi.indices) n.hi[i] = high.contains(nets.find(key(n.id, i)))
            n.lit = n.kind == Kind.LAMP && n.hi[0]
            n.burnt = false
            if (n.kind == Kind.HEX) n.hexVal = hexOf(n)
        }
        for (w in wires) w.high = high.contains(nets.find(key(w.a, w.ap)))
    }

    private fun hexOf(n: Node): Int =
        (if (n.hi[0]) 1 else 0) or (if (n.hi[1]) 2 else 0) or (if (n.hi[2]) 4 else 0) or (if (n.hi[3]) 8 else 0)

    /* ---------------- FULL: nodal analysis of the real circuit ---------------- */
    private fun settleAnalog() {
        val nets = Nets()
        val wired = HashSet<Int>()
        for (w in wires) {
            nets.union(key(w.a, w.ap), key(w.b, w.bp))
            wired.add(key(w.a, w.ap))
            wired.add(key(w.b, w.bp))
        }
        // reference (0 V) net: a wired GND, otherwise the minus of a battery
        var refRoot = -1
        val gndNode = nodes.firstOrNull { it.kind == Kind.GND && wired.contains(key(it.id, 0)) }
        if (gndNode != null) {
            refRoot = nets.find(key(gndNode.id, 0))
        } else {
            val bat = nodes.firstOrNull { it.kind == Kind.BATTERY && wired.contains(key(it.id, 1)) }
            if (bat != null) refRoot = nets.find(key(bat.id, 1))
        }
        val idx = HashMap<Int, Int>()
        for (k in wired) {
            val r = nets.find(k)
            if (r != refRoot && !idx.containsKey(r)) idx[r] = idx.size
        }
        val size = idx.size
        val pnet = HashMap<Int, IntArray>()
        for (nd in nodes) {
            pnet[nd.id] = IntArray(Dim.pinCount(nd.kind)) { i ->
                val k = key(nd.id, i)
                if (!wired.contains(k)) NONE else {
                    val r = nets.find(k)
                    if (r == refRoot) -1 else idx[r]!!
                }
            }
        }

        var x = DoubleArray(size)
        fun volt(i: Int): Double = if (i < 0) 0.0 else x[i]
        val powered = HashMap<Int, Boolean>()

        fun upd(nd: Node, p: Int, q: Int, vf: Double): Boolean {
            val on = p != NONE && q != NONE && volt(p) - volt(q) > vf
            val ch = on != nd.j1
            nd.j1 = on
            return ch
        }

        for (iter in 0 until 150) {
            val a = Array(size) { DoubleArray(size) }
            val b = DoubleArray(size)
            for (i in 0 until size) a[i][i] += 1e-9

            fun g(p: Int, q: Int, gg: Double) {
                if (p == NONE || q == NONE) return
                if (p >= 0) a[p][p] += gg
                if (q >= 0) a[q][q] += gg
                if (p >= 0 && q >= 0) { a[p][q] -= gg; a[q][p] -= gg }
            }

            fun cur(p: Int, q: Int, amps: Double) {
                if (p == NONE || q == NONE) return
                if (p >= 0) b[p] += amps
                if (q >= 0) b[q] -= amps
            }

            fun diode(p: Int, q: Int, on: Boolean, vf: Double, gg: Double) {
                if (on) { g(p, q, gg); cur(p, q, gg * vf) } else g(p, q, 1e-9)
            }

            for (nd in nodes) {
                val p = pnet[nd.id]!!
                when (nd.kind) {
                    Kind.BATTERY -> { g(p[0], p[1], 20.0); cur(p[0], p[1], 20.0 * nd.value) }
                    Kind.RESISTOR -> g(p[0], p[1], 1.0 / max(nd.value.toDouble(), 0.001))
                    Kind.SPST -> g(p[0], p[1], if (nd.on) 1000.0 else 1e-9)
                    Kind.DIODE -> diode(p[0], p[1], nd.j1, 0.7, 2.0)
                    Kind.LAMP -> diode(p[0], p[1], nd.j1, 1.9, 0.1)
                    Kind.NPN -> { diode(p[0], p[2], nd.j1, 0.7, 0.1); g(p[1], p[2], if (nd.j1) 0.2 else 1e-9) }
                    Kind.PNP -> { diode(p[2], p[0], nd.j1, 0.7, 0.1); g(p[2], p[1], if (nd.j1) 0.2 else 1e-9) }
                    Kind.SWITCH, Kind.BUTTON, Kind.CLOCK -> { g(p[0], -1, 0.1); if (nd.on) cur(p[0], -1, 0.5) }
                    else -> if (nd.kind.isIC) {
                        val vcc = volt(p[13])
                        val gnd = volt(p[6])
                        val pw = p[13] != NONE && p[6] != NONE && vcc - gnd > 3.0
                        powered[nd.id] = pw
                        if (pw) for (u in unitsOf(nd)) {
                            g(p[u.out], p[6], 0.02)
                            if (nd.memo[u.out]) cur(p[u.out], p[6], 0.02 * (vcc - gnd))
                        }
                    }
                }
            }

            val sol = solve(a, b) ?: break
            x = sol

            var changed = false
            for (nd in nodes) {
                val p = pnet[nd.id]!!
                when (nd.kind) {
                    Kind.DIODE -> changed = upd(nd, p[0], p[1], 0.7) || changed
                    Kind.LAMP -> changed = upd(nd, p[0], p[1], 1.9) || changed
                    Kind.NPN -> changed = upd(nd, p[0], p[2], 0.7) || changed
                    Kind.PNP -> changed = upd(nd, p[2], p[0], 0.7) || changed
                    else -> if (nd.kind.isIC) {
                        val pw = p[13] != NONE && p[6] != NONE && volt(p[13]) - volt(p[6]) > 3.0
                        if (pw != (powered[nd.id] ?: false)) { powered[nd.id] = pw; changed = true }
                    }
                }
            }
            if (changed) continue

            // logic: update one gate per solve so that feedback circuits settle in order
            var logic = false
            outer@ for (nd in nodes) {
                if (!nd.kind.isIC) continue
                val p = pnet[nd.id]!!
                val pw = powered[nd.id] ?: false
                val gnd = volt(p[6])
                val thr = gnd + 0.5 * (volt(p[13]) - gnd)
                for (u in unitsOf(nd)) {
                    val v = pw && gateFn(nd.kind, u.ins.map { volt(p[it]) > thr })
                    if (v != nd.memo[u.out]) { nd.memo[u.out] = v; logic = true; break@outer }
                }
            }
            if (!logic) break
        }

        for (nd in nodes) {
            val p = pnet[nd.id]!!
            for (i in nd.hi.indices) nd.hi[i] = p[i] != NONE && volt(p[i]) > 2.0
            nd.lit = false
            nd.burnt = false
            if (nd.kind == Kind.LAMP && nd.j1) {
                val vDrop = volt(p[0]) - volt(p[1])
                val amps = 0.1 * (vDrop - 1.9)
                nd.lit = amps > 0.001
                // Standard safe LED current is ~20mA (e.g., 5V with 330Ω ≈ 9.4mA).
                // Exceeding ~25mA (or >2.15V directly across the lamp) triggers the overvoltage warning.
                nd.burnt = amps > 0.025
            }
            if (nd.kind == Kind.HEX) nd.hexVal = hexOf(nd)
        }
        for (w in wires) w.high = volt(pnet[w.a]!![w.ap]) > 2.0
    }

    /* ---------------- save / load ---------------- */
    fun serialize(): String = buildString {
        appendLine("M|${mode1.name}")
        nodes.forEach { n ->
            val roles = if (n.kind.isIC) n.roles.joinToString("") { r -> r.ch.toString() } else "-"
            val lab = n.label.replace("|", " ").replace("\n", " ")
            appendLine("N|${n.id}|${n.kind.name}|${n.x}|${n.y}|${if (n.on) 1 else 0}|${n.value}|$roles|${n.rot}|$lab")
        }
        wires.forEach { appendLine("W|${it.a}|${it.ap}|${it.b}|${it.bp}") }
    }

    fun load(text: String) {
        clearAll()
        mode1 = Mode.NORMAL
        var maxId = 0
        text.lines().forEach { line ->
            val p = line.split("|")
            when (p.firstOrNull()) {
                "M" -> {
                    mode1 = runCatching { Mode.valueOf(p[1]) }.getOrDefault(Mode.NORMAL)
                }
                "N" -> {
                    if (p.size >= 8) {
                        val k = runCatching { Kind.valueOf(p[2]) }.getOrNull()
                        val id = p[1].toIntOrNull()
                        if (k != null && id != null) {
                            val hasRot = p.size >= 9 && p[8].toIntOrNull() != null
                            val lab = if (hasRot) p.drop(9).joinToString(" ") else p.drop(8).joinToString(" ")
                            val n = Node(id, k, p[3].toFloatOrNull() ?: 0f, p[4].toFloatOrNull() ?: 0f, lab)
                            n.on = p[5] == "1"
                            n.value = p[6].toFloatOrNull() ?: defaultValue(k)
                            if (hasRot) n.rot = (p[8].toIntOrNull() ?: 0) and 3
                            if (k.isIC && p[7].length == 14) {
                                p[7].forEachIndexed { i, ch ->
                                    n.roles[i] = when (ch) {
                                        'I' -> Role.IN
                                        'O' -> Role.OUT
                                        else -> Role.NC
                                    }
                                }
                            }
                            nodes.add(n)
                            maxId = max(maxId, id)
                        }
                    }
                }
                "W" -> {
                    if (p.size >= 5) {
                        val a = p[1].toIntOrNull()
                        val ap = p[2].toIntOrNull()
                        val b = p[3].toIntOrNull()
                        val bp = p[4].toIntOrNull()
                        if (a != null && ap != null && b != null && bp != null) wires.add(Wire(a, ap, b, bp))
                    }
                }
                else -> {}
            }
        }
        nextId = maxId + 1
        wires.removeAll { w ->
            val a = byId(w.a)
            val b = byId(w.b)
            a == null || b == null || w.ap !in 0 until Dim.pinCount(a.kind) || w.bp !in 0 until Dim.pinCount(b.kind)
        }
        settle()
    }
}

/* ============================================================
 *  Undo / redo, storage, app state
 * ============================================================ */
class History {
    private val undoStack = ArrayDeque<String>()
    private val redoStack = ArrayDeque<String>()
    var canUndo by mutableStateOf(false)
    var canRedo by mutableStateOf(false)

    private fun sync() { canUndo = undoStack.isNotEmpty(); canRedo = redoStack.isNotEmpty() }

    fun push(before: String) {
        undoStack.addLast(before)
        if (undoStack.size > 60) undoStack.removeFirst()
        redoStack.clear()
        sync()
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
    private val sp = ctx.getSharedPreferences("logic_lab_v3", Context.MODE_PRIVATE)
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

/* ============================================================
 *  Values like 4.7k / 1M
 * ============================================================ */
fun formatValue(v: Float): String {
    fun t(x: Float): String = if (x == x.toInt().toFloat()) x.toInt().toString() else "%.1f".format(x)
    return when {
        v >= 1_000_000f -> t(v / 1_000_000f) + "M"
        v >= 1000f -> t(v / 1000f) + "k"
        else -> t(v)
    }
}

fun parseValue(s: String): Float? {
    val t = s.trim().lowercase().removeSuffix("v").removeSuffix("ohm").removeSuffix("Ω".lowercase())
    val mult = when {
        t.endsWith("m") -> 1_000_000f
        t.endsWith("k") -> 1000f
        else -> 1f
    }
    val num = t.trimEnd('k', 'm').toFloatOrNull() ?: return null
    return num * mult
}

/* ============================================================
 *  Demos and lessons (templates)
 * ============================================================ */
class Lesson(val name: String, val idea: String, val table: String, val key: String, val mode: Mode = Mode.NORMAL)

val lessons = listOf(
    Lesson("AND", "Output is 1 only when every input is 1.", "A B | Y\n0 0 | 0\n0 1 | 0\n1 0 | 0\n1 1 | 1", "AND"),
    Lesson("OR", "Output is 1 when at least one input is 1.", "A B | Y\n0 0 | 0\n0 1 | 1\n1 0 | 1\n1 1 | 1", "OR"),
    Lesson("NOT", "Flips the input: 0 becomes 1 and 1 becomes 0.", "A | Y\n0 | 1\n1 | 0", "NOT"),
    Lesson("XOR", "Output is 1 when the two inputs are different.", "A B | Y\n0 0 | 0\n0 1 | 1\n1 0 | 1\n1 1 | 0", "XOR"),
    Lesson("NAND", "An AND followed by a NOT. A universal gate.", "A B | Y\n0 0 | 1\n0 1 | 1\n1 0 | 1\n1 1 | 0", "NAND"),
    Lesson("NOR", "An OR followed by a NOT. Also universal.", "A B | Y\n0 0 | 1\n0 1 | 0\n1 0 | 0\n1 1 | 0", "NOR"),
    Lesson("XNOR", "Output is 1 when the two inputs are equal.", "A B | Y\n0 0 | 1\n0 1 | 0\n1 0 | 0\n1 1 | 1", "XNOR"),
    Lesson("Half adder", "Adds two bits. XOR gives the sum, AND gives the carry.", "A B | S C\n0 0 | 0 0\n0 1 | 1 0\n1 0 | 1 0\n1 1 | 0 1", "HALF"),
    Lesson(
        "Full adder", "Adds two bits plus a carry-in, using one XOR IC, one AND IC and one OR IC.",
        "A B Ci | S Co\n0 0 0 | 0 0\n0 0 1 | 1 0\n0 1 0 | 1 0\n0 1 1 | 0 1\n1 0 0 | 1 0\n1 0 1 | 0 1\n1 1 0 | 0 1\n1 1 1 | 1 1", "FULLADD"
    ),
    Lesson(
        "SR latch", "Two NOR gates of one 7402 remember a bit. Pulse S to set Q, pulse R to reset it.",
        "S R | Q\n0 0 | hold\n1 0 | 1\n0 1 | 0\n1 1 | invalid", "SR"
    ),
    Lesson(
        "LED + resistor", "A battery, a resistor and an LED in a closed loop. The resistor limits the current.",
        "Battery+ -> R -> LED+\nLED- -> GND\nBattery- -> GND", "LEDRES", Mode.FULL
    ),
    Lesson(
        "Transistor switch", "A small base current turns the NPN on, so the LED lights. Flip the switch to test it.",
        "Switch -> Rb -> B\nBattery+ -> R -> LED -> C\nE -> GND", "NPNSW", Mode.FULL
    ),
    Lesson(
        "Powered AND IC", "In Full mode an IC needs power: Battery+ to pin 14 (VCC) and GND to pin 7.",
        "VCC (14) = Battery+\nGND (7) = GND\nA, B = pins 1, 2\nY = pin 3", "POWAND", Mode.FULL
    )
)

/** Pin numbers for ICs are 1-based (as printed on a real chip); other parts use 0-based pin indexes. */
private fun Circuit.wire(a: Node, ap: Int, b: Node, bp: Int) {
    connect(a, if (a.kind.isIC) ap - 1 else ap, b, if (b.kind.isIC) bp - 1 else bp)
}

fun buildDemo(c: Circuit, key: String) {
    c.clearAll()
    c.mode1 = lessons.firstOrNull { it.key == key }?.mode ?: Mode.NORMAL
    when (key) {
        "HALF" -> {
            val a = c.add(Kind.SWITCH, 10f, 60f, "A")
            val b = c.add(Kind.SWITCH, 10f, 200f, "B")
            val x = c.add(Kind.XOR, 120f, 0f)
            val n = c.add(Kind.AND, 120f, 150f)
            val s = c.add(Kind.LAMP, 270f, 50f, "Sum")
            val co = c.add(Kind.LAMP, 270f, 200f, "Carry")
            c.wire(a, 0, x, 1); c.wire(b, 0, x, 2)
            c.wire(a, 0, n, 1); c.wire(b, 0, n, 2)
            c.wire(x, 3, s, 0); c.wire(n, 3, co, 0)
        }
        "FULLADD" -> {
            val a = c.add(Kind.SWITCH, 10f, 20f, "A")
            val b = c.add(Kind.SWITCH, 10f, 110f, "B")
            val ci = c.add(Kind.SWITCH, 10f, 210f, "Cin")
            val xr = c.add(Kind.XOR, 110f, 0f)
            val an = c.add(Kind.AND, 110f, 160f)
            val orr = c.add(Kind.OR, 260f, 90f)
            val s = c.add(Kind.LAMP, 380f, 20f, "Sum")
            val co = c.add(Kind.LAMP, 380f, 130f, "Cout")
            c.wire(a, 0, xr, 1); c.wire(b, 0, xr, 2)
            c.wire(xr, 3, xr, 4); c.wire(ci, 0, xr, 5)
            c.wire(xr, 6, s, 0)
            c.wire(a, 0, an, 1); c.wire(b, 0, an, 2)
            c.wire(xr, 3, an, 4); c.wire(ci, 0, an, 5)
            c.wire(an, 3, orr, 1); c.wire(an, 6, orr, 2)
            c.wire(orr, 3, co, 0)
        }
        "SR" -> {
            val r = c.add(Kind.SWITCH, 10f, 20f, "R")
            val s = c.add(Kind.SWITCH, 10f, 170f, "S")
            val ic = c.add(Kind.NOR, 120f, 20f)
            val q = c.add(Kind.LAMP, 270f, 20f, "Q")
            val qb = c.add(Kind.LAMP, 270f, 170f, "Q'")
            c.wire(r, 0, ic, 1); c.wire(ic, 6, ic, 2)
            c.wire(s, 0, ic, 4); c.wire(ic, 3, ic, 5)
            c.wire(ic, 3, q, 0); c.wire(ic, 6, qb, 0)
        }
        "NOT", "BUF" -> {
            val a = c.add(Kind.SWITCH, 10f, 60f, "A")
            val g = c.add(Kind.valueOf(key), 130f, 30f)
            val l = c.add(Kind.LAMP, 290f, 70f, "Y")
            c.wire(a, 0, g, 1); c.wire(g, 2, l, 0)
        }
        "LEDRES" -> {
            val bat = c.add(Kind.BATTERY, 10f, 60f, "5V")
            val r = c.add(Kind.RESISTOR, 110f, 40f, "R")
            val led = c.add(Kind.LAMP, 230f, 28f, "LED")
            val gnd = c.add(Kind.GND, 120f, 140f)
            c.wire(bat, 0, r, 0); c.wire(r, 1, led, 0)
            c.wire(led, 1, gnd, 0); c.wire(bat, 1, gnd, 0)
        }
        "NPNSW" -> {
            val bat = c.add(Kind.BATTERY, 10f, 200f, "5V")
            val gnd = c.add(Kind.GND, 130f, 250f)
            val sw = c.add(Kind.SWITCH, 10f, 90f, "IN")
            val rb = c.add(Kind.RESISTOR, 96f, 100f, "Rb"); rb.value = 10000f
            val tr = c.add(Kind.NPN, 190f, 80f)
            val rl = c.add(Kind.RESISTOR, 270f, 0f, "R"); rl.value = 330f
            val led = c.add(Kind.LAMP, 290f, 60f, "LED")
            c.wire(sw, 0, rb, 0); c.wire(rb, 1, tr, 0)
            c.wire(bat, 0, rl, 0); c.wire(rl, 1, led, 0)
            c.wire(led, 1, tr, 1); c.wire(tr, 2, gnd, 0)
            c.wire(bat, 1, gnd, 0)
        }
        "POWAND" -> {
            val bat = c.add(Kind.BATTERY, 10f, 190f, "5V")
            val gnd = c.add(Kind.GND, 100f, 270f)
            val a = c.add(Kind.SWITCH, 10f, 20f, "A")
            val b = c.add(Kind.SWITCH, 10f, 100f, "B")
            val ic = c.add(Kind.AND, 130f, 0f)
            val rl = c.add(Kind.RESISTOR, 260f, 60f, "R"); rl.value = 330f
            val led = c.add(Kind.LAMP, 350f, 48f, "Y")
            c.wire(a, 0, ic, 1); c.wire(b, 0, ic, 2)
            c.wire(bat, 0, ic, 14); c.wire(ic, 7, gnd, 0); c.wire(bat, 1, gnd, 0)
            c.wire(ic, 3, rl, 0); c.wire(rl, 1, led, 0); c.wire(led, 1, gnd, 0)
        }
        else -> { // AND OR NAND NOR XOR XNOR
            val k = Kind.valueOf(key)
            val a = c.add(Kind.SWITCH, 10f, 30f, "A")
            val b = c.add(Kind.SWITCH, 10f, 170f, "B")
            val g = c.add(k, 130f, 30f)
            val l = c.add(Kind.LAMP, 290f, 80f, "Y")
            c.wire(a, 0, g, 1); c.wire(b, 0, g, 2); c.wire(g, 3, l, 0)
        }
    }
    c.settle()
}

/* ============================================================
 *  Truth table
 * ============================================================ */
class TTResult(val error: String?, val heads: List<String>, val nIn: Int, val rows: List<List<String>>, val totalRows: Int = 0)
fun buildTruthTable(c: Circuit, page: Int = 0, pageSize: Int = 64): TTResult {
    val ins = c.nodes.filter { it.kind.isSource }
    val outs = c.nodes.filter { it.kind == Kind.LAMP || it.kind == Kind.HEX }
    if (ins.isEmpty() || outs.isEmpty())
        return TTResult("Add at least one input and one output (lamp or display).", emptyList(), 0, emptyList())
    if (ins.size > 11)
        return TTResult("Truth tables support up to 11 inputs. This circuit has ${ins.size}.", emptyList(), 0, emptyList())

    val totalRows = 1 shl ins.size
    val startMask = (page * pageSize).coerceIn(0, totalRows)
    val endMask = (startMask + pageSize).coerceIn(startMask, totalRows)

    val inNames = ins.mapIndexed { i, n -> n.label.ifBlank { ('A' + i).toString() } }
    val outNames = outs.mapIndexed { i, n -> n.label.ifBlank { "Y${i + 1}" } }
    val saved = ins.map { it.on }
    val rows = ArrayList<List<String>>(endMask - startMask)

    for (mask in startMask until endMask) {
        ins.forEachIndexed { i, n -> n.on = ((mask shr (ins.size - 1 - i)) and 1) == 1 }
        c.settle()
        val row = ins.map { if (it.on) "1" else "0" } +
                outs.map {
                    if (it.kind == Kind.HEX) it.hexVal.toString(16).uppercase()
                    else if (it.lit) "1" else "0"
                }
        rows.add(row)
    }

    ins.forEachIndexed { i, n -> n.on = saved[i] }
    c.settle()
    return TTResult(null, inNames + outNames, ins.size, rows, totalRows)
}
