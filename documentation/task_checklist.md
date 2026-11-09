# AutoComm Implementation Roadmap & Tasks

This roadmap breaks down tasks into automated agent steps (**Agent AI**) and physical/testing procedures (**Human**). Section references (§) point to `product_requirement_document.md`.

---

## Phase 0: Hardware Validation (Gate — do before any app code)

- [ ] **[Human]** Purchase both 64x16 iPixel Color panels; power them on the bench from 5V/2A USB.
- [ ] **[Human]** Install the stock **iPixel Color** app; confirm both panels pair and display text.
- [ ] **[Human]** Using **nRF Connect**, record each panel's MAC address and confirm the GATT profile: service `FA00`, write `FA02`, notify `FA03` (§4.2.1).
- [ ] **[Human]** On a laptop, `pip install pypixelcolor`; send `send_text`, `send_image`, `set_brightness`, `set_power`, `show_slot` to each panel. Confirm device type byte = `0x83` (64x16).
- [ ] **[Human]** Enable Android **Bluetooth HCI snoop log** on the Moto G; capture a send from the stock app to confirm byte layouts in §4.2.4–4.2.6.
- [ ] **[Human]** Confirm the Moto G Stylus holds **two simultaneous BLE connections** (nRF Connect, both panels connected).
- [ ] **[Human]** Measure: time from send to illumination for a short text and a multi-window image.
- [ ] **[Human]** Check whether the Moto G can auto-boot on charger power (`fastboot oem off-mode-charge 0`, needs unlocked bootloader) or must stay on in the car (§3.8.1).
- [ ] **[Human]** Note what each panel displays on power-up (default content / last slot) and time to advertise after power-on.
- [ ] **[Human]** Decision checkpoint: update §4.2 with any protocol differences found.

---

## Phase 1: Project Setup

- [ ] **[Agent AI]** Generate Android Studio project (Kotlin, Jetpack Compose, Material 3, minSdk 30, targetSdk 34).
- [ ] **[Agent AI]** Add dependencies in `build.gradle.kts`:
  - `androidx.compose.material3`
  - `androidx.glance:glance-appwidget`
  - `androidx.room:room-runtime` + `room-ktx` + `ksp`
  - `androidx.datastore:datastore-preferences`
  - `androidx.lifecycle:lifecycle-service`
  - `org.jetbrains.kotlinx:kotlinx-coroutines-android`
  - Reorderable list library for Compose (drag & drop, §3.3)
- [ ] **[Agent AI]** Configure `AndroidManifest.xml` permissions:
  - `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT` (API 31+); `BLUETOOTH`, `BLUETOOTH_ADMIN` (API 30)
  - `RECORD_AUDIO`
  - `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE`
  - `RECEIVE_BOOT_COMPLETED`, `VIBRATE`, `WAKE_LOCK`
- [ ] **[Agent AI]** Register AutoComm as a **Home** (launcher) activity; `BOOT_COMPLETED` and `ACTION_POWER_CONNECTED` receivers (§3.8.1).
- [ ] **[Agent AI]** Runtime permission request flow on first launch.
- [ ] **[Agent AI]** Bundle assets: UNIFONT (`.otf`), Twemoji 72x72 set (offline).

---

## Phase 2: iPixel Protocol Layer (§4.2)

Port from `pypixelcolor` (`commands/send_text`, `commands/send_image`, `lib/transport/*`, `lib/device_info.py`).

- [ ] **[Agent AI]** `IPixelCommands.kt` — simple frames: Get Device Info, Power On/Off, Brightness, Orientation, Show Slot, Delete Slot (§4.2.4). Exclude Clear ROM from runtime API.
- [ ] **[Agent AI]** `IPixelTextEncoder.kt` — `data_payload` = num_chars + 13-byte properties + character bitmaps (§4.2.5).
- [ ] **[Agent AI]** `IPixelImageEncoder.kt` — PNG/JPG/WEBP/BMP/GIF/HEIC → 64x16 frames, Crop/Fit (§3.4.3).
- [ ] **[Agent AI]** `IPixelTransport.kt` — 12 KB windows, CRC32, save-slot byte, 244-byte chunked write-with-response, ACK wait on `FA03` (codes `00`/`01`/`03`), 2 s timeout + one retry, cancel between windows (§4.2.6).
- [ ] **[Agent AI]** Unit tests: byte-for-byte parity with `pypixelcolor` output for identical inputs (generate fixtures from Python).

---

## Phase 3: Rendering Engine (§3.4.7, §4.2.8)

