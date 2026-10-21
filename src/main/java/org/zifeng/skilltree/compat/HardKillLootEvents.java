package org.zifeng.skilltree.compat;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingDeathEvent;

/**
 * 死亡兜底：给「硬杀补掉落」加一条不依赖具体击杀通道的入口（2026-10-21）。
 *
 * <p>原来只在 {@code AuraEvents#voidSpearKill} 里补发，但盖亚也可能被
 * <b>杀戮光环的普通伤害</b>打死（不是虚空诛灭那条硬杀分支）——
 * 那条路走的是原版 {@code hurt()} + {@code die()}，而 Botania 在召唤阶段
 * （{@code mobSpawnTicks > 0}）的 {@code getLootTable()} 返回 EMPTY，
 * 原版直接 return，一颗都不掉。所以这里在死亡事件上兜底：
 * 命中配置 + 原版掉落表为空（= 原版本来就不会掉）→ 按战利品表补一次。
 *
 * <p>⚠️ 只在「原版掉落表为空」时补，正常掉的 Boss 不会被重复补。
 */
public final class HardKillLootEvents {

    private HardKillLootEvents() {
    }

    public static void onDeath(LivingDeathEvent event) {
        final LivingEntity entity = event.getEntity();
        if (entity == null || entity.level().isClientSide() || !(entity.level() instanceof ServerLevel level)) {
            return;
        }
        if (event.getSource() == null) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof ServerPlayer killer)) {
            return;
        }
        // 技能门槛：只有学了【杀戮光环】的玩家才有这条支线
        if (!HardKillLootGrant.hasBranchSkill(killer, level)) {
            return;
        }
        // 不在补掉落配置里的实体 → 完全不管（原版自己处理）
        if (!HardKillLootGrant.matchesConfig(entity)) {
            return;
        }
        // ① 首选：解除「召唤阶段掉落锁」，让实体走它自己的死亡掉落逻辑
        //    —— 这样多人分摊、作者硬编码的掉落、战利品大爆发、挪移术全部由原版链路自然处理
        if (HardKillLootGrant.tryReleaseSpawnLock(entity)) {
            return;
        }
        // ② 兜底：解锁不了（机制不同/没有参战名单）→ 手动抽表补一份
        HardKillLootGrant.grantIfConfigured(killer, entity);
    }
}
