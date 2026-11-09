# Nightcrawler AutoComm v0.1

In-car Android app (Lenovo TB-8505FS, Android 10) driving front/rear PixelJoy BLE LED panels (T074).

## Features (v0.1)
- Assign FRONT / REAR panels by BLE scan (saved; auto-reconnect every 3 s)
- PixelJoy handshake (CCHIP challenge/response), reads panel resolution
- Voice-to-text (Google speech) → auto-sends to panels
- Typed messages, 8 preset macros, 6 colors, target FRONT / REAR / BOTH
- Text that fits → static PNG; longer text → scrolling GIF (rendered on-device)
- CLOCK mode and DISPLAY OFF

## Build
1. Open this folder in Android Studio → let Gradle sync (accept any AGP upgrade prompt).
2. Tablet connected with USB debugging → Run ▶ `app`.

## Protocol
Based on github.com/sdylewski/openJoy (docs/PROTOCOL.md). UUIDs: service 0000E0xx, write 0000A0xx, notify 0000F0xx (xx = model id from the panel name, e.g. P0AD → AD).

## Next (v0.2)
- Foreground service (keeps panels connected with screen off)
- Editable presets / animations library
- Brightness (needs BLE capture from PixelJoy)