- [ ] **[Agent AI]** `TextRasterizer.kt` — Android `Canvas`/`Bitmap` glyph rendering: font, size, offset X/Y, pixel threshold, variable width, per-character color from inline `[#RRGGBB]…[/]` tags, emoji via bundled Twemoji.
- [ ] **[Agent AI]** `MirrorTransform.kt` — horizontal flip of glyph bitmaps + reverse character order + swap Scroll Left/Right; flip every image/GIF frame.
- [ ] **[Agent AI]** Per-panel render: front = mirrored (when `Mirror front panel` ON), rear = normal.
- [ ] **[Agent AI]** `DotMatrixPreview.kt` — shared Compose Canvas preview driven by the same rasterizer (un-mirrored by default; mirrored view option).
- [ ] **[Agent AI]** Unit tests: mirrored output = flipped normal output; scroll direction swap.

---

## Phase 4: BLE Service & Data Layer

- [ ] **[Agent AI]** `BleManagerService.kt` (foreground service, `connectedDevice` type):
  - Dual `BluetoothGatt` connections (front / rear), MTU 247 request.
  - Connection handshake (§4.2.3): notify enable → Get Device Info → verify `0x83` → apply brightness/orientation.
  - `StateFlow<ConnectionState>` per panel (Connected / Connecting / Disconnected / Unsupported).
  - Auto-reconnect every 3 s (§5).
  - Zone routing: FRONT / REAR / BOTH in parallel coroutines (§4.2.2).
  - Per-panel auto-clear timer → Power Off; Power On before next send (§4.2.7).
- [ ] **[Agent AI]** Room: `MacroEntity` with full schema (§3.4.8), `MacroDao` (CRUD, ordered by `sortOrder`, filter `showOnMain`), migrations.
- [ ] **[Agent AI]** DataStore: panel MAC addresses, mirror toggles, brightness, last-sent message (for Repeat Last).
- [ ] **[Agent AI]** Seed default macros on first run.
- [ ] **[Agent AI]** User power state per panel (ON/OFF) in DataStore; routing skips OFF panels; re-apply after handshake; keep separate from auto-clear power-off (§4.2.2, §4.2.7).

---

## Phase 4b: Bluetooth Setup Screen (§3.6)

- [ ] **[Agent AI]** `BleScanner.kt` — 10 s low-latency scan, filter by `FA00` service / iPixel name pattern, live RSSI, restartable (`⟳ SCAN`).
- [ ] **[Agent AI]** `BluetoothSetupScreen.kt` — Assigned Panels, Nearby Devices, Panel Settings sections.
- [ ] **[Agent AI]** Assign to Front/Rear (handshake → save MAC), replace-confirm dialog, Identify (3 s Blink label), Swap, Reconnect, Disconnect, Forget, Device Info.
- [ ] **[Agent AI]** Panel Settings: mirror, brightness, rotation — apply live and persist.
- [ ] **[Agent AI]** Bluetooth-off banner, permission prompts, empty-scan guidance.
- [ ] **[Agent AI]** Auto-open on first launch when no panels assigned.

---

## Phase 5: Main Screen UI (§3.2)

- [ ] **[Agent AI]** `HeaderPanelStatus.kt` — FRONT/REAR badges (incl. `SET UP` state); tap to force reconnect; per-panel `[⏻]` power buttons (tap = toggle, long-press = both); `[ᛒ]` Bluetooth button with alert dot.
- [ ] **[Agent AI]** `MarqueePreview.kt` — live preview, countdown badge, `X` dismiss, `MIRRORED` tag.
- [ ] **[Agent AI]** `DirectionalTriggers.kt` — LEFT / U-TURN / RIGHT latching toggles (tap on → send, tap off → Idle), one active at a time, hold P1/P2 (latest) while active.
- [ ] **[Agent AI]** Directional assets: LEFT/RIGHT chevron sweep animations, U-TURN yellow diamond with black U-turn arrow (static), normal + mirrored variants.
- [ ] **[Agent AI]** `MacroGrid.kt` — 2-column grid, `showOnMain` only, ordered by priority (1–2 row 1, 3–4 row 2…), zone color bars, long-press → editor, `[⚙]` → Macro Settings.
- [ ] **[Agent AI]** `VoiceZoneSelector.kt` — `VOICE TO: FRONT | BOTH | REAR`, applies to voice only.
- [ ] **[Agent AI]** Two-finger swipe detector (↑ ↓ ← →) → assigned macro.
- [ ] **[Agent AI]** `[🕘]` History button on Saved Macros header.
- [ ] **[Agent AI]** `PttButton.kt` — hold ≥ 300 ms = record/send; tap < 300 ms = Repeat Last; idle shows `↻ <last message>` or greyed when none.

---

## Phase 6: Macro Settings & Message Editor (§3.3, §3.4)

