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
- **Menu → Connect → Bluetooth:** one page for everything connection-related: permissions,
  turning Bluetooth on, making the phone discoverable, scanning for and pairing with nearby
  devices, and connecting to or disconnecting from a paired computer.

## Requirements

- Android 9 (API 28) or newer. The app uses Android's `BluetoothHidDevice` API.
- A phone whose manufacturer hasn't disabled the Bluetooth HID Device profile. Most do support
  it, but a few devices don't. In that case the app says so at the top of the screen.

## Building

1. Open this folder in Android Studio (Ladybug or newer).
2. Let Gradle sync. Android Studio will download the Gradle wrapper and dependencies.
3. Run the `app` configuration on a physical phone. The emulator has no Bluetooth.

## Using it

1. Open the app and grant the Bluetooth permissions.
2. Open the menu (top left) and go to **Connect → Bluetooth**.
3. Pair with your computer. Pairing from the computer is the most reliable:
   - If the phone was paired with the computer before (e.g. as a phone), remove it on the
     computer and forget the computer on the phone first.
   - Tap **Start** next to *Make discoverable*.
   - On Windows: **Settings › Bluetooth & devices › Add device › Bluetooth**, choose the phone,
     and confirm the code matches on both screens.

   Alternatively, tap **Scan** and then **Pair** next to the computer. Windows then shows a
   notification asking to allow the pairing, which must be accepted within a few seconds.
4. The computer usually connects by itself once paired. If not, tap **Connect** next to it
   under *Paired devices*. The dot in the header turns green when connected.

If something goes wrong, the Bluetooth page shows the reason in red at the top.

## Project structure

```
app/src/main/java/io/github/phonemousekeyboard/
├── MainActivity.kt             Permissions and system dialogs; hosts the UI
├── MainViewModel.kt            Turns UI events into Bluetooth actions; survives rotation
├── BluetoothPermissions.kt     Which permissions are needed on which Android version
├── bluetooth/
│   ├── HidDescriptor.kt        The report descriptor: "I am a keyboard + mouse"
│   ├── BluetoothHidController  Registers with Android, connects, sends input reports
│   ├── BluetoothDeviceManager  Bluetooth on/off, paired devices, scanning, pairing
│   └── ConnectionState.kt      Connection states shown in the UI
├── input/
│   ├── KeyStroke.kt            Key code + modifiers; common special keys
│   ├── UsKeyboardLayout.kt     Character → key stroke for a US layout host
│   ├── TextDiff.kt             Text box edits → Backspaces + typed characters
│   └── MouseButton.kt
└── ui/
    ├── MainScreen.kt           Header, current page, bottom tabs, menu
    ├── Screen.kt               All pages, and which ones are bottom tabs
    ├── AppHeader.kt            Menu button, connection dot + centred title, ⋮ menu
    ├── AppMenu.kt              Slide-out menu
    ├── BottomTabBar.kt
    ├── mouse/                  Trackpad, gestures, mouse buttons
    ├── keyboard/               Text box page
    ├── bluetooth/              Bluetooth settings page
    ├── components/             Reusable settings groups, rows and buttons
    └── theme/                  Minimal dark theme
```

## Troubleshooting

**The computer only sees a phone, not a keyboard/mouse.** The phone advertises itself as a
keyboard/mouse only while *Keyboard & mouse service* on the Bluetooth page says **Ready**.
If the computer paired before that, it remembered a plain phone. Remove the phone on the
computer, forget the computer on the phone, wait for **Ready**, then pair again.

**The service never gets to Ready.** After a few seconds the page explains which step failed:
- *Android refused the "HID Device" feature*: the phone's manufacturer has disabled Android's
  Bluetooth keyboard/mouse feature. No app can work around that on this phone.
- *The feature didn't respond*: the app has already retried three times. Turn Bluetooth off and
  on or restart the phone, then tap **Retry**. If it never responds, the manufacturer has most
  likely disabled the feature.
- *Android didn't accept this app*: another app is acting as a Bluetooth keyboard/mouse. Close
  it, turn Bluetooth off and on, and tap **Retry**.

To check whether your phone supports the feature at all, try another app that uses it, such as
[Bluke](https://f-droid.org/packages/dev.arnv.bluke/) or
[Bluetooth Remote](https://f-droid.org/packages/io.github.jqssun.btremote/). If they can't
work as a keyboard either, the phone doesn't support it.

## Known limitations

- **US keyboard layout only.** HID keyboards send key *positions*, and the computer turns them
  into characters with its own layout setting. On a non-US layout, some symbols will come out
  differently. Other layouts can be added next to `UsKeyboardLayout`.
- **Type at the end of the text box.** Edits are mirrored on the assumption that the cursor is at
  the end. Moving the cursor into the middle of the text will not move the computer's cursor.
- Characters with no key on a US keyboard (e.g. emoji) are skipped.

## Contributing

Issues and pull requests are welcome. Please keep the code readable and the UI minimal.
