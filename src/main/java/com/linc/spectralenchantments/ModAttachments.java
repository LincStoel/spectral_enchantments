package com.linc.spectralenchantments;

import com.mojang.serialization.Codec;
import java.util.function.Supplier;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, SpectralEnchantments.MOD_ID);

    /**
     * A player's enchantment study seed, like vanilla's enchanting seed: it picks what studying or copying a given
     * item gives, and only changes once a study or copy is done, so letting go and trying again changes nothing.
     * 0 until first used.
     */
    public static final Supplier<AttachmentType<Integer>> STUDY_SEED = ATTACHMENT_TYPES.register("study_seed",
            () -> AttachmentType.builder(() -> 0).serialize(Codec.INT).copyOnDeath().build());

    private ModAttachments() {}
}
