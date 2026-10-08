package com.linc.spectralenchantments.client;

import com.linc.spectralenchantments.curse.Curses;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * While Cursed, the drop key does nothing with a cursed item in hand. Swallowed before the key bindings are handled,
 * so the client never predicts the drop (the server refuses it anyway, see {@link Curses#onItemToss}).
 */
final class CurseInput {
    private CurseInput() {}

    static void onClientTick(ClientTickEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || !Curses.isLocked(player, player.getMainHandItem())) {
            return;
        }
        boolean refused = false;
        while (minecraft.options.keyDrop.consumeClick()) {
            refused = true;
        }
        if (refused) {
            Curses.refuse(player);
        }
    }
}
