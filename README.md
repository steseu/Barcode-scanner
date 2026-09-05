# BarcodeBridge

Native Android app for scanning 1D/2D barcodes, managing a searchable scan
history, exporting to CSV/TXT, and sending scans straight to a Windows PC via
Bluetooth HID (keyboard emulation), Wi-Fi/TCP (companion app), or an HTTP
webhook - fully offline, on-device, no account required.

## Tech stack

- Kotlin, Jetpack Compose, Material 3 (dynamic color, dark mode)
- minSdk 26, targetSdk 35, Gradle Kotlin DSL
- CameraX + ML Kit Barcode Scanning (on-device, no network access)
- MVVM + Repository, Hilt for DI, Coroutines/Flow
- Room (scan history), DataStore (settings)

## Architecture overview

```
app/src/main/java/com/barcodebridge/app/
├── data/
│   ├── local/        Room entities/DAOs (ScanEntity, SessionEntity)
│   ├── repository/   ScanRepository, SessionRepository (Flow-based)
│   └── settings/     AppSettings (single JSON document in DataStore)
├── domain/
│   ├── model/        BarcodeFormat, ScanRecord, content-type parsers
│   │                 (Wi-Fi QR, vCard, iCalendar)
│   └── export/        CsvExporter, TxtExporter, ExportRow — pure Kotlin,
│                       no Android dependency, fully unit-testable
├── scanner/           CameraX analyzer, ML Kit glue, image/gallery scanning,
│                       sound/vibration feedback
├── export/            SAF document creation, FileProvider sharing,
│                       filename templating, export dialog + view model
├── transport/
│   ├── hid/           Bluetooth HID keyboard: KeymapEngine, KeymapLoader,
│   │                   HidCalibration (see "Keyboard layouts" below)
│   ├── tcp/           TCP client + offline queue for the Windows companion
│   │                   app, pairing QR parser
│   └── http/          OkHttp-based webhook sender
├── ui/                Compose screens per feature (scan/history/settings),
│                       navigation, Material3 theme
└── di/                Hilt modules (database, DataStore, repositories,
                        transport)
```

Data flows one way: `scanner` detects a barcode → `ScanViewModel` persists it
via `ScanRepository` (Room) → `HistoryViewModel` observes the same repository
reactively (Flow) → `export` reads whatever the history screen currently
shows → `transport` sends the raw content to whichever transfer method is
active in `AppSettings`. No layer reaches backward.

## Setup

1. Install [Android Studio](https://developer.android.com/studio) (current
   stable) with an Android 15 (API 35) SDK platform installed.
