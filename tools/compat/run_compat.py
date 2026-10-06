"""Spectral Enchantments compatibility session. See README.md.

Launches the compat server and two bot clients (Bot1 mines, Bot2 watches), then runs
the scenarios, taking screenshots and probing client state. Results:
  run-compat/results/session.log      every command, check and verdict
  run-compat/bot{1,2}/screenshots/    screenshots
  run-compat/bot{1,2}/harness-*.txt   client events and probes

    python tools/compat/run_compat.py              # everything
    python tools/compat/run_compat.py vanilla ars  # chosen scenarios (vanilla, iris, ars, sneak)
"""
import pathlib
import re
import subprocess
import sys
import time

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from rcon import wait_for_server  # noqa: E402

ROOT = pathlib.Path(__file__).resolve().parents[2]
COMPAT = ROOT / "run-compat"
RESULTS = COMPAT / "results"
BOT_DIRS = {"Bot1": COMPAT / "bot1", "Bot2": COMPAT / "bot2"}

# The wall: plane z = 10, mined block at (0, 1, 10); Bot1 faces it from the north side.
WALL_Z = 10
ORIGIN = (0, 1, WALL_Z)
FAILURES = []


def log(msg):
    line = time.strftime("%H:%M:%S ") + msg
    print(line, flush=True)
    RESULTS.mkdir(parents=True, exist_ok=True)
    with open(RESULTS / "session.log", "a", encoding="utf-8") as f:
        f.write(line + "\n")


def check(ok, what):
    log(("PASS " if ok else "FAIL ") + what)
    if not ok:
        FAILURES.append(what)


class Session:
    def __init__(self):
        self.r = wait_for_server()

    def c(self, command, quiet=False):
        out = self.r.cmd(command)
        if not quiet:
            log(f"> {command}\n  {out.strip()[:400]}")
        return out

    def do(self, bot, action, arg=""):
        self.c(f"bbh do {bot} {action} {arg}".strip(), quiet=True)

    def is_block(self, pos, block):
        x, y, z = pos
        return "passed" in self.c(f"execute if block {x} {y} {z} {block}", quiet=True).lower()

    def damage(self, bot):
        out = self.c(f'data get entity {bot} SelectedItem.components."minecraft:damage"', quiet=True)
        m = re.search(r"data: (\d+)", out)
        return int(m.group(1)) if m else 0


def gradle(task, logname):
    RESULTS.mkdir(parents=True, exist_ok=True)
    return subprocess.Popen(["cmd", "/c", str(ROOT / "gradlew.bat"), task, "--console=plain"], cwd=str(ROOT),
                            stdout=open(RESULTS / logname, "w"), stderr=subprocess.STDOUT)


def wait_players(s, names, timeout=900):
    deadline = time.time() + timeout
    while time.time() < deadline:
        listing = s.c("list", quiet=True)
        if all(n in listing for n in names):
            return True
        crashes = [p for d in BOT_DIRS.values() for p in (d / "crash-reports").glob("*.txt")]
        if crashes:
            log("! bot crashed: " + ", ".join(str(p) for p in crashes))
            return False
        time.sleep(5)
    return False


def events(bot):
    path = BOT_DIRS[bot] / "harness-events.txt"
    return path.read_text(encoding="utf-8") if path.exists() else ""


def wait_event(bot, pattern, since, timeout=60):
    """Waits for an event line matching pattern that appeared after `since` characters."""
    deadline = time.time() + timeout
    while time.time() < deadline:
        m = re.search(pattern, events(bot)[since:])
        if m:
            return m
        time.sleep(0.5)
    return None


def shot(s, bot, name):
    s.do(bot, "shot", name)
    time.sleep(1.5)


def probe(s, label):
    for bot in BOT_DIRS:
        s.do(bot, "probe", label)
    time.sleep(1)


def last_probe(bot):
    path = BOT_DIRS[bot] / "harness-probe.txt"
    return path.read_text(encoding="utf-8").strip().splitlines()[-1] if path.exists() else ""


def wall_area(level):
    x0, y0, z = ORIGIN
    return [(x0 + dx, y0 + dy, z) for dx in range(-level, level + 1) for dy in range(-1, 2 * level)]


def build_wall(s, level, specials=()):
    """Clears the arena and builds a stone wall sized for `level`, plus (offset, block) specials."""
    s.c("fill -8 0 2 8 10 14 air", quiet=True)
    for pos in wall_area(level):
        s.c(f"setblock {pos[0]} {pos[1]} {pos[2]} stone", quiet=True)
    for (dx, dy), block in specials:
        s.c(f"setblock {ORIGIN[0] + dx} {ORIGIN[1] + dy} {ORIGIN[2]} {block}", quiet=True)


def arm(s, bot, level, tool="diamond_pickaxe"):
    s.c(f'item replace entity {bot} weapon.mainhand with minecraft:{tool}'
        f'[minecraft:enchantments={{levels:{{"spectral_enchantments:broad_breaker":{level}}}}}]', quiet=True)


