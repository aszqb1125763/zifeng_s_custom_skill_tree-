package org.zifeng.skilltree.event;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.GameRules;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.zifeng.skilltree.data.PlayerSkillRecord;
import org.zifeng.skilltree.data.PlayerSkillSavedData;
import org.zifeng.skilltree.skill.SkillEffects;
import org.zifeng.skilltree.skill.Skills;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 杀戮光环（AURA，独立系统，不受属性加成，由 SkillTreeMod 手动注册）：
 * <ul>
 *   <li>视觉：客户端纯渲染淡红圆环（AuraRingRenderer，零实体零剑模型）</li>
 *   <li>范围伤害：360° 球形范围（玩家为中心）内全部有效目标逐个造成伤害（参考 ProjectE attackAOE + Draconic dealAOEDamage）</li>
 *   <li>治愈光环：每级每秒治疗周围友好生物</li>
 *   <li>时之环：锁定世界时间为正午——用原版 gamerule doDaylightCycle=false 停止时间流动（性能最优，平时零开销），
 *       仅在被睡觉//time 命令破坏时纠正回正午；全部玩家关闭/登出后恢复 doDaylightCycle=true</li>
 *   <li>晴空环：锁定天气为晴天——用原版 gamerule doWeatherCycle=false 停止天气循环，
 *       仅在被 /weather 命令破坏时纠正回晴天；全部玩家关闭/登出后恢复 doWeatherCycle=true</li>
 * </ul>
 */
public class AuraEvents {

    /** 目标模式 */
    public static final int MODE_HOSTILE = 0;
    public static final int MODE_FRIENDLY = 1;
    public static final int MODE_ALL = 2;

    // ============ 时之环/晴空环全局锁定状态（原版 gamerule 机制，多玩家共享） ============
    /** 玩家 UUID → 当前是否开启时之环（状态 diff 用） */
    private static final Map<UUID, Boolean> timeLockState = new HashMap<>();
    /** 当前开启时之环的玩家数（最后一个关闭时恢复 gamerule） */
    private static int timeLockCount = 0;
    /** 玩家 UUID → 当前是否开启晴空环（状态 diff 用） */
    private static final Map<UUID, Boolean> weatherLockState = new HashMap<>();
    /** 当前开启晴空环的玩家数（最后一个关闭时恢复 gamerule） */
    private static int weatherLockCount = 0;

