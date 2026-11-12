# Contributing to Thumbcast

<p><b>English</b> · <a href="CONTRIBUTING.pl.md">Polski</a></p>

Thanks for wanting to help. Read [FAIR_PLAY.md](FAIR_PLAY.md) first: the one touch = one input rule limits which changes we accept.

## What we do not accept

Pull requests that add any of the following will be closed without discussing the implementation:

- macros, sequences, “combos”, rotations and other chains of inputs from one touch;
- turbo, auto-fire, auto-repeat, “hold it for me” toggles that work without a finger (except `latch` for modifiers);
- delays, timers and schedules that generate presses;
- analysis of the stream image (OCR, CV, reading pixels, reacting to game state);
- reading game memory or files, integration through add-ons;
- input broadcasting, multiboxing, several sessions at once;
- new output types in the profile schema beyond: one gamepad button, one axis, one key, one mouse button, scroll wheel, mouse movement, a local app action;
- Firebase, Crashlytics or Google Play Services dependencies (an F-Droid requirement);
- names, icons, artwork and screenshots of Blizzard games, and the name “Moonlight” in resources ([docs/naming.md](docs/naming.md)).

Changes to `ActionPolicy`, to the profile validator or to the policy tests need a maintainer review and a justification in the PR description.

## How to submit a change

1. Open an issue (templates in `.github/ISSUE_TEMPLATE/`) if the change is bigger than a fix.
2. Fork, then use a branch named `feature/<short-description>` or `fix/<short-description>`.
3. Sign off your commits under the DCO: `git commit -s` (a `Signed-off-by:` line). We do not require a CLA, because GPLv3 upstream code cannot be relicensed anyway.
4. Open a PR with the fair-play checklist filled in (PR template).

## Style

- New code: Kotlin (JVM target 17). Upstream code (`:app`, Java): change as little as possible to keep rebases easy, inside `THUMBCAST` blocks, and regenerate `patches/0001-thumbcast.diff` (`git diff v12.2 -- app/`).
- Identifiers in English. Code comments and developer documentation are in Polish. Public documents (README, FAIR_PLAY, CONTRIBUTING, SECURITY) have an English version (`*.md`, the default) and a Polish one (`*.pl.md`); update both.
- `kotlin.code.style=official`, formatting per `.editorconfig`.
- No reflection or magic in the input path. Avoid per-event allocations on the critical path (touch → send) where possible.
- Module boundaries and shared types are described in `CONTRACT.md`. Record signature changes in `CONTRACT-NOTES.md`.

## Tests

- Run `./gradlew :control-model:test :control-engine:test` before every PR.
- Every new input feature (gesture, control type, mode) needs:
  - a unit test with `FakeInputSink`;
  - an extension of the policy property tests (class name contains `Policy`, module `:control-engine`), so the new behavior is covered by random event streams.
- Property tests with full iterations: `-Pthumbcast.policy.iterations=100000`.
- New presets in `presets/` must pass `ProfileValidator` (test in `:control-model`).
- Describe in-game behavior changes with the result of the matching scenario from [docs/test-plan.md](docs/test-plan.md).

## License of contributions

By sending a PR you agree to release your contribution under GPL-3.0-or-later. Artwork: CC BY-SA 4.0 or GPL.
