# Spectral Enchantments

A NeoForge mod adding ten enchantments and two curses. The theme of the mod centers around spectral copies of items and entities that aid or harm the player. Additionally, this mod hides curses until equipped meaning that you might actually interact with cursed items instead of just treating them as junk.

- Minecraft 1.21.1
- NeoForge 21.1.255 or later
- Required on both client and server

## Enchantments

| Enchantment | Applies to | Max level | Incompatible with | Treasure by default |
|---|---|---|---|---|
| Broad Breaker | Pickaxes, shovels | III | Far Miner | Yes |
| Far Miner | Mining tools | III | Broad Breaker, Animated | Yes |
| Gather | Mining tools, melee weapons, bows, crossbows, tridents, maces | I | — | No |
| Branch Finding | Hoes, shears | I | — | No |
| Animated | Melee weapons (any item via anvil) | I | Far Miner, Loyalty | Yes |
| Catapulting | Bows | I | — | Yes |
| Explosive Bolts | Crossbows | I | — | Yes |
| Doppelganger | Chestplates | I | Protection | Yes |
| High Jump | Boots | III | — | Yes |
| Springy | Boots | I | Feather Falling | No |
| Curse of Possession | Melee weapons | I | — | Always |
| Curse of Haunting | Armor | I | — | Always |

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

- Starting to sneak on the ground while wearing the chestplate leaves a ghostly double of the player behind (sneaking in mid-air leaves it on landing). The double fades out over a second when the player stops sneaking, and disappears at once if they move more than 32 blocks away. It's almost invisible while the player stands inside it, filling in as they move 3 blocks away.
- Monsters within 16 blocks of the double that are targeting the player switch to the double. This applies both to monsters already chasing the player and to ones that pick the player as a target later.
- The double can't be hurt and doesn't move. The player's own attacks and projectiles pass through it. Monsters' projectiles stop at it.
- It looks like the owner's skin, rendered pale and translucent.
- 1-second cooldown between doubles (configurable).
- With Quark installed, the double plays random Quark emotes.

### High Jump

Charge a leap like a horse.

- Jumping is unchanged, but holding the key no longer jumps again on landing. Instead the horse jump bar takes the experience bar's place and charges just like a horse's: full at half a second, then sagging back a little if held longer. Letting go leaps.
- A full charge leaps 2, 3 or 4 blocks high at levels I, II and III (configurable). Letting go early or late leaps less.
- If the player holds on for 3 seconds (configurable), the bar goes away and letting go does nothing.
- Dust puffs out from the ground as the player takes off.
- The leap's extra height doesn't count toward fall damage on landing.

### Springy

Bounce off the ground instead of breaking your legs.

- Landing from more than 3 blocks bounces the player instead of hurting them, in creative too. Straight down, they bounce back up three quarters of the height of the fall. Landing at an angle turns part of the bounce into forward speed.
- Jumping just as they land (up to 3 ticks before, or the tick after) bounces them back up 95% of the fall instead.
- Sneaking while landing takes the fall damage as usual, with no bounce. Blocks that already cancel fall damage don't bounce either, nor do blocks in the `spectral_enchantments:springy_ignored` tag (slime blocks by default).
- While falling, a translucent ghost slime gathers from slime flecks around the player's boots. It squashes and springs each time they land and bounce, and bursts in a splash once they land without bouncing. It keeps a slime's own green unless the boots carry a Quark rune.
- The minimum fall, bounce heights, timing window and fastest bounce are configurable.

### Curse of Possession

The evil alter ego of the animated weapon enchantment.

- The first time a weapon with this curse enters a player's inventory, it pulls itself free and attacks them as a hostile flying weapon.
- Its health depends on the durability it had left (0.05 health per point, between 6 and 40). It has 2 armor and deals the weapon's own damage with its enchantments. All of these are configurable.
- When killed, it has a 10% chance to drop the weapon with its curses removed, 5% durability left, and Animated added.
- With Ars Nouveau, casting Dispel on it always frees the weapon, with the same durability it had and with Animated added.
- It's saved with the world and doesn't despawn, including in Peaceful. In Peaceful, a newly picked-up possessed weapon stays dormant until the difficulty is raised.

### Curse of Haunting

Is that you herobrine?

- While the player wears armor with this curse, a pale ghost of them appears when they put the armor on, and again every 3 to 8 minutes. It stands on a block 12 to 20 blocks away, somewhere it can see the player, and stares at them for 30 seconds.
- The ghost also appears immediately behind any mob that hurts the player.
- If the player comes within 8 blocks of a staring ghost, or goes more than 32 blocks from it, it vanishes in a puff.
- When monsters are within 16 blocks of the player, the ghost floats over and haunts the nearest one, reaching out to it as if slowly swimming toward it. The haunted monster slowly heals (like Regeneration I, undead included), and every 4 to 10 seconds the ghost raises its arms like an evoker casting a spell, giving it and every monster within 5 blocks of it Speed, Strength or Resistance I for 15 seconds. Once the monsters are gone, so is the ghost.
- Hitting the ghost (melee or projectile) banishes it for 10 to 20 seconds. Monsters' projectiles pass through it, and monsters ignore it.
- The ghost looks like Steve with glowing white eyes.
- Monsters can spawn wearing armor with this curse, and in the Nether 2% of armor-wearing monsters (zombified piglins, piglins, wither skeletons and the like) spawn with a piece of leather, chainmail, iron or gold armor carrying it. If they already wear armor in that slot, that piece gets the curse instead.
- Such a monster has its own ghost at its shoulder that haunts it the same way. At most 4 monster ghosts appear within 32 blocks of each other, so mob farms don't fill with ghosts. Monster ghosts can be turned off with `monsterGhosts`.
- All timings, distances and the spell's effect level and length are configurable.
- Tags for customising the Nether spawns: `spectral_enchantments:haunting/nether_armor_wearers` (entity types) and `spectral_enchantments:haunting/nether_armor` (the armor pieces it can add).

