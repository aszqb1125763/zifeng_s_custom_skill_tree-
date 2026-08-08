package org.zifeng.skilltree.event;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import net.minecraft.world.level.storage.loot.entries.LootPoolSingletonContainer;
import net.minecraft.world.level.storage.loot.functions.ApplyBonusCount;
import net.minecraft.world.level.storage.loot.functions.EnchantedCountIncreaseFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceWithEnchantedBonusCondition;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
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

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    // ============ 凤凰涅槃状态 ============
    private static final Map<UUID, Long> reviveCooldownUntil = new HashMap<>(); // 世界时间 tick

    // ============ 疾风连斩连击状态 ============
    private static final Map<UUID, Integer> comboCount = new HashMap<>();
    private static final Map<UUID, Long> lastAttackTick = new HashMap<>();

    /** 疾风连斩：连击≥3 时挂的攻速临时修饰符 id */
    private static final ResourceLocation COMBO_MOD = ResourceLocation.fromNamespaceAndPath(SkillTreeMod.MOD_ID, "ult_combo_speed");

    /** 技能授予的飞行权限记录（用于"关闭技能→回收"与"登出→回收"，防止误关创造模式/其他模组的飞行） */
    private static final Set<UUID> SKILL_FLIGHT_GRANTED = new HashSet<>();

    /** 玩家登出/切换存档时清理该玩家的临时状态（防跨会话残留） */
    public static void clearPlayer(ServerPlayer player) {
        UUID uuid = player.getUUID();
        comboCount.remove(uuid);
        lastAttackTick.remove(uuid);
        goldenCooldownUntil.remove(uuid);
        goldenNoRegenUntil.remove(uuid);
        reviveCooldownUntil.remove(uuid);
        SKILL_FLIGHT_GRANTED.remove(uuid);
        // 移除连击攻速修饰符（防跨会话残留）
        applyComboModifier(player, false);
    }

    /**
     * 回收技能授予的飞行权限（关闭技能 / 登出时调用）：
     * <ul>
     *   <li>只在"飞行是本模组技能授予"时才回收，绝不触碰创造模式（instabuild）与其他模组设置的 mayfly</li>
     *   <li>登出后玩家重新进世界时，tick 会按技能点亮状态重新授予</li>
     * </ul>
     */
    public static void clearPlayerFlight(ServerPlayer player) {
        if (player == null || !SKILL_FLIGHT_GRANTED.remove(player.getUUID())) {
            return;
        }
        Abilities abilities = player.getAbilities();
        if (!abilities.instabuild) {
            abilities.mayfly = false;
            abilities.flying = false;
        }
        player.onUpdateAbilities();
    }

    /**
     * 重置飞行速度，防跨存档残留（技能每 tick 把属性值写入 abilities.flyingSpeed，原版会持久化到 player.dat）：
     * 只重置 flyingSpeed，**不干预 mayfly/flying**——创造模式的飞行权限属于游戏模式，
     * 其他模组的飞行也不应被本模组强制关闭。
     */
    public static void resetFlyingSpeed(ServerPlayer player) {
        if (player == null) {
            return;
        }
        Abilities abilities = player.getAbilities();
        abilities.setFlyingSpeed(0.05F); // 原版默认飞行速度
        player.onUpdateAbilities();
    }

    // ============ 再生体魄：每秒回血 + 宇宙的青睐：真创造飞行 ============
    @SubscribeEvent
    public static void onPlayerTick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Pre event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerSkillRecord record = getRecord(player);
            long gameTime = player.level().getGameTime();
            // 疾风连斩：连击超时（Config 可调，默认 1 秒）→ 重置并移除攻速加成
            UUID uuid = player.getUUID();
            Long lastAttack = lastAttackTick.get(uuid);
            if (lastAttack != null && gameTime - lastAttack > org.zifeng.skilltree.Config.COMBO_RESET_TICKS.get()) {
                lastAttackTick.remove(uuid);
                comboCount.remove(uuid);
                applyComboModifier(player, false);
            }
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
            // 宇宙的青睐：技能飞行权限管理（点亮→授予，关闭→回收，绝不触碰创造模式/其他模组的飞行）
            boolean favor = record.getLearnedPoints(Skills.ULT_FAVOR) > 0 && record.isEnabled(Skills.ULT_FAVOR);
            Abilities abilities = player.getAbilities();
            if (favor) {
                if (!abilities.instabuild && !abilities.mayfly) {
                    abilities.mayfly = true; // 授予技能飞行（创造模式本身就能飞，无需重复授予）
                    player.onUpdateAbilities();
                }
                SKILL_FLIGHT_GRANTED.add(player.getUUID());
            } else if (SKILL_FLIGHT_GRANTED.remove(player.getUUID())) {
                // 关闭技能/未点亮 → 回收本模组授予的飞行（非创造才关闭，创造模式是游戏模式权限）
                if (!abilities.instabuild) {
                    abilities.mayfly = false;
                    abilities.flying = false;
                }
                player.onUpdateAbilities();
            }
            // 御空术/御空增幅：同步 FLYING_SPEED 属性 → abilities.flyingSpeed（原版飞行实际用 abilities，不走属性）
            // ⚠️ 基准换算：FLYING_SPEED 属性默认 0.4，而 abilities.flyingSpeed 原版基准 0.05 → 同步时 ÷8 对齐
            // （否则新存档飞行速度会被设成 0.4，比原版快 8 倍！）
            // 只在学过且启用的技能时同步；关闭后还原默认 → 不覆盖其他模组设置的飞行速度
            int flyPoints = record.isEnabled(Skills.FLY) ? record.getActiveLevel(Skills.FLY) : 0;
            int ampFlyPoints = record.isEnabled(Skills.AMP_FLY) ? record.getActiveLevel(Skills.AMP_FLY) : 0;
            var flyAttr = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.FLYING_SPEED);
            if (flyAttr != null) {
                if (flyPoints > 0 || ampFlyPoints > 0) {
                    float flyingSpeed = (float) (flyAttr.getValue() / 8.0);
                    if (Math.abs(abilities.getFlyingSpeed() - flyingSpeed) > 0.0001f) {
                        abilities.setFlyingSpeed(flyingSpeed);
                        player.onUpdateAbilities();
                    }
                } else if ((record.getLearnedPoints(Skills.FLY) > 0 || record.getLearnedPoints(Skills.AMP_FLY) > 0)
                        && Math.abs(abilities.getFlyingSpeed() - 0.05F) > 0.0001f) {
                    // 学过御空但已关闭 → 还原原版默认飞行速度
                    abilities.setFlyingSpeed(0.05F);
                    player.onUpdateAbilities();
                }
            }
            // 星瞳·夜视：永久夜视，永不闪烁
            // ⚠️ 原版闪烁机制：LightTexture.getNightVisionFlashIntensity 在夜视剩余 ≤200 tick（10秒）
            //    时亮度按正弦波 0.25~1.0 波动（倒计时闪烁）。因此刷新阈值必须 > 200 tick，
            //    否则玩家会看到像原版快过期一样的明暗闪烁。这里阈值 300、时长 600，
            //    剩余时长永远落在 300~600 之间，完全避开闪烁区。
            if (player.tickCount % 20 == 0) { // 每秒检查一次（够及时，减少每 tick 开销）
                if (record.getLearnedPoints(Skills.NIGHT_VISION) > 0 && record.isEnabled(Skills.NIGHT_VISION)) {
                    var nightVision = player.getEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION);
                    if (nightVision == null || nightVision.getDuration() < 300) {
                        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                                net.minecraft.world.effect.MobEffects.NIGHT_VISION, 600, 0, false, false, false));
                    }
                }
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
                // 受伤倍率（Config 可调，默认 1.2 = +20%，常驻代价）
                event.setNewDamage(event.getNewDamage() * org.zifeng.skilltree.Config.BLOOD_INCOMING_MULTIPLIER.get().floatValue());
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
            // 生命低于阈值（Config 可调，默认 30%）→ 近战伤害增幅（默认 +50%）
            if (healthRatio < org.zifeng.skilltree.Config.BLOOD_THRESHOLD.get() && melee) {
                event.setNewDamage(event.getNewDamage() * (float) (1 + org.zifeng.skilltree.Config.BLOOD_DAMAGE_BONUS.get()));
            }
        }
            // 暴击：暴击精通（几率）+ 暴击增幅（伤害），任意来源攻击（含光环）都可触发
            if (attacker.getRandom().nextFloat() < (float) SkillEffects.getCritChance(record)) {
                event.setNewDamage(event.getNewDamage() * (float) SkillEffects.getCritMultiplier(record));
            }
            // 破甲精通/破甲增幅：无视护甲的最终伤害增幅（每点 +0.15% ×(1+增幅)）
            double pen = SkillEffects.getArmorPenPercent(record);
            if (pen > 0) {
                event.setNewDamage(event.getNewDamage() * (float) (1 + pen));
            }
            // 死神凝视（处决）：攻击非玩家生物，目标生命低于阈值（Config，默认 15%）时按概率直接处决
            if (record.getLearnedPoints(Skills.ULT_REAPER) > 0 && record.isEnabled(Skills.ULT_REAPER)
                    && !(event.getEntity() instanceof ServerPlayer)
                    && event.getEntity() instanceof LivingEntity target
                    && target.isAlive()
                    && target.getHealth() / Math.max(1, target.getMaxHealth()) < org.zifeng.skilltree.Config.REAPER_THRESHOLD.get().floatValue()
                    && attacker.getRandom().nextFloat() < org.zifeng.skilltree.Config.REAPER_CHANCE.get().floatValue()) {
                event.setNewDamage(org.zifeng.skilltree.Config.REAPER_DAMAGE.get().floatValue()); // 巨额伤害直接处决（护甲减伤后仍足以秒杀）
            }
        }
    }

    // ============ 生命汲取：按造成的伤害回复生命（伤害结算后触发） ============
    @SubscribeEvent
    public static void onDamagePost(LivingDamageEvent.Post event) {
        if (event.getSource().getDirectEntity() instanceof ServerPlayer attacker) {
            PlayerSkillRecord record = getRecord(attacker);
            double lifesteal = SkillEffects.getLifestealRate(record);
            float damage = event.getNewDamage();
            if (lifesteal > 0 && damage > 0 && attacker.isAlive()) {
                attacker.heal((float) (damage * lifesteal));
            }
        }
    }

    // ============ 凤凰涅槃：死亡时原地复活 ============
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerSkillRecord record = getRecord(player);
            // 守卫光环 100 级（100% 防护）：拦截一切死亡，包括 /kill 指令（直接 die() 不走伤害事件）
            // 以及虚空/命令/创造测试等任何致死方式
            int guard = record.isEnabled(Skills.AURA_GUARD) ? record.getActiveLevel(Skills.AURA_GUARD) : 0;
            if (guard >= 100) {
                event.setCanceled(true);
                player.setHealth(player.getMaxHealth());
                player.removeAllEffects(); // 清负面状态，防死亡后残留
                // 原版不死图腾同款复活粒子动画
                player.level().broadcastEntityEvent(player, (byte) 35);
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "🛡 守卫光环 100% 防护生效，死亡被完全免疫！"));
                return;
            }
            if (record.getLearnedPoints(Skills.ULT_REVIVE) <= 0 || !record.isEnabled(Skills.ULT_REVIVE)) {
                return;
            }
            long now = player.level().getGameTime();
            UUID uuid = player.getUUID();
            long cdUntil = reviveCooldownUntil.getOrDefault(uuid, 0L);
            if (now < cdUntil) {
                return; // 冷却中
            }
            // 阻止死亡，原地复活
            event.setCanceled(true);
            float maxHealth = player.getMaxHealth();
            player.setHealth(Math.max(1.0F, maxHealth * org.zifeng.skilltree.Config.REVIVE_HEALTH_RATIO.get().floatValue()));
            player.removeAllEffects();
            // 吸收效果缓冲（5 秒 5 颗黄心），代替无敌帧
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.ABSORPTION, 100, 4, false, false, false));
            // 复活动画（原版不死图腾同款）
            player.level().broadcastEntityEvent(player, (byte) 35);
            long cooldown = org.zifeng.skilltree.Config.REVIVE_COOLDOWN_TICKS.get();
            reviveCooldownUntil.put(uuid, now + cooldown);
            int minutes = (int) (cooldown / 20 / 60);
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "🔥 凤凰涅槃！你已原地复活（冷却 " + minutes + " 分钟）"));
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
        int resetTicks = org.zifeng.skilltree.Config.COMBO_RESET_TICKS.get();
        combo = (now - last <= resetTicks) ? combo + 1 : 1;
        comboCount.put(uuid, combo);
        lastAttackTick.put(uuid, now);

        // 第 3 次起额外攻速（Config 可调，默认 +30%；连击中断/登出时移除）
        applyComboModifier(sp, combo >= 3);
    }

    /**
     * 疾风连斩攻速修饰符：active=true 时给攻速属性 +攻速增幅（ADD_MULTIPLIED_TOTAL，Config 可调），否则移除。
     * 幂等：重复调用先移除再添加，不会叠加。
     */
    private static void applyComboModifier(ServerPlayer player, boolean active) {
        AttributeInstance attr = player.getAttribute(Attributes.ATTACK_SPEED);
        if (attr == null) return;
        attr.removeModifier(COMBO_MOD);
        if (active) {
            attr.addTransientModifier(new AttributeModifier(
                    COMBO_MOD, org.zifeng.skilltree.Config.COMBO_SPEED_BONUS.get(), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    // ============ 物理减伤（自定义属性）+ 不坏金身：致命伤害保 1 血 + 3 秒无敌 ============
    @SubscribeEvent
    public static void onLivingIncomingDamage(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerSkillRecord record = getRecord(player);
            // 0. 守卫光环：每级 +1% 全伤害防护（对所有伤害源生效，包括真伤/混沌/命令等任何标签的伤害）
            //    100 级 = 100% 减伤 = 免疫一切走伤害事件的伤害（/kill 走死亡事件，由 onLivingDeath 拦截）
            int guard = record.isEnabled(Skills.AURA_GUARD) ? record.getActiveLevel(Skills.AURA_GUARD) : 0;
            if (guard > 0) {
                float guardReduction = (float) Math.min(1.0, guard * org.zifeng.skilltree.Config.AURA_GUARD_REDUCTION_PER_LEVEL.get());
                event.setAmount(event.getAmount() * (1 - guardReduction));
            }
            // 1. 物理减伤（自定义属性，独立于原版护甲的计算层，替代原 CombatRulesMixin 的全局修改）
            //    仅对会被护甲阻挡的伤害生效（BYPASSES_ARMOR 的伤害不减免），与原版行为一致
            if (!event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_ARMOR)) {
                double reduction = Math.min(1.0, player.getAttributeValue(
                        org.zifeng.skilltree.init.ModAttributes.DAMAGE_REDUCTION));
                if (reduction > 0) {
                    event.setAmount(event.getAmount() * (float) (1 - reduction));
                }
            }
            // 1.5 荆棘反伤（受击时对攻击者反弹伤害；不反弹给自己，空手/物理攻击才反）
            double thorns = SkillEffects.getThornsDamage(record);
            if (thorns > 0) {
                var direct = event.getSource().getDirectEntity();
                if (direct instanceof LivingEntity attacker && attacker != player) {
                    attacker.hurt(player.damageSources().thorns(player), (float) thorns);
                }
            }
            // 2. 不坏金身：减伤后的伤害仍致命才触发保 1 血 + 无敌
            if (record.getLearnedPoints(Skills.ULT_GOLDEN) <= 0) return;
            long now = player.level().getGameTime();
            UUID uuid = player.getUUID();
            long cdUntil = goldenCooldownUntil.getOrDefault(uuid, 0L);
            if (now < cdUntil) return; // 冷却中

            float amount = event.getAmount();
            if (amount >= player.getHealth()) { // 致命伤害
                event.setAmount(Math.max(0, player.getHealth() - 1));
                int invuln = org.zifeng.skilltree.Config.GOLDEN_INVULNERABILITY_TICKS.get();
                int cooldown = org.zifeng.skilltree.Config.GOLDEN_COOLDOWN_TICKS.get();
                int noRegen = org.zifeng.skilltree.Config.GOLDEN_NO_REGEN_TICKS.get();
                event.setInvulnerabilityTicks(invuln); // 无敌时长（默认 3 秒）
                goldenCooldownUntil.put(uuid, now + cooldown); // 冷却（默认 180 秒）
                goldenNoRegenUntil.put(uuid, now + noRegen);   // 禁回血（默认 10 秒）
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
        // 仅对基础挖掘时间 ≤ 1.5 秒的方块生效（速度阈值 Config 可调，默认 8）
        if (baseSpeed >= org.zifeng.skilltree.Config.DIG_MIN_BASE_SPEED.get().floatValue()) {
            if (sp.getRandom().nextFloat() < org.zifeng.skilltree.Config.DIG_INSTANT_CHANCE.get().floatValue()) {
                event.setNewSpeed(1000f); // 瞬间完成
            }
        }
    }

    // ============ 掉落增幅：只对支持时运（方块）/抢夺（生物）的掉落表生效 ============

    // 反射访问 Minecraft loot 表内部结构（LootTable.pools → LootPool.entries → entry.conditions/functions）
    private static final Field TABLE_POOLS;
    private static final Field POOL_ENTRIES;
    private static final Field ENTRY_CONDITIONS;
    private static final Field ENTRY_FUNCTIONS;
    private static final Field COMPOSITE_CHILDREN;
    // EnchantedCountIncreaseFunction（enchanted_count_increase，抢夺类函数的附魔字段）
    private static final Field ECI_ENCHANTMENT;

    static {
        try {
            TABLE_POOLS = LootTable.class.getDeclaredField("pools");
            TABLE_POOLS.setAccessible(true);
            POOL_ENTRIES = LootPool.class.getDeclaredField("entries");
            POOL_ENTRIES.setAccessible(true);
            ENTRY_CONDITIONS = LootPoolEntryContainer.class.getDeclaredField("conditions");
            ENTRY_CONDITIONS.setAccessible(true);
            ENTRY_FUNCTIONS = LootPoolSingletonContainer.class.getDeclaredField("functions");
            ENTRY_FUNCTIONS.setAccessible(true);
            COMPOSITE_CHILDREN = net.minecraft.world.level.storage.loot.entries.CompositeEntryBase.class.getDeclaredField("children");
            COMPOSITE_CHILDREN.setAccessible(true);
            ECI_ENCHANTMENT = EnchantedCountIncreaseFunction.class.getDeclaredField("enchantment");
            ECI_ENCHANTMENT.setAccessible(true);
        } catch (Exception e) {
            throw new RuntimeException("zifeng: 无法初始化 loot 表反射字段", e);
        }
    }

    // 缓存：掉落表 key -> 是否支持时运/抢夺（避免每次掉落都反射遍历）
    private static final Map<ResourceKey<LootTable>, Boolean> FORTUNE_SUPPORT = new HashMap<>();
    private static final Map<ResourceKey<LootTable>, Boolean> LOOTING_SUPPORT = new HashMap<>();

    /**
     * 生物掉落（空手/任何武器/箭矢击杀都生效，但仅限掉落表含"抢夺类"条件的生物）。
     * 修复：原来用 (mult-1) 概率复制一份，mult>2 时 clamp 到 1 导致倍率完全不对（21 倍只出 2 份）；
     * 改为确定性数量倍率：最终数量 = 原数量 × 倍率（小数部分随机进位），拆堆防溢出。
     */
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
        // 只对掉落表含"抢夺"条件的生物生效（如骷髅的骨头、僵尸的腐肉；猪肉/皮革不受抢夺影响不放大）
        net.minecraft.resources.ResourceKey<LootTable> lootKey = event.getEntity().getLootTable();
        if (lootKey == null || !supportsLooting(lootKey, sp.serverLevel())) {
            return;
        }
        applyDropMultiplier(event.getDrops(), sp, mult);
    }

    /**
     * 方块掉落（时运类）：玩家挖掘方块时按掉落增幅放大掉落物数量，
     * 但仅限掉落表含"时运"加成函数的方块（如矿物；泥土/石头不受时运影响不放大）。
     * BlockDropsEvent 在原版掉落（含原版时运附魔）生成后触发 → 与原版时运叠加生效。
     */
    @SubscribeEvent
    public static void onBlockDrops(net.neoforged.neoforge.event.level.BlockDropsEvent event) {
        if (event.getBreaker() instanceof ServerPlayer sp) {
            PlayerSkillRecord record = getRecord(sp);
            double mult = SkillEffects.getDropMultiplier(record);
            if (mult > 1.0) {
                net.minecraft.resources.ResourceKey<LootTable> lootKey = event.getState().getBlock().getLootTable();
                if (lootKey != null && supportsFortune(lootKey, sp.serverLevel())) {
                    applyDropMultiplier(event.getDrops(), sp, mult);
                }
            }
        }
    }

    /** 方块掉落表是否含时运加成（ApplyBonusCount 函数 或 附魔=FORTUNE 的概率条件） */
    private static boolean supportsFortune(ResourceKey<LootTable> key, net.minecraft.server.level.ServerLevel level) {
        return FORTUNE_SUPPORT.computeIfAbsent(key, k -> {
            LootTable table = level.getServer().reloadableRegistries().getLootTable(k);
            Holder<Enchantment> fortune = level.registryAccess()
                    .lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.FORTUNE);
            return hasEnchantedBonus(table, fortune, true);
        });
    }

    /** 生物掉落表是否含抢夺加成（附魔=LOOTING 的概率条件） */
    private static boolean supportsLooting(ResourceKey<LootTable> key, net.minecraft.server.level.ServerLevel level) {
        return LOOTING_SUPPORT.computeIfAbsent(key, k -> {
            LootTable table = level.getServer().reloadableRegistries().getLootTable(k);
            Holder<Enchantment> looting = level.registryAccess()
                    .lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.LOOTING);
            return hasEnchantedBonus(table, looting, false);
        });
    }

    /**
     * 遍历掉落表，检查是否含目标附魔的"附魔加成"条件（LootItemRandomChanceWithEnchantedBonusCondition，
     * 原 RandomChanceWithLooting，1.21 通用化后用于时运/抢夺），或时运专属函数 ApplyBonusCount。
     * ⚠️ 关键：掉落表常用 AlternativesEntry（minecraft:alternatives）嵌套结构，
     * 时运/抢夺函数在 children 里，必须递归遍历（钻石矿 = alternatives → item 带 apply_bonus）。
     */
    private static boolean hasEnchantedBonus(LootTable table, Holder<Enchantment> target, boolean checkFortuneFunction) {
        try {
            List<?> pools = (List<?>) TABLE_POOLS.get(table);
            if (pools == null) {
                return false;
            }
            for (Object pool : pools) {
                List<?> entries = (List<?>) POOL_ENTRIES.get(pool);
                if (entries == null) {
                    continue;
                }
                for (Object entry : entries) {
                    if (checkEntry(entry, target, checkFortuneFunction)) {
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {
            // 反射失败/表损坏 → 保守返回 false（不放大掉落）
        }
        return false;
    }

    /** 递归检查单个掉落条目（含 AlternativesEntry 的 children 展开） */
    private static boolean checkEntry(Object entry, Holder<Enchantment> target, boolean checkFortuneFunction) throws IllegalAccessException {
        // 1. 条件：LootItemRandomChanceWithEnchantedBonusCondition 且附魔匹配（时运/抢夺通用）
        List<?> conditions = (List<?>) ENTRY_CONDITIONS.get(entry);
        if (conditions != null) {
            for (Object c : conditions) {
                if (c instanceof LootItemRandomChanceWithEnchantedBonusCondition ebc
                        && ebc.enchantment().is(target)) {
                    return true;
                }
            }
        }
        // 2. 函数（仅 LootPoolSingletonContainer 有 functions 字段）：
        if (entry instanceof LootPoolSingletonContainer) {
            List<?> functions = (List<?>) ENTRY_FUNCTIONS.get(entry);
            if (functions != null) {
                for (Object f : functions) {
                    // 2a. ApplyBonusCount 时运加成（方块矿物专用：apply_bonus）
                    if (checkFortuneFunction && f instanceof ApplyBonusCount) {
                        return true;
                    }
                    // 2b. EnchantedCountIncreaseFunction 附魔数量增加（抢夺类：enchanted_count_increase）
                    //     ——骷髅骨头/僵尸腐肉/箭等主掉落用这个，之前漏检查导致生物抢夺不生效！
                    if (f instanceof EnchantedCountIncreaseFunction eci) {
                        try {
                            @SuppressWarnings("unchecked")
                            Holder<Enchantment> eciEnchant = (Holder<Enchantment>) ECI_ENCHANTMENT.get(eci);
                            if (eciEnchant != null && eciEnchant.is(target)) {
                                return true;
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        }
        // 3. 复合条目（AlternativesEntry）：递归检查 children
        if (entry instanceof net.minecraft.world.level.storage.loot.entries.CompositeEntryBase) {
            List<?> children = (List<?>) COMPOSITE_CHILDREN.get(entry);
            if (children != null) {
                for (Object child : children) {
                    if (checkEntry(child, target, checkFortuneFunction)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * 按倍率放大掉落物数量（确定性）：
     * 每个掉落物最终总数量 = floor(原数量 × 倍率 + 随机小数)，
     * 超出单堆上限的拆成多个 ItemEntity（保持原位置/速度/拾取延迟）。
     */
    private static void applyDropMultiplier(java.util.Collection<net.minecraft.world.entity.item.ItemEntity> drops,
                                            ServerPlayer sp, double mult) {
        java.util.List<net.minecraft.world.entity.item.ItemEntity> extra = new java.util.ArrayList<>();
        for (net.minecraft.world.entity.item.ItemEntity drop : drops) {
            if (drop == null || drop.getItem().isEmpty()) {
                continue;
            }
            net.minecraft.world.item.ItemStack stack = drop.getItem();
            int count = stack.getCount();
            // 数量倍率：小数部分按概率进位（mult=1.21 → 平均 1.21 倍）
            int total = (int) Math.floor(count * mult + sp.getRandom().nextFloat());
            int remaining = total - count;
            if (remaining <= 0) {
                continue;
            }
            int max = stack.getMaxStackSize();
            while (remaining > 0) {
                int batch = Math.min(max, remaining);
                net.minecraft.world.item.ItemStack extraStack = stack.copy();
                extraStack.setCount(batch);
                net.minecraft.world.entity.item.ItemEntity extraDrop = new net.minecraft.world.entity.item.ItemEntity(
                        sp.level(), drop.getX(), drop.getY(), drop.getZ(), extraStack);
                extraDrop.setDeltaMovement(drop.getDeltaMovement());
                extraDrop.setPickUpDelay(10);
                extra.add(extraDrop);
                remaining -= batch;
            }
        }
        drops.addAll(extra);
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