def stage(s):
    """Bot1 stands 2.5 blocks north of the wall facing it; Bot2 watches from the side, further back."""
    s.c("tp Bot1 0.5 0 7.5 0 8", quiet=True)  # pitch 8: eye ray meets the wall at y ~1.3
    s.c("tp Bot2 7.5 0 3.5 47 -5", quiet=True)
    time.sleep(2)


def mine_and_watch(s, prefix, level, specials):
    """Builds the wall, aims, mines it as Bot1 while both bots take screenshots; returns ticks taken."""
    build_wall(s, level, specials)
    arm(s, "Bot1", level)
    s.c("gamemode survival Bot1", quiet=True)
    stage(s)
    time.sleep(2)
    shot(s, "Bot1", f"{prefix}-aim")
    since = len(events("Bot1"))
    s.do("Bot1", "mine", f"{ORIGIN[0]} {ORIGIN[1]} {ORIGIN[2]} north")
    time.sleep(3)
    shot(s, "Bot1", f"{prefix}-mid")
    shot(s, "Bot2", f"{prefix}-remote")
    probe(s, f"{prefix}-mid")
    done = wait_event("Bot1", r"mine-done .+? in (\d+) ticks", since, timeout=60)
    time.sleep(0.4)
    shot(s, "Bot2", f"{prefix}-remote-after")
    return int(done.group(1)) if done else None


