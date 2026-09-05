# BarcodeBridge Companion (Windows)

Companion app for transfer method **B (WLAN/TCP)** described in the main
project's README. It listens for a TCP connection from the BarcodeBridge
Android app and types every received scan into whatever window currently has
focus, using `SendInput` with `KEYEVENTF_UNICODE`. Because Unicode input
bypasses the active Windows keyboard layout entirely, this path has none of
the layout-guessing problems of the Bluetooth-HID method (transfer method A) -
it's the recommended choice whenever barcodes contain special characters or
the PC's keyboard layout is unknown.

## Requirements

- Windows 10/11
- [.NET 8 SDK](https://dotnet.microsoft.com/download) (the app targets
  `net8.0-windows` and uses WPF, which is Windows-only)

## Build & run

```powershell
cd companion-windows
dotnet restore
dotnet run --project BarcodeBridgeCompanion
```

Or open `BarcodeBridgeCompanion.sln` in Visual Studio 2022+ and press F5.

To produce a standalone `.exe`:

```powershell
dotnet publish BarcodeBridgeCompanion -c Release -r win-x64 --self-contained true -p:PublishSingleFile=true
```

The published executable is written under
`BarcodeBridgeCompanion/bin/Release/net8.0-windows/win-x64/publish/`.

## Pairing with the phone

1. Launch the companion app. It starts listening on TCP port `9999` and
   shows a QR code plus the same host/port/token as text (for manual entry
   if the phone's camera can't scan the screen).
2. In the Android app, go to **Settings → PC transfer → Wi-Fi / TCP
   companion app** and tap **Scan pairing QR code**, then point the phone's
   camera at the QR code shown on the PC.
3. Both devices must be on the same local network (Wi-Fi or LAN). No
   internet access is required or used.
4. Scan a barcode on the phone - it should be typed into whichever window is
   focused on the PC (e.g. Notepad, Excel, a web form).

If the PC's IP address changes (new network, DHCP renewal), the app updates
the QR code automatically the next time it (re)starts; use **New pairing
code** to force a new token if you suspect the old one leaked.

## Protocol

The app expects a single line `AUTH <token>` immediately after the TCP
connection opens, matching the token shown in its own pairing QR code /
text. Every line after that is treated as one scanned value and typed
verbatim (plus an optional trailing Enter, toggle in the window). This
mirrors exactly what `TcpTransport.kt` on the Android side sends.

## Known limitations

- Only one phone can be usefully connected at a time; a second connection
  is accepted but its keystrokes interleave with the first.
- The window is always-on-top by design (so you can see connection status
  while scanning into another app) - close it if that's not wanted.
- No installer/auto-start is provided; use `dotnet publish` above and add a
  shortcut to your Windows startup folder if you want it running
  automatically.
