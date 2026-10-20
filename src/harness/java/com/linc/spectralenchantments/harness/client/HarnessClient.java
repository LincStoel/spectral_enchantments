package com.linc.spectralenchantments.harness.client;

import com.linc.spectralenchantments.harness.HarnessMod;
import com.linc.spectralenchantments.harness.HarnessNet;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import com.linc.spectralenchantments.animated.AnimatedWeaponEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Bot-client half of the harness. Instructions (action, arg):
 * <ul>
 *   <li>{@code mine "x y z face"}: hold attack on that block until it breaks (or {@code stop})</li>
 *   <li>{@code stop}: release attack, aborting the current mining</li>
 *   <li>{@code sneak on|off}, {@code use on|off} (hold use), {@code hud on|off}, {@code iris on|off}</li>
 *   <li>{@code slot n}: select hotbar slot n</li>
 *   <li>{@code move walk|sprint|stop}: hold forward (and sprint)</li>
 *   <li>{@code shot name}: screenshot to screenshots/name.png</li>
 *   <li>{@code probe label}: append Broad Breaker / Animated companion / Iris client state to harness-probe.txt</li>
 * </ul>
 * Everything notable is appended to harness-events.txt in the game directory.
 */
@EventBusSubscriber(modid = HarnessMod.MOD_ID, value = Dist.CLIENT)
public final class HarnessClient {
    @Nullable
    private static BlockPos miningPos;
    private static Direction miningFace = Direction.NORTH;
    private static int miningTicks;
    private static boolean trace;

    private HarnessClient() {}

