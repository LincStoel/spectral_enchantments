# Spectral Enchantments

A NeoForge mod adding eight enchantments and two curses. The theme of the mod centers around spectral copies of items and entities that aid or harm the player. Additionally, this mod hides curses until equipped meaning that you might actually interact with cursed items instead of just treating them as junk.

- Minecraft 1.21.1
- NeoForge 21.1.255 or later
- Required on both client and server

## Enchantments

| Enchantment | Applies to | Max level | Incompatible with | Treasure by default |
|---|---|---|---|---|
| Broad Breaker | Pickaxes, shovels | III | Far Miner | No |
| Far Miner | Mining tools | III | Broad Breaker, Animated | No |
| Gather | Mining tools, melee weapons, bows, crossbows, tridents, maces | I | — | No |
| Branch Finding | Hoes, shears | I | — | No |
| Animated | Melee weapons (any item via anvil) | I | Far Miner, Loyalty | Yes |
| Catapulting | Bows | I | — | No |
| Explosive Bolts | Crossbows | I | — | No |
| Doppelganger | Chestplates | I | Protection | No |
| Curse of Possession | Melee weapons | I | — | Yes |
| Curse of Haunting | Armor | I | — | Yes |

"Melee weapons" is the `spectral_enchantments:melee_weapons` item tag: swords, axes, maces, tridents and anything in the `c:swords`, `c:tools/melee_weapon`, `c:tools/spear` or `c:tools/mace` tags.

### Broad Breaker

A magical implementation of the common "hammer" item in modded minecraft.

- Mines an area of blocks at once: 3×3 at level I, 5×5 at II, 7×7 at III (configurable).
- Mining a floor or ceiling breaks a square centred on the mined block. Mining a wall breaks a square with the mined block centred horizontally and second from the bottom.
- Only blocks the tool can correctly harvest are included. Unbreakable blocks, blocks outside the world border and blocks the player isn't allowed to modify are skipped.
- The whole area breaks at the speed of its slowest block.
- Durability cost is half the number of blocks broken (minimum 1).
- Every block in the area gets a selection outline, and a ghostly copy of the tool appears at each extra block, swinging at it. Other players see the ghost tools.
- Sneaking mines a single block.

### Far Miner

A magical way to mine at a distance!

- Look at a block beyond normal reach for 0.75 seconds and the tool locks on to it. Hold attack to mine it.
- Range from the player's eyes: 8 blocks at level I, 12 at II, 16 at III (configurable).
- Mines at 20% of normal speed (configurable). This replaces the vanilla underwater and airborne penalties instead of stacking with them. Mining Fatigue still applies.
- The server checks range and line of sight, so blocks behind walls can't be mined.
- Drops, durability, stats and other mods' break events work as they do for a normal mine. Other players see the break cracks and the spectral tool.
- The lock is released when the block breaks, attack is released, the tool changes, the player mines a block within normal reach, or the player sneaks. Before mining starts, looking away also releases it.
- Sneaking turns Far Miner off.
- Quark's pickarangs never get Far Miner (`spectral_enchantments:far_miner_excluded`).

### Gather

Automatically pick up broken blocks and XP. (works great with far miner)

- Drops from blocks broken with the item go straight into the player's inventory. This includes blocks broken by Broad Breaker and Far Miner.
- Drops from creatures killed with the item also go to the inventory. This covers melee, arrows from an enchanted bow or crossbow, thrown tridents and Animated weapons.
- Experience from those blocks and kills goes to the player as well, so Mending picks it up.
- Anything that doesn't fit in the inventory drops normally.
- Each drop goes through the normal item pickup events, so backpack pickup upgrades, void filters and similar mods still see it.

### Branch Finding

Find those pesky branches hidden in large oaks and quark trumpet trees.

- Using the tool on leaves searches the connected leaf mass, including leaves joined only diagonally, and outlines every log touching it.
- Outlines are visible through blocks, only to the user, and fade out over 16 seconds (configurable).
- Searches up to 4096 leaves, nearest first (configurable). Very large searches are spread over several ticks.
- Costs 1 durability per use. Has a 1-second cooldown per item (configurable).
- Plays a chime, at a lower pitch if no logs were found.
- Hoes still till ground as normal.
- Outlined blocks are set by the `spectral_enchantments:branch_finding_targets` block tag (default `#minecraft:logs`).

### Animated

An animated weapon that fights on its own in four ways.