## Curse mechanics

These apply to all curses, including vanilla ones.

- **Hidden curses.** A curse doesn't appear in an item's tooltip until the item has been in a survival or adventure player's inventory. For armor and other wearables, it stays hidden until the item is worn. Players in creative mode always see curses, and enchanted books always show theirs. This can be turned off with `curses.hideUntilHeld`.
- **Cursed effect.** When a curse is revealed, the player gets the Cursed effect for 60 seconds (configurable, 0 to disable). While it lasts, no cursed item they carry can be dropped, moved into another container or bundle, put into a block that holds items, placed in an item frame, or given to an armor stand or allay. Milk and Dispel don't remove it. Blocks that refuse cursed items are set by `spectral_enchantments:cursed/refuses_items` (default: decorated pot).

## Studying and copying enchantments

A lectern can teach you an item's enchantments, or copy an enchanted book. Both write the enchantment at level I and cost experience.

Both start with a blank book and quill on a lectern. When done, it becomes an enchanted book that floats up off the lectern and drops back down.

- **Studying.** Hold an enchanted item in your main hand and an amethyst shard in your off hand, then hold use on the lectern. You rub the amethyst on the glowing item for a moment, and both vanish in a puff of purple sparks. The book gets one of the item's enchantments.
- **Copying.** Hold use on the lectern with an enchanted book. The book and quill gets one of its enchantments, and you keep the enchanted book.
- Sometimes your mistakes result in a random curse instead: 1 in 10 at level 0, falling steadily to 1 in 2000 at level 30 and above. The curse lashes out for half a heart and knocks you back from the lectern, and the book rises in a shower of blood.
- Use must be held until it's done (2 seconds). Letting go uses up nothing. While you work, enchanting runes stream from your hands to the lectern and the enchantment's name is spelled out in enchanting runes above the hotbar.
- Each costs 27 experience points (what it takes to reach level 3), double for treasure enchantments like Mending. Curses cost the base amount. Free in creative.
- Which enchantment you get is fixed for each player and item until you finish a study or copy, as with the enchanting table, so letting go and trying again gives the same result.
- Only enchantments actually on the item count. For example, the Fortune that Quark gives gold tools can't be studied.
- Enchantments that are switched off can't be studied or copied, and treasure enchantments can't be copied. They are left out when choosing; if nothing is left, you are told which ones are too powerful.
- Tags: `spectral_enchantments:study/catalyst` (items to rub with; amethyst shard), `study/unstudyable` (empty), `study/uncopyable` (`#minecraft:treasure`), `study/curses` (curses that can be written; `#minecraft:curse`).

## Obtaining

- **Enchanting table:** Gather (uncommon), Branch Finding and Springy (rare) by default; any other enchantment with `treasure_only` set to false (very rare). Curses never.
- **Treasure loot, enchanted-book loot and villager trades:** all enchantments. Treasure-only enchantments cost double in trades, as Mending does.
- **Villager Trade Rebalance experiment:** each enchantment is added to one biome's librarian trades.

| Biome | Enchantments |
|---|---|
| Plains | Broad Breaker, Gather |
| Snow | Far Miner |
| Jungle | Branch Finding |
| Taiga (special) | Animated |
| Taiga | Doppelganger |
| Savanna | Catapulting, High Jump |
| Desert | Explosive Bolts |
| Swamp | Curse of Possession, Curse of Haunting, Springy |

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
- `treasure_only`: when true, the enchantment can't be rolled at the enchanting table and is found like Mending instead. Curses have no such setting: like vanilla's, they are always treasure.

Also `enchantmentStudy.enabled` (turns off studying and copying), `enchantmentStudy.studying.enabled` and `enchantmentStudy.copying.enabled`, all true. These take effect at once.

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
| `highJump` | `heights` [2.0, 3.0, 4.0] (1.25 to 16), `chargeTimeoutTicks` 60 |
| `springy` | `minFallDistance` 3.0, `restitution` 0.75, `perfectRestitution` 0.95, `perfectWindowTicks` 3, `maxBounceSpeed` 2.0 |
| `curses` | `hideUntilHeld` true, `cursedSeconds` 60 |
| `possessionCurse` | `healthPerDurability` 0.05, `minHealth` 6, `maxHealth` 40, `healthPerHardness` 4.0, `itemHealth` 1.0, `armor` 2.0, `damageMultiplier` 1.0, `dropChance` 0.1, `dropDurability` 0.05 |
| `hauntingCurse` | `appearMinSeconds` 180, `appearMaxSeconds` 480, `stareSeconds` 30, `stareMinDistance` 12, `stareMaxDistance` 20, `approachDistance` 8, `vanishDistance` 32, `monsterRadius` 16, `buffRadius` 5, `buffLevel` 1, `buffSeconds` 15, `banishMinSeconds` 10, `banishMaxSeconds` 20, `castMinSeconds` 4, `castMaxSeconds` 10, `monsterGhosts` true, `monsterGhostCap` 4, `netherChance` 0.02 |
| `enchantmentStudy` | `studying` and `copying` each: `curseChanceMax` 0.1 (at level 0), `curseChanceMin` 0.0005, `curseChanceMinLevel` 30, `xpCost` 27, `durationTicks` 40; `treasureMultiplier` 2.0 |

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
