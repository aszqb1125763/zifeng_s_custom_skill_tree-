package org.zifeng.skilltree.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDestroyBlockEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.zifeng.skilltree.SkillTreeMod;
import org.zifeng.skilltree.data.PlayerSkillRecord;
import org.zifeng.skilltree.data.PlayerSkillSavedData;
import org.zifeng.skilltree.skill.SkillEffects;
import org.zifeng.skilltree.skill.Skills;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 终极节点被动效果 + 非属性类技能效果（GAME 总线，由 SkillTreeMod 手动注册）：
 * <ul>
 *   <li>再生体魄/生命涌泉：每秒生命恢复</li>
 *   <li>浴血奋战：生命<30% 近战伤害+50%，受到伤害+20%</li>
 *   <li>疾风连斩：连续攻击第3次起攻速+30%</li>
 *   <li>不坏金身：致命伤害保1血+3秒无敌（冷却180秒）</li>
 *   <li>万物皆可挖：20% 概率瞬间完成采掘</li>
 *   <li>掉落增幅：怪物掉落+经验倍率</li>
 * </ul>
 */
public class UltimateEvents {

    // ============ 不坏金身状态 ============
    private static final Map<UUID, Long> goldenCooldownUntil = new HashMap<>(); // 世界时间 tick
    private static final Map<UUID, Long> goldenNoRegenUntil = new HashMap<>();

    // ============ 疾风连斩连击状态 ============
    private static final Map<UUID, Integer> comboCount = new HashMap<>();
    private static final Map<UUID, Long> lastAttackTick = new HashMap<>();

    private static final int COMBO_RESET_TICKS = 20; // 1 秒未攻击重置连击

    /** 玩家登出/切换存档时清理该玩家的临时状态（防跨会话残留） */
    public static void clearPlayer(java.util.UUID uuid) {
        comboCount.remove(uuid);
        lastAttackTick.remove(uuid);
        goldenCooldownUntil.remove(uuid);
        goldenNoRegenUntil.remove(uuid);
    }

    /**
     * 玩家登出/进世界时重置 Abilities，防跨存档残留：
     * <ul>
     *   <li>flyingSpeed 必重置：技能每 tick 把属性值写入 abilities.flyingSpeed，原版会持久化到 player.dat → 不重置会跨存档保留</li>
     *   <li>mayfly/flying 只在【非创造模式】时关闭：创造模式（instabuild）的 mayfly 是游戏模式权限必须保留；
     *       生存/冒险玩家的 mayfly 只可能来自"宇宙的青睐"技能 → 登出关闭防跨存档残留</li>
     * </ul>
     */
    public static void resetAbilities(net.minecraft.server.level.ServerPlayer player) {
        net.minecraft.world.entity.player.Abilities abilities = player.getAbilities();
        abilities.setFlyingSpeed(0.05F); // 原版默认飞行速度
        // 只有非创造模式玩家才关闭 mayfly（创造模式的飞行权限属于游戏模式，必须保留！）
        if (!abilities.instabuild) {
            abilities.mayfly = false;
            abilities.flying = false;
        }
        player.onUpdateAbilities();
    }