- **Companion.** The leftmost Animated weapon in the hotbar or off hand floats at the player's side. The item in the main hand never does this. When the player hits something, the weapon attacks the same target and keeps fighting it until it dies or leaves range (12 blocks by default). It swings at half the weapon's normal attack speed. It never targets players, the player's pets or armor stands. Sneaking, sleeping, dying or switching to spectator mode sends it back.
- **Deployed.** Sneak-use the weapon on a block to leave it floating there. It fights hostile mobs within 4 blocks for 15 seconds, then flies back. It also comes back if the player moves more than 16 blocks away. Using or attacking with it while it's out calls it back early. After a recall it can't be swung for 2 seconds (cooldown applies only to that item).
- **Thrown.** An Animated trident, spear or weapon fired from a Catapulting bow guards the spot where it lands. It returns when time runs out or the owner moves more than 32 blocks away. If the owner is offline, dead or in another dimension, it waits where it is and is saved with the world. Loyalty brings the weapon back as usual instead.
- **Armor stand.** An armor stand holding an Animated weapon sends it after hostile mobs within 8 blocks. These kills give no XP and no player-only drops.

Other details:
- Hits use the weapon's own damage and enchantments (including Looting on companion and deployed kills) and wear down the real item, so Unbreaking applies.
- An Animated weapon with Mending repairs itself from the experience its kills drop.
- Hostile mobs only. Neutral mobs (endermen, zombified piglins) are targeted only once they're angry.
- Swing style (sweep, chop, thrust, slam) comes from Better Combat's weapon data when Better Combat is installed, and from the item type otherwise.
- An enchanted book in an anvil can put Animated on any item, not just weapons. Non-weapons fight weakly: half a heart, very slowly.
- While the weapon is out, its inventory icon is overlaid with a ghost. Recall cooldowns show vanilla's cooldown sweep.
- Items in `spectral_enchantments:enchantable/animated_excluded` can't get Animated, even in an anvil (default: Simply Swords' unique weapons).

### Catapulting

An enchantment for those who think arrows are for chumps. A bow in the main hand fires whatever is in the off hand, at arrow speed and range. Arrows in the off hand are still fired as arrows.

- **Weapons and tools** fly point first, deal the weapon's own attack damage and enchantments (scaled by draw), and stick where they land. Only the shooter can pick them up, and they don't despawn. They return to the off hand if it's free. Loyalty brings them back like a trident. Animated makes them guard the landing spot. Flame on the bow sets them alight. A weapon that would break from the durability cost isn't fired.
- **Throwables** (snowballs, eggs, ender pearls, splash and lingering potions, experience bottles, wind charges, fishing rods) are thrown as normal, but at bow speed. Snowballs leave a layer of snow where they land.
- **Fireworks** fly straight, as from a crossbow.
- **Buckets of fish or other mobs** fling the creature. The empty bucket stays in hand.
- **Blocks** are placed where they land, as if the shooter placed them there, so protection and spawn protection apply. On the way they pass through creatures, damaging and knocking back each one and slowing down. Anvils deal falling-anvil damage and chip based on how far they flew.
- **Other items** are used where they land, as if clicked there. For example, fire charges light fires, spawn eggs hatch, bone meal grows plants, paintings and item frames are hung, and full buckets pour out (the empty bucket goes back to the shooter). An end crystal that can't be placed explodes, but not where the shooter couldn't build.
- **Items hitting a creature:** armor is put on if the creature can wear it and the slot is free, food is eaten, drinks are drunk, spawn eggs hatch, fire charges set it alight, and shears and similar tools are used on it. Anything else deals damage based on rarity: 1, 2, 3 or 4 hearts for common, uncommon, rare and epic at full draw (configurable).
- Whatever isn't used up drops where it landed.
- Each shot costs the bow 2 durability. A fired weapon loses 1 durability. Both are configurable, and Unbreaking applies.
- Never fired: shields and totems of undying (`spectral_enchantments:catapulting/excluded`), and cursed items locked by the Cursed effect.
- With Sable installed and `physicsBlocks` enabled, blocks fly as physics objects, with heavier blocks flying slower. They place themselves where they come to rest, or stay loose if they hit a creature on the way. This is off by default.
- Item tags for customising behaviour, all under `spectral_enchantments:catapulting/`: `weapons`, `throwables`, `excluded`, `no_use`, `no_physics`, `render_as_icon`, `flipped_sprite`. Entity tag: `wears_armor`.

### Explosive Bolts

Adds the ability for crossbows to fire a variety of additional ammo types. Ammo is chosen in vanilla's order (hands first, then inventory), and Multishot and Quick Charge apply.

| Ammunition | Effect |
|---|---|
| Fire charge | Small fireball, as from a dispenser |
| Blaze rod | Burning bolt that hits like an arrow, then sprays fire within 3 blocks of where it lands and sets creatures there alight for 5 seconds |
| Ghast tear | Ghast fireball, explosion power 1 |
| Water bottle | Bursts on impact and puts out fires, lit campfires, candles and burning creatures within 4 blocks. Damages creatures hurt by water. |
| Splash / lingering potion | Thrown potion at bolt speed |

