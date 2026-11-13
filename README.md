<p align="center"><img src="store/icon-512.png" alt="" width="96"></p>

<h1 align="center">Thumbcast</h1>

<p align="center">Play your PC games on your Android phone, with touch controls or a gamepad.</p>

<p align="center"><b>English</b> · <a href="README.pl.md">Polski</a></p>

---

Thumbcast streams games from your own Windows PC to your Android phone. The game runs on your PC; the phone shows the picture and sends your input back as a virtual Xbox gamepad, a keyboard or a mouse. It is built for games with native gamepad support, including MMOs with a gamepad mode (for example World of Warcraft: Forever; game names here only describe compatibility).

Thumbcast is based on [Moonlight for Android](https://github.com/moonlight-stream/moonlight-android) (GPLv3). It is not an official Moonlight project.

**Status: early access (0.1.0).** It works end to end on a real phone and PC. Website and privacy policy: <https://serwur.github.io/thumbcast/>.

## Features

- On-screen buttons in an Xbox or PlayStation layout, mouse and keyboard controls, a desktop touchpad and a Steam Big Picture layout.
- Use a physical gamepad with your phone; on-screen buttons hide while you play with it.
- Layout editor and control profiles, saved per game and per PC app, with import and export.
- In-game menu: image quality, stats overlay, controls, button size and closing the game.
- First-run setup wizard and an offline “How to connect a PC” guide, always one tap away.
- Play away from home through Tailscale or ZeroTier, without port forwarding.
- English and Polish.

## Fair play: one touch = one input

Every control sends exactly one input. There are no macros, sequences, turbo, timers or image analysis. The rules are enforced in code and in CI. Details and an honest look at the risks: [FAIR_PLAY.md](FAIR_PLAY.md).

## Quick start

About 20 minutes, once. You need a **Windows PC** with your game, an **Android phone** (8.0 or newer) and both on the **same Wi-Fi**.

> Thumbcast is in early access. The app file (APK) is on the [Releases page](https://github.com/Serwur/thumbcast/releases/latest). A Google Play version is being prepared.

### Part 1. On your PC (once)

1. **Install Sunshine**
   Sunshine is the free program that sends your game's picture to the phone.
   - [Download Sunshine](https://github.com/SunshineStream/Sunshine/releases/latest) and open the file named `Sunshine-Windows-AMD64-installer.msi` (under *Assets*).
   - Click through the installer. If Windows asks about the firewall, choose **Allow**.

2. **Set a Sunshine password**
   - Open `https://localhost:47990` in your browser.
   - The browser says the connection is not private. That is normal. Click *Advanced*, then *Continue*.
   - Choose a username and a password and write them down.

3. **Install the gamepad driver**
   It lets games see your phone as a normal Xbox gamepad.
   - [Download ViGEmBus](https://github.com/nefarius/ViGEmBus/releases/latest), run the `.exe` installer and restart the PC if it asks.

4. **Choose the gamepad in Sunshine**
   - In the Sunshine page open **Configuration → Input**.
   - Set the emulated gamepad type to **Xbox 360** (`gamepad = x360`), then save and restart Sunshine when asked.
   - No gamepad driver? You can still play with the mouse and keyboard controls.

### Part 2. On your phone

5. **Install Thumbcast**
   - Download the APK from the [Releases page](https://github.com/Serwur/thumbcast/releases/latest) on your phone and open it.
   - Android asks to allow installs from your browser. Allow it for this one install.

6. **Find your PC**
   - Open Thumbcast. The setup wizard walks you through the same steps.
   - Your PC appears on the home screen. If not, check that both devices are on the same Wi-Fi and Sunshine is running, or tap **Add PC** and type the PC's IP address.

7. **Pair (once)**
   - Tap your PC. The phone shows a **PIN**.
   - On the PC open the Sunshine page, go to the **PIN** tab, type the PIN, give the phone any name and press **Send**.

8. **Play**
   - Tap **Desktop** or **Steam Big Picture**, then start your game.
   - The default controls are a PlayStation-style gamepad on the screen. The **☰** menu in the game changes the profile, quality and more.
   - In the game's own settings, choose borderless (windowed fullscreen) and turn on gamepad support if the game has such an option.

### Part 3. Play away from home with Tailscale (optional)

9. **Do the first two parts at home first**
   Pair the phone with the PC on your home Wi-Fi. You do it only once.

10. **Install Tailscale on the PC**
   Tailscale is a free private network between your own devices. You don't need to open anything on your router.
   - [Download Tailscale](https://tailscale.com/download) for Windows, install it and **sign in** (a Google, Microsoft or Apple account is enough).
   - Leave it running. It starts with Windows.

11. **Install Tailscale on the phone**
   - Install [Tailscale from Google Play](https://play.google.com/store/apps/details?id=com.tailscale.ipn) and sign in with the **same account** as on the PC.
   - Turn it on (the switch at the top).

12. **Copy the PC's Tailscale address**
   - In the Tailscale app on the phone tap your PC. Copy its address: it looks like `100.x.y.z`.

13. **Add the address in Thumbcast**
   - On the PC list, **long-press your PC → Connection addresses → Add address**, then paste it.
   - You don't pair again. At home Thumbcast uses Wi-Fi; away from home it uses Tailscale.

14. **Tips**
   - Keep Tailscale on in both devices.
   - On mobile data use 720p or 1080p at 60 FPS and a bitrate of 10–20 Mbps (Thumbcast settings).
   - Do not forward ports on your router.

## Add your own games

Every item in Sunshine's **Applications** tab shows up as a game in Thumbcast. To add one: open the Sunshine page (`https://localhost:47990`) → **Applications → Add New**, fill in the fields below, **Save**, and reopen your PC in Thumbcast.

**Steam game**

- **Application Name**: the game's name.
- **Detached Commands**: `steam://rungameid/<game id>`. The game id is the number in the game's Steam store page address.

**Battle.net game**

- **Detached Commands**: `"C:\Program Files (x86)\Battle.net\Battle.net.exe" --exec="launch <code>"`.
- Game codes: `WoW` for World of Warcraft; `WoWF` for WoW: Forever (not verified yet). Battle.net must be signed in.

**Any other game (an .exe file)**

- **Command**: the full path to the game's `.exe` file.

**Settings for one game**

- In Thumbcast **long-press the game → Thumbcast settings for the app**. You can choose the control profile, resolution and FPS just for that game.

**Make the game close from the phone**

Games started through Steam or Battle.net do not close by themselves. In the Sunshine app, open **Command Preparations → Add** and put `taskkill /IM <game file>.exe` in the **Undo** field. Find the file name in Task Manager → Details.
Then **Close game on PC** in the Thumbcast menu closes the game too.

## Something doesn't work?

- **PC not on the list**: same Wi-Fi? Is Sunshine running (icon near the clock)? Try **Add PC** with the PC's IP address.
- **The game doesn't see a gamepad**: install ViGEmBus, set the gamepad to Xbox 360 in Sunshine and restart the PC. Or switch to the mouse and keyboard profile.
- **Not working away from home**: check that Tailscale is on, on the PC and on the phone, and that the address is added.
- Details for advanced users: [host-setup.md](docs/host-setup.md), [host-apps.md](docs/host-apps.md), [tailscale.md](docs/tailscale.md) (in Polish). Bugs: [GitHub issues](https://github.com/Serwur/thumbcast/issues).

## How it works

```
PC (Windows / macOS)                         Phone (Android)
┌───────────────────────────┐               ┌───────────────────────────────────┐
│ Game                      │               │ Thumbcast (moonlight-android fork)│
│   ▲ gamepad / keyboard / mouse            │                                   │
│ Sunshine                  │  video/audio  │  nvstream + decoder (unchanged)   │
│   ViGEmBus / Virtual HID  │ ────────────► │  ThumbcastOverlay (one View)      │
│   / Win32                 │ ◄──────────── │   → InputEngine + ActionPolicy    │
└───────────────────────────┘     input     │   → InputSink (pad / keys / mouse)│
            LAN or Tailscale (direct)       └───────────────────────────────────┘
```

| Module | Type | Role |
|---|---|---|
| `:app` | Android app | The Moonlight for Android v12.2 client with Thumbcast hooks (`THUMBCAST` blocks, full diff in `patches/0001-thumbcast.diff`). |
| `:control-model` | Kotlin/JVM | Control profile (JSON schema v1), validation, migrations. The schema has no macros or sequences. |
| `:control-engine` | Kotlin/JVM | Input engine: pointer tracking, gestures, `ActionPolicy`, output mapping. Property-tested. |
| `:control-android` | Android library | `ThumbcastOverlay`, profile library, in-game menu and stats, host addresses, per-app settings. |
| `:control-editor` | Compose | Layout editor. |
| `:control-ui` | Compose | Theme, setup wizard, “How to connect a PC” guide. |
| `presets/` | JSON | Built-in profiles: Pad – PlayStation (default), Pad – Xbox, MMO – Pad+, MMO – Keyboard, Desktop – Touchpad, Big Picture. |

## Building

Requirements: JDK 17+ (the JetBrains Runtime bundled with Android Studio works), Android SDK with compileSdk 37, NDK 29.0.14206865.

```bash
git clone --recurse-submodules https://github.com/Serwur/thumbcast.git
cd thumbcast
./gradlew :app:assembleNonRootDebug
```

Tests (the policy property tests run with full iterations in CI):

```bash
./gradlew :control-model:test :control-engine:test :control-android:testDebugUnitTest
./gradlew :control-engine:test --tests '*Policy*' -Pthumbcast.policy.iterations=100000
```

Releases are built by `.github/workflows/release.yml` from a `v*` tag: a signed APK for GitHub and an AAB for Google Play.

## Documentation

Public documents come in English and Polish. Developer documentation in `docs/` and in the modules is written in Polish.

- [FAIR_PLAY.md](FAIR_PLAY.md): rules and risks
- [CONTRIBUTING.md](CONTRIBUTING.md): how to contribute
- [SECURITY.md](SECURITY.md): reporting vulnerabilities
- [docs/host-setup.md](docs/host-setup.md), [docs/host-apps.md](docs/host-apps.md), [docs/tailscale.md](docs/tailscale.md): PC setup, games in Sunshine, remote play (Polish)

## License

GPL-3.0-or-later, like upstream. See [LICENSE](LICENSE) and [NOTICE](NOTICE).

World of Warcraft and Blizzard are trademarks of Blizzard Entertainment, Inc. Xbox is a trademark of Microsoft; PlayStation is a trademark of Sony Interactive Entertainment. Moonlight belongs to its authors. Names are used only to describe compatibility; Thumbcast is not affiliated with or endorsed by any of them.