    public static void handle(HarnessNet.Instruct payload) {
        Minecraft mc = Minecraft.getInstance();
        String arg = payload.arg();
        switch (payload.action()) {
            case "mine" -> {
                String[] p = arg.trim().split("\\s+");
                miningPos = new BlockPos(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
                miningFace = Direction.byName(p[3]);
                miningTicks = 0;
                event("mine-start " + arg);
            }
            case "stop" -> {
                if (miningPos != null && mc.gameMode != null) {
                    mc.gameMode.stopDestroyBlock();
                }
                event("mine-stopped after " + miningTicks + " ticks");
                miningPos = null;
            }
            case "trace" -> trace = arg.equals("on");
            case "sneak" -> mc.options.keyShift.setDown(arg.equals("on"));
            case "use" -> mc.options.keyUse.setDown(arg.equals("on"));
            case "hud" -> mc.options.hideGui = arg.equals("off");
            case "inventory" -> mc.setScreen(arg.equals("open") && mc.player != null ? new InventoryScreen(mc.player) : null);
            case "slot" -> {
                if (mc.player != null) {
                    mc.player.getInventory().selected = Integer.parseInt(arg.trim());
                }
            }
            case "move" -> {
                mc.options.keyUp.setDown(!arg.equals("stop"));
                mc.options.keySprint.setDown(arg.equals("sprint"));
                if (mc.player != null) {
                    mc.player.setSprinting(arg.equals("sprint"));
                }
            }
            case "iris" -> event("iris " + arg + " -> " + setIris(arg.equals("on")));
            case "shot" -> Screenshot.grab(mc.gameDirectory, arg + ".png", mc.getMainRenderTarget(),
                    message -> event("shot " + arg + ": " + message.getString()));
            case "probe" -> probe(mc, arg);
            default -> event("unknown action " + payload.action());
        }
    }

    /** Called from Minecraft#continueAttack; returns true when the harness handled this tick's attack. */
    public static boolean holdAttack(Minecraft mc) {
        if (miningPos == null || mc.level == null || mc.player == null || mc.gameMode == null) {
            return false;
        }
        if (mc.level.getBlockState(miningPos).isAir()) {
            event("mine-done " + miningPos.toShortString() + " in " + miningTicks + " ticks");
            miningPos = null;
            return false;
        }
        miningTicks++;
        boolean ok = mc.gameMode.continueDestroyBlock(miningPos, miningFace);
        if (ok) {
            mc.player.swing(InteractionHand.MAIN_HAND);
        }
        if (trace) {
            event("tick " + miningTicks + " ok=" + ok + " destroying=" + mc.gameMode.isDestroying()
                    + " pos=" + mc.gameMode.destroyBlockPos.toShortString() + " progress=" + mc.gameMode.destroyProgress
                    + " player=" + mc.player.blockPosition().toShortString());
        }
        return true;
    }

    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGHEST, receiveCanceled = true)
    public static void onLeftClickFirst(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.LeftClickBlock event) {
        if (trace && event.getLevel().isClientSide()) {
            event("client-leftclick-first " + event.getAction() + " " + event.getPos().toShortString() + " canceled=" + event.isCanceled());
        }
    }

    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST, receiveCanceled = true)
    public static void onLeftClickLast(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.LeftClickBlock event) {
        if (trace && event.getLevel().isClientSide()) {
            event("client-leftclick-last " + event.getAction() + " " + event.getPos().toShortString() + " canceled=" + event.isCanceled()
                    + " useItem=" + event.getUseItem() + " useBlock=" + event.getUseBlock());
        }
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (miningPos != null && mc.level != null && mc.level.getBlockState(miningPos).isAir()) {
            holdAttack(mc); // logs completion even if continueAttack isn't reached this tick
        }
    }

    private static void probe(Minecraft mc, String label) {
        StringBuilder out = new StringBuilder(label);
        out.append(" | irisPackInUse=").append(irisCall("isShaderPackInUse"));
        out.append(" | spectralSessions=").append(sessionCount());
        out.append(" | spectralCracks=").append(spectralCrackCount(mc));
        out.append(" | companions=").append(companions(mc));
        out.append(" | studying=").append(studyCount());
        out.append(" | actionbar=").append(actionBar(mc));
        if (mc.gameMode != null) {
            out.append(" | destroying=").append(mc.gameMode.isDestroying());
        }
        if (mc.player != null) {
            out.append(" | mainhand=").append(mc.player.getMainHandItem().getItem())
                    .append(" damage=").append(mc.player.getMainHandItem().getDamageValue());
        }
        out.append(" | fps=").append(mc.getFps());
        append("harness-probe.txt", out.toString());
    }

    /** Players this client shows studying or copying at a lectern. */
    private static Object studyCount() {
        try {
            Field f = Class.forName("com.linc.spectralenchantments.client.StudyClient").getDeclaredField("ACTIVE");
            f.setAccessible(true);
            return ((Map<?, ?>) f.get(null)).size();
        } catch (ReflectiveOperationException e) {
            return "n/a (" + e + ")";
        }
    }

    /** The action bar's text, if it is showing. */
    private static String actionBar(Minecraft mc) {
        try {
            Field text = mc.gui.getClass().getDeclaredField("overlayMessageString");
            Field time = mc.gui.getClass().getDeclaredField("overlayMessageTime");
            text.setAccessible(true);
            time.setAccessible(true);
            Object message = text.get(mc.gui);
            return (int) time.get(mc.gui) > 0 && message != null ? "'" + ((net.minecraft.network.chat.Component) message).getString() + "'" : "''";
        } catch (ReflectiveOperationException e) {
            return "n/a (" + e + ")";
        }
    }

    private static Object sessionCount() {
        try {
            Field f = Class.forName("com.linc.spectralenchantments.client.SpectralSessions").getDeclaredField("SESSIONS");
            f.setAccessible(true);
            return ((Map<?, ?>) f.get(null)).size();
        } catch (ReflectiveOperationException e) {
            return "n/a (" + e + ")";
        }
    }

    /** Animated weapons fighting beside their players that this client knows about: item, slot, side, target, engage and swing state. */
    private static String companions(Minecraft mc) {
        if (mc.level == null) {
            return "[]";
        }
        StringBuilder out = new StringBuilder("[");
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof AnimatedWeaponEntity weapon && weapon.isCompanion()) {
                Player owner = weapon.getOwner();
                out.append(owner != null ? owner.getName().getString() : "?").append(':').append(weapon.getItem().getItem())
                        .append(" slot=").append(weapon.getSlot())
                        .append(owner != null ? sideOf(owner, weapon) : " ?")
                        .append(" target=").append(weapon.hasTarget())
                        .append(" engage=").append(String.format("%.2f", weapon.engage))
                        .append(" tilt=").append(String.format("%.1f", weapon.tilt))
                        .append(" style=").append(weapon.attackStyle)
                        .append(" dist=").append(owner != null ? String.format("%.2f", weapon.distanceTo(owner)) : "?")
                        .append("; ");
            }
        }
        return out.append(']').toString();
    }

    /** Which side of its owner (by body facing) the weapon floats on. */
    private static String sideOf(Player owner, Entity weapon) {
        double yaw = Math.toRadians(owner.yBodyRot);
        double forwardX = -Math.sin(yaw);
        double forwardZ = Math.cos(yaw);
        double right = (weapon.getX() - owner.getX()) * -forwardZ + (weapon.getZ() - owner.getZ()) * forwardX;
        return right > 0 ? " right" : " left";
    }

    /** Crack overlays registered under Broad Breaker's negative breaker ids. */
    private static Object spectralCrackCount(Minecraft mc) {
        try {
            Field f = mc.levelRenderer.getClass().getDeclaredField("destroyingBlocks");
            f.setAccessible(true);
            return ((Map<?, ?>) f.get(mc.levelRenderer)).keySet().stream().filter(k -> ((Integer) k) < 0).count();
        } catch (ReflectiveOperationException e) {
            return "n/a (" + e + ")";
        }
    }

    private static Object irisCall(String method) {
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object instance = api.getMethod("getInstance").invoke(null);
            return api.getMethod(method).invoke(instance);
        } catch (ReflectiveOperationException e) {
            return "no-iris";
        }
    }

    private static Object setIris(boolean on) {
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object config = api.getMethod("getConfig").invoke(api.getMethod("getInstance").invoke(null));
            Method set = Class.forName("net.irisshaders.iris.api.v0.IrisApiConfig").getMethod("setShadersEnabledAndApply", boolean.class);
            set.invoke(config, on);
            return irisCall("isShaderPackInUse");
        } catch (ReflectiveOperationException e) {
            return "failed: " + e;
        }
    }

    private static void event(String text) {
        append("harness-events.txt", text);
    }

    private static void append(String file, String line) {
        try {
            Files.writeString(Minecraft.getInstance().gameDirectory.toPath().resolve(file),
                    System.currentTimeMillis() + " " + line + System.lineSeparator(),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
        }
    }
}