- [ ] **[Agent AI]** `MacroSettingsScreen.kt` — list of all saved messages, Show on Main toggle, favorite star (max 6), swipe assignment (unique per direction), drag-handle reorder persisting `sortOrder`, `[ + ]` → editor, tap row → editor (Edit mode).
- [ ] **[Agent AI]** `MessageEditorScreen.kt`:
  - General: label, type (Text / Image-GIF), zone, display duration.
  - Text: message, emoji, animation, speed, text color, per-word color, background, rainbow, font + import, size, offset, threshold, variable width.
  - Image: picker, Crop/Fit, animated preview.
  - Slot caching toggle + slot picker (requires successful TEST first).
  - TEST, SAVE, DELETE (edit mode), discard-changes dialog.
  - Validation and warnings (§3.4.6).
- [ ] **[Agent AI]** Font import to app-private storage.

---

## Phase 6b: App Behavior, History & Idle (§3.6.5–3.6.6, §3.7, §3.8)

- [ ] **[Agent AI]** Startup sequence: direct connect to saved MACs → 15 s → background scan → retry; open Bluetooth Setup if none assigned.
- [ ] **[Agent AI]** Startup "Laser AutoComm" animation asset (normal + mirrored) → then Idle.
- [ ] **[Agent AI]** Idle Content per panel: Dark / saved message / custom text / image; cached slot.
- [ ] **[Agent AI]** `HistoryScreen.kt` — last 10 sends, re-send, save voice as macro, Clear History; Room `history` table capped at 10.
- [ ] **[Agent AI]** Keep screen on, portrait lock, dark theme.
- [ ] **[Agent AI]** Send feedback: success chime + vibration, failure double-buzz; toggles.
- [ ] **[Agent AI]** Cooldown (default 3 s, 0–10 s); directionals/Sudden Stop exempt.
- [ ] **[Agent AI]** `SimulatorMode` — virtual front (mirrored) and rear panels, full encode pipeline, frames logged.
- [ ] **[Agent AI]** `AppLogger.kt` — rolling 5 MB / 7-day log, crash handler, verbose hex mode, Export Logs (.zip share).
- [ ] **[Agent AI]** Settings screen (§3.8.8) + About / version / Check for updates link.
- [ ] **[Agent AI]** Release build signing config; GitHub Actions workflow publishing signed APK to GitHub Releases.
- [ ] **[Human]** Install **Obtainium** on the Moto G, add the GitHub repo, allow *Install unknown apps*.
- [ ] **[Human]** Set AutoComm as default Home app; disable lock screen on the dedicated phone.

---

## Phase 6c: Sudden Stop Detection (§4.3)

- [ ] **[Agent AI]** `SuddenStopDetector.kt` — linear acceleration + gyroscope rejection, forward-axis calibration, 0.45 g / 300 ms default, sensitivity levels.
- [ ] **[Agent AI]** Rear-only P0 "STOP" hazard asset; restore previous rear content 3 s after deceleration ends.
- [ ] **[Agent AI]** Calibrate flow, On/Off + sensitivity settings, *Simulate Hard Stop* in Simulator Mode.
- [ ] **[Human]** Real-world tuning: hard stops vs. potholes, speed bumps, normal braking.

---

## Phase 7: Voice, Priority Queue & Repeat (§4.1)

- [ ] **[Human]** Download the offline English speech pack on the Moto G (Google app → Voice → Offline speech recognition).
- [ ] **[Agent AI]** `SpeechRecognitionManager.kt` — `SpeechRecognizer` with `EXTRA_PREFER_OFFLINE`, partial results, cleanup of final text.
- [ ] **[Agent AI]** `MessagePriorityQueue.kt` — P0 > P1 > P2 preemption, P2 queued while P0/P1 active, Repeat Last inherits original priority, cancel in-flight lower-priority transfers.
- [ ] **[Agent AI]** `RepeatLastManager.kt` — capture last macro/voice send (not directionals, not repeats); replay to original zone(s) with original settings; prefer cached slot.

---

## Phase 8: Panel Slot Caching (§3.4.4)

- [ ] **[Agent AI]** Slot allocator per panel (1–10), conflict prevention.
- [ ] **[Agent AI]** Upload mirrored variant to front slot and normal variant to rear slot.
- [ ] **[Agent AI]** Re-upload on edit; `delete <slot>` on macro delete.
- [ ] **[Agent AI]** Reserved slots: LEFT, RIGHT, U-TURN, Sudden Stop, Idle, Startup — pre-cached per panel (mirrored front).

---

## Phase 9: Home Screen Widgets (§3.5)