    /** 玩家登出/切换存档时清理锁定计数（防跨会话残留计数，导致 gamerule 永远锁死） */
    public static void onPlayerLogout(ServerPlayer player) {
        if (player == null) {
            return;
        }
        UUID uuid = player.getUUID();
        Boolean prev = timeLockState.remove(uuid);
        if (prev != null && prev) {
            timeLockCount = Math.max(0, timeLockCount - 1);
            if (timeLockCount == 0) {
                restoreTimeLock(player);
            }
        }
        Boolean prevW = weatherLockState.remove(uuid);
        if (prevW != null && prevW) {
            weatherLockCount = Math.max(0, weatherLockCount - 1);
            if (weatherLockCount == 0) {
                restoreWeatherLock(player);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerSkillRecord record = getRecord(player);
            boolean auraTotalOn = record.isAuraEnabled();
            // 时之环：学习+开启+总开关开启才锁定（总开关关闭时按 off 处理，计数同步减）
            boolean timeOn = auraTotalOn && record.getLearnedPoints(Skills.AURA_TIME) > 0 && record.isEnabled(Skills.AURA_TIME);
            updateTimeLock(player, timeOn);
            if (timeOn) {
                enforceTimeLock(player);
            }
            // 晴空环：同理
            boolean weatherOn = auraTotalOn && record.getLearnedPoints(Skills.AURA_WEATHER) > 0 && record.isEnabled(Skills.AURA_WEATHER);
            updateWeatherLock(player, weatherOn);
            if (weatherOn) {
                enforceWeatherLock(player);
            }
            if (!auraTotalOn) {
                return; // 光环总开关关闭：不攻击不治疗
            }
            auraAttack(player, record);
            auraHeal(player, record);
        }
    }

    // ============ 时之环：锁定世界时间正午（原版 doDaylightCycle=false 机制） ============

    /** 状态 diff：计数开启玩家数；最后一个关闭时恢复 doDaylightCycle=true */
    private static void updateTimeLock(ServerPlayer player, boolean on) {
        Boolean prev = timeLockState.put(player.getUUID(), on);
        if (prev != null && prev == on) {
            return; // 状态未变，零开销
        }
        if (on) {
            timeLockCount++;
        } else {
            timeLockCount = Math.max(0, timeLockCount - 1);
            if (timeLockCount == 0) {
                restoreTimeLock(player); // 全部关闭：恢复时间自然流动
            }
        }
    }

    /** 锁定期间每 tick 确保：doDaylightCycle=false + 时间=正午6000（仅被睡觉//time 破坏时才纠正，平时只读零开销） */
    private static void enforceTimeLock(ServerPlayer player) {
        MinecraftServer server = player.serverLevel() != null ? player.serverLevel().getServer() : null;
        if (server == null) {
            return;
        }
        ServerLevel overworld = server.overworld();
        // gamerule 幂等设置：已为 false 时不重复设置（避免每 tick 向客户端同步 gamerule 的网络开销）
        var rule = overworld.getGameRules().getRule(GameRules.RULE_DAYLIGHT);
        if (rule.get()) {
            rule.set(false, server);
        }
        // 时间校正到正午（保持天数不变；睡觉跳天//time 命令破坏后才纠正，平时比较一次就过）
        long dayTime = overworld.getDayTime();
        long inDay = Math.floorMod(dayTime, 24000L);
        if (inDay != 6000) {
            overworld.setDayTime(dayTime + (6000 - inDay));
        }
    }

    /** 恢复时间自然流动（doDaylightCycle=true） */
    private static void restoreTimeLock(ServerPlayer player) {
        MinecraftServer server = player.serverLevel() != null ? player.serverLevel().getServer() : null;
        if (server == null) {
            return;
        }
        server.overworld().getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(true, server);
    }

    // ============ 晴空环：锁定晴天（原版 doWeatherCycle=false 机制） ============

    /** 状态 diff：计数开启玩家数；最后一个关闭时恢复 doWeatherCycle=true */
    private static void updateWeatherLock(ServerPlayer player, boolean on) {
        Boolean prev = weatherLockState.put(player.getUUID(), on);
        if (prev != null && prev == on) {
            return; // 状态未变，零开销
        }
        if (on) {
            weatherLockCount++;
        } else {
            weatherLockCount = Math.max(0, weatherLockCount - 1);
            if (weatherLockCount == 0) {
                restoreWeatherLock(player); // 全部关闭：恢复天气自然循环
            }
        }
    }

    /** 锁定期间每 tick 确保：doWeatherCycle=false + 晴天（仅被 /weather 命令破坏时才纠正，平时只读零开销） */
    private static void enforceWeatherLock(ServerPlayer player) {
        MinecraftServer server = player.serverLevel() != null ? player.serverLevel().getServer() : null;
        if (server == null) {
            return;
        }
        ServerLevel overworld = server.overworld();
        var rule = overworld.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE);
        if (rule.get()) {
            rule.set(false, server);
        }
        // 被 /weather 命令下雨/雷暴后纠正回晴天（拉满 20 分钟晴天倒计时，doWeatherCycle=false 期间不递减）
        if (overworld.isRaining() || overworld.isThundering()) {
            overworld.setWeatherParameters(24000, 0, false, false);
        }
    }

    /** 恢复天气自然循环（doWeatherCycle=true） */
    private static void restoreWeatherLock(ServerPlayer player) {
        MinecraftServer server = player.serverLevel() != null ? player.serverLevel().getServer() : null;
        if (server == null) {
            return;
        }
        server.overworld().getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(true, server);
    }

    // ============ 自动攻击 ============

    private static void auraAttack(ServerPlayer player, PlayerSkillRecord record) {
        int damageLevel = record.getLearnedPoints(Skills.AURA_DAMAGE);
        if (damageLevel <= 0) {
            return;
        }
        if (!record.isEnabled(Skills.AURA_DAMAGE)) {
            return;
        }
        // 攻击频率 = 玩家实际攻速属性 - 基准偏移（Config 可调；ATTACK_SPEED 基础 4.0 → 基础频率 1 次/秒；
        // 光环速度 +0.19/级、疾攻术 +0.02/级、攻速增幅/全能精通百分比都会加成，100 级光环 = 20 次/秒）
        double frequency = player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED)
                - org.zifeng.skilltree.Config.AURA_FREQUENCY_BASE_OFFSET.get();
        if (!record.isEnabled(Skills.AURA_SPEED)) {
            frequency = 1.0; // 关闭速度光环则基础频率
        }
        frequency = Math.max(0.1, frequency);
        // 攻击间隔（tick），clamp 至少 1 tick；用世界时间判断保证稳定触发
        int interval = Math.max(1, (int) Math.round(20.0 / frequency));
        if (player.level().getGameTime() % interval != 0) {
            return;
        }
        // 伤害 = 玩家实际攻击伤害属性值（基础1 + 光环伤害每级+5%乘算 + 锋刃 + 增幅/全能精通百分比加成）
        float damage = (float) player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        int mode = record.getAuraTargetMode();
        // 光环速度升级额外获得【无视每帧伤害】：学了速度光环且开启 → 每次攻击无视目标受击无敌帧（原版生物受伤后 1 秒内免疫，限制高频攻击）
        boolean ignoreIFrames = record.getLearnedPoints(Skills.AURA_SPEED) > 0 && record.isEnabled(Skills.AURA_SPEED);

