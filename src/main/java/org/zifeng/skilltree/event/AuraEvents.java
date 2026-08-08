package org.zifeng.skilltree.event;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
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
        // 攻击间隔（tick）：基础 10 秒（200 tick），每级光环速度 -9.5 tick，20 级 = 10 tick = 每秒 2 次。
        // ⚠️ 性能优化：不再与攻速属性挂钩（原先每 tick 攻击太耗性能），改为低频间隔触发。
        int baseInterval = org.zifeng.skilltree.Config.AURA_BASE_INTERVAL_TICKS.get();
        int speedLevel = record.isEnabled(Skills.AURA_SPEED) ? record.getActiveLevel(Skills.AURA_SPEED) : 0;
        double reduction = org.zifeng.skilltree.Config.AURA_SPEED_INTERVAL_REDUCTION.get();
        int interval = Math.max(10, (int) Math.round(baseInterval - speedLevel * reduction));
        if (player.level().getGameTime() % interval != 0) {
            return;
        }
        // 伤害 = 玩家实际攻击伤害属性值（基础1 + 光环伤害每级+5%乘算 + 锋刃 + 增幅/全能精通百分比加成）
        float damage = (float) player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        int mode = record.getAuraTargetMode();
        // 光环速度升级额外获得【无视每帧伤害】：学了速度光环且开启 → 每次攻击无视目标受击无敌帧（原版生物受伤后 1 秒内免疫，限制高频攻击）
        boolean ignoreIFrames = record.getLearnedPoints(Skills.AURA_SPEED) > 0 && record.isEnabled(Skills.AURA_SPEED);

        // 范围伤害：360° 球形（玩家为中心），固定半径 Config 可调；xyz 三轴全半径（不再压缩 Y）
        // 参考 ProjectE attackAOE（玩家自身为中心全向范围 + 谓词过滤敌友）
        // ⚠️ 扫描 Entity.class 而非 LivingEntity.class：DE 的守卫水晶（GuardianCrystalEntity）直接继承 Entity，
        //    不是 LivingEntity！只扫 LivingEntity 会导致光环永远打不到水晶。
        double radius = org.zifeng.skilltree.Config.AURA_ATTACK_RADIUS.get();
        List<Entity> targets = player.level().getEntitiesOfClass(Entity.class,
                player.getBoundingBox().inflate(radius, radius, radius),
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
        for (Entity targetEntity : targets) {
            // 破盾：目标举盾格挡 → 解除格挡 + 盾牌冷却（原版 Player.disableShield，参考 Draconic 穿透箭破盾逻辑）
            if (targetEntity instanceof Player p && p.isBlocking() && p.getUseItem().getItem() instanceof ShieldItem) {
                p.disableShield();
            }
            // DE 守卫水晶特判：GuardianCrystalEntity 是 Entity 不是 LivingEntity，
            // 普通伤害完全免疫（getCrystalDamageModifier 返回 0），必须用混沌伤害源（chaotic 标签）攻击。
            // ⚠️ 连击削盾：DE 守卫有单次伤害上限（500）+ hitCooldown 保护（伤害 < 上次×1.1 会被忽略），
            //    用多次递增伤害（×1.15）绕过保护，一次光环攻击累计打出大量伤害。
            if (isDraconicCrystal(targetEntity)) {
                DamageSource chaosSource = buildChaosSource(serverLevel, player);
                if (chaosSource != null) {
                    float base = Math.max(5000.0F, damage * 400.0F);
                    boolean hit = false;
                    for (int i = 0; i < 30; i++) {
                        if (targetEntity.hurt(chaosSource, base * (float) Math.pow(1.15, i))) {
                            hit = true;
                        }
                    }
                    if (hit) {
                        serverLevel.sendParticles(ParticleTypes.DRAGON_BREATH,
                                targetEntity.getX(), targetEntity.getY() + targetEntity.getBbHeight() * 0.5, targetEntity.getZ(),
                                10, 0.3, 0.3, 0.3, 0.02);
                    }
                }
                continue; // 水晶不参与 LivingEntity 逻辑
            }
            if (!(targetEntity instanceof LivingEntity target)) {
                continue; // 其他非 LivingEntity 实体（如物品/箭）跳过
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
            // Boss 特判：混沌伤害源可穿透 Boss 护盾/免疫机制（DE 混沌守卫、原版 Boss、其他模组 Boss 自动生效）
            // 原理：DE 的 getDamageLevel() 识别带 draconicevolution:chaotic 标签的伤害为 CHAOTIC 级 → chaoticBypassCrystalShield 生效；
            //      其他 Boss 则因混沌伤害 = 无视护甲的真实伤害 + 高倍率，能有效输出。
            // ⚠️ 连击削盾：DE 守卫单次伤害上限 500 + hitCooldown（伤害 < 上次×1.1 忽略），
            //    用多次递增伤害（×1.15）绕过保护，一次光环攻击累计打出大量伤害（约 60 万+）。
            if (isBossEntity(target)) {
                DamageSource chaosSource = buildChaosSource(serverLevel, player);
                if (chaosSource != null) {
                    float base = Math.max(5000.0F, finalDamage * 400.0F);
                    boolean hit = false;
                    // 混沌连击削盾（正常伤害通道）
                    for (int i = 0; i < 30; i++) {
                        if (target.hurt(chaosSource, base * (float) Math.pow(1.15, i))) {
                            hit = true;
                        }
                    }
                    // 无视水晶护盾直击：DE 混沌守卫在水晶存活时 hurt 会被 onGuardianAttacked 完全格挡，
                    // 用反射调用 protected attackDragonFrom 绕过格挡直接扣血（不依赖 DE 编译，保留战斗节奏）
                    if (isDraconicGuardian(target)) {
                        attackDragonDirect(target, chaosSource, base * 2.0F);
                        hit = true;
                    }
                    if (hit) {
                        serverLevel.sendParticles(ParticleTypes.DRAGON_BREATH,
                                target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                                10, 0.3, 0.3, 0.3, 0.02);
                    }
                    continue; // 混沌连击已生效，跳过普通伤害
                }
            }
            if (target.hurt(source, finalDamage)) {
                // 触发武器附魔的命中效果（火焰附加点燃、冰霜之刃减速等）
                if (!weapon.isEmpty()) {
                    EnchantmentHelper.doPostAttackEffectsWithItemSource(serverLevel, target, source, weapon);
                }
                // 混沌伤害：光环攻击附带无视护甲的真实伤害（参考龙之研究混沌武器——混沌能量无视护甲/无敌帧）
                // 比例 Config 可调（默认主伤害的 20%），独立于护甲/减伤结算
                double chaosRatio = org.zifeng.skilltree.Config.AURA_CHAOS_DAMAGE_RATIO.get();
                if (chaosRatio > 0) {
                    float chaosDamage = Math.max(1.0F, finalDamage * (float) chaosRatio);
                    // 魔法伤害无视护甲（混沌武器的真实伤害特性）
                    target.hurt(player.damageSources().indirectMagic(player, player), chaosDamage);
                    // 混沌能量粒子（紫色龙息，混沌主题）
                    serverLevel.sendParticles(ParticleTypes.DRAGON_BREATH,
                            target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                            5, 0.2, 0.2, 0.2, 0.02);
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

    private static boolean isTargetValid(ServerPlayer player, Entity target, int mode) {
        if (target == player || !target.isAlive() || target.isInvulnerable()) {
            return false;
        }
        // DE 守卫水晶：非 LivingEntity（直接继承 Entity），但必须作为敌对目标（否则打不到水晶）
        if (isDraconicCrystal(target)) {
            return mode != MODE_FRIENDLY; // 敌对/所有模式可攻击，友好模式不打
        }
        if (!(target instanceof LivingEntity living)) {
            return false; // 其他非 LivingEntity 实体（物品/箭等）不是攻击目标
        }
        if (living.isDeadOrDying()) {
            return false;
        }
        // Enemy 接口覆盖面比 instanceof Monster 更广（ProjectE 最佳实践：所有敌对生物标记接口）
        boolean hostile = living instanceof Enemy;
        return switch (mode) {
            case MODE_HOSTILE -> hostile;
            case MODE_FRIENDLY -> !hostile;
            default -> true;
        };
    }

    /** DE 守卫水晶特判（GuardianCrystalEntity 是 Entity 不是 LivingEntity，用类名匹配不依赖 DE 编译） */
    private static boolean isDraconicCrystal(Entity target) {
        String name = target.getClass().getName();
        return name.startsWith("com.brandon3055.draconicevolution.entity.")
                && (name.contains("GuardianCrystal") || name.contains("ChaosCrystal"));
    }

    /** DE 混沌守卫本体特判（类名匹配，不依赖 DE 编译） */
    private static boolean isDraconicGuardian(LivingEntity target) {
        String name = target.getClass().getName();
        return name.startsWith("com.brandon3055.draconicevolution.entity.")
                && (name.contains("DraconicGuardian") || name.contains("ChaosGuardian"));
    }

    /** 反射缓存：DE 守卫的 protected attackDragonFrom(DamageSource, float) 方法 */
    private static java.lang.reflect.Method attackDragonFromMethod;

    /**
     * 无视水晶护盾直击 DE 混沌守卫：反射调用 protected attackDragonFrom（真正扣血通道）。
     * 守卫本体 hurt() → attackEntityPartFrom() 在水晶存活时被 onGuardianAttacked 格挡（返回 false）；
     * attackDragonFrom 绕过该格挡直接调用 super.hurt() 扣血。反射避免编译依赖 DE，失败静默降级。
     */
    private static void attackDragonDirect(LivingEntity target, DamageSource source, float amount) {
        try {
            Class<?> clazz = target.getClass();
            if (attackDragonFromMethod == null) {
                attackDragonFromMethod = clazz.getDeclaredMethod("attackDragonFrom", DamageSource.class, float.class);
                attackDragonFromMethod.setAccessible(true);
            }
            attackDragonFromMethod.invoke(target, source, amount);
        } catch (Exception ignored) {
            // 反射失败（类名变了/方法不存在）→ 静默降级，不影响其他逻辑
        }
    }

    /**
     * 构造混沌伤害源（可穿透 DE 混沌守卫/水晶护盾）：
     * <ol>
     *   <li>优先：DE 的 draconicevolution:chaos_implosion 伤害类型——数据驱动定义且自带 chaotic 标签，
     *       DE 的 getDamageLevel() 直接判定为 CHAOTIC（100% 可靠，不依赖玩家手持混沌武器）</li>
     *   <li>回退：我们自己的 zifeng_s_custom_skill_tree:chaos_damage（data JSON 定义 + 尝试打标签）</li>
     *   <li>都没有：返回 null（调用方回退普通伤害）</li>
     * </ol>
     * 攻击者是玩家（attacker=player）→ DE 守卫本体也认（attackEntityPartFrom 要求攻击者是 Player）。
     */
    private static DamageSource buildChaosSource(ServerLevel level, Player player) {
        var registry = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE);
        // 1. DE chaos_implosion（自带 chaotic 标签）
        var deKey = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DAMAGE_TYPE,
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("draconicevolution", "chaos_implosion"));
        var deHolder = registry.getHolder(deKey).orElse(null);
        if (deHolder != null) {
            return new DamageSource(deHolder, player);
        }
        // 2. 我们自己的 chaos_damage（data JSON 定义）
        var ourKey = org.zifeng.skilltree.init.ModDamageTypes.chaosDamageKey();
        var ourHolder = registry.getHolder(ourKey).orElse(null);
        if (ourHolder != null) {
            return new DamageSource(ourHolder, player);
        }
        return null;
    }

    /**
     * Boss 实体判定（混沌伤害特判目标，覆盖整合包无白名单的所有 Boss）：
     * <ul>
     *   <li>原版 Boss：net.minecraft.world.entity.boss 包（末影龙/凋灵）</li>
     *   <li>DE 混沌守卫/水晶：com.brandon3055.draconicevolution.entity 包的 Guardian/Crystal</li>
     *   <li>超高血量（≥500）：大多数 Boss 特征（防御性兜底，覆盖其他模组 Boss）</li>
     * </ul>
     */
    private static boolean isBossEntity(LivingEntity target) {
        // 1. 原版 Boss（末影龙/凋灵）
        if (target instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon
                || target instanceof net.minecraft.world.entity.boss.wither.WitherBoss) {
            return true;
        }
        // 2. DE 守卫/水晶（类名匹配，不依赖 DE 编译）
        String name = target.getClass().getName();
        if (name.startsWith("com.brandon3055.draconicevolution.entity.")
                && (name.contains("DraconicGuardian") || name.contains("GuardianCrystal")
                    || name.contains("ChaosGuardian") || name.contains("ChaosCrystal"))) {
            return true;
        }
        // 3. 其他 Boss 兜底：有 Boss 血条的模组 Boss 通常有超高血量（≥500）
        //    （末影龙 200 / 凋灵 300，但混沌守卫 1000+、龙之研究/其他科技模组 Boss 常 >500）
        return target.getMaxHealth() >= 500.0f;
    }

    // ============ 治愈光环：给周围友方单位施加生命回复效果（等级 = 技能等级） ============

    private static void auraHeal(ServerPlayer player, PlayerSkillRecord record) {
        int level = record.isEnabled(Skills.AURA_HEAL) ? record.getActiveLevel(Skills.AURA_HEAL) : 0;
        if (level <= 0) {
            return;
        }
        // 每 20 tick（1 秒）刷新一次生命回复效果（等级 = 技能等级，时长 6 秒防闪烁）
        if ((player.level().getGameTime() + player.getId()) % 20 != 0) {
            return;
        }
        double radius = SkillEffects.getAuraHealRadius();
        // xyz 三轴全 10 格（立方体范围，不压缩 Y）
        List<LivingEntity> allies = player.level().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(radius, radius, radius),
                target -> {
                    // 治疗对象：非敌对（Enemy 接口，覆盖面比 Monster 更广）且非玩家自身
                    return target.isAlive() && target != player
                            && !(target instanceof Enemy)
                            && target.getHealth() < target.getMaxHealth();
                });
        // 生命回复效果：amplifier = level - 1（1 级 = 生命回复I，50 级 = 生命回复50）
        var regen = new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.REGENERATION, 120, level - 1, false, false, true);
        for (LivingEntity ally : allies) {
            // 只在效果缺失或等级不够时补（避免每 tick 覆盖刷新造成粒子闪烁）
            var cur = ally.getEffect(net.minecraft.world.effect.MobEffects.REGENERATION);
            if (cur == null || cur.getAmplifier() < level - 1) {
                ally.addEffect(regen);
            }
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
