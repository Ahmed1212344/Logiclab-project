# ⚡ Logic Lab — Interactive Digital & Analog Circuit Simulator

**Logic Lab** is a native, offline-first Android application built with **Kotlin** and **Jetpack Compose** that allows students and electronics enthusiasts to design, simulate, and test digital logic circuits and basic analog electronics directly on their mobile devices.

## 📱 Features

### 1. Dual Simulation Modes

* **Normal Mode (Pure Digital Logic):**

  * Focuses on pure logic behavior (`0` and `1`) using standard 14-pin DIP integrated circuits (`7408` AND, `7432` OR, `7400` NAND, `7402` NOR, `7486` XOR, `74266` XNOR, `7404` NOT, `7407` BUF).

  * Automatically disables `VCC` (pin 14) and `GND` (pin 7) so users can focus purely on logic gates, switches, push buttons, clocks, lamps, and 7-segment hex displays.

* **Full Mode (Modified Nodal Analog Simulation):**

  * Simulates real electrical behavior including **Batteries**, **Ground (GND)**, **Resistors** (supporting `k` and `M` multipliers like `4.7k` or `1M`), **Diodes**, **SPST Switches**, and **NPN / PNP Transistors**.

  * Requires 74xx ICs to be properly powered through pins 14 (`VCC`) and 7 (`GND`).

  * **Overcurrent / Overvoltage Protection Warning:** Automatically calculates real branch currents; if a lamp/LED receives excessive voltage/current (e.g., high battery voltage without a proper current-limiting resistor), the lamp stays lit (`1`) while displaying a red warning badge (`!`).

### 2. Interactive Infinite Canvas & Multi-Touch Gestures

* **Two-Finger Pinch-to-Zoom & Pan:** Smooth centroid-based zooming (`25%` to `400%`) and panning across the workspace, plus one-finger canvas panning on empty space and a **Fit-to-Screen** button.

* **Four Dedicated Workspace Tools:**

  * **Move:** Drag components freely across the grid; tap switches to toggle or hold push buttons.

  * **Pan / Wire:** Drag between pins to draw smooth cubic-Bezier wires that glow with a gradient when carrying a `HIGH` (`1`) signal. Tap IC pins to cycle pin roles (`In`, `Out`, `NC`).

  * **Erase:** Tap any component or wire to remove it.

  * **Label:** Rename components or customize resistance ($\Omega$) and battery voltage ($V$) values.

### 3. Educational & Productivity Tools

* **Automatic Truth Table Generator:** Evaluates all $2^n$ input combinations (up to 6 inputs) in real time and displays a formatted truth table without disrupting your current switch states.

* **Interactive Lessons & Templates:** Built-in step-by-step lessons and ready-to-run templates (*Half Adder*, *Full Adder*, *SR Latch*, *LED + Resistor*, *Transistor Switch*, and *Powered AND IC*).

* **Undo / Redo History:** 60-step state snapshot history (`History`) for fearless experimentation.

* **Offline Local Persistence:** Saves and loads projects locally via `SharedPreferences` using a lightweight custom serialization format.

* **Adaptive Light & Dark Themes:** Automatically syncs with the Android system theme while also providing an instant in-app Light/Dark mode toggle without losing editor state.

## 🏗️ Project Architecture

```
com.legendAhmed.logiclab
├── MainActivity.kt          # App entry point, Navigation, HomeScreen, EditorScreen, Canvas rendering & gestures
├── Model.kt                 # Data models (Node, Wire, Kind), Geometry (Dim), Digital & Nodal Solvers, Store & History
└── ui.theme/
    ├── Color.kt             # Brand gradients, signal colors, and dynamic Light/Dark palette (Pal)
    ├── Theme.kt             # Material 3 LogicLabTheme wrapper & system status bar styling
    └── Type.kt              # Typography definitions

```

### How the Simulation Engine Works (`Model.kt`)

1. **Digital Solver (`settleDigital`):** Uses a Disjoint-Set (`Nets`) structure to merge connected pins into electrical nets and iteratively evaluates IC gate units (`memo[14]`) until outputs converge—allowing sequential feedback circuits like **SR Latches** to hold state reliably.

2. **Analog Solver (`settleAnalog`):** Builds a conductance matrix $A$ and current source vector $b$ using **Modified Nodal Analysis (MNA)** and solves $Ax = b$ via **Gaussian elimination with partial pivoting (`solve`)**, iteratively resolving non-linear junctions (diodes, LEDs, and BJT base-emitter junctions).

## 🛠️ Tech Stack

* **Language:** Kotlin

* **UI Framework:** Jetpack Compose (Material Design 3)

* **Graphics & Gestures:** Compose `Canvas`, `DrawScope`, `TextMeasurer`, `withTransform`, `pointerInput`

* **Concurrency:** Kotlin Coroutines (`LaunchedEffect` for real-time clock pulses)

* **Local Storage:** Android `SharedPreferences`

* **Minimum SDK:** Android 7.0 (API 24) / Target SDK 35

## 🚀 Getting Started

1. **Clone the repository:**

   ```
   git clone https://github.com/your-username/logic-lab.git
   
   ```

2. **Open in Android Studio:**

   * Launch **Android Studio** (Ladybug or newer recommended).

   * Select **File > Open** and choose the project root directory.

3. **Sync & Run:**

   * Let Gradle sync dependencies automatically.

   * Run the `app` configuration on an Android emulator or physical device.

## 👥 Development Team

* **Level 3 — Computer Science Department - MET**

* Built with Native Android & Jetpack Compose.