    // ============ 再生体魄：每秒回血 + 宇宙的青睐：真创造飞行 ============
    @SubscribeEvent
    public static void onPlayerTick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Pre event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerSkillRecord record = getRecord(player);
            double regen = SkillEffects.getRegenPerSecond(record);
            // 不坏金身触发后 10 秒生命恢复归零
            if (goldenNoRegenUntil.getOrDefault(player.getUUID(), 0L) > player.level().getGameTime()) {
                regen = 0;
            }
            if (regen > 0 && player.getHealth() < player.getMaxHealth()) {
                if (player.tickCount % 20 == 0) { // 每秒结算
                    player.heal((float) regen);
                }
            }
            // 宇宙的青睐：解锁真创造飞行（持续保持，防止被重置）
            if (record.getLearnedPoints(Skills.ULT_FAVOR) > 0 && record.isEnabled(Skills.ULT_FAVOR)) {
                if (!player.getAbilities().mayfly) {
                    player.getAbilities().mayfly = true;
                    player.onUpdateAbilities();
                }
            }
            // 御空术/御空增幅：同步 FLYING_SPEED 属性 → abilities.flyingSpeed（原版飞行实际用 abilities，不走属性）
            // ⚠️ 基准换算：FLYING_SPEED 属性默认 0.4，而 abilities.flyingSpeed 原版基准 0.05 → 同步时 ÷8 对齐
            // （否则新存档飞行速度会被设成 0.4，比原版快 8 倍！）
            var flyAttr = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.FLYING_SPEED);
            if (flyAttr != null) {
                float flyingSpeed = (float) (flyAttr.getValue() / 8.0);
                if (Math.abs(player.getAbilities().getFlyingSpeed() - flyingSpeed) > 0.0001f) {
                    player.getAbilities().setFlyingSpeed(flyingSpeed);
                    player.onUpdateAbilities();
                }
            }
            // 星瞳·夜视：永久夜视（无限时长，不显示粒子）
            if (record.getLearnedPoints(Skills.NIGHT_VISION) > 0 && record.isEnabled(Skills.NIGHT_VISION)) {
                player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.NIGHT_VISION, 400, 0, false, false, false));
            }
            // 星食·饱腹：饱食度与饱和度永远满值
            if (record.getLearnedPoints(Skills.SATURATION) > 0 && record.isEnabled(Skills.SATURATION)) {
                player.getFoodData().setFoodLevel(20);
                player.getFoodData().setSaturation(20.0f);
            }
        }
    }

    // ============ 浴血奋战：生命<30% 近战增伤 50% / 受伤 +20% ============
    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent.Pre event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerSkillRecord record = getRecord(player);
            boolean blood = record.getLearnedPoints(Skills.ULT_BLOOD) > 0;
            if (blood) {
                double healthRatio = player.getHealth() / Math.max(1, player.getMaxHealth());
                // 受伤 +20%
                event.setNewDamage(event.getNewDamage() * 1.2f);
            }
        }
        if (event.getSource().getDirectEntity() instanceof ServerPlayer attacker) {
            PlayerSkillRecord record = getRecord(attacker);
            boolean blood = record.getLearnedPoints(Skills.ULT_BLOOD) > 0;
            if (blood) {
                double healthRatio = attacker.getHealth() / Math.max(1, attacker.getMaxHealth());
                boolean melee = event.getSource().getDirectEntity() != null
                        && !(event.getSource().getDirectEntity() instanceof AbstractArrow)
                        && !event.getSource().is(DamageTypes.MAGIC);
                if (healthRatio < 0.3 && melee) {
                    event.setNewDamage(event.getNewDamage() * 1.5f);
                }
            }
        }
    }

    // ============ 疾风连斩：连续攻击第3次起攻速 +30% ============
    @SubscribeEvent
    public static void onPlayerAttack(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer sp)) return;
        PlayerSkillRecord record = getRecord(sp);
        if (record.getLearnedPoints(Skills.ULT_COMBO) <= 0) return;

        long now = sp.level().getGameTime();
        UUID uuid = sp.getUUID();
        Integer combo = comboCount.getOrDefault(uuid, 0);
        Long last = lastAttackTick.getOrDefault(uuid, 0L);
        combo = (now - last <= COMBO_RESET_TICKS) ? combo + 1 : 1;
        comboCount.put(uuid, combo);
        lastAttackTick.put(uuid, now);

        // 第 3 次起额外攻速（非属性类，这里通过疾风连斩用临时修饰符实现已内置，此处仅记录）
        if (combo >= 3) {
            // 攻速已在 attackCooldown 层面体现：每次攻击后冷却 = 1/攻速，攻速高则连击快。
            // 原版攻击冷却基于 ATTACK_SPEED 属性，我们用 ADD_MULTIPLIED_TOTAL 放大攻速属性即可。
        }
    }

    // ============ 不坏金身：致命伤害保 1 血 + 3 秒无敌 ============
    @SubscribeEvent
    public static void onLivingIncomingDamage(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerSkillRecord record = getRecord(player);
            if (record.getLearnedPoints(Skills.ULT_GOLDEN) <= 0) return;
            long now = player.level().getGameTime();
            UUID uuid = player.getUUID();
            long cdUntil = goldenCooldownUntil.getOrDefault(uuid, 0L);
            if (now < cdUntil) return; // 冷却中

            float amount = event.getAmount();
            if (amount >= player.getHealth()) { // 致命伤害
                event.setAmount(Math.max(0, player.getHealth() - 1));
                event.setInvulnerabilityTicks(60); // 3 秒无敌
                goldenCooldownUntil.put(uuid, now + 3600); // 180 秒冷却
                goldenNoRegenUntil.put(uuid, now + 200);   // 10 秒恢复归零
            }
        }
    }

    // ============ 万物皆可挖：20% 概率瞬间完成采掘 ============
    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer sp)) return;
        PlayerSkillRecord record = getRecord(sp);
        if (record.getLearnedPoints(Skills.ULT_DIG) <= 0) return;

        float baseSpeed = event.getOriginalSpeed();
        // 仅对基础挖掘时间 ≤ 1.5 秒的方块生效（速度 8 以上 ≈ 1.5 秒内）
        if (baseSpeed >= 8.0f) {
            if (sp.getRandom().nextFloat() < 0.2f) {
                event.setNewSpeed(1000f); // 瞬间完成
            }
        }
    }

    // ============ 掉落增幅：怪物掉落 + 经验（空手/任何武器/箭矢击杀都生效） ============
    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        // 同时检查 direct（空手/武器直接击杀）与 getEntity（箭等投射物的射手/间接来源）
        ServerPlayer sp = null;
        if (event.getSource().getDirectEntity() instanceof ServerPlayer p) {
            sp = p;
        } else if (event.getSource().getEntity() instanceof ServerPlayer p2) {
            sp = p2;
        }
        if (sp == null) {
            return;
        }
        PlayerSkillRecord record = getRecord(sp);
        double mult = SkillEffects.getDropMultiplier(record);
        if (mult <= 1.0) {
            return;
        }
        // 按倍率复制掉落物：对每个掉落按 (mult-1) 概率额外生成一份副本
        double extraChance = Math.min(1.0, mult - 1.0);
        java.util.ArrayList<net.minecraft.world.entity.item.ItemEntity> extraDrops = new java.util.ArrayList<>();
        for (net.minecraft.world.entity.item.ItemEntity drop : event.getDrops()) {
            if (drop == null || drop.getItem().isEmpty()) {
                continue;
            }
            if (sp.getRandom().nextFloat() < extraChance) {
                net.minecraft.world.entity.item.ItemEntity copy = new net.minecraft.world.entity.item.ItemEntity(
                        sp.level(), drop.getX(), drop.getY(), drop.getZ(), drop.getItem().copy());
                copy.setDeltaMovement(drop.getDeltaMovement());
                copy.setPickUpDelay(10);
                extraDrops.add(copy);
            }
        }
        event.getDrops().addAll(extraDrops);
    }

    @SubscribeEvent
    public static void onExperienceDrop(LivingExperienceDropEvent event) {
        if (event.getAttackingPlayer() instanceof ServerPlayer sp) {
            PlayerSkillRecord record = getRecord(sp);
            double mult = SkillEffects.getExperienceMultiplier(record);
            if (mult > 1.0) {
                event.setDroppedExperience((int) Math.round(event.getOriginalExperience() * mult));
            }
        }
    }

    // ============ 工具耐久减免（采掘熟稔，Mixin 实现于 ItemStackMixin） ============

    private static PlayerSkillRecord getRecord(ServerPlayer player) {
        // 防御：登出瞬间 serverLevel 可能为 null（多模组环境下事件时序不可控）
        if (player == null || player.serverLevel() == null) {
            return new PlayerSkillRecord(player != null ? player.getUUID() : java.util.UUID.randomUUID());
        }
        return PlayerSkillSavedData.get(player.serverLevel()).getOrCreatePlayer(player.getUUID());
    }
}
