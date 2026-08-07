package org.zifeng.skilltree.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.zifeng.skilltree.SkillTreeMod;
import org.zifeng.skilltree.data.PlayerSkillRecord;
import org.zifeng.skilltree.data.PlayerSkillSavedData;
import org.zifeng.skilltree.entity.AuraSwordEntity;
import org.zifeng.skilltree.skill.SkillEffects;
import org.zifeng.skilltree.skill.Skills;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 杀戮光环（AURA，独立系统，不受属性加成，由 SkillTreeMod 手动注册）：
 * <ul>
 *   <li>实体管理：按武器等级生成/销毁环绕钻石剑实体</li>
 *   <li>自动攻击：按速度等级的攻击频率攻击 20 格内目标，按目标模式过滤（敌对/友好/所有）</li>
 *   <li>伤害 = 基础 1 + 伤害等级×0.5</li>
 * </ul>
 */
public class AuraEvents {

    private static final double ATTACK_RADIUS = 20.0;
    private static final String SWORD_IDS_KEY = "zifeng_aura_sword_ids";

    /** 目标模式 */
    public static final int MODE_HOSTILE = 0;
    public static final int MODE_FRIENDLY = 1;
    public static final int MODE_ALL = 2;

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerSkillRecord record = getRecord(player);
            if (!record.isAuraEnabled()) {
                // 光环总开关关闭：清空环绕剑，不攻击
                clearSwords(player);
                return;
            }
            manageSwords(player, record);
            auraAttack(player, record);
        }
    }

    /** 清除玩家所有环绕剑（供关闭光环时立即调用） */
    public static void clearSwords(ServerPlayer player) {
        int[] existing = player.getPersistentData().getIntArray(SWORD_IDS_KEY);
        for (int id : existing) {
            Entity e = player.level().getEntity(id);
            if (e instanceof AuraSwordEntity sword) {
                sword.discard();
            }
        }
        player.getPersistentData().putIntArray(SWORD_IDS_KEY, new int[0]);
    }

    // ============ 钻石剑实体管理 ============

    private static void manageSwords(ServerPlayer player, PlayerSkillRecord record) {
        int desired = record.isEnabled(Skills.AURA_WEAPON) ? SkillEffects.getAuraSwordCount(record) : 0;
        int[] existing = player.getPersistentData().getIntArray(SWORD_IDS_KEY);

        List<AuraSwordEntity> alive = new ArrayList<>();
        for (int id : existing) {
            Entity e = player.level().getEntity(id);
            if (e instanceof AuraSwordEntity sword && player.getUUID().equals(sword.getOwnerUuid())) {
                alive.add(sword);
            }
        }
        // 数量超过需求 → 销毁多余的
        while (alive.size() > desired) {
            AuraSwordEntity extra = alive.remove(alive.size() - 1);
            extra.discard();
        }
        // 需求超过现有 → 创建补齐
        while (alive.size() < desired) {
            AuraSwordEntity sword = new AuraSwordEntity(player.level(), player.getUUID(), alive.size());
            sword.setPos(player.getX(), player.getY() + 1.2, player.getZ());
            player.level().addFreshEntity(sword);
            alive.add(sword);
        }
        // 更新持久化 ID 列表
        int[] ids = alive.stream().mapToInt(Entity::getId).toArray();
        player.getPersistentData().putIntArray(SWORD_IDS_KEY, ids);
    }

    // ============ 自动攻击 ============

    private static void auraAttack(ServerPlayer player, PlayerSkillRecord record) {
        int weaponLevel = record.getLearnedPoints(Skills.AURA_WEAPON);
        int damageLevel = record.getLearnedPoints(Skills.AURA_DAMAGE);
        int speedLevel = record.getLearnedPoints(Skills.AURA_SPEED);
        if (weaponLevel <= 0 && damageLevel <= 0) {
            return;
        }
        if (!record.isEnabled(Skills.AURA_WEAPON) && !record.isEnabled(Skills.AURA_DAMAGE)) {
            return;
        }
        // 攻击频率 = 玩家实际攻速属性 - 3（ATTACK_SPEED 基础 4.0 → 基础频率 1 次/秒；
        // 光环速度 +0.19/级、疾攻术 +0.02/级、攻速增幅/全能精通百分比都会加成，100 级光环 = 20 次/秒）
        double frequency = player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED) - 3.0;
        if (!record.isEnabled(Skills.AURA_SPEED)) {
            frequency = 1.0; // 关闭速度光环则基础频率
        }
        frequency = Math.max(0.1, frequency);
        // 攻击间隔（tick），clamp 至少 1 tick；用世界时间判断保证稳定触发
        int interval = Math.max(1, (int) Math.round(20.0 / frequency));
        if (player.level().getGameTime() % interval != 0) {
            return;
        }
        // 伤害 = 玩家实际攻击伤害属性值（基础1 + 光环伤害0.5/级 + 锋刃 + 战斗强化/全能精通百分比加成）
        float damage = (float) player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        int mode = record.getAuraTargetMode();

        List<LivingEntity> targets = player.level().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(ATTACK_RADIUS),
                target -> isTargetValid(player, target, mode));
        for (LivingEntity target : targets) {
            target.hurt(player.damageSources().playerAttack(player), damage);
        }
    }

    private static boolean isTargetValid(ServerPlayer player, LivingEntity target, int mode) {
        if (target == player || target.isDeadOrDying() || !target.isAlive() || target.isInvulnerable()) {
            return false;
        }
        boolean hostile = target instanceof Monster;
        return switch (mode) {
            case MODE_HOSTILE -> hostile;
            case MODE_FRIENDLY -> !hostile;
            default -> true;
        };
    }

    private static PlayerSkillRecord getRecord(ServerPlayer player) {
        // 防御：登出瞬间 serverLevel 可能为 null（多模组环境下事件时序不可控）
        if (player == null || player.serverLevel() == null) {
            return new PlayerSkillRecord(player != null ? player.getUUID() : java.util.UUID.randomUUID());
        }
        return PlayerSkillSavedData.get(player.serverLevel()).getOrCreatePlayer(player.getUUID());
    }
}
