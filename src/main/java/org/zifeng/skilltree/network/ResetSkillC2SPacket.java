package org.zifeng.skilltree.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.zifeng.skilltree.SkillTreeMod;
import org.zifeng.skilltree.data.PlayerSkillRecord;
import org.zifeng.skilltree.data.PlayerSkillSavedData;
import org.zifeng.skilltree.event.AuraEvents;
import org.zifeng.skilltree.event.UltimateEvents;
import org.zifeng.skilltree.skill.SkillEffects;
import org.zifeng.skilltree.skill.Skills;

/**
 * 重置单个技能请求（客户端 → 服务端，技能树界面 Ctrl+R + 鼠标悬停触发）：
 * 服务端按该技能总消耗 × 返还率（Config）加回技能点并移除该技能，更新属性修饰符。
 */
public record ResetSkillC2SPacket(String skillId) implements CustomPacketPayload {
    public static final Type<ResetSkillC2SPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkillTreeMod.MOD_ID, "reset_skill"));
    public static final StreamCodec<FriendlyByteBuf, ResetSkillC2SPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ResetSkillC2SPacket::skillId,
            ResetSkillC2SPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ResetSkillC2SPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.flow().isServerbound() && ctx.player() instanceof ServerPlayer player) {
                String skillId = packet.skillId();
                if (skillId == null || !Skills.ALL_SKILLS.contains(skillId)) {
                    return;
                }
                PlayerSkillSavedData data = PlayerSkillSavedData.get(player.serverLevel());
                PlayerSkillRecord record = data.getOrCreatePlayer(player.getUUID());
                int learned = record.getLearnedPoints(skillId);
                if (learned <= 0) {
                    player.sendSystemMessage(Component.translatable("chat.zifeng_s_custom_skill_tree.reset_not_learned"));
                    return;
                }
                // 计算该技能总消耗并返还
                // ★ 2026-10-09 修复：改用 resetSkill 的【返回值】作为返还额（权威——它内部就是 totalSpent × rate），
                //   不再在外面另算一次（避免两处逻辑漂移；也顺便省一次 totalSpent 调用）
                double rate = org.zifeng.skilltree.Config.RESET_REFUND_RATE.get();
                double refund = record.resetSkill(skillId);
                data.setDirty();
                // 更新属性修饰符 + 清理终极被动临时状态
                SkillEffects.applyAll(player, record);
                // ⚠️ 顺序关键：先回收飞行权限（clearPlayerFlight 依赖 SKILL_FLIGHT_GRANTED 集合），
                //    再 clearPlayer 清状态（clearPlayer 会移除 SKILL_FLIGHT_GRANTED 条目，先清会导致无法回收）
                // 重置御空术/御空增幅 → 还原原版飞行速度（防止残留被持久化）
                if (Skills.FLY.equals(skillId) || Skills.AMP_FLY.equals(skillId)) {
                    UltimateEvents.resetFlyingSpeed(player);
                }
                // 重置宇宙的青睐 → 回收技能飞行权限（非创造才关闭）
                if (Skills.ULT_FAVOR.equals(skillId)) {
                    UltimateEvents.clearPlayerFlight(player);
                }
                UltimateEvents.clearPlayer(player);

                // ⚠️ 2026-09-20 修复（严重）：重置寰宇法则必须同时释放 gamerule 全局锁定。
                //    链路：重置时之环/晴空环 → resetSkill 把已学点数清 0 →
                //    GlobalRuleModule.activeCondition 变 false → 模块**冬眠** →
                //    AuraEvents.tickGlobalLocks 再也不会调 updateTimeLock(player,false) →
                //    timeLockCount 卡在 1（状态 map 里的条目也留着）→
                //    而**唯一**能把 RULE_DAYLIGHT 设回 true 的 restoreTimeLock 只在 count 归零时被调
                //    → doDaylightCycle / doWeatherCycle **全服永久锁死**，只能靠该玩家登出恢复。
                //    /zifengskilltree reset 命令己有同样的调用（SkillTreeAdminCommands），此处是遗漏。
                //    只在重置寰宇法则时调用：重置普通技能时调用会瞬时放锁再重锁，造成时间/天气闪动。
                if (Skills.isGlobalSkill(skillId)) {
                    AuraEvents.onPlayerLogout(player);
                }

                // 重置村庄英雄/发光/战利品爆炸/虚空之躯等被动 → 状态由事件每次 tick 按记录重判，无残留
                // ⚠️ 2026-10-09 修复（显示错误）：两条文案占位符数量不同 ——
                //    reset_full    = 2 个（技能名 / 返还点数）
                //    reset_partial = 3 个（技能名 / 返还率 / 返还点数）
                //    旧代码【统一传 3 个参数】→ 全额返还时第 2 个 %s 吃到了 rate*100（=100），
                //    导致无论实际返还多少，永远显示「全额返还 100 技能点」（默认返还率就是 1.0）。
                //    现按分支各传对应数量，金额用 resetSkill 的返回值（与真正入账数额完全一致）。
                player.sendSystemMessage(rate >= 1.0
                        ? Component.translatable("chat.zifeng_s_custom_skill_tree.reset_full",
                                Skills.getDisplayNameComponent(skillId), fmtPoints(refund))
                        : Component.translatable("chat.zifeng_s_custom_skill_tree.reset_partial",
                                Skills.getDisplayNameComponent(skillId),
                                String.format("%.0f", rate * 100), fmtPoints(refund)));
                org.zifeng.skilltree.network.ModNetwork.sendToPlayer(player,
                        SkillTreeDataS2CPacket.from(record));
            }
        });
    }

    /**
     * 技能点数值显示（★ 2026-10-09）：整数不拖小数尾巴（1000 而非 1000.0），小数保留 1 位（1234.5）。
     * <p>⚠️ <b>不把小数四舍五入成整数</b> —— 避免显示值与真实入账不符。
     */
    private static String fmtPoints(double v) {
        if (!Double.isFinite(v)) {
            return String.format("%.1f", v);
        }
        return v == Math.rint(v) ? String.format("%.0f", v) : String.format("%.1f", v);
    }
}
