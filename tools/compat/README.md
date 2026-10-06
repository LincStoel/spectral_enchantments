# Compat harness

Dev-only tooling for checking Spectral Enchantments against the mods in `run/mods` (Ars Nouveau,
Iris + Sodium, Create, The Aether, ...). Adapted from crop_climates' stress harness. None
of it ships: the `spectral_enchantments_harness` mod lives in the `harness` source set and is
loaded only by the compat runs.

## Pieces

| Where | What |
|---|---|
| `src/harness/` | `spectral_enchantments_harness` mod: `/bbh do <players> <action> [arg]` instructs bot clients (mine, stop, sneak, slot, move walk/sprint/stop, hud, inventory open/close, iris on/off, shot, probe, trace); `/bbh arsbreak <player> <pos>` breaks a block the way an Ars Nouveau spell does (`BlockUtil.destroyBlockSafely` with the player as caster); `/bbh trace true\|false` logs every left-click and break event server-side as `[bbh-trace]` |
| `tools/compat/setup.py` | Prepares `run-compat/`: copies `run/mods` (minus client-only Iris/Sodium for the servers), flat world, RCON on 127.0.0.1 with a random password, `eula=true`, BSL selected for Iris (off until toggled). Jars in `run-compat/overrides/bots/` replace the same mod in the bots |
| `tools/compat/rcon.py` | Stdlib RCON client (`python tools/compat/rcon.py "<command>" ...`) |
| `tools/compat/run_compat.py` | Launches the server and two bots (Bot1 mines, Bot2 watches) and runs the scenarios: `vanilla`, `iris`, `ars`, `sneak`, `spectral` (default), plus `debug` (two back-to-back mines with client and server tracing) |

## Workflow

```
python tools/compat/setup.py              # once, and whenever run/mods changes
./gradlew runCompatGameTest               # the GameTests with every mod loaded (headless)
python tools/compat/run_compat.py         # all scenarios; or name some: run_compat.py iris ars
```

Results: `run-compat/results/session.log` (every check, PASS/FAIL), screenshots in
`run-compat/bot{1,2}/screenshots/`, client events and probes in
`run-compat/bot{1,2}/harness-{events,probe}.txt`.

## Notes

- The dev runs use `isDisableRecompilation`: recompiled Minecraft renumbers lambdas,
  which breaks other mods' mixins (Accessories, via The Aether) in dev.
- Iris 1.8.12 requires Sodium 0.6.x (with Sodium 0.8 it crashes on world join), but Sable and
  Veil require Sodium 0.8.12+. `run-compat/overrides/bots/` therefore carries
  `iris-neoforge-1.8.14-beta.1+mc1.21.1.jar` (from Modrinth, hash-checked), the Iris built for
  Sodium 0.8.
- Judge the ghosts from the screenshots: probes confirm the sessions and cracks exist, not that
  anything is visible (a custom core shader, for one, drew nothing under BSL).
- The bots mine through a mixin on `Minecraft#continueAttack`, the same path a held
  attack key takes, so client prediction, break speed and the server's checks all run
  for real.