Radius, burn time and explosion power are configurable. Ammunition types are set by the item tags `spectral_enchantments:explosive_ammunition/{fire_charge, blaze_spray, ghast_fireball, water_burst, thrown_potion}`.

### Doppelganger

Sneak away while a ghost doppelganger takes the heat!

- Starting to sneak while wearing the chestplate leaves a ghostly double of the player behind. The double disappears when the player stops sneaking or moves more than 32 blocks away.
- Monsters within 16 blocks of the double that are targeting the player switch to the double. This applies both to monsters already chasing the player and to ones that pick the player as a target later.
- The double can't be hurt and doesn't move. The player's own attacks and projectiles pass through it. Monsters' projectiles stop at it.
- It looks like the owner's skin, rendered pale and translucent.
- 1-second cooldown between doubles (configurable).
- With Quark installed, the double plays random Quark emotes.

### Curse of Possession

The evil alter ego of the animated weapon enchantment.

- The first time a weapon with this curse enters a player's inventory, it pulls itself free and attacks them as a hostile flying weapon.
- Its health depends on the durability it had left (0.05 health per point, between 6 and 40). It has 2 armor and deals the weapon's own damage with its enchantments. All of these are configurable.
- When killed, it has a 10% chance to drop the weapon with its curses removed, 5% durability left, and Animated added.
- With Ars Nouveau, casting Dispel on it always frees the weapon, with the same durability it had and with Animated added.
- It's saved with the world and doesn't despawn, including in Peaceful. In Peaceful, a newly picked-up possessed weapon stays dormant until the difficulty is raised.

### Curse of Haunting

Is that you herobrine?

- While the player wears armor with this curse, a pale ghost of them appears when they put the armor on, and again every 3 to 8 minutes. It appears 12 to 20 blocks away, somewhere it can see the player, and stares at them for 30 seconds.
- The ghost also appears immediately behind any mob that hurts the player.
- If the player comes within 8 blocks, the ghost flees and disappears.
- When monsters are within 16 blocks of the player, the ghost moves to them and gives Strength I to every monster within 6 blocks of it.
- Hitting the ghost (melee or projectile) banishes it for 10 to 20 seconds. Monsters' projectiles pass through it, and monsters ignore it.
- The ghost looks like Steve with glowing white eyes. Now and then it raises its arms like an evoker casting a spell.
- Monsters can spawn wearing armor with this curse. Such a monster has its own ghost at its shoulder that gives Strength to monsters around it. This can be turned off with `monsterGhosts`.
- All timings, distances and the Strength level are configurable.

## Curse mechanics

These apply to all curses, including vanilla ones.

- **Hidden curses.** A curse doesn't appear in an item's tooltip until the item has been in a survival or adventure player's inventory. For armor and other wearables, it stays hidden until the item is worn. Players in creative mode always see curses, and enchanted books always show theirs. This can be turned off with `curses.hideUntilHeld`.
- **Cursed effect.** When a curse is revealed, the player gets the Cursed effect for 60 seconds (configurable, 0 to disable). While it lasts, no cursed item they carry can be dropped, moved into another container or bundle, put into a block that holds items, placed in an item frame, or given to an armor stand or allay. Milk and Dispel don't remove it. Blocks that refuse cursed items are set by `spectral_enchantments:cursed/refuses_items` (default: decorated pot).

## Obtaining

- **Enchanting table:** all non-treasure enchantments.
- **Treasure loot, enchanted-book loot and villager trades:** all enchantments. Treasure-only enchantments cost double in trades, as Mending does.
- **Villager Trade Rebalance experiment:** each enchantment is added to one biome's librarian trades.

| Biome | Enchantments |
|---|---|
| Plains | Broad Breaker, Gather |
| Snow | Far Miner |
| Jungle | Branch Finding |
| Taiga (special) | Animated |
| Taiga | Doppelganger |
| Savanna | Catapulting |
| Desert | Explosive Bolts |
| Swamp | Curse of Possession, Curse of Haunting |

- **Built-in data pack "Spectral Enchantments: Loot"** (enabled by default; can be disabled from the data pack list):

| Source | Item | Chance |
|---|---|---|
| Abandoned mineshaft chests | Damaged iron or stone pickaxe with Broad Breaker I | 8% |
| Dungeon chests | Damaged iron or chainmail armor piece with Curse of Haunting, plus random enchantments | 6% |
| Stronghold chests | Damaged iron sword or axe with Curse of Possession, plus random enchantments | 6% |
| Nether fortress, bastion and Nether ruined portal chests | Damaged gold armor with Curse of Haunting | 2.5% |
| Same | Damaged gold sword or axe with Curse of Possession | 2.5% |
| Piglin bartering | Gold boots or book with Curse of Haunting | 0.5% |
| Piglin bartering | Gold sword or book with Curse of Possession | 0.5% |