def scenario_mining(s, prefix):
    level = 3
    specials = [((2, 2), "obsidian"), ((-2, 3), "dirt"), ((-1, 0), "iron_ore"), ((1, 4), "coal_ore")]
    ticks = mine_and_watch(s, prefix, level, specials)
    check(ticks is not None, f"[{prefix}] Bot1 finished mining the level 3 area")
    # Diamond pick: obsidian takes 50 / 8 * 30 = 187.5 -> 188 ticks; stone alone would be ~6.
    check(ticks is not None and ticks >= 180, f"[{prefix}] area took the obsidian's time ({ticks} ticks, expected ~188)")
    p1, p2 = last_probe("Bot1"), last_probe("Bot2")
    log(f"  probe Bot1: {p1}\n  probe Bot2: {p2}")
    check("spectralSessions=1" in p1 and re.search(r"spectralCracks=47\b", p1) is not None,
          f"[{prefix}] Bot1 has its local spectral session and cracks on all 47 extra blocks")
    check("spectralSessions=1" in p2 and re.search(r"spectralCracks=47\b", p2) is not None,
          f"[{prefix}] Bot2 sees Bot1's spectral session and cracks (remote)")
    time.sleep(1)
    area = wall_area(level)
    dirt = (ORIGIN[0] - 2, ORIGIN[1] + 3, ORIGIN[2])
    broken = [p for p in area if p != dirt and s.is_block(p, "air")]
    check(len(broken) == len(area) - 1, f"[{prefix}] all {len(area) - 1} pickaxe blocks broke ({len(broken)})")
    check(s.is_block(dirt, "dirt"), f"[{prefix}] dirt (wrong tool) was left standing")
    dmg = s.damage("Bot1")
    check(dmg == 48 // 2, f"[{prefix}] durability used {dmg}, expected {48 // 2}")


def scenario_vanilla(s):
    for bot in BOT_DIRS:
        s.do(bot, "iris", "off")
    time.sleep(5)
    scenario_mining(s, "vanilla")


def scenario_iris(s):
    for bot in BOT_DIRS:
        s.do(bot, "iris", "on")
    time.sleep(25)  # BSL compiles its programs on enable
    probe(s, "iris-on")
    check("irisPackInUse=true" in last_probe("Bot1"), "[iris] BSL is active on Bot1")
    scenario_mining(s, "iris")
    # Ghosts at night, where a translucent white silhouette matters most.
    s.c("time set midnight", quiet=True)
    build_wall(s, 2, [((1, 1), "obsidian")])
    arm(s, "Bot1", 2)
    stage(s)
    s.do("Bot1", "mine", f"{ORIGIN[0]} {ORIGIN[1]} {ORIGIN[2]} north")
    time.sleep(3)
    shot(s, "Bot1", "iris-night-mid")
    shot(s, "Bot2", "iris-night-remote")
    s.do("Bot1", "stop")
    s.c("time set noon", quiet=True)
    for bot in BOT_DIRS:
        s.do(bot, "iris", "off")
    time.sleep(5)


def scenario_ars(s):
    """Ars Nouveau spells break blocks as the casting player; they must not trigger area mining."""
    s.c("gamemode survival Bot1", quiet=True)
    for case in ("after-abort", "fresh"):
        build_wall(s, 1, [((1, 1), "obsidian")])
        arm(s, "Bot1", 1)
        stage(s)
        if case == "after-abort":
            # Start mining the origin, give up, then a spell breaks that same block.
            s.do("Bot1", "mine", f"{ORIGIN[0]} {ORIGIN[1]} {ORIGIN[2]} north")
            time.sleep(1)
            s.do("Bot1", "stop")
            time.sleep(0.5)
        out = s.c(f"bbh arsbreak Bot1 {ORIGIN[0]} {ORIGIN[1]} {ORIGIN[2]}")
        time.sleep(0.5)
        check("arsbreak true" in out and s.is_block(ORIGIN, "air"), f"[ars:{case}] the spell broke its target")
        neighbours = [p for p in wall_area(1) if p != ORIGIN]
        standing = sum(1 for p in neighbours if not s.is_block(p, "air"))
        check(standing == len(neighbours), f"[ars:{case}] spell break did not trigger area mining ({standing}/{len(neighbours)} neighbours standing)")
    # And mining still works normally with Ars loaded right after.
    build_wall(s, 1)
    arm(s, "Bot1", 1)
    stage(s)
    since = len(events("Bot1"))
    s.do("Bot1", "mine", f"{ORIGIN[0]} {ORIGIN[1]} {ORIGIN[2]} north")
    check(wait_event("Bot1", r"mine-done", since, timeout=30) is not None, "[ars] normal mining still completes")
    time.sleep(1)
    check(all(s.is_block(p, "air") for p in wall_area(1)), "[ars] normal mining still breaks the 3x3")


def scenario_sneak(s):
    build_wall(s, 1)
    arm(s, "Bot1", 1)
    stage(s)
    s.do("Bot1", "sneak", "on")
    time.sleep(1)
    shot(s, "Bot1", "sneak-aim")
    since = len(events("Bot1"))
    s.do("Bot1", "mine", f"{ORIGIN[0]} {ORIGIN[1]} {ORIGIN[2]} north")
    wait_event("Bot1", r"mine-done", since, timeout=30)
    time.sleep(1)
    s.do("Bot1", "sneak", "off")
    standing = sum(1 for p in wall_area(1) if p != ORIGIN and not s.is_block(p, "air"))
    check(standing == 8, f"[sneak] sneaking mined only the target ({standing}/8 neighbours standing)")


def scenario_debug(s):
    """Two back-to-back level 1 mines with client and server tracing on."""
    s.c("bbh trace true", quiet=True)
    s.do("Bot1", "trace", "on")
    s.c("gamemode survival Bot1", quiet=True)
    for attempt in (1, 2):
        build_wall(s, 1, [((1, 1), "obsidian")])
        arm(s, "Bot1", 1)
        stage(s)
        since = len(events("Bot1"))
        s.do("Bot1", "mine", f"{ORIGIN[0]} {ORIGIN[1]} {ORIGIN[2]} north")
        done = wait_event("Bot1", r"mine-done .+? in (\d+) ticks", since, timeout=30)
        time.sleep(1)
        standing = sum(1 for p in wall_area(1) if p != ORIGIN and not s.is_block(p, "air"))
        check(done is not None and standing == 0, f"[debug:{attempt}] area mined ({standing} standing, done={done and done.group(1)})")
    s.do("Bot1", "trace", "off")
    s.c("bbh trace false", quiet=True)


SCENARIOS = {"debug": scenario_debug, "vanilla": scenario_vanilla, "iris": scenario_iris, "ars": scenario_ars, "sneak": scenario_sneak}


def main():
    chosen = sys.argv[1:] or [n for n in SCENARIOS if n != "debug"]
    procs = [gradle("runCompatServer", "server-gradle.log")]
    s = Session()
    log("server up; launching bots")
    procs.append(gradle("runCompatClient", "bot1-gradle.log"))
    time.sleep(20)
    procs.append(gradle("runCompatClient2", "bot2-gradle.log"))
    try:
        if not wait_players(s, BOT_DIRS):
            log("! bots never joined; see run-compat/results/*-gradle.log")
            return 1
        time.sleep(15)
        for cmd in ("gamerule doDaylightCycle false", "gamerule doWeatherCycle false", "time set noon",
                    "weather clear", "gamerule doTileDrops false", "gamemode creative Bot2",
                    "effect give Bot2 minecraft:night_vision infinite 0 true"):
            s.c(cmd, quiet=True)
        for bot in BOT_DIRS:
            s.do(bot, "hud", "off")
        for name in chosen:
            log(f"=== scenario {name}")
            SCENARIOS[name](s)
    finally:
        s.c("stop", quiet=True)
        time.sleep(10)
        for p in procs:
            # The gradle wrapper runs the game as a child; take the whole tree down.
            subprocess.run(["taskkill", "/PID", str(p.pid), "/T", "/F"], capture_output=True)
    log(f"=== {len(FAILURES)} failure(s)" + "".join(f"\n  - {f}" for f in FAILURES))
    return 1 if FAILURES else 0


if __name__ == "__main__":
    sys.exit(main())
