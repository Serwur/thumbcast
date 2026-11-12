# Fair play rules

<p><b>English</b> · <a href="FAIR_PLAY.pl.md">Polski</a></p>

Thumbcast is a controller, not a bot. This document describes what the app does, what it does not do, and what risk you take when you use it with games whose terms forbid automation.

**Thumbcast is not approved by Blizzard Entertainment or by any other game publisher.** There is no official approval for this or any other input-mapping tool.

## The rule: one touch = one input

Each touch on the screen produces at most one logical input to the game: one gamepad button, one axis, one key, one mouse button, the scroll wheel or mouse movement. The input lasts exactly as long as your finger touches the control.

The code enforces this in three places:
1. **`ActionPolicy`** (`:control-engine` module): one pointer (finger) holds at most one input at a time. Every change of an output state must be caused by a touch event in the same handling cycle. Events without a cause (from a timer or another thread) are rejected and logged.
   - Exception 1: upstream resends the **identical** gamepad state after 25, 50 and 75 ms. This works around packet loss; it is not a new press.
   - Exception 2: camera deflection decays to zero after your finger stops.
2. **Profile schema** (`:control-model` module): every control has exactly one output. The types `sequence`, `macro`, `repeat`, `turbo`, `delay` and `toggle_auto` do not exist. The parser rejects unknown fields and types, so importing someone else's profile cannot add automation.
3. **Property tests in CI** (required check `policy-tests`): for 100,000 random touch streams, the number of presses on the output may not exceed the number of touches with an assigned output, and every press must have a cause. A violation blocks merges and releases.

## What the app does not do (and never will)

- Macros, sequences or “rotations” fired by one touch.
- Turbo, auto-fire or auto-repeat in the app.
- Delays and timers that generate presses.
- Analysis of the stream image (OCR, object detection, reacting to game state). The app does not read pixels.
- Reading game memory or files, or requiring add-ons.
- Input broadcasting and multiboxing: one session, one host.
- Analog control “through the mouse” in keyboard mode (automatic turning).

Pull requests that add any of these will be closed ([CONTRIBUTING.md](CONTRIBUTING.md)).

## Grey area: chords

A chord is one touch that presses two inputs at once, for example Shift+1 or LB+RB. It breaks the one touch = one input rule.

**Since 11 November 2026, Thumbcast does not support chords at all.** The profile validator rejects every button with a `chord` field, even with `global.allowChords = true`, so importing a profile with a chord fails. The `chord` and `allowChords` fields remain in the schema only to avoid a migration; they enable nothing.

Instead of a chord, use separate modifier buttons in `hold` mode (hold it) or `latch` mode (tap to press, tap again to release). Shift+1 is then two touches: the `SHIFT` modifier and the `1` button. `latch` is still one touch = one change of state.

## Risk: an honest view

As of November 2026 (sources are in the project plan):

- **Blizzard's EULA** (version of 21 March 2024) forbids:
  - bots, that is software “that allows the automated control of a Game” (1.C.ii.2);
  - unauthorized software that “changes and/or facilitates the gameplay” (1.C.ii.4);
  - unauthorized streaming from a third-party cloud (1.C.v). Thumbcast streams from your own PC. We believe this is not a “third-party cloud”, but that is our interpretation, not Blizzard's position.
- **Input broadcasting** has been forbidden since 3 November 2020.
- **Blizzard support's informal rule** (2021): “one key press = one action is the general rule of thumb”. On 29 October 2026, Blizzard support declined to assess specific setups.
- **History:** in 2021 there were reports of bans for gamepad-mapping tools (WoWMapper, ConsolePort). They were not confirmed, and the reported bans were reversed on appeal.

What this means:
- One-to-one remapping is tolerated in practice, but **not guaranteed**. The risk to your account is low, but **not zero**.
- The safest choice is a gamepad profile: it sends native gamepad input, which the game officially supports.
- Whether to use the app with a given game, and the risk that comes with it, is your decision. The authors are not responsible for decisions of game publishers.

We will update this document if a publisher changes its position. If you know of newer official information, open an issue with the source.