Loot entries for a disabled enchantment are not added.

## Configuration

**Common** (`config/spectral_enchantments-common.toml`). Takes effect after a restart or `/reload`. For each enchantment:
- `enabled`: when false, the enchantment stays registered so existing items keep it, but it does nothing and no longer appears in enchanting tables, loot, trades or recipes.
- `treasure_only`: when true, the enchantment can't be rolled at the enchanting table and is found like Mending instead.

**Server** (`serverconfig/spectral_enchantments-server.toml`, per world, synced to clients):

| Section | Settings (default) |
|---|---|
| `broadBreaker` | `areas` [3, 5, 7] (odd numbers, max 15) |
| `farMiner` | `ranges` [8, 12, 16] (5 to 64), `speedMultiplier` 0.2 |
| `branchFinding` | `durationSeconds` 16, `maxLeaves` 4096, `cooldownTicks` 20 |
| `animated` | `companionRange` 12, `standRadius` 8, `deployRadius` 4, `deploySeconds` 15, `recallDistance` 16, `thrownRecallDistance` 32, `recallCooldownSeconds` 2.0 |
| `catapulting` | `bowDurabilityCost` 2, `weaponDurabilityCost` 1, `rarityHearts` [1, 2, 3, 4], `placeBlocks` true, `useItems` true, `explodeCrystals` true, `physicsBlocks` false |
| `explosiveBolts` | `blazeFireRadius` 3, `blazeBurnSeconds` 5, `ghastExplosionPower` 1, `waterDouseRadius` 4 |
| `doppelganger` | `lureRadius` 16, `maxDistance` 32, `cooldownTicks` 20, `emoteMinSeconds` 5, `emoteMaxSeconds` 20 |
| `curses` | `hideUntilHeld` true, `cursedSeconds` 60 |
| `possessionCurse` | `healthPerDurability` 0.05, `minHealth` 6, `maxHealth` 40, `healthPerHardness` 4.0, `itemHealth` 1.0, `armor` 2.0, `damageMultiplier` 1.0, `dropChance` 0.1, `dropDurability` 0.05 |
| `hauntingCurse` | `appearMinSeconds` 180, `appearMaxSeconds` 480, `stareSeconds` 30, `stareMinDistance` 12, `stareMaxDistance` 20, `approachDistance` 8, `monsterRadius` 16, `strengthRadius` 6, `strengthLevel` 1, `banishMinSeconds` 10, `banishMaxSeconds` 20, `castMinSeconds` 8, `castMaxSeconds` 20, `monsterGhosts` true |

Invalid values are reset to their defaults.

**Client** (`config/spectral_enchantments-client.toml`): `shaderBrightness` 0.5. This sets the brightness of ghost tools and weapons while an Iris shaderpack is active.

## Optional dependencies

None are required.

| Mod | Integration |
|---|---|
| [Better Combat](https://modrinth.com/mod/better-combat) | Animated and possessed weapons use the weapon's Better Combat attack combo (swing style, damage multiplier, upswing, sound). Items Better Combat can swing count as weapons for Animated and Catapulting. This also covers Simply Swords, which defines its weapons through Better Combat data. |
| [Ars Nouveau](https://modrinth.com/mod/ars-nouveau) | Enchanting Apparatus recipes for level I of every enchantment and curse (Animated is made from a Loyalty book). Dispel frees a possessed weapon. |
| [Quark](https://modrinth.com/mod/quark) | Color Runes tint ghost tools, Animated weapons and Doppelganger doubles. Doubles play Quark emotes. Pickarangs can be fired by Catapulting but can't get Far Miner. |
| Sable | Catapulting can fire blocks as physics objects (off by default). |
| [Iris](https://modrinth.com/mod/iris) | Ghost rendering is adjusted to work with shaderpacks. Brightness is set in the client config. |
| [Configured](https://modrinth.com/mod/configured) | In-game config screen (Mods → Spectral Enchantments → Config). |
| [Enchantment Descriptions](https://modrinth.com/mod/enchantment-descriptions) | Shows the provided enchantment descriptions in tooltips. Description lines for hidden curses are hidden with them. |
| Simply Swords | Unique weapons are excluded from Animated and Curse of Possession. |

Item tags also include the common `c:` tags (`c:swords`, `c:tools/melee_weapon`, `c:tools/mining_tool`, `c:tools/shear`, `c:tools/spear`, `c:tools/shield`, …), so other mods' tools and weapons are included where they use them.

## License

GNU General Public License v3.0 or later. See [LICENSE](LICENSE).
