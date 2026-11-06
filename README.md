# Phone Mouse & Keyboard

An Android app that turns your phone into a Bluetooth mouse and keyboard for any computer,
tablet or TV that accepts Bluetooth input devices. Nothing has to be installed on the device
being controlled: the phone shows up as an ordinary Bluetooth keyboard/mouse.

## Features

The app has three main pages: Mouse, Keyboard and Air mouse. Swipe the title at the top to move between them;
the names of the other pages show faintly either side of the current one.

The line under the header shows the connection at a glance: grey when nothing is connected,
green when connected, and blue with ripples running along it while input is being sent.

- **Mouse page:** a trackpad with Left and Right buttons
  - one-finger drag moves the pointer
  - tap to left-click, two-finger tap to right-click
  - two-finger drag scrolls
  - hold a button while dragging on the trackpad to click-and-drag
  - **⋮ → Mouse settings:** pointer speed and acceleration, scroll speed, direction (natural or
    classic) and smooth scrolling, tap to click, two-finger tap to right-click, show/hide the
    buttons, vibrate on click, glowing touch orbs under your fingers. Settings are saved
    automatically.
- **Keyboard page:** a text box. Whatever you type with the phone's keyboard (including
  autocorrect, swipe typing and backspace) is replayed on the computer.
  - **⋮ → Keyboard settings:** a delay per character and a random extra delay, so long pasted
    text is typed out gradually (with a **Stop** button while it sends); typed text fading out of
    the box after a set time; phone autocorrect on or off; clear the box after Enter.
- **Air mouse page:** point the top of the phone at the screen and turn it left/right or tilt
  it up/down to move the pointer, like a TV remote. Uses the gyroscope, plus gravity to know
  which way is up, so it works with the phone held flat or upright. The page shows a small
  picture of a screen with the pointer moving on it, mirroring what's being sent, plus live
  readings; Left/Right buttons sit underneath. Pause at any time.
  - **⋮ → Air mouse settings:** speed, steadiness (ignores hand tremor), smoothing, move only
    while touching, invert either direction.
- **Menu → Connect → Bluetooth:** one page for everything connection-related: permissions,
  pairing instructions, and connecting to or disconnecting from a computer.

## How it connects

The phone acts as a **Bluetooth Low Energy keyboard and mouse** ("HID over GATT"), the way most
modern wireless mice and keyboards work. Computers support this natively, so nothing needs to be
installed on the computer.

The computer needs Bluetooth 4.0 or newer and an operating system that supports Bluetooth Low
Energy keyboards and mice: current Windows, macOS, Linux, ChromeOS, iPadOS and Android all do.

