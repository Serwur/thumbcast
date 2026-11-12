## Description

<!-- What this PR changes and why. Link the issue: Closes #... -->

## Type of change

- [ ] Bug fix
- [ ] New feature
- [ ] Documentation / CI
- [ ] Change in upstream code (`:app` / `patches/`)

## Fair-play checklist (required)

Rules: [FAIR_PLAY.md](../FAIR_PLAY.md). A PR that breaks any of them will be closed.

- [ ] Each touch produces at most one logical input (one touch = one input).
- [ ] No macros, sequences, combos, turbo or auto-repeat in the app.
- [ ] No timers, delays or threads that generate presses without a touch.
- [ ] No analysis of the stream image (reading pixels, OCR, CV).
- [ ] No reading of game memory or files, no add-on requirements.
- [ ] No new output types in the profile schema beyond the allowed ones (if any: justification below).
- [ ] Input changes are covered by the policy property tests (`*Policy*` in `:control-engine`).
- [ ] No game trademarks and no “Moonlight” in names, resources, icons or metadata.
- [ ] No Firebase / Crashlytics / Play Services.

## Tests

- [ ] `./gradlew :control-model:test :control-engine:test` locally
- [ ] Policy tests with `-Pthumbcast.policy.iterations=100000`
- [ ] Scenarios from `docs/test-plan.md` (if the change affects gameplay): <!-- e.g. T-01, T-03 + result -->

## License

- [ ] Commits have `Signed-off-by:` (DCO, `git commit -s`); the contribution is GPL-3.0-or-later.
