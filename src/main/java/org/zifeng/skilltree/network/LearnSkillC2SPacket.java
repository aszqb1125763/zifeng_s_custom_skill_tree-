package org.zifeng.skilltree.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.zifeng.skilltree.SkillTreeMod;
import org.zifeng.skilltree.data.PlayerSkillRecord;
import org.zifeng.skilltree.data.PlayerSkillSavedData;
import org.zifeng.skilltree.skill.SkillEffects;
import org.zifeng.skilltree.skill.Skills;

/**
 * 学习技能请求（客户端 → 服务端）：携带技能 ID 与学习级数，服务端校验后扣技能点并应用效果。
 * levels ≥ 1；Shift+点击一次加 10 级时 levels=10（服务端逐级校验，点数/上限不足自动停）。
 */
public record LearnSkillC2SPacket(String skillId, int levels) implements CustomPacketPayload {
    public static final Type<LearnSkillC2SPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkillTreeMod.MOD_ID, "learn_skill"));
    public static final StreamCodec<FriendlyByteBuf, LearnSkillC2SPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, LearnSkillC2SPacket::skillId,
            ByteBufCodecs.VAR_INT, LearnSkillC2SPacket::levels,
            LearnSkillC2SPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(LearnSkillC2SPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.flow().isServerbound() && ctx.player() instanceof ServerPlayer player) {
                PlayerSkillSavedData data = PlayerSkillSavedData.get(player.serverLevel());
                PlayerSkillRecord record = data.getOrCreatePlayer(player.getUUID());
                String skillId = packet.skillId();
                // 校验技能 ID 合法 + 级数有效（防刷包）
                int levels = Math.max(1, Math.min(100, packet.levels()));
                if (Skills.ALL_SKILLS.contains(skillId) && checkUltimateRequirements(record, skillId)) {
                    boolean learned = false;
                    for (int i = 0; i < levels; i++) {
                        if (record.learnSkill(skillId)) {
                            learned = true;
                            // 修复：升级后自动生效最高等级（否则 activeLevels 残留旧值导致加成一直是 0）
                            record.setActiveLevel(skillId, record.getLearnedPoints(skillId));
                        } else {
                            break; // 点数/上限不足停止
                        }
                    }
                    if (learned) {
                        data.setDirty();
                        SkillEffects.applyAll(player, record);
                    }
                }
                PacketDistributor.sendToPlayer(player,
                        SkillTreeDataS2CPacket.from(record));
            }
        });
    }

    /** 终极节点前置校验：前置是终极节点只需解锁（1点）；是基础/增幅技能需各投入 {@link Skills#ultimateRequirePoints()} 点 */
    private static boolean checkUltimateRequirements(PlayerSkillRecord record, String skillId) {
        if (Skills.getType(skillId) != Skills.SkillType.ULTIMATE) {
            return true;
        }
        for (String required : Skills.getUltimateRequirements(skillId)) {
            int need = Skills.getType(required) == Skills.SkillType.ULTIMATE ? 1 : Skills.ultimateRequirePoints();
            if (record.getLearnedPoints(required) < need) {
                return false;
            }
        }
        return true;
    }
}
