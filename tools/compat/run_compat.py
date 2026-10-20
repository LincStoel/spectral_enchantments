"""Spectral Enchantments compatibility session. See README.md.

Launches the compat server and two bot clients (Bot1 mines, Bot2 watches), then runs
the scenarios, taking screenshots and probing client state. Results:
  run-compat/results/session.log      every command, check and verdict
  run-compat/bot{1,2}/screenshots/    screenshots
  run-compat/bot{1,2}/harness-*.txt   client events and probes

    python tools/compat/run_compat.py              # everything
    python tools/compat/run_compat.py vanilla ars  # chosen scenarios (vanilla, iris, ars, sneak, spectral, doppelganger, haunting, study)
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


def give_spectral(s, bot, slot, item, rune=None):
    """Puts an Animated item in a slot (e.g. hotbar.0, weapon.offhand), optionally etched with a Quark rune."""
    item_id = item if ":" in item else f"minecraft:{item}"
    etched = f',quark:rune_color="{rune}"' if rune else ""
    s.c(f'item replace entity {bot} {slot} with {item_id}'
        f'[minecraft:enchantments={{levels:{{"spectral_enchantments:animated":1}}}}{etched}]', quiet=True)


def mob_health(s, tag):
    out = s.c(f"data get entity @e[tag={tag},limit=1] Health", quiet=True)
    m = re.search(r"([\d.]+)f", out)
    return float(m.group(1)) if m else None


def ghosts(probe_line):
    """The companions=[...] part of a probe line."""
    m = re.search(r"companions=\[(.*?)\]", probe_line)
    return m.group(1) if m else ""


def spectral_probe(s, label):
    probe(s, label)
    p1, p2 = last_probe("Bot1"), last_probe("Bot2")
    log(f"  probe Bot1: {p1}\n  probe Bot2: {p2}")
    return p1, p2


def spectral_stage(s):
    """Bot1 faces south (+z) in an open arena; Bot2 watches from behind its left shoulder, the target in view."""
    s.c("fill -8 0 2 8 10 18 air", quiet=True)
    s.c("kill @e[tag=sw_any]", quiet=True)
    s.c("clear Bot1", quiet=True)
    s.c("gamemode survival Bot1", quiet=True)
    s.c("tp Bot1 0.5 0 7.5 0 0", quiet=True)
    s.c("tp Bot2 6.0 0.5 5.0 60 12", quiet=True)
    s.do("Bot1", "slot", "4")
    time.sleep(2)


def summon_target(s, mob, x, z, tag="sw_target"):
    """A still mob (helmeted, so it won't burn) tagged for the scenario."""
    s.c(f"summon minecraft:{mob} {x} 0 {z} {{NoAI:1b,PersistenceRequired:1b,Tags:[\"{tag}\",\"sw_any\"],"
        f"ArmorItems:[{{}},{{}},{{}},{{id:\"minecraft:leather_helmet\",count:1}}]}}", quiet=True)


def owner_hits(s, tag):
    """Bot1 'hits' the tagged mob (a token player attack), which is what an Animated companion locks on to."""
    s.c(f"damage @e[tag={tag},limit=1] 0.01 minecraft:player_attack by Bot1", quiet=True)


def watch_attack(s, prefix, item, mob="zombie", rune=None):
    """Gives Bot1 `item` in hotbar slot 0, summons a still mob in range, has Bot1 hit it, screenshots the swings."""
    spectral_stage(s)
    give_spectral(s, "Bot1", "hotbar.0", item, rune)
    time.sleep(2)
    summon_target(s, mob, 0.5, 11.5)
    time.sleep(0.5)
    owner_hits(s, "sw_target")
    before = mob_health(s, "sw_target")
    styles = set()
    s.healths = [before]
    for i in range(6):
        time.sleep(0.25)
        shot(s, "Bot2", f"{prefix}-swing{i}")
        s.do("Bot1", "probe", f"{prefix}-swing{i}")
        time.sleep(0.3)
        styles.update(re.findall(r"style=([A-Z_]+)", ghosts(last_probe("Bot1"))))
        s.healths.append(mob_health(s, "sw_target"))
    after = mob_health(s, "sw_target")
    spectral_probe(s, f"{prefix}-attack")
    log(f"  styles seen: {sorted(styles)}")
    return before, after, styles


def scenario_spectral(s):
    """Animated in the hotbar: rest pose, lock-on, attacks with each weapon style, sneak, off hand, anvil items, Quark runes."""
    s.c("difficulty easy", quiet=True)  # peaceful deletes the target mobs on spawn
    for bot in BOT_DIRS:
        s.do(bot, "iris", "off")
    time.sleep(5)

    spectral_stage(s)
    give_spectral(s, "Bot1", "hotbar.0", "diamond_sword")
    time.sleep(2)
    shot(s, "Bot2", "spectral-rest")
    shot(s, "Bot1", "spectral-rest-firstperson")
    p1, p2 = spectral_probe(s, "spectral-rest")
    check("Bot1:minecraft:diamond_sword slot=0 left" in p1, "[spectral] Bot1 sees its own sword resting on the left")
    check("Bot1:minecraft:diamond_sword slot=0 left" in p2, "[spectral] Bot2 sees Bot1's sword (remote)")

    # The hotbar icon of the manifested item gets a ghostly overlay (and in the inventory screen).
    s.do("Bot1", "hud", "on")
    time.sleep(0.5)
    shot(s, "Bot1", "spectral-hotbar-overlay")
    s.do("Bot1", "inventory", "open")
    time.sleep(0.8)
    shot(s, "Bot1", "spectral-inventory-overlay")
    s.do("Bot1", "inventory", "close")
    s.do("Bot1", "hud", "off")

    # It doesn't attack on its own: a hostile mob in range is ignored until Bot1 hits it.
    summon_target(s, "zombie", 0.5, 10.5)
    time.sleep(3)
    health = mob_health(s, "sw_target")
    check(health is not None and health >= 20.0, f"[spectral] ignores a zombie Bot1 hasn't hit ({health})")
    p1, _ = spectral_probe(s, "spectral-idle")
    check("target=false" in ghosts(p1), "[spectral] no target before Bot1 hits anything")

    # Locked on: hitting a second mob doesn't pull it away from the first.
    summon_target(s, "spider", 3.5, 9.5, tag="sw_second")
    time.sleep(0.5)
    owner_hits(s, "sw_target")
    time.sleep(0.2)
    owner_hits(s, "sw_second")
    second_before = mob_health(s, "sw_second")
    time.sleep(0.8)
    shot(s, "Bot2", "spectral-locked")
    time.sleep(3)
    first, second = mob_health(s, "sw_target"), mob_health(s, "sw_second")
    check(first is None or first < 19.0, f"[spectral] the locked-on zombie is attacked ({first})")
    check(second is not None and second_before is not None and abs(second - second_before) < 1e-3,
          f"[spectral] the second mob Bot1 hit is left alone while locked on ({second_before} -> {second})")
    # Sneaking clears the lock; afterwards it stays idle beside Bot1.
    s.do("Bot1", "sneak", "on")
    time.sleep(1.5)
    s.do("Bot1", "sneak", "off")
    time.sleep(1.5)
    p1, _ = spectral_probe(s, "spectral-unlocked")
    check("diamond_sword" in ghosts(p1) and "target=false" in ghosts(p1), "[spectral] sneaking clears the lock")
    s.c("kill @e[tag=sw_any]", quiet=True)

    # Held in the main hand: no ghost.
    s.do("Bot1", "slot", "0")
    time.sleep(1.5)
    p1, _ = spectral_probe(s, "spectral-mainhand")
    check("diamond_sword" not in ghosts(p1), "[spectral] nothing fights beside Bot1 while the sword is in the main hand")
    s.do("Bot1", "slot", "4")
    time.sleep(1.5)

    # Leftmost wins; the off hand counts too, and rests on the left like the rest.
    give_spectral(s, "Bot1", "hotbar.2", "iron_axe")
    time.sleep(1.5)
    p1, _ = spectral_probe(s, "spectral-two")
    check("diamond_sword" in ghosts(p1) and "iron_axe" not in ghosts(p1), "[spectral] only the leftmost enchanted item manifests")
    give_spectral(s, "Bot1", "weapon.offhand", "golden_sword")
    time.sleep(2)
    shot(s, "Bot2", "spectral-offhand")
    p1, _ = spectral_probe(s, "spectral-offhand")
    check("Bot1:minecraft:golden_sword slot=40 left" in p1, "[spectral] off-hand weapon manifests instead, on the left")
    # ...but not while another weapon is in the main hand (no dual-wielding a duplicate).
    s.c("item replace entity Bot1 hotbar.4 with minecraft:stone_sword", quiet=True)
    time.sleep(2)
    p1, _ = spectral_probe(s, "spectral-offhand-blocked")
    check("golden_sword" not in ghosts(p1) and "diamond_sword" in ghosts(p1),
          "[spectral] a weapon in the main hand keeps the off-hand one from manifesting")
    s.c("item replace entity Bot1 hotbar.4 with minecraft:air", quiet=True)
    time.sleep(1.5)

    # Sneaking dismisses it.
    s.do("Bot1", "sneak", "on")
    time.sleep(2)
    shot(s, "Bot2", "spectral-sneak")
    p1, _ = spectral_probe(s, "spectral-sneak")
    check("golden_sword" not in ghosts(p1), "[spectral] sneaking sends the weapon home")
    s.do("Bot1", "sneak", "off")
    time.sleep(1)

    # Walking and sprinting tilt the tip forward.
    spectral_stage(s)
    give_spectral(s, "Bot1", "hotbar.0", "diamond_sword")
    time.sleep(1.5)
    s.do("Bot1", "move", "walk")
    time.sleep(1.2)
    p1, _ = spectral_probe(s, "spectral-walk")
    shot(s, "Bot2", "spectral-walk")
    s.do("Bot1", "move", "sprint")
    time.sleep(1.2)
    p2, _ = spectral_probe(s, "spectral-sprint")
    shot(s, "Bot2", "spectral-sprint")
    s.do("Bot1", "move", "stop")
    walk = re.search(r"tilt=([\d.]+)", p1)
    sprint = re.search(r"tilt=([\d.]+)", p2)
    check(walk is not None and 10 < float(walk.group(1)) < 30, f"[spectral] walking tilts ~20 deg ({walk and walk.group(1)})")
    check(sprint is not None and float(sprint.group(1)) > 40, f"[spectral] sprinting tilts ~55 deg ({sprint and sprint.group(1)})")

    # Expected swings follow Better Combat's combos (run/mods has it): sword slash/slash/stab, axe slashes,
    # mace slash/slash/slam, trident and spear stab, Simply Swords' halberd slash/stab/stab/chop.
    for prefix, item, style in (("sword", "diamond_sword", "STAB"), ("axe", "diamond_axe", "SLASH_H"),
                                ("trident", "trident", "STAB"), ("mace", "mace", "SMASH"),
                                ("halberd", "simplyswords:diamond_halberd", "STAB"),
                                ("rapier", "simplyswords:diamond_rapier", None)):
        before, after, styles = watch_attack(s, f"spectral-{prefix}", item)
        # None afterwards: the zombie was killed.
        check(before is not None and (after is None or after < before),
              f"[spectral:{prefix}] the weapon hurt the zombie ({before} -> {'dead' if after is None else after})")
        if style:
            check(style in styles, f"[spectral:{prefix}] swings include {style} ({sorted(styles)})")

    # Anvil-only items: weak bonks; a block shows as a turning block.
    before, after, _ = watch_attack(s, "spectral-dirt", "dirt", mob="spider")
    # One bonk every 4 s: between samples (~2 s apart) health drops by at most half a heart.
    drops = [a - b for a, b in zip(s.healths, s.healths[1:]) if a is not None and b is not None and a - b > 1e-3]
    check(bool(drops) and max(drops) <= 1.0001,
          f"[spectral:dirt] a dirt block bonks for half a heart at a time (health {s.healths})")
    shot(s, "Bot2", "spectral-dirt-rest")

    # Quark's Color Runes: the real item flies, so it glints in the rune's colour on its own (see the screenshots);
    # the slot overlay takes the colour too.
    for rune in ("red", "cyan"):
        spectral_stage(s)
        give_spectral(s, "Bot1", "hotbar.0", "diamond_sword", rune=rune)
        time.sleep(2)
        shot(s, "Bot2", f"spectral-rune-{rune}")
        p1, p2 = spectral_probe(s, f"spectral-rune-{rune}")
        check("diamond_sword" in ghosts(p1) and "diamond_sword" in ghosts(p2),
              f"[spectral:quark] a {rune}-etched weapon fights beside its owner on both clients")
    s.do("Bot1", "hud", "on")
    time.sleep(0.5)
    shot(s, "Bot1", "spectral-rune-hotbar-overlay")
    s.do("Bot1", "hud", "off")
    watch_attack(s, "spectral-rune-attack", "diamond_sword", rune="red")

    # Under shaders, at night.
    for bot in BOT_DIRS:
        s.do(bot, "iris", "on")
    time.sleep(25)
    s.c("time set midnight", quiet=True)
    watch_attack(s, "spectral-iris-night", "diamond_sword")
    watch_attack(s, "spectral-iris-night-rune", "diamond_sword", rune="cyan")
    s.c("time set noon", quiet=True)
    for bot in BOT_DIRS:
        s.do(bot, "iris", "off")
    s.c("kill @e[tag=sw_any]", quiet=True)
    s.c("clear Bot1", quiet=True)
    s.c("difficulty peaceful", quiet=True)
    time.sleep(5)


def ghost_count(s):
    out = s.c("execute if entity @e[type=spectral_enchantments:doppelganger]", quiet=True)
    m = re.search(r"count: (\d+)", out, re.I)
    return int(m.group(1)) if m else 0


def zombie_at_double(s):
    return "passed" in s.c("execute as @e[tag=dg_zombie] at @s if entity "
                           "@e[type=spectral_enchantments:doppelganger,distance=..3]", quiet=True).lower()


def scenario_doppelganger(s):
    """Doppelganger: sneaking leaves a ghostly double that draws a zombie off its owner and plays Quark emotes."""
    s.c("fill -12 0 -4 12 10 24 air", quiet=True)
    s.c("kill @e[tag=dg_any]", quiet=True)
    s.c("clear Bot1", quiet=True)
    s.c("gamemode survival Bot1", quiet=True)
    s.c("difficulty easy", quiet=True)
    s.c("effect give Bot1 minecraft:resistance infinite 4 true", quiet=True)
    s.c('item replace entity Bot1 armor.chest with minecraft:iron_chestplate'
        '[minecraft:enchantments={levels:{"spectral_enchantments:doppelganger":1}},quark:rune_color="cyan"]', quiet=True)
    s.c("tp Bot1 0.5 0 7.5 0 0", quiet=True)
    s.c("tp Bot2 -2.5 1 12.5 -149 12", quiet=True)  # in front of the double, looking back at it
    time.sleep(2)
    s.do("Bot1", "sneak", "on")
    time.sleep(1.5)
    check(ghost_count(s) == 1, "[doppelganger] sneaking leaves one double behind")
    shot(s, "Bot2", "doppelganger-appear")
    # Bot1 crawls away, still sneaking; a zombie that wants Bot1 should go for the double instead.
    s.c("tp Bot1 0.5 0 -2.5 180 0", quiet=True)
    s.c('summon minecraft:zombie 7.5 0 14.5 {PersistenceRequired:1b,Tags:["dg_zombie","dg_any"],'
        'ArmorItems:[{},{},{},{id:"minecraft:leather_helmet",count:1}]}', quiet=True)
    s.c("damage @e[tag=dg_zombie,limit=1] 0.01 minecraft:player_attack by Bot1", quiet=True)
    reached = False
    for i in range(15):
        time.sleep(2)
        if i % 2 == 0:
            shot(s, "Bot2", f"doppelganger-idle{i}")
        reached = reached or zombie_at_double(s)
    check(reached, "[doppelganger] the zombie goes for the double, not its owner")
    check(ghost_count(s) == 1, "[doppelganger] the double survives the zombie")

    for bot in BOT_DIRS:
        s.do(bot, "iris", "on")
    time.sleep(25)
    shot(s, "Bot2", "doppelganger-iris")
    s.c("time set midnight", quiet=True)
    time.sleep(2)
    shot(s, "Bot2", "doppelganger-iris-night")
    s.c("time set noon", quiet=True)
    for bot in BOT_DIRS:
        s.do(bot, "iris", "off")

    s.c("kill @e[tag=dg_any]", quiet=True)
    time.sleep(1)
    s.do("Bot1", "sneak", "off")
    time.sleep(0.15)
    shot(s, "Bot2", "doppelganger-puff")
    check(ghost_count(s) == 0, "[doppelganger] standing up takes the double away")
    shot(s, "Bot2", "doppelganger-gone")
    log("  Quark emotes played: " + str(re.findall(r"Doppelganger double plays (\S+)",
        (RESULTS / "bot2-gradle.log").read_text(encoding="utf-8", errors="replace"))))
    s.c("kill @e[tag=dg_any]", quiet=True)
    s.c("clear Bot1", quiet=True)
    s.c("effect clear Bot1", quiet=True)
    s.c("difficulty peaceful", quiet=True)
    time.sleep(3)

HAUNTED = '{"minecraft:enchantments":{levels:{"spectral_enchantments:haunting_curse":1}}}'


def haunting_ghosts(s):
    out = s.c("execute if entity @e[type=spectral_enchantments:haunting_ghost]", quiet=True)
    m = re.search(r"count: (\d+)", out, re.I)
    return int(m.group(1)) if m else 0


def has_strength(s, tag):
    return "strength" in s.c(f"data get entity @e[tag={tag},limit=1] active_effects", quiet=True).lower()


def scenario_haunting(s):
    """Curse of Haunting: a zombie in a haunted helmet has a ghost beside it, strengthening its friends; a haunted
    player's ghost comes out when the armor goes on, and again at once when a zombie strikes them."""
    s.c("fill -12 0 -4 12 10 24 air", quiet=True)
    s.c("kill @e[tag=hg_any]", quiet=True)
    s.c("clear Bot1", quiet=True)
    s.c("gamemode survival Bot1", quiet=True)
    s.c("difficulty easy", quiet=True)
    # Resistance IV, not V: the zombie's blow has to land (for 1 damage in 5) to call the ghost.
    s.c("effect give Bot1 minecraft:resistance infinite 3 true", quiet=True)
    s.c("tp Bot1 0.5 0 7.5 0 0", quiet=True)
    s.c("tp Bot2 4.5 1 10.5 40 12", quiet=True)
    time.sleep(2)
    s.c('summon minecraft:zombie 0.5 0 14.5 {NoAI:1b,PersistenceRequired:1b,Rotation:[180f,0f],Tags:["hg_haunted","hg_any"],'
        f'ArmorItems:[{{}},{{}},{{}},{{id:"minecraft:iron_helmet",count:1,components:{HAUNTED}}}]}}', quiet=True)
    s.c('summon minecraft:zombie 3.5 0 14.5 {NoAI:1b,PersistenceRequired:1b,Rotation:[180f,0f],Tags:["hg_friend","hg_any"],'
        'ArmorItems:[{},{},{},{id:"minecraft:leather_helmet",count:1}]}', quiet=True)
    time.sleep(3)
    check(haunting_ghosts(s) == 1, "[haunting] the haunted zombie has its ghost out")
    check(has_strength(s, "hg_haunted") and has_strength(s, "hg_friend"), "[haunting] the ghost strengthens the zombies")
    shot(s, "Bot2", "haunting-zombie")

    s.c('item replace entity Bot1 armor.chest with minecraft:iron_chestplate'
        '[minecraft:enchantments={levels:{"spectral_enchantments:haunting_curse":1}},quark:rune_color="cyan"]', quiet=True)
    time.sleep(2)
    check(haunting_ghosts(s) == 2, "[haunting] putting the armor on brings out the player's ghost at once")
    # It drifts over to the monster nearest the player (the haunted zombie) to lend it Strength.
    time.sleep(4)
    shot(s, "Bot2", "haunting-both")
    shot(s, "Bot1", "haunting-bot1-view")
    # The player's ghost raises its arms like an evoker every 8-20 s, for 2 s: a frame every ~1.5 s catches it.
    for i in range(16):
        shot(s, "Bot2", f"haunting-cast{i:02d}")

    for bot in BOT_DIRS:
        s.do(bot, "iris", "on")
    time.sleep(25)
    shot(s, "Bot2", "haunting-iris")
    s.c("time set midnight", quiet=True)
    time.sleep(2)
    shot(s, "Bot2", "haunting-iris-night")
    s.c("time set noon", quiet=True)
    for bot in BOT_DIRS:
        s.do(bot, "iris", "off")
    time.sleep(5)

    s.c("execute as @e[tag=hg_haunted] at @s run damage @e[type=spectral_enchantments:haunting_ghost,sort=nearest,limit=1] "
        "1 minecraft:player_attack by Bot1", quiet=True)
    time.sleep(0.2)
    shot(s, "Bot2", "haunting-zombie-ghost-struck")
    check(haunting_ghosts(s) == 2, "[haunting] a struck zombie's ghost fades out but stays")

    s.c("kill @e[tag=hg_any]", quiet=True)
    time.sleep(2)
    check(haunting_ghosts(s) == 1, "[haunting] the zombie's ghost goes with it")

    # Send the player's ghost away (it's the only one left), so a zombie's blow has to call it back.
    s.c("kill @e[type=spectral_enchantments:haunting_ghost]", quiet=True)
    time.sleep(1)
    n = haunting_ghosts(s)
    check(n == 0, f"[haunting] the player's ghost went ({n} ghosts left)")
    s.c('summon minecraft:zombie -3.5 0 12.5 {NoAI:1b,PersistenceRequired:1b,Rotation:[180f,0f],Tags:["hg_attacker","hg_any"],'
        'ArmorItems:[{},{},{},{id:"minecraft:leather_helmet",count:1}]}', quiet=True)
    s.c("damage Bot1 5 minecraft:mob_attack by @e[tag=hg_attacker,limit=1]", quiet=True)
    time.sleep(2)
    check(haunting_ghosts(s) == 1, "[haunting] a zombie's blow brings out the player's ghost")
    shot(s, "Bot2", "haunting-provoked")

    s.c("kill @e[tag=hg_any]", quiet=True)
    s.c("clear Bot1", quiet=True)
    s.c("effect clear Bot1", quiet=True)
    s.c("difficulty peaceful", quiet=True)
    time.sleep(3)
    check(haunting_ghosts(s) == 0, "[haunting] taking the armor off ends the haunting")



LECTERN = (0, 0, 9)
STUDY_SWORD = ('minecraft:diamond_sword[minecraft:enchantments={levels:{"minecraft:sharpness":3,'
               '"minecraft:unbreaking":2}}]')
COPY_SOURCE_ITEM = ('minecraft:enchanted_book[minecraft:stored_enchantments={levels:{"minecraft:sharpness":5,'
                    '"minecraft:unbreaking":3}}]')


def lectern(s, book):
    x, y, z = LECTERN
    s.c(f"setblock {x} {y} {z} air", quiet=True)
    s.c(f"setblock {x} {y} {z} minecraft:lectern[facing=north,has_book=true]{{Book:{book}}}", quiet=True)


def written_book(s, floating):
    """Whether a written enchanted book is in the air above the lectern (floating) or has come down near it."""
    x, y, z = LECTERN
    dy = f"y={y + 1.5},dy=6" if floating else f"y={y - 1},dy=2.4"
    out = s.c(f'execute if entity @e[type=minecraft:item,x={x - 2},dx=5,z={z - 2},dz=5,{dy},'
              f'nbt={{Item:{{id:"minecraft:enchanted_book"}}}}]', quiet=True)
    return "passed" in out.lower()


def holds(s, item):
    """Whether Bot1 carries `item` (clearing none of it)."""
    return "no items" not in s.c(f"clear Bot1 {item} 0", quiet=True).lower()


def study_stage(s):
    """Bot1 faces a lectern 2 blocks south; Bot2 watches from Bot1's right, side on."""
    s.c("fill -8 0 2 8 10 18 air", quiet=True)
    s.c("clear Bot1", quiet=True)
    s.c("gamemode survival Bot1", quiet=True)
    s.c("xp set Bot1 0 points", quiet=True)
    s.c("xp set Bot1 30 levels", quiet=True)
    s.c("tp Bot1 0.5 0 7.5 0 25", quiet=True)  # pitch 25: eye ray meets the lectern's top
    s.c("tp Bot2 3.0 0 8.0 104 12", quiet=True)
    s.do("Bot1", "slot", "0")
    time.sleep(2)


def hold_use(s, prefix, seconds, shots_at=(0.4, 1.0, 1.6), then=None):
    """Holds use as Bot1 for `seconds`, screenshotting both bots (and probing) at the given times, then calls `then`."""
    s.do("Bot1", "use", "on")
    start = time.time()
    for i, at in enumerate(shots_at):
        time.sleep(max(0.0, start + at - time.time()))
        s.do("Bot1", "shot", f"{prefix}-{i}")
        s.do("Bot2", "shot", f"{prefix}-remote-{i}")
        s.do("Bot1", "probe", f"{prefix}-{i}")
    time.sleep(max(0.0, start + seconds - time.time()))
    s.do("Bot1", "shot", f"{prefix}-done")
    s.do("Bot1", "probe", f"{prefix}-done")
    if then:
        then()
    time.sleep(0.5)
    s.do("Bot1", "use", "off")
    time.sleep(1)
    log(f"  probe Bot1: {last_probe('Bot1')}")


def scenario_study(s):
    """Studying and copying at a lectern: hands together, glow and rubbing, runes, the written book floating off."""
    study_stage(s)
    s.do("Bot1", "hud", "on")

    lectern(s, '{id:"minecraft:writable_book",count:1}')
    s.c(f"item replace entity Bot1 weapon.mainhand with {STUDY_SWORD}", quiet=True)
    s.c("item replace entity Bot1 weapon.offhand with minecraft:amethyst_shard 4", quiet=True)
    time.sleep(1)
    hold_use(s, "study", 2.6, then=lambda: (s.do("Bot2", "shot", "study-float-remote"),
                                           check(written_book(s, True), "[study] the written book floats up off the lectern")))
    check(s.is_block(LECTERN, "minecraft:lectern[has_book=false]"), "[study] the lectern is left empty")
    check(not holds(s, "minecraft:diamond_sword"), "[study] the studied sword is gone")
    check(holds(s, "minecraft:amethyst_shard"), "[study] only one amethyst shard is used")
    time.sleep(3)
    check(written_book(s, False), "[study] and drops back down")
    s.c("kill @e[type=minecraft:item]", quiet=True)

    lectern(s, '{id:"minecraft:writable_book",count:1}')
    s.c("clear Bot1", quiet=True)
    s.c(f"item replace entity Bot1 weapon.mainhand with {COPY_SOURCE_ITEM}", quiet=True)
    time.sleep(1)
    hold_use(s, "copy", 2.6, then=lambda: (s.do("Bot2", "shot", "copy-float-remote"),
                                           check(written_book(s, True), "[copy] the written book floats up off the lectern")))
    check(holds(s, "minecraft:enchanted_book"), "[copy] the copied book is kept")
    check(s.is_block(LECTERN, "minecraft:lectern[has_book=false]"), "[copy] the lectern is left empty")
    time.sleep(3)
    s.c("kill @e[type=minecraft:item]", quiet=True)

    # A curse: at level 4 about one copy in 11 curses, so copy until one does (survival, to see the half heart).
    s.c("xp set Bot1 0 levels", quiet=True)
    s.c("effect give Bot1 minecraft:instant_health 1 5 true", quiet=True)
    cursed = False
    for attempt in range(30):
        lectern(s, '{id:"minecraft:writable_book",count:1}')
        s.c("tp Bot1 0.5 0 7.5 0 25", quiet=True)
        s.c("xp set Bot1 0 points", quiet=True)
        s.c("xp set Bot1 4 levels", quiet=True)
        s.c(f"item replace entity Bot1 weapon.mainhand with {COPY_SOURCE_ITEM}", quiet=True)
        time.sleep(0.5)
        s.do("Bot1", "use", "on")
        time.sleep(2.3)
        s.do("Bot1", "probe", "curse-try")
        time.sleep(0.3)
        if "mistakes" in last_probe("Bot1"):
            s.do("Bot2", "shot", "curse-remote")
            s.do("Bot1", "shot", "curse")
            time.sleep(0.4)
            s.do("Bot2", "shot", "curse-remote-later")
            cursed = True
        s.do("Bot1", "use", "off")
        time.sleep(1)
        if cursed:
            log(f"  cursed on attempt {attempt + 1}; probe Bot1: {last_probe('Bot1')}")
            break
        s.c("kill @e[type=minecraft:item]", quiet=True)
    check(cursed, "[curse] a copy at level 4 eventually writes a curse")
    if cursed:
        out = s.c("data get entity Bot1 Pos[2]", quiet=True)
        m = re.search(r"(-?\d+\.\d+)d", out)
        check(m is not None and float(m.group(1)) < 7.3, f"[curse] the player is knocked back from the lectern ({out.strip()})")
        out = s.c("data get entity Bot1 Health", quiet=True)
        m = re.search(r"(\d+\.\d+)f", out)
        log(f"  Bot1 health after the curse: {out.strip()}")
    s.c("gamemode survival Bot1", quiet=True)
    s.c("xp set Bot1 30 levels", quiet=True)
    s.c("kill @e[type=minecraft:item]", quiet=True)

    # Letting go part way uses nothing.
    lectern(s, '{id:"minecraft:writable_book",count:1}')
    s.c("clear Bot1", quiet=True)
    s.c(f"item replace entity Bot1 weapon.mainhand with {STUDY_SWORD}", quiet=True)
    s.c("item replace entity Bot1 weapon.offhand with minecraft:amethyst_shard 1", quiet=True)
    time.sleep(1)
    s.do("Bot1", "use", "on")
    time.sleep(0.8)
    s.do("Bot1", "use", "off")
    time.sleep(3)
    check(holds(s, "minecraft:diamond_sword") and holds(s, "minecraft:amethyst_shard"), "[study] letting go part way uses nothing")
    check(s.is_block(LECTERN, 'minecraft:lectern{Book:{id:"minecraft:writable_book"}}'), "[study] and leaves the book and quill")

    for bot in BOT_DIRS:
        s.do(bot, "iris", "on")
    time.sleep(25)
    hold_use(s, "study-iris", 2.6)
    s.c("kill @e[type=minecraft:item]", quiet=True)
    for bot in BOT_DIRS:
        s.do(bot, "iris", "off")
    s.do("Bot1", "hud", "off")
    s.c("clear Bot1", quiet=True)
    time.sleep(3)


SCENARIOS = {"debug": scenario_debug, "vanilla": scenario_vanilla, "iris": scenario_iris, "ars": scenario_ars,
             "sneak": scenario_sneak, "spectral": scenario_spectral,
             "doppelganger": scenario_doppelganger, "haunting": scenario_haunting, "study": scenario_study}


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
