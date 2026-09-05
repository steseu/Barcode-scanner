using System.Runtime.InteropServices;

namespace BarcodeBridgeCompanion.Services;

/// <summary>
/// Types text into whatever window currently has focus using
/// <c>SendInput</c> with <c>KEYEVENTF_UNICODE</c>. This bypasses the active
/// Windows keyboard layout entirely - every Unicode character (umlauts, the
/// Euro sign, non-Latin scripts) arrives correctly regardless of which
/// physical keyboard layout is selected, unlike the phone's Bluetooth-HID
/// path, which has to guess the layout. This is the "layoutunabhängig"
/// fallback the app recommends for barcodes with special characters.
/// </summary>
public static class UnicodeKeyboardSender
{
    private const uint InputKeyboard = 1;
    private const uint KeyEventFUnicode = 0x0004;
    private const uint KeyEventFKeyUp = 0x0002;
    private const ushort VkReturn = 0x0D;

    // The real Win32 INPUT struct is `{ DWORD type; union { MOUSEINPUT; KEYBDINPUT;
    // HARDWAREINPUT; } }`. SendInput validates the exact native size of this struct
    // (40 bytes on x64), so the union must be modeled explicitly with FieldOffset(0) -
    // a flattened "keyboard-only" struct would marshal to the wrong size and make
    // SendInput silently fail.
    [StructLayout(LayoutKind.Sequential)]
    private struct InputEvent
    {
        public uint Type;
        public InputUnion Union;
    }

    [StructLayout(LayoutKind.Explicit)]
    private struct InputUnion
    {
        [FieldOffset(0)] public MouseInput Mouse;
        [FieldOffset(0)] public KeyboardInput Keyboard;
        [FieldOffset(0)] public HardwareInput Hardware;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct MouseInput
    {
        public int Dx;
        public int Dy;
        public uint MouseData;
        public uint Flags;
        public uint Time;
        public IntPtr ExtraInfo;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct KeyboardInput
    {
        public ushort VirtualKey;
        public ushort ScanCode;
        public uint Flags;
        public uint Time;
        public IntPtr ExtraInfo;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct HardwareInput
    {
        public uint Msg;
        public ushort ParamL;
        public ushort ParamH;
    }

    [DllImport("user32.dll", SetLastError = true)]
    private static extern uint SendInput(uint numberOfInputs, InputEvent[] inputs, int structSize);

    /// <summary>Types <paramref name="text"/> character-by-character, optionally followed by Enter.</summary>
    public static void Type(string text, bool pressEnterAfter, int perKeyDelayMs = 5)
    {
        foreach (char c in text)
        {
            SendUnicodeChar(c);
            if (perKeyDelayMs > 0) Thread.Sleep(perKeyDelayMs);
        }

        if (pressEnterAfter)
        {
            SendVirtualKey(VkReturn);
        }
    }

    private static void SendUnicodeChar(char c)
    {
        var down = new InputEvent
        {
            Type = InputKeyboard,
            Union = new InputUnion
            {
                Keyboard = new KeyboardInput
                {
                    VirtualKey = 0,
                    ScanCode = c,
                    Flags = KeyEventFUnicode,
                    Time = 0,
                    ExtraInfo = IntPtr.Zero,
                },
            },
        };
        var up = down;
        up.Union.Keyboard.Flags = KeyEventFUnicode | KeyEventFKeyUp;

        SendInput(2, new[] { down, up }, Marshal.SizeOf<InputEvent>());
    }

    private static void SendVirtualKey(ushort virtualKey)
    {
        var down = new InputEvent
        {
            Type = InputKeyboard,
            Union = new InputUnion
            {
                Keyboard = new KeyboardInput
                {
                    VirtualKey = virtualKey,
                    ScanCode = 0,
                    Flags = 0,
                    Time = 0,
                    ExtraInfo = IntPtr.Zero,
                },
            },
        };
        var up = down;
        up.Union.Keyboard.Flags = KeyEventFKeyUp;

        SendInput(2, new[] { down, up }, Marshal.SizeOf<InputEvent>());
    }
}