> **Classic Bluetooth was removed.** Earlier versions could also connect over classic Bluetooth
> (Android's "HID Device" profile), which helps with some older computers, TVs and consoles that
> don't support Bluetooth Low Energy keyboards. It was dropped to keep the app simple, because
> many phones disable that profile. The last version with it is on the
> [`classic-bluetooth`](../../tree/classic-bluetooth) branch, and the code is built to plug back
> in behind the `HidTransport` interface if it's needed again.

## Requirements

- Android 9 (API 28) or newer.
- A phone that supports Bluetooth Low Energy advertising (most do).

## Building

1. Open this folder in Android Studio (Ladybug or newer).
2. Let Gradle sync. Android Studio will download the Gradle wrapper and dependencies.
3. Run the `app` configuration on a physical phone. The emulator has no Bluetooth.

## Using it

1. Open the app and grant the Bluetooth permissions.
2. Open the menu (top left) and go to **Connect → Bluetooth**. Wait until
   *Keyboard & mouse service* says **Ready**.
3. If the phone was paired with the computer before (e.g. as a phone), remove it on the computer
   and forget the computer on the phone.
4. Pair from the computer. On Windows: **Settings › Bluetooth & devices › Add device ›
   Bluetooth**, then choose the phone. The phone is visible automatically while the app is open.
   Accept the pairing prompts on the phone (there may be two) and on the computer.
5. The computer connects by itself. The line under the header turns green when connected.

If something goes wrong, the Bluetooth page shows the reason in red.

## Project structure

```
app/src/main/java/io/github/phonemousekeyboard/
├── MainActivity.kt               Permissions and system dialogs; hosts the UI
├── MainViewModel.kt              Turns UI events into Bluetooth actions; survives rotation
├── BluetoothPermissions.kt       Which permissions are needed on which Android version
├── bluetooth/
│   ├── HidDescriptor.kt          The report descriptor: "I am a keyboard + mouse"
│   ├── HidTransport.kt           Interface for presenting the phone as keyboard + mouse
│   ├── ScrollResolutionMemory.kt Which computers enabled smooth scrolling
│   ├── ble/
│   │   ├── BleHidTransport.kt    Bluetooth Low Energy: GATT server, advertising, pairing
│   │   ├── HidGattProfile.kt     The HID-over-GATT services and characteristics
│   │   ├── ReportQueue.kt        Paces input; merges mouse movement
│   │   └── KnownHosts.kt         Remembers computers that have paired over BLE
│   ├── BluetoothDeviceManager.kt Bluetooth on/off, paired devices, pairing progress
│   └── ConnectionState.kt        Connection states shown in the UI
├── input/
│   ├── HidInput.kt               Mouse/keyboard actions → input reports
│   ├── KeyStroke.kt              Key code + modifiers; common special keys
│   ├── UsKeyboardLayout.kt       Character → key stroke for a US layout host
│   ├── TextDiff.kt               Text box edits → Backspaces + typed characters
│   ├── TypingQueue.kt            Sends key strokes one by one, with optional delays
│   ├── InputActivity.kt          Whether input is being sent right now
│   ├── AirMouseSensors.kt        Gyroscope + gravity readings for the Air mouse
│   ├── AirMotionProcessor.kt     Phone rotation → pointer movement (pure, testable)
│   └── MouseButton.kt
└── ui/
    ├── MainScreen.kt             Header, current page, menu
    ├── Screen.kt                 All pages, and which ones are main (swipeable) pages
    ├── AppHeader.kt              Menu button, title, ⋮ menu
    ├── TitleSwitcher.kt          Swipeable title with faded neighbouring page names
    ├── StatusLine.kt             Glowing header line: connection and input activity
    ├── AppMenu.kt                Slide-out menu: pages, settings, about
    ├── icons/                    Line icons not in the Material set (mouse, keyboard, …)
    ├── mouse/                    Trackpad, gestures, mouse buttons, mouse settings
    ├── keyboard/                 Text box page, keyboard settings
    ├── airmouse/                 Air mouse page, illustration, settings
    ├── bluetooth/                Bluetooth settings page
    ├── components/               Reusable settings groups, rows and buttons
    └── theme/                    Minimal dark theme
```

## Troubleshooting

**Scrolling isn't smooth after updating the app.** Smooth scrolling needs the computer to
learn about it during pairing. Remove the phone on the computer and pair again once.

**The computer only sees a phone, not a keyboard/mouse.** The phone presents itself as a
keyboard/mouse only while *Keyboard & mouse service* says **Ready**. If the computer paired
before that, it remembered a plain phone. Remove the phone on the computer, forget the computer
on the phone, wait for **Ready**, then pair again.

**The phone doesn't appear in Windows' "Add device" list.** Make sure the
app is open on the Bluetooth page and says **Ready**. On Windows 11, set **Settings › Bluetooth
& devices › Devices › Bluetooth devices discovery** to **Advanced** to list every device.

**Bluetooth Low Energy says it can't start.** The phone can't advertise over Bluetooth Low
Energy, or too many apps are already doing so. Close other Bluetooth apps and tap **Retry**.

## Known limitations

- **US keyboard layout only.** HID keyboards send key *positions*, and the computer turns them
  into characters with its own layout setting. On a non-US layout, some symbols will come out
  differently. Other layouts can be added next to `UsKeyboardLayout`.
- **Backspace always reaches the computer**, even when the text box is empty or its text has
  faded away: an invisible character at the start of the box catches it.
- **Type at the end of the text box.** Edits are mirrored on the assumption that the cursor is at
  the end. Moving the cursor into the middle of the text will not move the computer's cursor.
- Characters with no key on a US keyboard (e.g. emoji) are skipped.

## Acknowledgements

The Bluetooth Low Energy implementation follows the approach of
[BLE-HID-Peripheral-for-Android](https://github.com/kshoji/BLE-HID-Peripheral-for-Android) by
kshoji (Apache 2.0). The former classic Bluetooth start-up handling was informed by
[Bluke](https://github.com/arnav-kr/Bluke).

## Contributing

Issues and pull requests are welcome. Please keep the code readable and the UI minimal.
