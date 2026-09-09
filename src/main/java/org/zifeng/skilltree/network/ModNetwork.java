package org.zifeng.skilltree.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.zifeng.skilltree.SkillTreeMod;

/**
 * 网络注册（NeoForge PayloadRegistrar）。
 * <p>v1-3 收编（2026-09-09）：补 sendToServer/sendToPlayer 薄封装，与 1.20.1 调用点形态对齐——
 * 全工程发包统一走本类，升级新 MC 版本时只需改这里 + 各包外壳。</p>
 */
public class ModNetwork {

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(SkillTreeMod.MOD_ID);
        registrar.playToServer(OpenSkillTreeC2SPacket.TYPE, OpenSkillTreeC2SPacket.STREAM_CODEC, OpenSkillTreeC2SPacket::handle);
        registrar.playToServer(BlinkC2SPacket.TYPE, BlinkC2SPacket.STREAM_CODEC, BlinkC2SPacket::handle);
        registrar.playToServer(ContainerHaulC2SPacket.TYPE, ContainerHaulC2SPacket.STREAM_CODEC, ContainerHaulC2SPacket::handle);
        registrar.playToClient(SkillTreeDataS2CPacket.TYPE, SkillTreeDataS2CPacket.STREAM_CODEC, SkillTreeDataS2CPacket::handle);
        registrar.playToClient(ReviveCooldownS2CPacket.TYPE, ReviveCooldownS2CPacket.STREAM_CODEC, ReviveCooldownS2CPacket::handle);
        registrar.playToClient(SkillPointRateS2CPacket.TYPE, SkillPointRateS2CPacket.STREAM_CODEC, SkillPointRateS2CPacket::handle);
        registrar.playToServer(LearnSkillC2SPacket.TYPE, LearnSkillC2SPacket.STREAM_CODEC, LearnSkillC2SPacket::handle);
        registrar.playToServer(SetSkillToggleC2SPacket.TYPE, SetSkillToggleC2SPacket.STREAM_CODEC, SetSkillToggleC2SPacket::handle);
        registrar.playToServer(AuraTargetC2SPacket.TYPE, AuraTargetC2SPacket.STREAM_CODEC, AuraTargetC2SPacket::handle);
        registrar.playToServer(SetSkillLevelC2SPacket.TYPE, SetSkillLevelC2SPacket.STREAM_CODEC, SetSkillLevelC2SPacket::handle);
        registrar.playToServer(ToggleAuraC2SPacket.TYPE, ToggleAuraC2SPacket.STREAM_CODEC, ToggleAuraC2SPacket::handle);
        registrar.playToServer(ToggleMagnetC2SPacket.TYPE, ToggleMagnetC2SPacket.STREAM_CODEC, ToggleMagnetC2SPacket::handle);
        registrar.playToServer(ToggleLockC2SPacket.TYPE, ToggleLockC2SPacket.STREAM_CODEC, ToggleLockC2SPacket::handle);
        registrar.playToServer(ResetSkillC2SPacket.TYPE, ResetSkillC2SPacket.STREAM_CODEC, ResetSkillC2SPacket::handle);
        registrar.playToServer(ConverterUnlimitedC2SPacket.TYPE, ConverterUnlimitedC2SPacket.STREAM_CODEC, ConverterUnlimitedC2SPacket::handle);
        registrar.playToServer(ConverterRateC2SPacket.TYPE, ConverterRateC2SPacket.STREAM_CODEC, ConverterRateC2SPacket::handle);
        registrar.playToServer(WeatherModeC2SPacket.TYPE, WeatherModeC2SPacket.STREAM_CODEC, WeatherModeC2SPacket::handle);
        registrar.playToServer(StickToolC2SPacket.TYPE, StickToolC2SPacket.STREAM_CODEC, StickToolC2SPacket::handle); // 木棍工具层（2026-09-08）
        registrar.playToServer(ZoneC2SPacket.TYPE, ZoneC2SPacket.STREAM_CODEC, ZoneC2SPacket::handle); // 机械共鸣·操作区（2026-09-08）
        registrar.playToClient(GlobalStateS2CPacket.TYPE, GlobalStateS2CPacket.STREAM_CODEC, GlobalStateS2CPacket::handle);
        registrar.playToClient(SkillPointDeltaS2CPacket.TYPE, SkillPointDeltaS2CPacket.STREAM_CODEC, SkillPointDeltaS2CPacket::handle);
        // 磁铁屏蔽区（2026-09-07）：C2S 加/删区，S2C 回发当前区列表
        registrar.playToServer(MagnetExclusionC2SPacket.TYPE, MagnetExclusionC2SPacket.STREAM_CODEC, MagnetExclusionC2SPacket::handle);
        registrar.playToClient(MagnetExclusionS2CPacket.TYPE, MagnetExclusionS2CPacket.STREAM_CODEC, MagnetExclusionS2CPacket::handle);
    }

    /** 服务端 → 指定玩家 */
    public static void sendToPlayer(ServerPlayer player, net.minecraft.network.protocol.common.custom.CustomPacketPayload packet) {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, packet);
    }

    /** 客户端 → 服务端 */
    public static void sendToServer(net.minecraft.network.protocol.common.custom.CustomPacketPayload packet) {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(packet);
    }
}
