package org.zifeng.skilltree.network;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.zifeng.skilltree.SkillTreeMod;

/**
 * 网络注册（NeoForge PayloadRegistrar）。
 */
public class ModNetwork {

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(SkillTreeMod.MOD_ID);
        registrar.playToServer(OpenSkillTreeC2SPacket.TYPE, OpenSkillTreeC2SPacket.STREAM_CODEC, OpenSkillTreeC2SPacket::handle);
        registrar.playToClient(SkillTreeDataS2CPacket.TYPE, SkillTreeDataS2CPacket.STREAM_CODEC, SkillTreeDataS2CPacket::handle);
        registrar.playToClient(ReviveCooldownS2CPacket.TYPE, ReviveCooldownS2CPacket.STREAM_CODEC, ReviveCooldownS2CPacket::handle);
        registrar.playToServer(LearnSkillC2SPacket.TYPE, LearnSkillC2SPacket.STREAM_CODEC, LearnSkillC2SPacket::handle);
        registrar.playToServer(SetSkillToggleC2SPacket.TYPE, SetSkillToggleC2SPacket.STREAM_CODEC, SetSkillToggleC2SPacket::handle);
        registrar.playToServer(AuraTargetC2SPacket.TYPE, AuraTargetC2SPacket.STREAM_CODEC, AuraTargetC2SPacket::handle);
        registrar.playToServer(SetSkillLevelC2SPacket.TYPE, SetSkillLevelC2SPacket.STREAM_CODEC, SetSkillLevelC2SPacket::handle);
        registrar.playToServer(ToggleAuraC2SPacket.TYPE, ToggleAuraC2SPacket.STREAM_CODEC, ToggleAuraC2SPacket::handle);
        registrar.playToServer(ToggleMagnetC2SPacket.TYPE, ToggleMagnetC2SPacket.STREAM_CODEC, ToggleMagnetC2SPacket::handle);
        registrar.playToServer(ToggleLockC2SPacket.TYPE, ToggleLockC2SPacket.STREAM_CODEC, ToggleLockC2SPacket::handle);
        registrar.playToServer(ResetSkillC2SPacket.TYPE, ResetSkillC2SPacket.STREAM_CODEC, ResetSkillC2SPacket::handle);
        registrar.playToServer(SetConverterRateC2SPacket.TYPE, SetConverterRateC2SPacket.STREAM_CODEC, SetConverterRateC2SPacket::handle);
    }
}
