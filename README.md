# Phone Mouse & Keyboard

An Android app that turns your phone into a Bluetooth mouse and keyboard for any computer,
tablet or TV that accepts Bluetooth input devices. Nothing has to be installed on the device
being controlled: the phone shows up as an ordinary Bluetooth keyboard/mouse.

## Features

- **Mouse tab:** a trackpad with Left and Right buttons
  - one-finger drag moves the pointer
  - tap to left-click, two-finger tap to right-click
  - two-finger drag scrolls
  - hold a button while dragging on the trackpad to click-and-drag
- **Keyboard tab:** a text box. Whatever you type with the phone's keyboard (including
  autocorrect, swipe typing and backspace) is replayed on the computer.

## Requirements

- Android 9 (API 28) or newer. The app uses Android's `BluetoothHidDevice` API.
- A phone whose manufacturer hasn't disabled the Bluetooth HID Device profile. Most do support
  it, but a few devices don't. In that case the app says so at the top of the screen.

## Building

1. Open this folder in Android Studio (Ladybug or newer).
2. Let Gradle sync. Android Studio will download the Gradle wrapper and dependencies.
3. Run the `app` configuration on a physical phone. The emulator has no Bluetooth.

## Using it

1. Open the app and grant the Bluetooth permission.
2. Tap **Connect**, then **Make discoverable**.
3. On the computer, open Bluetooth settings and pair with your phone.
4. Back in the app, tap **Connect** and pick the computer. Later you can reconnect straight from
   this list.

## Project structure

```
app/src/main/java/io/github/phonemousekeyboard/
├── MainActivity.kt             Permissions, "make discoverable" request, hosts the UI
├── MainViewModel.kt            Turns UI events into HID input; survives rotation
├── bluetooth/
│   ├── HidDescriptor.kt        The report descriptor: "I am a keyboard + mouse"
│   ├── BluetoothHidController  Registers with Android, connects, sends reports
│   └── ConnectionState.kt      Connection states shown in the UI
├── input/
│   ├── KeyStroke.kt            Key code + modifiers; common special keys
│   ├── UsKeyboardLayout.kt     Character → key stroke for a US layout host
│   ├── TextDiff.kt             Text box edits → Backspaces + typed characters
│   └── MouseButton.kt
└── ui/
    ├── MainScreen.kt           Connection bar, active tab, bottom tabs
    ├── BottomTabBar.kt
    ├── mouse/                  Trackpad, gestures, mouse buttons
    ├── keyboard/               Text box screen
    ├── connection/             Status bar and device picker
    └── theme/                  Minimal dark theme
```

## Known limitations

- **US keyboard layout only.** HID keyboards send key *positions*, and the computer turns them
  into characters with its own layout setting. On a non-US layout, some symbols will come out
  differently. Other layouts can be added next to `UsKeyboardLayout`.
- **Type at the end of the text box.** Edits are mirrored on the assumption that the cursor is at
  the end. Moving the cursor into the middle of the text will not move the computer's cursor.
- Characters with no key on a US keyboard (e.g. emoji) are skipped.

## Contributing

Issues and pull requests are welcome. Please keep the code readable and the UI minimal.
