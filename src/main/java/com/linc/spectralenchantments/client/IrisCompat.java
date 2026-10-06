package com.linc.spectralenchantments.client;

import java.lang.reflect.Method;
import javax.annotation.Nullable;
import net.neoforged.fml.ModList;

/**
 * Optional Iris integration, by reflection so Iris stays a soft dependency.
 *
 * <p>Shaderpacks such as BSL draw the glowing (emissive) ghost type opaque, ignoring its alpha, so while
 * one is active a fading ghost switches to the lit translucent type, whose alpha they keep.
 */
final class IrisCompat {
    @Nullable
    private static final Object API;
    @Nullable
    private static final Method IS_SHADER_PACK_IN_USE;

    static {
        Object api = null;
        Method inUse = null;
        if (ModList.get().isLoaded("iris")) {
            try {
                Class<?> apiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                api = apiClass.getMethod("getInstance").invoke(null);
                inUse = apiClass.getMethod("isShaderPackInUse");
            } catch (ReflectiveOperationException | LinkageError e) {
                api = null;
                inUse = null;
            }
        }
        API = api;
        IS_SHADER_PACK_IN_USE = inUse;
    }

    private IrisCompat() {}

    static boolean shaderPackInUse() {
        if (API == null || IS_SHADER_PACK_IN_USE == null) {
            return false;
        }
        try {
            return (boolean) IS_SHADER_PACK_IN_USE.invoke(API);
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }
}
