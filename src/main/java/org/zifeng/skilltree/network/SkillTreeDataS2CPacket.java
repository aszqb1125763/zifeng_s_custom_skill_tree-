package org.zifeng.skilltree.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.zifeng.skilltree.SkillTreeMod;
import org.zifeng.skilltree.client.screen.SkillTreeScreen;

import java.util.HashMap;
import java.util.Map;

/**
 * 技能树数据（服务端 → 客户端）：技能点 + 已学 + 开关 + 生效等级 + 光环状态。
 */
public record SkillTreeDataS2CPacket(double skillPoints, Map<String, Integer> learnedSkills, Map<String, Boolean> toggles,
                                     Map<String, Integer> activeLevels, boolean auraEnabled, int auraTargetMode) implements CustomPacketPayload {
    public static final Type<SkillTreeDataS2CPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkillTreeMod.MOD_ID, "skill_tree_data"));
    public static final StreamCodec<FriendlyByteBuf, SkillTreeDataS2CPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, SkillTreeDataS2CPacket::skillPoints,
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_INT), SkillTreeDataS2CPacket::learnedSkills,
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.BOOL), SkillTreeDataS2CPacket::toggles,
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_INT), SkillTreeDataS2CPacket::activeLevels,
            ByteBufCodecs.BOOL, SkillTreeDataS2CPacket::auraEnabled,
            ByteBufCodecs.VAR_INT, SkillTreeDataS2CPacket::auraTargetMode,
            SkillTreeDataS2CPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** 从玩家技能记录构建数据包（统一便捷入口） */
    public static SkillTreeDataS2CPacket from(org.zifeng.skilltree.data.PlayerSkillRecord record) {
        // 杀戮光环实际状态 = 伤害/速度【已学】且开启（K 键控制；isEnabled 默认 true，必须配合已学判断）
        boolean auraOn = (record.getLearnedPoints(org.zifeng.skilltree.skill.Skills.AURA_DAMAGE) > 0
                && record.isEnabled(org.zifeng.skilltree.skill.Skills.AURA_DAMAGE))
                || (record.getLearnedPoints(org.zifeng.skilltree.skill.Skills.AURA_SPEED) > 0
                && record.isEnabled(org.zifeng.skilltree.skill.Skills.AURA_SPEED));
        return new SkillTreeDataS2CPacket(record.getSkillPoints(), record.getLearnedSkills(), record.getToggles(),
                record.getActiveLevels(), auraOn, record.getAuraTargetMode());
    }

    public static void handle(SkillTreeDataS2CPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.flow().isClientbound()) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player == null) {
                    return;
                }
                // 校准光环目标模式本地状态（L 键循环 / UI Shift+右键切换都用它，防切换顺序错乱）
                org.zifeng.skilltree.client.ModKeyBindingEvents.setLastMode(packet.auraTargetMode());
                // 校准光环总开关本地状态（圆环渲染器判断是否显示淡红光环）
                org.zifeng.skilltree.client.ModKeyBindingEvents.setAuraEnabledClient(packet.auraEnabled());
                // 校准光环技能开关缓存（独立快捷键取反发送用）
                org.zifeng.skilltree.client.ModKeyBindingEvents.updateAuraToggles(packet.toggles());
                // 校准杀戮光环已学状态（圆环渲染防重置残留）
                org.zifeng.skilltree.client.ModKeyBindingEvents.updateAuraLearned(packet.learnedSkills());
                // 校准磁力光环已学状态（蓝色圆环显示用）
                org.zifeng.skilltree.client.ModKeyBindingEvents.setMagnetLearnedClient(
                        packet.learnedSkills().getOrDefault(org.zifeng.skilltree.skill.Skills.AURA_MAGNET, 0) > 0);
                // 只在技能树界面已打开时更新数据，绝不强制打开界面
                // （否则 K/L 键切换光环/目标时回发的数据包会把技能树界面弹出来）
                if (mc.screen instanceof SkillTreeScreen screen) {
                    screen.updateData(packet.skillPoints(), packet.learnedSkills(), packet.toggles(), packet.activeLevels(), packet.auraEnabled(), packet.auraTargetMode());
                }
            }
        });
    }
}
