"""Prepares run-compat/ for the compat harness (idempotent; --reset rewrites server.properties).

Copies every mod in run/mods into the bot clients (bot1, bot2), and all but the
client-only ones (Iris, Sodium and their compat) into the server and the GameTest
server. Writes a flat world's server.properties with RCON on 127.0.0.1 and a random
password, accepts the EULA for this local dev server, copies the shaderpacks and
points Iris at BSL (shaders start disabled; the harness toggles them live).
"""
import pathlib
import secrets
import shutil
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
SRC = ROOT / "run"
COMPAT = ROOT / "run-compat"
SERVER = COMPAT / "server"
GAMETEST = COMPAT / "gametest"
BOTS = [COMPAT / "bot1", COMPAT / "bot2"]
# Jars here replace the same mod's jar in the bots, e.g. a Sodium that matches Iris
# (Iris 1.8.12 needs Sodium 0.6.x; Sodium 0.8.x crashes it on world join).
BOT_OVERRIDES = COMPAT / "overrides" / "bots"
CLIENT_ONLY = ("iris", "sodium")
SHADERPACK = "BSL_v10.1.1.zip"

FLAT = ('{"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:stone","height":60},'
        '{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],'
        '"biome":"minecraft:plains","features":false,"lakes":false,"structure_overrides":[]}')


def mod_stem(name):
    """'sodium-neoforge-0.6.13+mc1.21.1.jar' -> 'sodium-neoforge' (the jar name before its version)."""
    parts = name.split("-")
    end = next((i for i, p in enumerate(parts) if p[:1].isdigit()), len(parts))
    return "-".join(parts[:end])


def sync_mods(dest, client):
    mods = dest / "mods"
    mods.mkdir(parents=True, exist_ok=True)
    wanted = {jar.name: jar for jar in (SRC / "mods").glob("*.jar")
              if client or not jar.name.lower().startswith(CLIENT_ONLY)}
    if client and BOT_OVERRIDES.is_dir():
        for override in BOT_OVERRIDES.glob("*.jar"):
            for name in [n for n in wanted if mod_stem(n) == mod_stem(override.name)]:
                del wanted[name]
            wanted[override.name] = override
    for jar in mods.glob("*.jar"):
        source = wanted.get(jar.name)
        if source is None or source.stat().st_size != jar.stat().st_size:
            jar.unlink()
    for name, jar in wanted.items():
        if not (mods / name).exists():
            shutil.copy2(jar, mods / name)


def write_server():
    SERVER.mkdir(parents=True, exist_ok=True)
    (SERVER / "eula.txt").write_text("eula=true\n", encoding="utf-8")
    props = SERVER / "server.properties"
    if props.exists() and "--reset" not in sys.argv:
        return
    props.write_text("\n".join([
        "online-mode=false",
        "enforce-secure-profile=false",
        "server-ip=127.0.0.1",
        "server-port=25565",
        "enable-rcon=true",
        "rcon.port=25575",
        f"rcon.password={secrets.token_hex(16)}",
        "broadcast-rcon-to-ops=false",
        "level-name=world",
        "level-seed=424242",
        r"level-type=minecraft\:flat",
        f"generator-settings={FLAT}",
        "generate-structures=false",
        "spawn-protection=0",
        "max-tick-time=-1",
        "view-distance=8",
        "simulation-distance=8",
        "difficulty=peaceful",
        "spawn-monsters=false",
        "spawn-animals=false",
        "spawn-npcs=false",
        "gamemode=creative",
        "motd=spectral_enchantments compat",
        "",
    ]), encoding="utf-8")


def write_bot(bot):
    bot.mkdir(parents=True, exist_ok=True)
    (bot / "options.txt").write_text("\n".join([
        "onboardAccessibility:false",
        "skipMultiplayerWarning:true",
        "joinedFirstServer:true",
        "tutorialStep:none",
        "pauseOnLostFocus:false",
        "enableVsync:false",
        "maxFps:120",
        "renderDistance:8",
        "simulationDistance:8",
        "narrator:0",
        "soundCategory_master:0.0",
        "fullscreen:false",
        "guiScale:2",
        "",
    ]), encoding="utf-8")
    packs = bot / "shaderpacks"
    packs.mkdir(exist_ok=True)
    for pack in (SRC / "shaderpacks").glob("*.zip"):
        if not (packs / pack.name).exists():
            shutil.copy2(pack, packs / pack.name)
    config = bot / "config"
    config.mkdir(exist_ok=True)
    (config / "iris.properties").write_text("\n".join([
        "allowUnknownShaders=false",
        "disableUpdateMessage=true",
        "enableDebugOptions=true",
        "enableShaders=false",
        f"shaderPack={SHADERPACK}",
        "",
    ]), encoding="utf-8")


def main():
    write_server()
    sync_mods(SERVER, client=False)
    sync_mods(GAMETEST, client=False)
    for bot in BOTS:
        write_bot(bot)
        sync_mods(bot, client=True)
    print("ready:", COMPAT)


if __name__ == "__main__":
    main()
