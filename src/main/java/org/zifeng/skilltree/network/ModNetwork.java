package org.zifeng.skilltree.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import org.zifeng.skilltree.SkillTreeMod;

import java.util.Optional;

/**
 * 网络注册（Forge 1.20.1 SimpleChannel）。
 */
public class ModNetwork {
    public static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(SkillTreeMod.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);
    private static int id = 0;

    public static void register() {
        // ===== 客户端 → 服务端 =====
        CHANNEL.messageBuilder(OpenSkillTreeC2SPacket.class, id++)
                .encoder(OpenSkillTreeC2SPacket::encode)
                .decoder(OpenSkillTreeC2SPacket::decode)
                .consumerMainThread(OpenSkillTreeC2SPacket::handle)
                .add();
        CHANNEL.messageBuilder(BlinkC2SPacket.class, id++)
                .encoder(BlinkC2SPacket::encode)
                .decoder(BlinkC2SPacket::decode)
                .consumerMainThread(BlinkC2SPacket::handle)
                .add();
        CHANNEL.messageBuilder(ContainerHaulC2SPacket.class, id++)
                .encoder(ContainerHaulC2SPacket::encode)
                .decoder(ContainerHaulC2SPacket::decode)
                .consumerMainThread(ContainerHaulC2SPacket::handle)
                .add();
        CHANNEL.messageBuilder(LearnSkillC2SPacket.class, id++)
                .encoder(LearnSkillC2SPacket::encode)
                .decoder(LearnSkillC2SPacket::decode)
                .consumerMainThread(LearnSkillC2SPacket::handle)
                .add();
        CHANNEL.messageBuilder(SetSkillToggleC2SPacket.class, id++)
                .encoder(SetSkillToggleC2SPacket::encode)
                .decoder(SetSkillToggleC2SPacket::decode)
                .consumerMainThread(SetSkillToggleC2SPacket::handle)
                .add();
        CHANNEL.messageBuilder(SetSkillLevelC2SPacket.class, id++)
                .encoder(SetSkillLevelC2SPacket::encode)
                .decoder(SetSkillLevelC2SPacket::decode)
                .consumerMainThread(SetSkillLevelC2SPacket::handle)
                .add();
        CHANNEL.messageBuilder(AuraTargetC2SPacket.class, id++)
                .encoder(AuraTargetC2SPacket::encode)
                .decoder(AuraTargetC2SPacket::decode)
                .consumerMainThread(AuraTargetC2SPacket::handle)
                .add();
        CHANNEL.messageBuilder(ToggleAuraC2SPacket.class, id++)
                .encoder(ToggleAuraC2SPacket::encode)
                .decoder(ToggleAuraC2SPacket::decode)
                .consumerMainThread(ToggleAuraC2SPacket::handle)
                .add();
        CHANNEL.messageBuilder(ToggleMagnetC2SPacket.class, id++)
                .encoder(ToggleMagnetC2SPacket::encode)
                .decoder(ToggleMagnetC2SPacket::decode)
                .consumerMainThread(ToggleMagnetC2SPacket::handle)
                .add();
        CHANNEL.messageBuilder(ToggleLockC2SPacket.class, id++)
                .encoder(ToggleLockC2SPacket::encode)
                .decoder(ToggleLockC2SPacket::decode)
                .consumerMainThread(ToggleLockC2SPacket::handle)
                .add();
        CHANNEL.messageBuilder(ResetSkillC2SPacket.class, id++)
                .encoder(ResetSkillC2SPacket::encode)
                .decoder(ResetSkillC2SPacket::decode)
                .consumerMainThread(ResetSkillC2SPacket::handle)
                .add();
        CHANNEL.messageBuilder(ConverterUnlimitedC2SPacket.class, id++)
                .encoder(ConverterUnlimitedC2SPacket::encode)
                .decoder(ConverterUnlimitedC2SPacket::decode)
                .consumerMainThread(ConverterUnlimitedC2SPacket::handle)
                .add();
        CHANNEL.messageBuilder(ConverterRateC2SPacket.class, id++)
                .encoder(ConverterRateC2SPacket::encode)
                .decoder(ConverterRateC2SPacket::decode)
                .consumerMainThread(ConverterRateC2SPacket::handle)
                .add();
        CHANNEL.messageBuilder(WeatherModeC2SPacket.class, id++)
                .encoder(WeatherModeC2SPacket::encode)
                .decoder(WeatherModeC2SPacket::decode)
                .consumerMainThread(WeatherModeC2SPacket::handle)
                .add();
        // 磁铁屏蔽区（2026-09-07）：C2S 加/删区
        CHANNEL.messageBuilder(MagnetExclusionC2SPacket.class, id++)
                .encoder(MagnetExclusionC2SPacket::encode)
                .decoder(MagnetExclusionC2SPacket::decode)
                .consumerMainThread(MagnetExclusionC2SPacket::handle)
                .add();
        // 木棍工具层（2026-09-08）：C2S 总开关/模式
        CHANNEL.messageBuilder(StickToolC2SPacket.class, id++)
                .encoder(StickToolC2SPacket::encode)
                .decoder(StickToolC2SPacket::decode)
                .consumerMainThread(StickToolC2SPacket::handle)
                .add();
        // 机械共鸣·操作区（2026-09-08）：C2S 框选设置/清除/触发放置挖掘
        CHANNEL.messageBuilder(ZoneC2SPacket.class, id++)
                .encoder(ZoneC2SPacket::encode)
                .decoder(ZoneC2SPacket::decode)
                .consumerMainThread(ZoneC2SPacket::handle)
                .add();
        // ===== 服务端 → 客户端 =====
        CHANNEL.messageBuilder(SkillTreeDataS2CPacket.class, id++)
                .encoder(SkillTreeDataS2CPacket::encode)
                .decoder(SkillTreeDataS2CPacket::decode)
                .consumerMainThread(SkillTreeDataS2CPacket::handle)
                .add();
        CHANNEL.messageBuilder(ReviveCooldownS2CPacket.class, id++)
                .encoder(ReviveCooldownS2CPacket::encode)
                .decoder(ReviveCooldownS2CPacket::decode)
                .consumerMainThread(ReviveCooldownS2CPacket::handle)
                .add();
        CHANNEL.messageBuilder(SkillPointRateS2CPacket.class, id++)
                .encoder(SkillPointRateS2CPacket::encode)
                .decoder(SkillPointRateS2CPacket::decode)
                .consumerMainThread(SkillPointRateS2CPacket::handle)
                .add();
        CHANNEL.messageBuilder(GlobalStateS2CPacket.class, id++)
                .encoder(GlobalStateS2CPacket::encode)
                .decoder(GlobalStateS2CPacket::decode)
                .consumerMainThread(GlobalStateS2CPacket::handle)
                .add();
        CHANNEL.messageBuilder(SkillPointDeltaS2CPacket.class, id++)
                .encoder(SkillPointDeltaS2CPacket::encode)
                .decoder(SkillPointDeltaS2CPacket::decode)
                .consumerMainThread(SkillPointDeltaS2CPacket::handle)
                .add();
        // 磁铁屏蔽区列表同步（2026-09-07）：S2C 回发
        CHANNEL.messageBuilder(MagnetExclusionS2CPacket.class, id++)
                .encoder(MagnetExclusionS2CPacket::encode)
                .decoder(MagnetExclusionS2CPacket::decode)
                .consumerMainThread(MagnetExclusionS2CPacket::handle)
                .add();
    }

    /** 服务端 → 指定玩家 */
    public static void sendToPlayer(ServerPlayer player, Object packet) {
        CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player), packet);
    }

    /** 客户端 → 服务端 */
    public static void sendToServer(Object packet) {
        CHANNEL.sendToServer(packet);
    }
}