2. Open the project root in Android Studio; it will resolve dependencies via
   Gradle automatically (all dependencies come from Google's and Maven
   Central's repositories - no custom repos needed).
3. Run on a device or emulator with a camera (the emulator's virtual camera
   works for manual/gallery testing but a real device is recommended for
   live barcode scanning).

From the command line:

```bash
./gradlew assembleDebug          # build a debug APK
./gradlew testDebugUnitTest      # run unit tests (CSV/TXT, keymap engine, ...)
./gradlew connectedAndroidTest   # run instrumented tests (needs a device/emulator)
./gradlew lint                   # MissingTranslation/HardcodedText are NOT suppressed
```

> This repository was assembled in a sandboxed environment without access to
> the Android SDK or Google's Maven repository, so the above commands could
> not be executed here. The pure-Kotlin business logic (CSV/TXT export, the
> entire HID keymap engine, QR/vCard/iCalendar parsers - everything under
> `domain/`, `export/` and `transport/hid`) was independently verified by
> copying it into a throwaway plain Kotlin/JUnit Gradle project (Maven
> Central only) and running the real test suite there; all 62 tests passed.
> Run the commands above yourself once you have Android Studio installed to
> exercise the Android-specific layers (Compose UI, Room, CameraX, Bluetooth).

## Pairing with the PC

The **Settings → PC transfer** screen switches between the three methods;
only one is active at a time.

### A) Bluetooth HID (no PC software needed)

1. Enable Bluetooth on both devices and pair the phone with the PC as you
   normally would (Windows Settings → Bluetooth & devices).
2. In the app, choose **Bluetooth HID (keyboard)**, pick the keyboard layout
   that matches what's currently active on the **Windows taskbar language
   switcher** (e.g. "DEU"), and start scanning - it types straight into
   whatever window has focus.
3. If characters come out wrong (or you're not sure which layout Windows is
   using), open **Calibration wizard**: it sends a test string, you type back
   what actually appeared on the PC, and the app tells you which layout
   actually matches.
4. If a barcode contains a character your chosen layout can't produce
   (default: warn and abort, never guess), the app offers to switch to
   method B instead - see below.

### B) Wi-Fi / TCP companion app (recommended for special characters)

1. Build and run the Windows companion app - see
   [`companion-windows/README.md`](companion-windows/README.md).
2. It shows a QR code (and the host/port/token as text). In the Android app,
   choose **Wi-Fi / TCP companion app** and tap **Scan pairing QR code**.
3. Both devices must be on the same local Wi-Fi/LAN. Scans typed this way
   bypass the Windows keyboard layout entirely (`SendInput` +
   `KEYEVENTF_UNICODE`), so this is the reliable choice for non-Latin text or
   an unknown PC layout.
4. If the connection drops, scans are queued on the phone and sent
   automatically once it reconnects.

### C) HTTP webhook

Point it at any endpoint you control (a local script, a cloud function, a
Zapier/n8n webhook, ...); choose JSON (`{"content": "..."}`) or plain text,
and add custom headers if the endpoint needs auth.

## Localization

UI strings live in `res/values/strings.xml` (English, default) and
`res/values-de/strings.xml` (German) - none are hardcoded in Kotlin. The
in-app **Sprache** setting (System/Deutsch/English) applies immediately via
Android's per-app language API (`AppCompatDelegate.setApplicationLocales`),
independently of the **HID keyboard layout** setting, which only affects
what gets typed on the PC. Date/time formatting in exports and the UI uses
`DateTimeFormatter` with the active locale.

## Known limitations

- **USB-HID was intentionally not implemented.** Acting as a USB HID device
  requires either USB On-The-Go host-mode gadget support that regular,
  non-rooted Android phones don't expose to apps, or a custom kernel/gadget
  driver - there is no public Android API for a stock phone to present
  itself as a USB keyboard to a host PC. Bluetooth HID (method A) is the
  supported "no companion software" path instead.
- **CH (Swiss) and AT (Austrian) keyboard layouts currently mirror the DE
  T1 mapping.** The umlaut/ß placement the spec calls out is genuinely
  shared across DE/AT/CH, but Swiss German keyboards differ from German ones
  in several AltGr/punctuation positions that could not be verified without
  physical hardware. If your Swiss/Austrian PC types the wrong punctuation,
  run the in-app calibration wizard and, if needed, adjust
  `app/src/main/assets/keymaps/ch.json` / `at.json` directly - no code
  changes required, since layouts are pure data (see "Keyboard layouts"
  below).
- **French AZERTY** is implemented to the same structural standard as the
  other layouts (full printable-ASCII coverage, dead keys, AltGr) but has
  lower confidence on some AltGr punctuation placements than the DE/US/UK
  layouts, which map to every explicit test case in the spec. Same
  remediation: calibration wizard, then edit `fr_azerty.json` if needed.
- **Tablet/landscape layouts** use the same responsive Compose layout as
  phones (no fixed widths, so it doesn't clip) but there's no dedicated
  two-pane tablet layout.
- The TCP companion app handles one actively-typing phone connection well;
  a second simultaneous connection is accepted but its keystrokes interleave
  with the first (documented in `companion-windows/README.md`).

## Keyboard layouts (HID) are pure data

Adding or fixing a layout never touches Kotlin code: each file under
`app/src/main/assets/keymaps/*.json` maps `"character": {"usageCode": ...,
"modifiers": [...], "deadKey": ...}`. `KeymapLoader`/`KeymapParser` validate
every entry at load time and report broken ones instead of crashing;
`KeymapEngine` turns text into HID reports (dead-key-then-Space commit
sequences, a forced empty report between two identical consecutive
keystrokes, AltGr sent as the right-Alt modifier bit `0x40` - never
Ctrl+Alt). See `transport/hid/GermanLayoutTest.kt` and
`KeymapRoundTripTest.kt` for the pinned behavior.

## Privacy

No analytics, no ad SDKs, no account/login. Camera frames never leave the
device (ML Kit barcode detection runs fully on-device). The Wi-Fi/TCP and
HTTP transfer methods are the only network activity, and only when the user
explicitly enables and configures them.
