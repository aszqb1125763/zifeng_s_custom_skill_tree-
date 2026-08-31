package org.zifeng.skilltree.event;

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
 *   <li>时之环·时间停止：锁定开启时的世界时间——用原版 gamerule doDaylightCycle=false 停止时间流动（性能最优，平时零开销），
 *       仅在被睡觉//time 命令破坏时纠正回锁定值；全部玩家关闭/登出后恢复 doDaylightCycle=true</li>
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
    /** 锁定的世界时间（一天内 0~23999；第一个开启时记录，-1 = 未锁定） */
    private static long lockedDayTime = -1;
    /** 玩家 UUID → 当前是否开启晴空环（状态 diff 用） */
    private static final Map<UUID, Boolean> weatherLockState = new HashMap<>();
    /** 当前开启晴空环的玩家数（最后一个关闭时恢复 gamerule） */
    private static int weatherLockCount = 0;
    /** 玩家 UUID → 晴空环天气模式（0=晴 1=雨 2=雷暴；2026-08-27：晴空环升级为可切换天气） */
    private static final Map<UUID, Integer> weatherModeByPlayer = new HashMap<>();
    /** 全局当前锁定天气模式（最后切换者生效；0=晴 1=雨 2=雷暴） */
    private static volatile int currentWeatherMode = 0;

    /** 玩家切换晴空环天气模式（WeatherModeC2SPacket 调用） */
    public static void setPlayerWeatherMode(ServerPlayer player, int mode) {
        weatherModeByPlayer.put(player.getUUID(), Math.max(0, Math.min(2, mode)));
        currentWeatherMode = Math.max(0, Math.min(2, mode));
        // 事件驱动：标记全局状态变化 → tick 末合并推送（2026-08-28 架构升级，避免每次发包）
        org.zifeng.skilltree.GlobalStateSync.markDirty();
    }

    /** 服务器当前晴空环天气模式码（GlobalStateSync 推送用，2026-08-28） */
    public static int getCurrentWeatherMode() {
        return currentWeatherMode;
    }

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
        weatherModeByPlayer.remove(uuid);
        // 清理光环攻击间隔 per-player 缓存（2026-08-24 多人修复：防 UUID 残留）
        cachedIntervalByPlayer.remove(uuid);
        cachedSpeedByPlayer.remove(uuid);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerSkillRecord record = getRecord(player);
            // 时之环/晴空环：不依赖光环总开关（独立的时间/天气锁定，与攻击光环无关），
            // 只要学了 + 技能开关开启就生效（修复整合包中不开总开关时不生效的问题）
            boolean timeOn = record.getLearnedPoints(Skills.AURA_TIME) > 0 && record.isEnabled(Skills.AURA_TIME);
            updateTimeLock(player, timeOn);
            // ⚠️ 2026-08-28 性能：enforce 每 5 tick 检查一次（gamerule 读取/比较是每玩家每 tick 开销；与 1.20.1 对齐）
            if (timeOn && player.tickCount % 5 == 0) {
                enforceTimeLock(player);
            }
            // 晴空环：同理
            boolean weatherOn = record.getLearnedPoints(Skills.AURA_WEATHER) > 0 && record.isEnabled(Skills.AURA_WEATHER);
            updateWeatherLock(player, weatherOn);
            if (weatherOn && player.tickCount % 5 == 0) {
                enforceWeatherLock(player);
            }
            // ⚠️ 2026-08-28 订阅改界面驱动（见 OpenSkillTreeC2SPacket）：打开技能树 → 订阅全部全局状态；
            //    关闭界面 → 取消订阅。此处不再按技能开关订阅（原先关闭/重置后订阅被清 → 全局状态不再推送）。
            // 攻击/治疗光环：直接按各技能开关执行（不再有总开关；K 键只控制伤害/速度）
            auraAttack(player, record);
            auraHeal(player, record);
        }
    }

    // ============ 时之环：锁定世界时间为开启瞬间的值（原版 doDaylightCycle=false 机制） ============

    /** 状态 diff：计数开启玩家数；最后一个关闭时恢复 doDaylightCycle=true */
    private static void updateTimeLock(ServerPlayer player, boolean on) {
        // 2026-08-27 性能：先读后写——状态未变时连 put 都省掉（每 tick 调用的热路径）
        Boolean prev = timeLockState.get(player.getUUID());
        if (prev != null && prev == on) {
            return; // 状态未变，零开销
        }
        timeLockState.put(player.getUUID(), on);
        if (on) {
            if (timeLockCount == 0 && player.serverLevel() != null) {
                // 第一个开启：记录当前世界时间（锁定开启时的时间，之后保持不变）
                lockedDayTime = Math.floorMod(player.serverLevel().getDayTime(), 24000L);
            }
            timeLockCount++;
        } else {
            timeLockCount = Math.max(0, timeLockCount - 1);
            if (timeLockCount == 0) {
                lockedDayTime = -1;
                restoreTimeLock(player); // 全部关闭：恢复时间自然流动
            }
        }
        // 事件驱动：时之环开关实际变化 → 标记全局状态变化（tick 末合并推送）
        org.zifeng.skilltree.GlobalStateSync.markDirty();
    }

    /** 锁定期间每 tick 确保：doDaylightCycle=false + 时间=锁定值（仅被睡觉//time 破坏时才纠正，平时只读零开销） */
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
        // 时间校正到锁定值（保持天数不变；睡觉跳天//time 命令破坏后才纠正，平时比较一次就过）
        if (lockedDayTime < 0) {
            return; // 未锁定（理论上不会到这，防御）
        }
        long dayTime = overworld.getDayTime();
        long inDay = Math.floorMod(dayTime, 24000L);
        if (inDay != lockedDayTime) {
            overworld.setDayTime(dayTime + (lockedDayTime - inDay));
        }
    }

    /** 恢复时间自然流动（doDaylightCycle=true）；⚠️ 登出瞬间 serverLevel 可能 null，用 player.getServer() 兜底 */
    private static void restoreTimeLock(ServerPlayer player) {
        MinecraftServer server = player.serverLevel() != null ? player.serverLevel().getServer() : player.getServer();
        if (server == null) {
            return;
        }
        server.overworld().getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(true, server);
    }

    // ============ 晴空环：锁定晴天（原版 doWeatherCycle=false 机制） ============

    /** 状态 diff：计数开启玩家数；最后一个关闭时恢复 doWeatherCycle=true */
    private static void updateWeatherLock(ServerPlayer player, boolean on) {
        // 2026-08-27 性能：先读后写——状态未变时连 put 都省掉（每 tick 调用的热路径）
        Boolean prev = weatherLockState.get(player.getUUID());
        if (prev != null && prev == on) {
            return; // 状态未变，零开销
        }
        weatherLockState.put(player.getUUID(), on);
        if (on) {
            weatherLockCount++;
        } else {
            weatherLockCount = Math.max(0, weatherLockCount - 1);
            if (weatherLockCount == 0) {
                restoreWeatherLock(player); // 全部关闭：恢复天气自然循环
            }
        }
        // 事件驱动：晴空环开关实际变化 → 标记全局状态变化（tick 末合并推送）
        org.zifeng.skilltree.GlobalStateSync.markDirty();
    }

    /** 锁定期间每 tick 确保：doWeatherCycle=false + 锁定玩家选择的天气模式（仅被 /weather 命令破坏时才纠正） */
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
        // 按全局当前天气模式纠正（拉满 20 分钟倒计时，doWeatherCycle=false 期间不递减）
        boolean wantRain = currentWeatherMode == 1 || currentWeatherMode == 2;
        boolean wantThunder = currentWeatherMode == 2;
        if (overworld.isRaining() != wantRain || overworld.isThundering() != wantThunder) {
            overworld.setWeatherParameters(24000, 0, wantRain, wantThunder);
        }
    }

    /** 恢复天气自然循环（doWeatherCycle=true）；⚠️ 登出瞬间 serverLevel 可能 null，用 player.getServer() 兜底 */
    private static void restoreWeatherLock(ServerPlayer player) {
        MinecraftServer server = player.serverLevel() != null ? player.serverLevel().getServer() : player.getServer();
        if (server == null) {
            return;
        }
        server.overworld().getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(true, server);
    }

    // ============ 自动攻击 ============

    /**
     * 攻击间隔 per-player 缓存（2026-08-13 起缓存，2026-08-24 多人修复）：
     * ⚠️ 原 static 单值缓存（cachedIntervalSpeed/cachedInterval）在多人服务器上不同玩家速度光环等级不同时
     *    互相踢缓存（每 tick 交替失效重算 Math.pow）→ 等价于没缓存。改为按玩家 UUID 缓存，登出清理。
     */
    private static final Map<UUID, Integer> cachedSpeedByPlayer = new HashMap<>();
    private static final Map<UUID, Integer> cachedIntervalByPlayer = new HashMap<>();

    /** 单轮光环攻击目标上限（2026-08-15 性能优化）：刷怪塔海量目标时每轮最多处理 N 个，防单轮全打卡顿 */
    private static final int MAX_AURA_TARGETS = 64;

    private static void auraAttack(ServerPlayer player, PlayerSkillRecord record) {
        // ⚠️ 性能优化（2026-08-15）：间隔判断提到最前——大部分 tick 在此直接返回，
        //    后续所有开销（getLearnedPoints/isEnabled/扫描/伤害）只在触发 tick 执行。
        int speedLevel = record.isEnabled(Skills.AURA_SPEED) ? record.getActiveLevel(Skills.AURA_SPEED) : 0;
        // 间隔 per-player 缓存：speedLevel 不变直接复用（避免每 tick Math.pow）
        UUID playerId = player.getUUID();
        Integer cachedSpeed = cachedSpeedByPlayer.get(playerId);
        Integer intervalObj = cachedIntervalByPlayer.get(playerId);
        int interval;
        if (cachedSpeed != null && cachedSpeed == speedLevel && intervalObj != null) {
            interval = intervalObj;
        } else {
            int baseInterval = org.zifeng.skilltree.Config.AURA_BASE_INTERVAL_TICKS.get();
            double reduction = org.zifeng.skilltree.Config.AURA_SPEED_INTERVAL_REDUCTION.get();
            interval = Math.max(10, (int) Math.round(baseInterval * Math.pow(1 - reduction, speedLevel)));
            cachedSpeedByPlayer.put(playerId, speedLevel);
            cachedIntervalByPlayer.put(playerId, interval);
        }
        if (player.level().getGameTime() % interval != 0) {
            return;
        }
        // —— 以下仅在触发 tick 执行 ——
        int damageLevel = record.getLearnedPoints(Skills.AURA_DAMAGE);
        // 虚空之矛：杀戮光环升级（2026-08-15 需求：开关分离——只跟随伤害光环开关）
        // 速度光环只加速攻击频率，不决定是否攻击；伤害光环关闭 → 光环完全停止（含虚空之矛）
        boolean voidSpear = record.getLearnedPoints(Skills.AURA_VOID) > 0
                && record.getLearnedPoints(Skills.AURA_DAMAGE) > 0
                && record.isEnabled(Skills.AURA_DAMAGE);
        // 杀戮光环·强化：学了才解锁混沌伤害/Boss混沌连击/破盾/守卫水晶特判（拆自光环本体的强化机制）
        boolean empower = record.getLearnedPoints(Skills.AURA_EMPOWER) > 0;
        if (damageLevel <= 0 && !voidSpear) {
            return;
        }
        // ⚠️ 2026-08-15 需求：伤害光环开关独立控制攻击——伤害关闭则完全停止（不再被速度光环带动）
        if (!record.isEnabled(Skills.AURA_DAMAGE)) {
            return;
        }
        // 伤害 = 玩家实际攻击伤害属性值（基础1 + 光环伤害每级+5%乘算 + 锋刃 + 增幅/全能精通百分比加成）
        float damage = (float) player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        // 2026-08-13 需求：每个光环独立目标模式（伤害光环用伤害自己的模式）
        int mode = record.getAuraTargetMode(Skills.AURA_DAMAGE);
        // 光环速度升级额外获得【无视每帧伤害】：学了速度光环且开启 → 每次攻击无视目标受击无敌帧（原版生物受伤后 1 秒内免疫，限制高频攻击）
        boolean ignoreIFrames = record.getLearnedPoints(Skills.AURA_SPEED) > 0 && record.isEnabled(Skills.AURA_SPEED);

        // 范围伤害：360° 球形（玩家为中心），固定半径 Config 可调；xyz 三轴全半径（不再压缩 Y）
        // 参考 ProjectE attackAOE（玩家自身为中心全向范围 + 谓词过滤敌友）
        // ⚠️ 虚空之矛：学了虚空之矛 → 光环攻击半径放大到 50 格（Config 可调，参考虚空之矛模组范围秒杀）
        double radius = voidSpear
                ? org.zifeng.skilltree.Config.VOID_AURA_RADIUS.get()
                : org.zifeng.skilltree.Config.AURA_ATTACK_RADIUS.get();
        // 性能优化（大型整合包 mspt）：主扫描用 LivingEntity.class（物品/经验球/箭/矿车等非攻击目标不进遍历，
        // 大整合包实体多时可减 50-80% 遍历量）；DE 守卫水晶（非 LivingEntity）用独立小查询补上。
        var box = player.getBoundingBox().inflate(radius, radius, radius);
        List<Entity> targets = new java.util.ArrayList<>();
        targets.addAll(player.level().getEntitiesOfClass(LivingEntity.class, box,
                target -> isTargetValid(player, target, mode)));
        // DE 守卫水晶特判：GuardianCrystalEntity 直接继承 Entity（非 LivingEntity），单独扫一次补进目标
        if (empower) { // 只有学了光环·强化（能打水晶）才扫
            targets.addAll(player.level().getEntitiesOfClass(Entity.class, box,
                    target -> isDraconicCrystal(target) && isTargetValid(player, target, mode)));
        }
        if (targets.isEmpty()) {
            return;
        }
        ServerLevel serverLevel = player.serverLevel();
        if (serverLevel == null) {
            return;
        }
        // ⚠️ 性能优化（2026-08-15）：单轮目标上限——刷怪塔海量目标时，一轮全打上百目标（每个 hurt 全链路结算）
        //    会卡死服务器。限制每轮最多处理 MAX_AURA_TARGETS 个，超出的下轮继续（视觉上仍是持续清怪）。
        if (targets.size() > MAX_AURA_TARGETS) {
            targets.subList(MAX_AURA_TARGETS, targets.size()).clear();
        }
        // 手持武器（主手）：光环伤害附带该武器全部附魔（锋利/亡灵杀手/节肢杀手/火焰附加/冰霜之刃等，伤害与效果全部生效）
        ItemStack weapon = player.getMainHandItem();
        // 对范围内全部有效目标逐个造成伤害（Draconic/ProjectE 全打思路），每个目标独立命中判定
        for (Entity targetEntity : targets) {
            // 破盾（光环·强化）：目标举盾格挡 → 解除格挡 + 盾牌冷却（原版 Player.disableShield，参考 Draconic 穿透箭破盾逻辑）
            if (empower && targetEntity instanceof Player p && p.isBlocking() && p.getUseItem().getItem() instanceof ShieldItem) {
                p.disableShield();
            }
            // DE 守卫水晶特判（光环·强化）：GuardianCrystalEntity 是 Entity 不是 LivingEntity，
            // 普通伤害完全免疫（getCrystalDamageModifier 返回 0），必须用混沌伤害源（chaotic 标签）攻击。
            // ⚠️ 连击削盾：DE 守卫有单次伤害上限（500）+ hitCooldown 保护（伤害 < 上次×1.1 会被忽略），
            //    用多次递增伤害（×1.15）绕过保护，一次光环攻击累计打出大量伤害。
            if (empower && isDraconicCrystal(targetEntity)) {
                DamageSource chaosSource = buildChaosSource(serverLevel, player);
                if (chaosSource != null) {
                    float base = Math.max(5000.0F, damage * 400.0F);
                    boolean hit = false;
                    for (int i = 0; i < 30; i++) {
                        if (targetEntity.hurt(chaosSource, base * (float) Math.pow(1.15, i))) {
                            hit = true;
                        }
                    }
                    // 粒子已删除（2026-08-15 测试：排查粒子是否导致卡顿）
                }
                continue; // 水晶不参与 LivingEntity 逻辑
            }
            if (!(targetEntity instanceof LivingEntity target)) {
                continue; // 其他非 LivingEntity 实体（如物品/箭）跳过
            }
            // 虚空之矛秒杀（参考虚空之矛 damageLoop + forceFinish）：对普通生物直接绝对秒杀（1 亿×循环+兜底强杀）
            // 虚空之矛秒杀（2026-08-15 优化）：
            //   · 普通怪：混沌伤害源秒杀（3 次 hurt + forceFinish），伤害挂 Boss 判定（chaotic 标签）
            //   · Boss/DE 守卫：混沌秒杀优先（混沌伤害源可穿透 Boss 免疫），未击杀再走混沌连击削盾兜底
            if (voidSpear && !isBossEntity(target)) {
                voidSpearKill(serverLevel, player, target);
                continue;
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
            // Boss 分支（2026-08-15 需求：虚空之矛伤害挂 Boss 判定）：
            //   · 学了虚空之矛 → 先用混沌伤害源对 Boss 尝试虚空秒杀（混沌秒杀挂 Boss 判定，可穿透 Boss 护盾/免疫）
            //   · 未学虚空之矛 或 混沌秒杀未击杀（DE 守卫水晶护盾格挡）→ 降级混沌连击削盾
            if (isBossEntity(target)) {
                DamageSource chaosSource = buildChaosSource(serverLevel, player);
                if (chaosSource != null) {
                    if (voidSpear) {
                        // 虚空之矛混沌秒杀 Boss：3 次混沌伤害（1 亿）→ 未死再 forceFinish
                        voidSpearKill(serverLevel, player, target);
                        if (!target.isAlive()) {
                            continue; // 秒杀成功
                        }
                        // 未击杀（DE 守卫水晶护盾格挡）：继续走混沌连击
                    }
                    // 混沌连击削盾（正常伤害通道，empower 才触发；无 empower 不削盾）
                    if (empower) {
                        float base = Math.max(5000.0F, finalDamage * 400.0F);
                        boolean hit = false;
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
                        // 粒子已删除（2026-08-15 测试）
                    }
                    continue; // Boss 已用混沌伤害处理，跳过普通伤害
                }
            }
            if (target.hurt(source, finalDamage)) {
                // 触发武器附魔的命中效果（火焰附加点燃、冰霜之刃减速等）
                if (!weapon.isEmpty()) {
                    EnchantmentHelper.doPostAttackEffectsWithItemSource(serverLevel, target, source, weapon);
                }
                // 混沌伤害（光环·强化）：光环攻击附带无视护甲的真实伤害（参考龙之研究混沌武器——混沌能量无视护甲/无敌帧）
                // 比例 Config 可调（默认主伤害的 20%），独立于护甲/减伤结算
                double chaosRatio = org.zifeng.skilltree.Config.AURA_CHAOS_DAMAGE_RATIO.get();
                if (empower && chaosRatio > 0) {
                    float chaosDamage = Math.max(1.0F, finalDamage * (float) chaosRatio);
                    // 魔法伤害无视护甲（混沌武器的真实伤害特性）
                    target.hurt(player.damageSources().indirectMagic(player, player), chaosDamage);
                    // 混沌能量粒子已删除（2026-08-15 测试）
                }
                // 伤害指示粒子已删除（2026-08-15 测试：排查粒子是否导致卡顿）
                float damageDealt = healthBefore - target.getHealth();
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

    /** DE 类名缓存（2026-08-28 性能：按 Class 缓存判断结果，避免触发 tick 时对每个目标 getClass().getName() 分配字符串；与 1.20.1 对齐） */
    private static final java.util.concurrent.ConcurrentHashMap<Class<?>, Boolean> DRACONIC_CRYSTAL_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentHashMap<Class<?>, Boolean> DRACONIC_GUARDIAN_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    /** DE 守卫水晶特判（GuardianCrystalEntity 是 Entity 不是 LivingEntity，用类名匹配不依赖 DE 编译） */
    private static boolean isDraconicCrystal(Entity target) {
        return DRACONIC_CRYSTAL_CACHE.computeIfAbsent(target.getClass(), cls -> {
            String name = cls.getName();
            return name.startsWith("com.brandon3055.draconicevolution.entity.")
                    && (name.contains("GuardianCrystal") || name.contains("ChaosCrystal"));
        });
    }

    /** DE 混沌守卫本体特判（类名匹配，不依赖 DE 编译） */
    private static boolean isDraconicGuardian(LivingEntity target) {
        return DRACONIC_GUARDIAN_CACHE.computeIfAbsent(target.getClass(), cls -> {
            String name = cls.getName();
            return name.startsWith("com.brandon3055.draconicevolution.entity.")
                    && (name.contains("DraconicGuardian") || name.contains("ChaosGuardian"));
        });
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

    // ============ 杀戮光环·虚空之矛：虚空秒杀（参考虚空之矛 damageLoop + forceFinish，数值不削弱） ============

    /**
     * 虚空秒杀：对单个目标执行绝对击杀（参考虚空之矛）：
     * <ul>
     *   <li>① 秒杀循环：1 亿伤害 × 64 次，每次清零受击无敌帧（invulnerableTime/hurtTime），
     *       换魔法伤害源（无视护甲），突破不同伤害源的免疫</li>
     *   <li>② forceFinish 兜底：清吸收 + Float.MAX 强杀 + 血量归零 + die()，对伤害免疫实体也能强杀</li>
     * </ul>
     * 伤害源归属玩家（indirectMagic(player, player)）→ 击杀归功/掉落增幅/生命汲取全部生效。
     */
    /**
     * 虚空秒杀：对单个目标执行绝对击杀（参考虚空之矛，2026-08-15 性能优化）：
     * <ul>
     *   <li>① 秒杀：1 亿伤害 × 3 次（原 64 次，性能优化——刷怪塔场景 50 目标 × 64 = 3200 次 hurt 卡死服务器；
     *       1 亿伤害第 1 次即秒杀绝大多数生物，3 次兜底无敌帧/免疫，forceFinish 处理极端免疫）</li>
     *   <li>② 伤害源优先用混沌伤害（buildChaosSource，带 chaotic 标签）→ 挂 Boss 判定：DE 守卫/Boss 免疫
     *       也能被秒杀命中；无混沌伤害源时降级魔法伤害</li>
     *   <li>③ forceFinish 兜底：清吸收 + Float.MAX 强杀 + 血量归零 + die()，对伤害免疫实体也能强杀</li>
     * </ul>
     * 伤害源归属玩家（attacker=player）→ 击杀归功/掉落增幅/生命汲取全部生效。
     */
    private static void voidSpearKill(ServerLevel level, ServerPlayer player, LivingEntity target) {
        if (target == null || !target.isAlive()) {
            return;
        }
        // ① 混沌伤害源优先（Boss 判定挂载：chaotic 标签可穿透 DE 守卫护盾/Boss 免疫）；
        //    无混沌源（未装 DE/无自定义伤害）时降级魔法伤害
        net.minecraft.world.damagesource.DamageSource voidSource = buildChaosSource(level, player);
        if (voidSource == null) {
            voidSource = player.damageSources().indirectMagic(player, player);
        }
        final float VOID_DAMAGE = 100_000_000.0F; // 1 亿
        // ② 秒杀循环：3 次（原 64 次）——1 亿伤害第 1 次即秒杀，3 次兜底无敌帧/单次伤害上限
        for (int i = 0; i < 3; i++) {
            if (!target.isAlive()) {
                break;
            }
            target.invulnerableTime = 0;
            target.hurtTime = 0;
            target.hurt(voidSource, VOID_DAMAGE);
        }
        // ③ forceFinish 兜底（参考虚空之矛）：清吸收 + 强杀 + 血量归零 + die，对伤害免疫实体也能击杀
        if (target.isAlive()) {
            target.setAbsorptionAmount(0);
            target.hurt(player.damageSources().magic(), Float.MAX_VALUE);
            if (target.isAlive()) {
                target.setHealth(0.0F);
                target.die(voidSource);
            }
        }
        // 虚空传送门粒子已删除（2026-08-15 测试）
    }

    // ============ 治愈光环：给周围友方单位施加生命回复效果（等级 = 技能等级） ============

    private static void auraHeal(ServerPlayer player, PlayerSkillRecord record) {
        int level = record.isEnabled(Skills.AURA_HEAL) ? record.getActiveLevel(Skills.AURA_HEAL) : 0;
        if (level <= 0) {
            return; // 关闭/未点亮：不加效果（有限时长会自然过期，无需主动回收，避免遍历范围造成卡顿）
        }
        // 2026-08-31：每 200 tick（10 秒）给范围内友方施加一次 2400 tick（2 分钟）生命回复，
        // 效果在 2 分钟内自然过期——关闭技能/离开范围后无需主动移除，避免遍历实体的性能开销
        if ((player.level().getGameTime() + player.getId()) % 200 != 0) {
            return;
        }
        double radius = SkillEffects.getAuraHealRadius();
        // 2026-08-13 需求：治愈光环用自己独立的目标模式（0 敌对 / 1 友好 / 2 所有）
        int healMode = record.getAuraTargetMode(Skills.AURA_HEAL);
        // xyz 三轴全 10 格（立方体范围，不压缩 Y）
        List<LivingEntity> allies = player.level().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(radius, radius, radius),
                target -> {
                    if (!target.isAlive() || target == player || target.getHealth() >= target.getMaxHealth()) {
                        return false;
                    }
                    boolean hostile = target instanceof Enemy;
                    return switch (healMode) {
                        case 1 -> !hostile;      // 友好：只奶非敌对
                        case 2 -> true;          // 所有：全奶
                        default -> !hostile;     // 敌对模式（默认）：非敌对（治愈默认奶友好）
                    };
                });
        // 生命回复效果：amplifier = level - 1（1 级 = 生命回复I，50 级 = 生命回复50）；时长 2400 tick = 2 分钟
        var regen = new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.REGENERATION, 2400, level - 1, false, false, true);
        for (LivingEntity ally : allies) {
            // 只在效果缺失或等级不够/剩余不足 2 分钟时补（避免每 10 秒覆盖刷新造成粒子闪烁）
            var cur = ally.getEffect(net.minecraft.world.effect.MobEffects.REGENERATION);
            if (cur == null || cur.getAmplifier() < level - 1 || cur.getDuration() < 2400) {
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