        // 范围伤害：360° 水平范围（玩家为中心），固定半径 Config 可调；Y 只扩 4 格（攻击是水平面伤害，缩小包围盒省性能）
        // 参考 ProjectE attackAOE（玩家自身为中心全向范围 + 谓词过滤敌友）
        double radius = org.zifeng.skilltree.Config.AURA_ATTACK_RADIUS.get();
        List<LivingEntity> targets = player.level().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(radius, 4.0, radius),
                target -> isTargetValid(player, target, mode));
        if (targets.isEmpty()) {
            return;
        }
        ServerLevel serverLevel = player.serverLevel();
        if (serverLevel == null) {
            return;
        }
        // 手持武器（主手）：光环伤害附带该武器全部附魔（锋利/亡灵杀手/节肢杀手/火焰附加/冰霜之刃等，伤害与效果全部生效）
        ItemStack weapon = player.getMainHandItem();
        // 对范围内全部有效目标逐个造成伤害（Draconic/ProjectE 全打思路），每个目标独立命中判定
        for (LivingEntity target : targets) {
            // 破盾：目标举盾格挡 → 解除格挡 + 盾牌冷却（原版 Player.disableShield，参考 Draconic 穿透箭破盾逻辑）
            if (target instanceof Player p && p.isBlocking() && p.getUseItem().getItem() instanceof ShieldItem) {
                p.disableShield();
            }
            // 无视无敌帧：每次攻击前清空目标受击无敌帧，保证每 tick 攻击都真实造成伤害（光环速度升级效果）
            if (ignoreIFrames) {
                target.invulnerableTime = 0;
            }
            float healthBefore = target.getHealth();
            DamageSource source = player.damageSources().playerAttack(player);
            // 附魔加成：modifyDamage 把武器附魔的伤害增幅算入本次光环攻击（锋利/亡灵/节肢等）
            float finalDamage = weapon.isEmpty() ? damage
                    : EnchantmentHelper.modifyDamage(serverLevel, weapon, target, source, damage);
            if (target.hurt(source, finalDamage)) {
                // 触发武器附魔的命中效果（火焰附加点燃、冰霜之刃减速等）
                if (!weapon.isEmpty()) {
                    EnchantmentHelper.doPostAttackEffectsWithItemSource(serverLevel, target, source, weapon);
                }
                // 伤害指示粒子数量随实际伤害缩放（Draconic dealAOEDamage 做法，打击感随伤害成长）
                float damageDealt = healthBefore - target.getHealth();
                int particleCount = Math.max(1, Math.min(30, (int) (damageDealt * 0.5)));
                serverLevel.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                        target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                        particleCount, 0.15, 0.15, 0.15, 0.05);
            }
        }
    }

    private static boolean isTargetValid(ServerPlayer player, LivingEntity target, int mode) {
        if (target == player || target.isDeadOrDying() || !target.isAlive() || target.isInvulnerable()) {
            return false;
        }
        // Enemy 接口覆盖面比 instanceof Monster 更广（ProjectE 最佳实践：所有敌对生物标记接口）
        boolean hostile = target instanceof Enemy;
        return switch (mode) {
            case MODE_HOSTILE -> hostile;
            case MODE_FRIENDLY -> !hostile;
            default -> true;
        };
    }

    // ============ 治愈光环：每级每秒治疗周围友好生物 ============

    private static void auraHeal(ServerPlayer player, PlayerSkillRecord record) {
        int level = record.isEnabled(Skills.AURA_HEAL) ? record.getActiveLevel(Skills.AURA_HEAL) : 0;
        if (level <= 0) {
            return;
        }
        // 每秒结算一次（世界时间对齐，多玩家错开避免同 tick 全部结算）
        if ((player.level().getGameTime() + player.getId()) % 20 != 0) {
            return;
        }
        double radius = SkillEffects.getAuraHealRadius();
        float heal = (float) (level * SkillEffects.getAuraHealPerLevel());
        // Y 只扩 4 格：治疗也是水平范围，缩小包围盒省性能
        List<LivingEntity> allies = player.level().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(radius, 4.0, radius),
                target -> {
                    // 治疗对象：非敌对（Enemy 接口，覆盖面比 Monster 更广）且非玩家自身
                    return target.isAlive() && target != player
                            && !(target instanceof Enemy)
                            && target.getHealth() < target.getMaxHealth();
                });
        for (LivingEntity ally : allies) {
            ally.heal(heal);
        }
    }

    private static PlayerSkillRecord getRecord(ServerPlayer player) {
        // 防御：登出瞬间 serverLevel 可能为 null（多模组环境下事件时序不可控）
        if (player == null || player.serverLevel() == null) {
            return new PlayerSkillRecord(player != null ? player.getUUID() : java.util.UUID.randomUUID());
        }
        return PlayerSkillSavedData.get(player.serverLevel()).getOrCreatePlayer(player.getUUID());
    }
}
