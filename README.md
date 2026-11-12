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

## What you need

**On your PC (Windows):**
- [Sunshine](https://github.com/SunshineStream/Sunshine/releases/latest), a free, open-source streaming host (Apollo and Vibepollo forks also work).
- [ViGEmBus](https://github.com/nefarius/ViGEmBus/releases/latest), the gamepad driver you need to play with a gamepad. Paid alternatives exist (LizardByte Virtual HID Driver), but ViGEmBus is enough. Without a gamepad driver, use the mouse and keyboard controls.
- Optional: [Tailscale](https://tailscale.com/download) to play away from home.

**On your phone:** Android 8.0 or newer, on the same Wi-Fi as the PC (or Tailscale).

The app's setup wizard walks you through installing and pairing. The same steps are on the [website](https://serwur.github.io/thumbcast/).

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
