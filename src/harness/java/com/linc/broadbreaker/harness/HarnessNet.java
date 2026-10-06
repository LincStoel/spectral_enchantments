package com.linc.broadbreaker.harness;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Server → bot client instructions. */
public final class HarnessNet {
    public record Instruct(String action, String arg) implements CustomPacketPayload {
        public static final Type<Instruct> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HarnessMod.MOD_ID, "instruct"));
        public static final StreamCodec<ByteBuf, Instruct> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Instruct::action,
                ByteBufCodecs.STRING_UTF8, Instruct::arg,
                Instruct::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private HarnessNet() {}

    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(HarnessMod.MOD_ID).versioned("1").optional().playToClient(Instruct.TYPE, Instruct.STREAM_CODEC,
                (payload, context) -> {
                    if (FMLEnvironment.dist.isClient()) {
                        com.linc.broadbreaker.harness.client.HarnessClient.handle(payload);
                    }
                });
    }
}
