# Product Requirement Document (PRD): AutoComm System

---

## 1. Executive Summary

**AutoComm** is a vehicle-to-vehicle and vehicle-to-pedestrian communication system designed for dedicated in-car operation. It combines a mounted 6.8-inch Android smartphone controller (**Motorola Moto G Stylus 2021 4G**) with two interior-mounted, flexible RGB LED marquee displays (**16x64 LED matrices** mounted on front windshield and rear window). 

The platform enables high-priority directional signals, situational macro messages, and real-time voice-to-text transcriptions with low-glance tactile controls, automated clearing timers, one-tap message repeat, and home screen widgets.

---

## 2. Hardware Bill of Materials (BOM) & System Specifications

### 2.1 Hardware Component Breakdown

| Component | Specification / Model | Qty | Power Source / Interface | Operational Role |
| :--- | :--- | :---: | :--- | :--- |
| **Dedicated Controller Phone** | Motorola Moto G Stylus 2021 4G (Snapdragon 678, 6.8" 1080x2400 IPS LCD, Android 11/12, Bluetooth 5.0 BLE) | 1 | Vehicle 12V Aux to USB-C (Fast Charge / Thermal-Managed) | Host control application, local STT processing, dual-node BLE GATT host, and UI operator screen. |
| **Front & Rear LED Panels** | Flexible 16x64 RGB LED Marquee Displays with integrated Bluetooth BLE controllers | 2 | Dual 5V/2A USB Power Harness (Auxiliary 12V-to-USB Buck Converters) | Displays scrolling text, directional animations, and macro graphics. |
| **Power Distribution** | Dual 12V Auxiliary to 5V/2.4A USB Step-Down Buck Converters (or Dual-Port 36W High-Output Car Charger) | 1 | Vehicle 12V Power Socket / Fuse Box Line | Sustained power supply to both 16x64 displays without battery drain issues. |
| **Phone Mount** | High-stability dash or vent phone mount | 1 | Mechanical | Anchors 6.8" controller phone within low-glance driver reach. |

---

### 2.2 Electrical & System Connection Architecture

```
                       [ Vehicle 12V Aux Power / Fuse Line ]
                                        │
             ┌──────────────────────────┴──────────────────────────┐
             ▼                                                     ▼
 [ 12V to 5V/2.4A Dual USB Converter A ]               [ 12V to 5V/2.4A USB Converter B ]
             │                                                     │
             ▼ (USB Power Cable)                                   ▼ (USB Power Cable)
┌──────────────────────────┐                          ┌──────────────────────────┐
│  Front 16x64 LED Panel   │                          │   Rear 16x64 LED Panel   │
│  (BLE GATT Controller)   │                          │  (BLE GATT Controller)   │
└────────────▲─────────────┘                          └────────────▲─────────────┘
             │                                                     │
             └────────────────── BLE 5.0 Wireless Bus ──────────────┘
                                           ▲
                                           │
                        ┌──────────────────┴──────────────────┐
                        │    Moto G Stylus 2021 4G (Host)     │
                        │    • 6.8" Dash Control App           │
                        │    • Jetpack Glance Home Widgets    │
                        └─────────────────────────────────────┘
```

* **Wireless Connection:** Bluetooth 5.0 Low Energy (BLE) with simultaneous dual-device connection maintained via background foreground service.
* **Power Draw per Display:** Nominal ~1.2A at 5V (~6W); Peak brightness (~2.0A at 5V per panel). Total system USB power overhead ~20W.

---

## 3. Application Architecture & UI Specs

### 3.1 Tech Stack & Target Platform
* **Target OS:** Android 11+ (API 30+) running on 1080 x 2400 display (386 ppi).
* **Language & UI Toolkit:** Kotlin 1.9+ with Jetpack Compose & Material 3 (`androidx.compose.material3`).
* **Widget Framework:** Jetpack Glance (`androidx.glance`).
* **Concurrency & Reactive State:** Kotlin Coroutines & `StateFlow` / `SharedFlow`.
* **Database / Storage:** Room Database (for custom macros and display logs) + Encrypted SharedPreferences/DataStore for BLE device MAC pairing.
* **Speech-to-Text Engine:** Local Android `SpeechRecognizer` API (`RecognizerIntent.ACTION_RECOGNIZE_SPEECH` / `SpeechRecognizer` service) with offline language model fallback for zero cellular latency.

---

### 3.2 UI Wireframe & Screen Layout Specification (6.8" Display Optimized)

The UI is optimized specifically for the 20:9 aspect ratio (1080 x 2400 pixels) on the Moto G Stylus 2021.

```
┌──────────────────────────────────────────────────────────┐
│ AUTO COMM  [⏻] FRONT: CONNECTED  [⏻] REAR: CONNECTED  [ᛒ] │ (Header Bar)
├──────────────────────────────────────────────────────────┤
│ LIVE BROADCAST PREVIEW                          [10s]    │
│ ◄ MY BAD ◄                                               │ (Dot-Matrix Preview)
├──────────────────────────────────────────────────────────┤
│ [  « LEFT  ]        [ ◆ U-TURN ◆ ]       [  RIGHT »  ]   │ (P0 Directionals)
├──────────────────────────────────────────────────────────┤
│ SAVED MACROS                                  [🕘] [⚙]   │
│ ▐ FRONT Tail lights out!   ▐ FRONT Nice sticker          │
│ ▐ FRONT My bad             ▐ REAR  Turn lights on!       │ (P2 Macros Grid)
│ ▐ REAR  Off my butt        ▐ REAR  Thank you             │
│ ▐ BOTH  Go ahead           ▐ BOTH  Sorry!                │
├──────────────────────────────────────────────────────────┤
│ VOICE TO: [  FRONT  |  BOTH  |  REAR  ]                  │ (Voice Zone)
├──────────────────────────────────────────────────────────┤
│                                                          │
│                   HOLD TO SPEAK                          │
│                RELEASE TO TRANSMIT                       │ (P1 Tactile PTT)
│                TAP TO REPEAT LAST                        │ (Repeat)
└──────────────────────────────────────────────────────────┘
```

#### Screen Component Specifications

1. **Header & Dual-Panel Status Bar (Top Anchor, Height: ~64dp):**
   * **App Identifier:** Bold text `AUTO COMM`.
   * **Connection Badges:** Two independent pills (`FRONT` & `REAR`):
     * **Connected:** Green border, solid green dot, `[FRONT: CONNECTED]`.
     * **Reconnecting / Scanning:** Amber border, pulsing amber dot, `[FRONT: CONNECTING...]`.
     * **Disconnected:** Red border, solid red dot, `[FRONT: DISCONNECTED]`. Tapping the badge forces an immediate reconnect trigger.
     * **Not Set Up:** Grey border, `[FRONT: SET UP]`. Tapping opens Bluetooth Setup (§3.6).
   * **Panel Power Buttons `[⏻]`:** One per panel, immediately left of its badge.
     * **Tap:** Toggles that panel's display **ON / OFF** (sends iPixel Power On / Off, §4.2.4).
     * **ON:** Icon lit in the panel's zone color (Purple = FRONT, Emerald = REAR).
     * **OFF:** Icon grey, badge text appends `· OFF`, panel preview dimmed. BLE connection stays alive.
     * While a panel is OFF it is **skipped by all sends**: `BOTH` sends only to the ON panel; a `FRONT`/`REAR`-only send to an OFF panel shows a toast "Front panel is off" with a **Turn On & Send** action.
     * Power state persists across app restarts and reconnects (re-applied after the §4.2.3 handshake).
     * **Long-press:** Turns **both** panels OFF/ON together.
   * **Bluetooth Button `[ᛒ]`:** Top-right; opens Bluetooth Setup (§3.6). Shows a red dot if either panel is Disconnected or Not Set Up.
2. **Live Marquee Preview Block (Height: ~120dp):**
   * **Visual Style:** Canvas-drawn 16x64 LED dot-matrix grid simulator with glowing color LED pixels matching physical panel text.
   * **Features:**
     * Active text scrolling representation.
     * Always rendered **driver-readable (un-mirrored)**, even for the front panel; a small `MIRRORED` tag appears when the front panel is active (§4.2.8).
     * Countdown timer badge (`10s`, `9s`...) showing active broadcast auto-clear time.
     * Manual dismiss button (`X`) to clear screens immediately.
3. **P0 Directional Signals Row (Height: ~72dp):**
   * 3-column high-contrast touch target grid (`LEFT`, `U-TURN`, `RIGHT`).
   * Color: Deep Amber/Orange background with bold white icons.
   * **Latching toggle:** Tap = turn ON and send to both panels (skips any panel powered OFF). Tap again = turn OFF and return panels to Idle (§3.6.5). No auto-clear timer.
   * **Active state:** Button pulses with a bright border while ON. Only one directional can be ON; tapping another switches directly to it.
   * **Panel Graphics (64x16):**

     | Signal | Panel Layout | Animation |
     | :--- | :--- | :--- |
     | **LEFT** | `‹‹‹ LEFT` — amber 90° chevrons on the **left** side of the word | Chevrons sweep outward to the left (sequential light-up away from the word, ~600 ms loop). Word static. |
     | **RIGHT** | `RIGHT ›››` — amber 90° chevrons on the **right** side of the word | Chevrons sweep outward to the right (~600 ms loop). Word static. |
     | **U-TURN** | `◆ U-TURN ◆` — yellow diamond sign with a black U-turn arrow centered inside, on **both** sides of the word | None (static). |

   * Graphics are built-in assets (animated frames sent as GIF/image payload), pre-cached to reserved panel slots for instant display (§3.4.4). Front panel versions are mirrored (§4.2.8).
   * **While a directional is ON:** voice and macro sends are held (latest one only) and play when the directional is turned OFF; toast "Signal active — message queued." Sudden Stop (§4.3) still shows on the rear panel.
4. **P2 Custom Macros Scrollable Grid (Flexible Middle Section):**
   * 2-column grid displaying stored message cards; every row (including the last) contains 2 macros.
   * Only macros toggled **Show on Main** in Macro Settings are rendered.
   * Ordering follows the Macro Settings priority list, filled left-to-right, top-to-bottom: priority 1 & 2 = row 1, 3 & 4 = row 2, 5 & 6 = row 3, etc.
   * Card UI: Colored vertical left edge bar (Purple = `FRONT`, Emerald Green = `REAR`, Cyan = `BOTH`).
   * Long-press: Opens CRUD Edit/Delete Dialog modal.
   * Section header: `[⚙]` gear icon button anchored top-right of the `SAVED MACROS` header; tap opens the **Macro Settings** screen (§3.3). `[🕘]` opens **History** (§3.7).
   * Tap sends the macro to its **saved zone** (Voice Zone selector does not apply).
5. **Voice Zone Selector (Height: ~56dp):**
   * Label `VOICE TO:` + 3-segment pill control (`FRONT` | `BOTH` | `REAR`).
   * Applies **only to voice (PTT) messages**. Macros always use their own saved zone; directionals always go to both panels.
6. **P1 Touch-to-Talk (PTT) Block (Bottom Anchor, Height: ~240dp):**
   * **Design:** Oversized tactile card occupying lower third of 6.8" screen.
   * **States:**
     * **Idle:** Dark Yellow/Amber background, Microphone Icon + "HOLD TO SPEAK".
     * **Active Recording:** Pulsing Bright Red background, Animated Waveform + "LISTENING... RELEASE TO SEND".
     * **Idle sub-label:** "TAP TO REPEAT LAST" + preview of the last message's text (e.g., `↻ Thank you`). Greyed out if no message has been sent yet.
   * **Interaction Logic (`PointerInputScope.detectTapGestures`):**
     * **Hold (press ≥ 300 ms):** Starts local STT stream; release stops STT, cleans text, and passes it to the BLE broadcast pipeline.
     * **Tap (press < 300 ms):** **Repeat Last** — re-sends the most recent custom message (last saved macro or voice transcription sent; directionals excluded) to the same zone(s) with the same content, animation, colors, and duration. Uses the cached panel slot if one exists.
   * **Repeat state:** Last-sent message stored in DataStore so it survives app restarts. Replaced each time a new macro or voice message is sent; not replaced by directionals or by a repeat.
7. **Swipe Shortcuts (whole main screen):**
   * **Two-finger swipe** Up / Down / Left / Right sends the message assigned to that direction (assigned in Macro Settings, §3.3). Two fingers avoid conflict with grid scrolling and PTT.
   * Unassigned direction: short "No message assigned" toast + error vibration.
   * Swipe sends follow the macro's saved zone and normal priority/cooldown rules.

---

### 3.3 Macro Settings Screen

Opened via the `[⚙]` gear icon on the Saved Macros section header.

```
┌──────────────────────────────────────────────────────────┐
│ ←  MACRO SETTINGS                                  [ + ] │ (Header / Create)
├──────────────────────────────────────────────────────────┤
│ SAVED MESSAGES (drag to set priority)                    │
│ (●) ☆ ▐ FRONT Tail lights out!                    ↑  ≡   │ 1
│ (●) ★ ▐ FRONT Nice sticker                           ≡   │ 2
│ (●) ▐ FRONT My bad                                   ≡   │ 3
│ (●) ▐ REAR  Turn lights on!                          ≡   │ 4
│ (○) ▐ REAR  Brights!                                 ≡   │ —
│ (●) ▐ REAR  Off my butt                              ≡   │ 5
│ (●) ▐ REAR  Thank you                                ≡   │ 6
│ ...                                                      │
└──────────────────────────────────────────────────────────┘
```

#### Component Specifications

1. **Header Bar:**
   * Back arrow returns to main screen.
   * `[ + ]` button (top-right) opens the **Create Custom Message** editor (§3.4) to add a new entry to Saved Messages.
   * New messages are appended to the bottom of the list with **Show on Main** enabled by default.
2. **Saved Messages List:**
   * Vertical list of all stored macros (`LazyColumn`).
   * Each row: `Show on Main` toggle → favorite star → zone color bar + zone label → message text → swipe badge → drag handle (`≡`).
   * **Favorite Star (☆ / ★):** Tap to toggle. Maximum **6** favorites; tapping a 7th shows "6 favorites max — remove one first." Favorites populate the Home Screen Favorites widget (§3.5) in priority order.
   * **Swipe Assignment:** Row overflow menu → *Assign swipe* → Up / Down / Left / Right / None. Each direction holds one message; assigning a taken direction moves it. Assigned direction shown as a small arrow badge (↑ ↓ ← →).
   * **Show on Main Toggle:** Leading toggle per row. On = rendered on main screen macro grid; Off = stored but excluded from main screen.
   * **Drag & Drop Reorder:** Long-press/drag handle to move a row. List order = priority order. Changes persist immediately to Room.
   * Priority numbering counts only rows with **Show on Main** enabled; excluded rows retain their list position but are skipped when building the main grid.
   * Tap row: Opens the message editor (§3.4) in Edit mode (same as main-screen long-press).
3. **Data Model (Room `macros` table additions):**
   * `sortOrder: Int` — list position / priority.
   * `showOnMain: Boolean` — main screen visibility (default `true`).
   * `isFavorite: Boolean` — max 6 enforced (default `false`).
   * `swipeDirection: Enum?` — `UP`, `DOWN`, `LEFT`, `RIGHT`, or null (unique).

---

### 3.4 Create / Edit Custom Message Screen

Opened via `[ + ]` on Macro Settings (Create mode) or by tapping/long-pressing an existing macro (Edit mode). Options mirror the stock **iPixel Color** app's text/image capabilities as documented by the open-source `pypixelcolor` / iPixel-CLI library (reference: https://github.com/SuperIlu/iPixel-CLI), which reverse-engineers the same BLE protocol used by the panels.

```
┌──────────────────────────────────────────────────────────┐
│ ←  NEW MESSAGE                          [TEST]  [SAVE]   │ (Header)
├──────────────────────────────────────────────────────────┤
│ ▓▓▓▓▓ LIVE 64x16 PREVIEW (plays selected effect) ▓▓▓▓▓  │ (Preview)
├──────────────────────────────────────────────────────────┤
│ Button Label: [ Thank you              ]                 │
│ Type:   [ TEXT | IMAGE / GIF ]                           │
│ Zone:   [ FRONT | BOTH | REAR ]                          │
├──────────────────────────────────────────────────────────┤
│ Text:  [ THANK YOU 👍                         ]  0/500   │
│        [B] Color selected word  [🙂] Emoji               │
│ Animation: [Static ▾]   Speed: ───●──── 80               │
│ Text Color: [■ #FFFFFF]  Background: [□ None]            │
│ Rainbow: [Off ▾]                                         │
│ Font: [UNIFONT ▾]  Size [16]  Offset X[0] Y[0]           │
│       Threshold ──●── 128   [ ] Variable width           │
├──────────────────────────────────────────────────────────┤
│ Display Duration: [10s ▾]                                │
│ [ ] Cache on panel   Slot: [ 1 ▾]                        │
└──────────────────────────────────────────────────────────┘
```

#### 3.4.1 General Fields

| Field | Values | Default | Notes |
| :--- | :--- | :--- | :--- |
| **Button Label** | Free text (≤24 chars) | First 24 chars of message | Text shown on the main-screen macro card and Macro Settings list. |
| **Content Type** | `TEXT`, `IMAGE / GIF` | `TEXT` | Switches the editor section below. |
| **Target Zone** | `FRONT`, `REAR`, `BOTH` | `BOTH` | Sets card edge color (§3.2). The Voice Zone selector never overrides a macro's saved zone. |
| **Display Duration** | 3s, 5s, 10s, 15s, 30s, 60s, Until Cleared | 10s | App-side auto-clear timer (panels have no native timeout); app sends clear/power-off when timer expires. |

#### 3.4.2 Text Content Options (`send_text`)

| Option | Values | Default | Notes |
| :--- | :--- | :--- | :--- |
| **Message Text** | 1–500 characters | — | UTF-8; emoji supported. |
| **Emoji** | Any Unicode emoji | — | Rendered as Twemoji bitmaps. Twemoji assets bundled in-app (offline; no CDN fetch while driving). |
| **Animation** | `Static`, `Scroll Left`, `Scroll Right`, `Blink`, `Fade` (breathing), `Snowflake` | `Static` | `Scroll Up/Down` hidden (32x32 panels only). The live preview shows exactly what fits and how the effect plays; user adds scrolling if words are cut off. |
| **Speed** | 0–100 | 80 | Animation speed slider. |
| **Text Color** | Hex RGB picker + preset swatches | `#FFFFFF` | Default color for the whole message. |
| **Per-Word Color** | Select text → pick color | — | Stored as inline tags `[#RRGGBB]word[/]`; nested tags supported. Disabled when Variable Width is on. |
| **Background Color** | None or hex RGB | None | Solid fill behind text. |
| **Rainbow Mode** | Off, 1–9 | Off | Panel-native rainbow color effects; overrides text color. |
| **Font** | Built-in `UNIFONT` or imported `.ttf` / `.otf` | `UNIFONT` | Imported fonts stored in app-private storage. |
| **Font Size** | 1–16 px | 16 (panel height) | |
| **Offset X / Y** | −8 to +8 px | 0 / 0 | Fine-tune glyph placement. |
| **Pixel Threshold** | 0–255 | 128 | Binarization cutoff for anti-aliased fonts (lower = bolder). |
| **Variable Width** | On / Off | Off | Proportional spacing for non-monospace fonts. Disables per-word color (single color only). |

#### 3.4.3 Image / GIF Content Options (`send_image`)

| Option | Values | Default | Notes |
| :--- | :--- | :--- | :--- |
| **Source** | Gallery picker or file: `.png`, `.jpg`, `.webp`, `.bmp`, `.gif` (animated), `.heic` | — | Copied into app-private storage on save. |
| **Resize Method** | `Crop` (fill & crop), `Fit` (letterbox, black padding) | `Crop` | Target is 64x16. |
| **Preview** | Live scaled preview incl. GIF playback | — | Shows exact pixels the panel will display. |

#### 3.4.4 Panel Slot Caching (Optional)

* Panels provide on-device storage **slots (1–10)**. A macro can be cached to a slot so it displays instantly via a short `show_slot` command instead of re-transmitting the full bitmap.
* Toggle **Cache on panel** + choose slot. App tracks slot assignments per panel and prevents conflicts.
* Message must pass a successful **TEST** send before slot caching is allowed (corrupted slot data can bootloop the panel).
* Editing a cached macro re-uploads to its slot; deleting a macro issues `delete <slot>`.

#### 3.4.5 Header Actions

* **TEST:** Sends the current draft to the selected zone immediately (respects §4.1 priority). Does not save.
* **SAVE:** Validates, persists to Room, appends to the bottom of Saved Messages with **Show on Main** on (Create mode), and returns to Macro Settings.
* **Back with unsaved changes:** "Discard changes?" confirm dialog.
* Edit mode adds **DELETE** (with confirm) at the bottom of the screen.

#### 3.4.6 Validation (applies on TEST and SAVE)

* Message text required (1–500 chars) for TEXT; image required for IMAGE / GIF.
* Button label required (auto-filled if empty).
* Warning (non-blocking) when Variable Width is on and per-word colors will be dropped.

#### 3.4.7 Rendering Pipeline

* The panel protocol accepts **pre-rendered glyph bitmaps**, not raw strings. Text is rasterized on the phone (Android `Canvas` / `Bitmap` with the selected font, size, offset, threshold), split into character/chunk bitmaps with per-character color, then framed and sent.
* Payloads are split into ≤12 KB windows with CRC32 and per-window ACK over BLE.
* The same rasterizer drives the editor preview and the main-screen Live Broadcast Preview, guaranteeing the preview matches the panel.
* Editor preview **plays the selected effect live** (scroll, blink, fade, snowflake, rainbow, speed) and updates instantly on any option change, so cut-off text is visible before saving.
* Front-panel output is horizontally mirrored as the final render step (§4.2.8). Editor preview shows normal orientation with a `Front: Mirrored` toggle-able preview option to view what the panel physically shows.

#### 3.4.8 Data Model (Room `macros` table)

| Column | Type | Notes |
| :--- | :--- | :--- |
| `id` | `Long` (PK) | |
| `label` | `String` | Button label. |
| `contentType` | `Enum` (`TEXT`, `IMAGE`) | |
| `zone` | `Enum` (`FRONT`, `REAR`, `BOTH`) | |
| `text` | `String?` | Raw text incl. inline color tags. |
| `animation` | `Int` (0–7) | |
| `speed` | `Int` (0–100) | |
| `color` | `String` (hex) | |
| `bgColor` | `String?` (hex) | |
| `rainbowMode` | `Int` (0–9) | |
| `fontId` | `String` | `UNIFONT` or imported font reference. |
| `fontSize`, `offsetX`, `offsetY`, `pixelThreshold` | `Int` | |
| `varWidth` | `Boolean` | |
| `imageUri` | `String?` | App-private file path. |
| `resizeMethod` | `Enum` (`CROP`, `FIT`) | |
| `durationSec` | `Int` (0 = until cleared) | |
| `slotFront`, `slotRear` | `Int?` | Cached slot per panel, null if not cached. |
| `sortOrder` | `Int` | §3.3 |
| `showOnMain` | `Boolean` | §3.3 |
| `isFavorite` | `Boolean` | §3.3, max 6 |
| `swipeDirection` | `Enum?` | §3.2 item 7 |

---

### 3.5 Home Screen Widgets (Jetpack Glance)

1. **4x4 Favorites Grid Widget:**
   * Renders the (up to 6) **starred** favorites (§3.3) in priority order.
   * Tap action: Executes background `CoroutineScope` task invoking `BleManagerService` to send standard payload to assigned target zone (10s auto-clear) without opening main app UI.
2. **2x4 Quick Voice Transcribe Widget:**
   * Large microphone tile with "Voice Transcribe" button.
   * Tap action: Launches `MainActivity` directly with `EXTRA_START_PTT = true`, immediately activating speech recognition.

---

### 3.6 Bluetooth Setup Screen

Opened from the header `[ᛒ]` button, a `SET UP` badge, or automatically on first launch when no panels are assigned.

```
┌──────────────────────────────────────────────────────────┐
│ ←  BLUETOOTH SETUP                          [ ⟳ SCAN ]   │ (Header)
├──────────────────────────────────────────────────────────┤
│ ASSIGNED PANELS                                          │
│ FRONT  LED-64x16-A1B2   ● Connected   [Identify] [⋮]     │
│ REAR   LED-64x16-C3D4   ● Connected   [Identify] [⋮]     │
├──────────────────────────────────────────────────────────┤
│ NEARBY DEVICES                        Scanning... (8s)   │
│ LED-64x16-E5F6   -62 dBm  64x16  [Front] [Rear]          │
│ LED-64x16-G7H8   -78 dBm  64x16  [Front] [Rear]          │
├──────────────────────────────────────────────────────────┤
│ PANEL SETTINGS                                           │
│ Front: Mirror [■]  Brightness ───●── 80  Rotate [0° ▾]   │
│ Rear:  Mirror [□]  Brightness ───●── 80  Rotate [0° ▾]   │
└──────────────────────────────────────────────────────────┘
```

#### 3.6.1 Scan / Refresh

* **`[ ⟳ SCAN ]`** starts a BLE scan (10 s timeout, low-latency scan mode); tap again during a scan to restart it.
* Scan filters for iPixel panels: advertised service `FA00` and/or iPixel device-name pattern (confirm pattern during Phase 0 hardware validation).
* Each result shows: device name, last 4 of MAC, RSSI (dBm, live-updating), and panel size once read via Get Device Info.
* Results sorted by signal strength; already-assigned panels are hidden from Nearby.
* Non-64x16 iPixel devices are listed but greyed with "Unsupported size".
* Empty result after timeout: "No panels found — check panel power and that the iPixel Color app is closed" + **Scan Again**.

#### 3.6.2 Assign & Connect

* **`[Front]` / `[Rear]`** on a nearby device: connects, runs the §4.2.3 handshake, stores the MAC in DataStore as that zone, and moves it to Assigned.
* Assigning to a zone that already has a panel prompts "Replace current Front panel?".
* **`[Identify]`**: sends a 3-second `FRONT` or `REAR` label (Blink) to that panel so the user can confirm which physical panel is which. Also available on nearby devices before assignment ("?" label).
* **Swap**: `[⋮]` menu → **Swap Front/Rear** exchanges the two assignments without re-pairing.

#### 3.6.3 Assigned Panel Actions (`[⋮]` menu)

| Action | Result |
| :--- | :--- |
| **Reconnect** | Drops and re-opens the GATT connection, re-runs handshake. |
| **Disconnect** | Closes connection; auto-reconnect paused until Reconnect or app restart. |
| **Swap Front/Rear** | Exchanges zone assignments. |
| **Forget** | Removes the MAC and clears the zone's slot cache records; badge returns to `SET UP`. |
| **Device Info** | Shows MAC, device type byte, panel size, firmware (if reported), RSSI. |

#### 3.6.4 Panel Settings

| Setting | Values | Default |
| :--- | :--- | :--- |
| Mirror | On / Off | Front **On**, Rear **Off** (§4.2.8) |
| Brightness | 0–100 | 80 |
| Rotation | 0° / 90° / 180° / 270° | 0° |

| Idle Content | Dark (off) / Saved message / Custom text / Image | **AutoComm** logo text |
| Startup Animation | On / Off | On |

Changes apply immediately to the connected panel and persist in DataStore.

#### 3.6.5 Idle Content

* Shown whenever nothing else is active: after a message auto-clears, after `X` dismiss, after a directional is turned OFF, and after the startup animation.
* Options: **Dark** (panel powered off), **Saved message** (pick from Saved Messages), **Custom text** (uses the §3.4 text editor), **Image / GIF**.
* Set per panel (front idle is mirrored, §4.2.8). Cached to a reserved slot for instant recall.

#### 3.6.6 Startup Animation ("Laser AutoComm")

* Plays on each panel once its connection handshake completes after power-up.
* Effect: a bright single-pixel-wide vertical "laser" line sweeps left→right across the panel, leaving the word **AutoComm** "etched" behind it (cyan text, white laser with glow trail), ~2 s, then holds 1 s and transitions to Idle Content.
* Built-in animated asset; mirrored on the front panel. Skippable via setting.

#### 3.6.7 Permissions & Edge Cases

* Bluetooth off → banner "Bluetooth is off" + **Turn On** (system `ACTION_REQUEST_ENABLE`).
* Missing `BLUETOOTH_SCAN` / `BLUETOOTH_CONNECT` → inline permission request; Scan disabled until granted.
* A panel connected to the stock iPixel Color app (or another phone) may not advertise; message prompts to close other apps.

---

### 3.7 History Screen

Opened via `[🕘]` on the Saved Macros header.

* Lists the **last 10** sent messages (macros, voice, repeats; directionals excluded), newest first.
* Each row: time sent, zone(s), message preview, source icon (macro / voice / swipe / widget), result (✓ sent, ⚠ partial, ✕ failed).
* Tap row → **Re-send** (same zone and settings). Long-press voice entry → **Save as Macro** (opens editor prefilled).
* **`[ Clear History ]`** button at top with confirm dialog.
* Storage: Room `history` table capped at 10 rows (oldest dropped on insert).

---

### 3.8 App Behavior & System Settings

#### 3.8.1 Power-Up & Auto-Start

* **Car start → panels power on** from switched 12V. The phone must be ready to connect:
  * **Phone already on (normal case):** `ACTION_POWER_CONNECTED` wakes the app, turns the screen on, brings AutoComm to the foreground, and starts reconnect.
  * **Phone powered off:** Configure the phone to boot when charging power is applied (Motorola: `fastboot oem off-mode-charge 0`; requires unlocked bootloader — verify in Phase 0). Otherwise the phone should be left on/asleep in the car.
  * **After boot:** `BOOT_COMPLETED` receiver starts `BleManagerService` and launches the app. Because Android blocks background activity launches, AutoComm is set as the phone's **default Home app** (dedicated device), so it is always the screen shown after boot/unlock.
* **Startup connection sequence:**
  1. Badges show `CONNECTING...`; connect directly to saved Front/Rear MACs (panels may need ~3–5 s to boot).
  2. If a saved panel isn't found within 15 s, run a background scan (§3.6.1) and connect when it appears; keep retrying every 3 s (§5).
  3. If no panels are assigned, open Bluetooth Setup (§3.6).
  4. On each successful handshake → Startup Animation (§3.6.6) → Idle Content.

#### 3.8.2 Phone Display

* Screen kept awake while AutoComm is in the foreground (`FLAG_KEEP_SCREEN_ON`).
* Locked to **portrait**.
* **Dark theme** UI to reduce night glare; follows system night mode, with manual override.
* Lock screen disabled on the dedicated phone (or Smart Lock) so the app is reachable immediately.

#### 3.8.3 Send Feedback

* **Success:** short chime + single short vibration once all target panels ACK.
* **Partial / failure:** distinct double-buzz + error tone; preview shows which panel failed.
* Sound and vibration each toggleable; sound uses the notification stream at app-set volume.

#### 3.8.4 Cooldown

* Identical message re-send blocked for **3 s** by default (0–10 s setting). Prevents double-tap duplicates from buttons, swipes, widgets, and repeat.

#### 3.8.5 Simulator Mode (In-House Testing)

* Setting: **Simulator Mode** — no BLE; two on-screen virtual 64x16 panels (Front shown mirrored, Rear normal) render exactly what would be sent.
* Full pipeline runs (render → encode → windows); encoded frames are written to the log for byte inspection.
* Lets all UI, queue, repeat, history, swipe, and Sudden Stop logic be tested at home before panels are installed in the vehicle.

#### 3.8.6 Logs

* Rolling local log file: BLE connect/disconnect, handshakes, sends (zone, size, result, ms), ACK timeouts, errors, crashes (uncaught exception handler), sudden-stop triggers.
* Capped at 5 MB / 7 days. **Export Logs** shares a `.zip` via the Android share sheet.
* Optional verbose mode logs raw frame hex.

#### 3.8.7 App Updates (Sideload)

* Distributed as a **signed release APK** (consistent signing key) published to **GitHub Releases**.
* Installed/updated through a third-party installer such as **Obtainium** (tracks the GitHub repo and installs new releases), or by opening the APK directly. Phone setting *Install unknown apps* enabled for that installer.
* Settings → About shows version and a **Check for updates** link to the Releases page.
* Database migrations required for every schema change so updates keep saved messages and settings.

#### 3.8.8 Settings Location

All settings above live in **Settings** (gear icon in Bluetooth Setup header or overflow on main header): Feedback, Cooldown, Sudden Stop (§4.3), Simulator Mode, Logs, Display, About/Updates. Panel-specific settings remain in Bluetooth Setup (§3.6.4).

---

## 4. Functional & System Specifications

### 4.1 Priority & Override Queue Engine

The app enforces strict signal prioritization to ensure urgent directional signals preempt secondary communications:

```
[ Incoming Action Request ]
            │
            ▼
┌───────────────────────────────┐
│ Check Event Priority Level    │
└──────────────┬────────────────┘
               │
               ├──► P0 (Directionals / Sudden Stop) ──► Immediate Preempt ──► Latched until tapped OFF (Sudden Stop: auto)
               │
               ├──► P1 (PTT Voice)    ──► Preempt P2/P3 ───────────────────► Render Transcribed Text (10s clear)
               │
               └──► P2 (Macros)       ──► Queue if P0/P1 active ────────────► Render Macro Payload
```

* **P0 (Directionals):** Immediate priority. Preempts active macro or speech output. Latching: stays on until the same button is tapped again, then panels return to Idle. P1/P2 requests during P0 are held (latest only) and played after P0 ends.
* **P0 (Sudden Stop, §4.3):** Rear panel only. Preempts everything on the rear panel, including a directional, then restores the previous rear content.
* **P1 (PTT Voice):** High priority. Overrides P2 macros. Displays transcribed text for 10 seconds.
* **P2 (Macros):** Standard priority. Displays preset payload. Auto-clears after preset duration (default 10s).
* **Repeat Last:** Inherits the priority of the original message (P1 if a voice message, P2 if a macro).
* **P3 (Idle):** Displays the panel's configured Idle Content (§3.6.5).
* **Cooldown:** The same message cannot be re-sent within **3 s** (configurable 0–10 s, §3.8). Blocked attempts give a short error vibration. Directionals and Sudden Stop are exempt.

---

### 4.2 BLE GATT Communication Protocol (iPixel Color)

Both panels are stock **iPixel Color** BLE devices (64x16, device type byte `0x83` → LED type 3). The app speaks the iPixel protocol as documented by the open-source `pypixelcolor` / iPixel-CLI library (https://github.com/SuperIlu/iPixel-CLI). Each panel is an **independent BLE peripheral** with its own GATT connection; there is no zone field in the protocol.

#### 4.2.1 GATT Profile

| Item | UUID | Properties | Use |
| :--- | :--- | :--- | :--- |
| Service | `0000FA00-0000-1000-8000-00805F9B34FB` | — | iPixel primary service (verify on first connect via service discovery). |
| Write Characteristic | `0000FA02-0000-1000-8000-00805F9B34FB` | Write (with response) | All commands and content payloads. |
| Notify Characteristic | `0000FA03-0000-1000-8000-00805F9B34FB` | Notify | ACKs and device-info responses. |

* **MTU / Chunking:** Request MTU 247; write payloads in **244-byte chunks** using write-with-response.
* **Pairing Storage:** Front and rear panel MAC addresses stored in DataStore (§3.1); assigned via Bluetooth Setup (§3.6).

#### 4.2.2 Zone Routing (App-Side)

| Zone | Action |
| :--- | :--- |
| `FRONT` | Write to front panel connection only. |
| `REAR` | Write to rear panel connection only. |
| `BOTH` | Write to both connections in parallel (independent coroutines); success reported per panel. |

* Rendering is done separately per panel: the **front payload is mirrored** (§4.2.8), the rear payload is normal. For `BOTH`, both payloads are built from the same source render.
* Panels powered OFF by the user (header `[⏻]`, §3.2) are excluded from routing.
* A failed write on one panel does not block the other; failed panel's badge shows status and the payload is retried on reconnect if still within its display duration.

#### 4.2.3 Connection Handshake

1. Connect GATT → discover services → enable notifications on `FA03`.
2. Send **Get Device Info**: `08 00 01 80 HH MM SS 00` (current hour/min/sec, language 0).
3. Parse response byte `[4]` = device type → confirm 64x16 (`0x83`). Mismatch → badge shows `UNSUPPORTED`.
4. Apply stored brightness and orientation, then mark panel `CONNECTED`.

#### 4.2.4 Simple Command Frames (single window)

Format: `[length] [0x00] [command ID] [type ID] [args…]` (length = total bytes).

| Command | Bytes | Use in AutoComm |
| :--- | :--- | :--- |
| Power On / Off | `05 00 07 01 01` / `05 00 07 01 00` | Auto-clear at end of display duration (power off); power on before next send. |
| Brightness | `05 00 04 80 <0–100>` | Settings / day–night brightness. |
| Orientation | `05 00 06 80 <0–3>` (0°/90°/180°/270°) | Per-panel mount correction (e.g., rear panel flipped). |
| Show Slot | `07 00 08 80 01 00 <slot>` | Instant display of a cached macro (§3.4.4). |
| Delete Slot | `07 00 02 01 01 00 <slot>` | Remove cached macro. |
| Clear ROM | `04 00 03 80` | **Never used at runtime** — wipes all slots and settings. Factory reset only. |

#### 4.2.5 Text Payload (`send_text`)

The panel does **not** accept strings. Text is rasterized on the phone into per-character bitmaps (§3.4.7) and sent as:

```
data_payload = [num_chars:1] + [properties:13] + [character bitmaps…]

properties (13 bytes):
  00 01 01                      fixed
  <animation 0–7>               0 Static, 1 Scroll L, 2 Scroll R, 5 Blink, 6 Fade, 7 Snowflake
  <speed 0–100>
  <rainbow 0–9>
  <R> <G> <B>                   default text color
  <bg_flag> <R> <G> <B>         bg_flag 01 + color, or 00 00 00 00 for none
```

#### 4.2.6 Windowed Transport (text & image)

Payloads are split into **12 KB windows**. Each window:

| Field | Size | Value |
| :--- | :---: | :--- |
| Length prefix | 2 B (LE) | Window frame length + 2 |
| Type | 2 B | `00 01` (text) — image uses its own type bytes |
| Option | 1 B | `00` first window, `02` continuation |
| Total payload size | 4 B (LE) | Full `data_payload` length |
| CRC32 | 4 B (LE) | CRC32 of full `data_payload` |
| Reserved | 1 B | `00` |
| Save slot | 1 B | `00` = display only, `1–10` = store in slot |
| Chunk | ≤12 KB | Slice of `data_payload` |

* Each window is written in 244-byte chunks, then the app **waits for an ACK** on `FA03`: frame `05 .. .. .. <code>` where code `00`/`01` = window accepted, `03` = transfer complete.
* ACK timeout: 2 s per window → retry window once → mark send failed and surface error on preview block.
* A newer higher-priority message (§4.1) cancels an in-flight lower-priority transfer between windows.

#### 4.2.7 Display Duration & Auto-Clear

* Panels have no native timeout. The app owns a per-panel countdown (shown as the preview badge in §3.2).
* On expiry or manual dismiss (`X`): send **Power Off**. Next outgoing message is preceded by **Power On**.
* This auto-clear power-off is internal and does **not** change the user power state; a panel the user turned OFF with `[⏻]` receives no Power On until the user turns it back ON.
* P3 Idle (§4.1) = configured Idle Content (§3.6.5); Power Off only when Idle Content is **Dark**.

#### 4.2.8 Front Panel Mirroring

The front panel faces forward through the windshield and is read by the driver ahead **in their rear-view mirror**. All front-panel content must therefore be **horizontally mirrored** so it reads correctly in the mirror. The rear panel is read directly and is never mirrored.

* **No native mirror command:** iPixel Orientation (§4.2.4) only supports rotation (0°/90°/180°/270°), which cannot produce a mirror image. Mirroring is done app-side during rendering.
* **Text:**
  1. Flip each character/chunk bitmap horizontally.
  2. Reverse character order.
  3. Swap scroll direction: `Scroll Left` ↔ `Scroll Right` (so text still enters from the correct side as seen in the mirror).
  4. Per-word colors follow their characters.
* **Images / GIFs:** Flip every frame horizontally before encoding.
* **Directionals (P0):** Arrow animations are mirrored the same way. Result: a `Left ◄` signal appears to point to the viewer's left in their mirror, matching my actual turn direction (both vehicles face the same way).
* **Slot caching:** Front and rear slots store their own versions (mirrored vs normal); a cached macro uploads both variants (`slotFront`, `slotRear`, §3.4.8).
* **Setting:** `Mirror front panel` toggle in app settings, default **ON**; per-panel so either panel can be mirrored if mounting changes.
* **Verification:** Bench test with a hand mirror before release; text must read left-to-right in the mirror.

#### 4.2.9 Implementation Notes

* Protocol is reverse-engineered (unofficial); byte layouts must be verified against the physical panels with Android HCI snoop logs before release.
* Port reference: `pypixelcolor` modules `commands/send_text`, `commands/send_image`, `lib/transport/send_plan.py`, `lib/transport/ack_manager.py`, `lib/device_info.py` → Kotlin `IPixelProtocol` + `BleManagerService`.

---

### 4.3 Sudden Stop Detection

Uses the phone's motion sensors (no GPS/location) to detect hard braking and warn the vehicle behind.

* **Sensors:** `TYPE_LINEAR_ACCELERATION` (accelerometer, gravity removed), with gyroscope used to reject phone movement/rotation in the mount.
* **Calibration:** Phone orientation in the mount is unknown, so the forward axis is learned once: Settings → Sudden Stop → *Calibrate* (accelerate gently in a straight line for ~3 s). Stored as a vector; recalibrate if the mount moves.
* **Trigger:** Deceleration along the forward axis ≥ **0.45 g (~4.4 m/s²)** sustained ≥ **300 ms**. Sensitivity setting: Low 0.55 g / Medium 0.45 g / High 0.35 g. Spikes shorter than 300 ms (potholes, bumps) ignored; ignored while the gyroscope shows the phone itself rotating.
* **Action:** Rear panel immediately shows a **flashing red/amber "STOP" / hazard graphic** (built-in, pre-cached slot) as P0. Front panel unaffected.
* **Clear:** 3 s after deceleration drops below threshold, rear panel restores whatever was showing before (directional, message, or Idle).
* **Feedback:** subtle single vibration; event logged.
* Setting: Sudden Stop On / Off (default On).
* Testable in Simulator Mode with a *Simulate Hard Stop* button.

---

## 5. Non-Functional Requirements

* **Latency:** App button tap or PTT release to BLE frame write: **< 100 ms**.
* **STT Finalization:** Local voice audio capture to clean text string: **< 300 ms**.
* **BLE Auto-Reconnection:** Service automatically attempts background GATT reconnection every 3 seconds if connection is dropped due to vehicle power state.
* **Thermal Resilience:** Continuous background execution without memory leaks under ambient vehicle cabin temperatures up to 60°C.
---

## 6. Future Release (v2.0 — Nice to Have)

Not in v1 scope. Documented here to guide v1 architecture so v2.0 features can be added without rework.

### 6.1 Wake Word & Hands-Free Voice Command

**Goal:** Say *"Yo AutoComm"*, speak a full command (e.g., *"Post to back only, 'Nice paint job. Smiley emoji'"*), and have the app determine the target zone, message text, and emoji, then send it — without touching the phone.

#### 6.1.1 Pipeline

```
[ Wake Word ] → [ Chime + Listening UI ] → [ STT + VAD ] → [ Command Parser ] → [ Confirm (2s) ] → [ Send ]
 "Yo AutoComm"     audio + visual cue      end on silence     local grammar         auto-send or       §4.1 queue
                                           or tap             → AI fallback         "cancel"
```

| Stage | Approach | Notes |
| :--- | :--- | :--- |
| **Wake Word** | **sherpa-onnx keyword spotting** (free, open source) or Picovoice **Porcupine** custom keyword "Yo AutoComm" | On-device, low CPU. Runs in `BleManagerService` with `foregroundServiceType="connectedDevice\|microphone"`. Sensitivity tunable for road noise/music. |
| **Listening Cue** | Short chime + PTT block turns red ("LISTENING...") | Panels keep current content until send. |
| **Speech-to-Text** | **sherpa-onnx streaming English model** (on-device), live partial transcript on screen | Fallbacks: Vosk small English, or Android `SpeechRecognizer`. Whisper tiny/base too slow on Snapdragon 678. |
| **End of Message** | Silero VAD — ends after ~1.2 s of silence, **or** single tap anywhere on the PTT block | Independent of Google's unreliable silence-length extras. Max utterance 15 s. |
| **Command Parser** | Two-tier, fully on-device (§6.1.2) | Optional cloud fallback. |
| **Text → Emoji** | Spoken-phrase dictionary (§6.1.3), on-device model fallback for unlisted phrases | Rendered via bundled Twemoji. |
| **Confirm & Send** | Parsed result shown on preview (e.g., `REAR · Nice paint job 🙂`); auto-sends after 2 s; "cancel" or `✕` aborts | Priority P1 (§4.1). Becomes the Repeat Last message. |

#### 6.1.2 Command Parser

**Tier 1 — Local Grammar (offline, ~10 ms):**

| Spoken Pattern | Result |
| :--- | :--- |
| "post to / send to / show on" + **"back" / "rear" / "behind"** (+ "only") | Zone = `REAR` |
| … + **"front" / "ahead"** | Zone = `FRONT` |
| … + **"both" / "everyone" / "all"** | Zone = `BOTH` |
| No zone phrase | Zone = current Zone Selector value |
| Remaining text (quotes/pauses delimit message) | Message text |
| "[phrase] emoji" | Replaced with emoji (§6.1.3) |
| Optional modifiers: "flashing" → Blink, "scrolling" → Scroll Left, "in [color]" → text color | Animation / color |
| "repeat" / "again" | Repeat Last |
| "clear" / "stop" | Dismiss panels |

**Tier 2 — On-Device AI (~1–2 s, estimate; verify on device):** Used when Tier 1 can't identify a message. Raw transcript passed to **FunctionGemma 270M** (Google, function-calling SLM) running via **LiteRT-LM**, fine-tuned for AutoComm commands, producing the strict JSON schema:

```json
{
  "zone": "FRONT | REAR | BOTH | null",
  "text": "Nice paint job 🙂",
  "animation": "static | scroll_left | blink | fade | null",
  "color": "#RRGGBB | null",
  "action": "send | repeat | clear"
}
```

* Null fields fall back to Zone Selector / defaults.
* Model timeout 3 s → send Tier 1 best-effort text (transcript minus zone phrase) to Zone Selector zone.
* **Optional Tier 3 — Cloud fallback** (off by default): LLM API (e.g., Claude Haiku) with the same schema when on-device output fails validation and network is available. API key in Encrypted DataStore.

#### 6.1.2a On-Device Model Stack

| Job | Model / Engine | Approx. Size | Notes |
| :--- | :--- | :---: | :--- |
| Wake word | sherpa-onnx KWS or Porcupine | < 2 MB | Always-on, low CPU. |
| End-of-speech | Silero VAD (bundled in sherpa-onnx) | ~2 MB | 1.2 s silence threshold. |
| Speech-to-text | sherpa-onnx streaming English (zipformer) | 40–70 MB | Real-time on Snapdragon 678. |
| Command parsing / emoji | FunctionGemma 270M (int8/int4) via LiteRT-LM | ~300 MB | Must be fine-tuned. |

* **Fine-tuning:** Generate ~300–500 synthetic command → JSON pairs (zones, phrasing variety, emoji phrases, modifiers, noisy STT errors). Fine-tune with Unsloth or Google's FunctionGemma guide on a free Colab GPU; export to `.litertlm`.
* **Delivery:** Models downloaded on first v2.0 enable (Wi-Fi) into app-private storage, not bundled in the APK.
* **Thermals:** Model loaded on wake word only and unloaded after 60 s idle; skip Tier 2 if device thermal status ≥ `THERMAL_STATUS_SEVERE`.
* **Avoid:** ≥ 1B-parameter models (Gemma 3 1B, Qwen 1.5B, Phi) — too slow/hot for this device. Gemini Nano (AICore) unsupported on Moto G Stylus 2021.

#### 6.1.3 Spoken Emoji Dictionary (starter set)

| Spoken | Emoji | Spoken | Emoji |
| :--- | :---: | :--- | :---: |
| smiley / smile / happy face | 🙂 | thumbs up | 👍 |
| laughing / LOL | 😂 | thumbs down | 👎 |
| heart / love | ❤️ | wave / waving | 👋 |
| wink | 😉 | fire | 🔥 |
| sad face | 🙁 | hundred / 100 | 💯 |
| angry face | 😠 | clapping | 👏 |
| sunglasses / cool | 😎 | peace sign | ✌️ |
| shrug | 🤷 | car | 🚗 |

User-editable in v2.0 settings.

#### 6.1.4 Settings (v2.0)

* Wake word on/off; listen **always** vs. **only while app in foreground**.
* Wake sensitivity slider.
* Auto-send delay: 0 s / 2 s / 4 s / require tap.
* On-device AI parser on/off; cloud fallback on/off + API key entry.
* Chime on/off.

#### 6.1.5 v1 Architecture Hooks

* Keep the send pipeline accepting a `ParsedCommand(zone, text, animation, color, action)` object so voice, PTT, macros, and v2.0 commands share one entry point.
* Keep emoji rendering independent of input source (already required by §3.4.2).
* Keep `BleManagerService` capable of adding the `microphone` foreground service type.

### 6.2 Physical Bluetooth Remote

* Cheap BLE/HID button remote (steering-wheel clip) paired to the phone.
* Buttons mapped in Settings to: Left, Right, U-Turn (latching), Repeat Last, Hold-to-Talk, and swipe-slot messages.
* Key events captured via `onKeyDown` / media session; works with the app in the foreground.
