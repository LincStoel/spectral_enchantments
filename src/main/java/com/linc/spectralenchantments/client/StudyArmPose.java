package com.linc.spectralenchantments.client;

import net.minecraft.client.model.HumanoidModel;
import net.neoforged.fml.common.asm.enumextension.EnumProxy;
import net.neoforged.neoforge.client.IArmPoseTransformer;

/**
 * The two-handed arm pose of a player studying or copying at a lectern, added to {@link HumanoidModel.ArmPose} by
 * {@code META-INF/enumextensions.json}. {@link StudyClient#poseArm} does the posing.
 */
public final class StudyArmPose {
    public static final EnumProxy<HumanoidModel.ArmPose> STUDY = new EnumProxy<>(HumanoidModel.ArmPose.class, true,
            (IArmPoseTransformer) StudyClient::poseArm);

    private StudyArmPose() {}
}