- [ ] **[Agent AI]** `FavoritesWidget4x4.kt` — renders starred favorites (≤ 6) in priority order; tap dispatches send via `BleManagerService`.
- [ ] **[Agent AI]** `MicLauncherWidget2x4.kt` — launches `MainActivity` with `EXTRA_START_PTT = true`.
- [ ] **[Agent AI]** Register widget providers in `AndroidManifest.xml`.

---

## Phase 9b: In-House Bench Test (before vehicle install)

- [ ] **[Human]** Run full app in Simulator Mode; verify all screens, queue, repeat, history, swipes, cooldown, Sudden Stop simulation.
- [ ] **[Human]** Connect real panels on the bench (indoor USB power); verify startup animation, idle, directionals, macros, voice, power buttons, mirroring (hand mirror).
- [ ] **[Human]** Power-cycle test: unplug/replug panels and phone charger; confirm auto-start, reconnect, startup animation.
- [ ] **[Human]** Review exported logs for errors/timeouts.
- [ ] **[Human]** Install an update via Obtainium; confirm saved messages and settings survive.

---

## Phase 10: In-Vehicle Installation & Validation

- [ ] **[Human]** Install both panels and buck converters on switched 12V; route cables.
- [ ] **[Human]** Mount phone; set up 12V USB-C charging.
- [ ] **[Human]** Mirror test: read front panel in a hand mirror — text reads left-to-right; Left arrow points left.
- [ ] **[Human]** Rear panel readability test from following vehicle distance, day and night; tune brightness.
- [ ] **[Human]** Ignition cycle test: panels and app reconnect automatically after power loss.
- [ ] **[Human]** Latency test: tap-to-illumination for directionals, cached macros, uncached macros.
- [ ] **[Human]** Voice accuracy test with HVAC and road noise.
- [ ] **[Human]** Heat soak: phone and panels in a parked car on a hot day.

---

## Open Items (PRD gaps to define before the phase that needs them)

| Item | Needed by | Notes |
| :--- | :--- | :--- |
| Day/night panel brightness (evaluate panels at night first) | Phase 10 | Panel brightness/mirror/rotation now in Bluetooth Setup (§3.6.4). |
| Realistic latency and STT targets (§5) | Phase 0 results | Set from measured numbers. |

---

## Phase 11 (v2.0 — Nice to Have): Wake Word & Voice Command (§6.1)

- [ ] **[Human]** Choose wake word engine: sherpa-onnx KWS (free) or Porcupine (create Picovoice account, train "Yo AutoComm", obtain AccessKey).
- [ ] **[Agent AI]** `WakeWordEngine.kt` — Porcupine in `BleManagerService`; add `microphone` foreground service type and permission handling.
- [ ] **[Agent AI]** `VoiceCommandSession.kt` — chime, listening UI, STT with live partial transcript, Silero VAD end-of-speech (~1.2 s), tap-to-end, 15 s max.
- [ ] **[Agent AI]** `CommandGrammarParser.kt` — Tier 1 zone/message/modifier/action parsing (§6.1.2) + unit tests with sample phrases.
- [ ] **[Agent AI]** `EmojiDictionary.kt` — spoken phrase → emoji map (§6.1.3), user-editable.
- [ ] **[Agent AI]** Integrate sherpa-onnx (streaming STT + Silero VAD) for Android.
- [ ] **[Agent AI]** Generate 300–500 synthetic command → JSON training pairs (§6.1.2a).
- [ ] **[Human]** Fine-tune FunctionGemma 270M (Colab + Unsloth/Google guide); export `.litertlm`; evaluate on held-out phrases.
- [ ] **[Agent AI]** `OnDeviceCommandParser.kt` — LiteRT-LM inference, JSON validation, 3 s timeout, load-on-wake / unload after 60 s, thermal skip.
- [ ] **[Agent AI]** Model download manager (Wi-Fi, app-private storage, checksum).
- [ ] **[Agent AI]** Optional `CloudCommandParser.kt` — Tier 3 LLM API fallback (off by default).
- [ ] **[Human]** Benchmark on Moto G: wake latency, STT accuracy, parse time, phone temperature.
- [ ] **[Agent AI]** Confirm-and-send overlay (2 s auto-send, "cancel" / `✕`).
- [ ] **[Agent AI]** v2.0 settings screen (§6.1.4).
- [ ] **[Human]** In-car tests: wake-word false triggers with music/road noise, command accuracy, end-of-speech timing.

---

## Phase 12 (v2.0 — Nice to Have): Physical Bluetooth Remote (§6.2)

- [ ] **[Human]** Choose and buy a BLE/HID steering-wheel button remote.
- [ ] **[Agent AI]** Key event capture + Settings button mapping (Left, Right, U-Turn, Repeat, Hold-to-Talk, swipe slots).
- [ ] **[Human]** In-car test of remote reliability and latency.
