package org.zifeng.skilltree.event;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
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
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.zifeng.skilltree.SkillTreeMod;
import org.zifeng.skilltree.data.PlayerSkillRecord;
import org.zifeng.skilltree.data.PlayerSkillSavedData;
import org.zifeng.skilltree.skill.SkillEffects;
import org.zifeng.skilltree.skill.Skills;

import java.lang.reflect.Field;
import java.util.ArrayList;
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
 *   <li>浴血奋战：常驻攻击力 +50%、最大生命 +50%</li>
 *   <li>不坏金身：常驻抗性提升/伤害吸收/抗火 buff</li>
 *   <li>凤凰涅槃：死亡复活</li>
 *   <li>掉落增幅：怪物掉落+经验倍率</li>
 * </ul>
 */
public class UltimateEvents {

    // ============ 凤凰涅槃状态 ============
    private static final Map<UUID, Long> reviveCooldownUntil = new HashMap<>(); // 世界时间 tick

    // ============ 全能精通免死状态 ============
    private static final Map<UUID, Long> masterUndyingUntil = new HashMap<>(); // 免死保底冷却
    private static final Map<UUID, Long> masterInvulnUntil = new HashMap<>(); // 免死触发后的无敌期

    /** 全能精通是否已学且启用 */
    private static boolean isMasterEnabled(PlayerSkillRecord record) {
        return record.getLearnedPoints(Skills.ULT_MASTER) > 0 && record.isEnabled(Skills.ULT_MASTER);
    }

    /** 虚空之躯是否已学且启用（三层无敌，优先于全能精通） */
    private static boolean isVoidBodyEnabled(PlayerSkillRecord record) {
        return record.getLearnedPoints(Skills.ULT_VOID_BODY) > 0 && record.isEnabled(Skills.ULT_VOID_BODY);
    }

    /** 技能授予的飞行权限记录（用于"关闭技能→回收"与"登出→回收"，防止误关创造模式/其他模组的飞行） */
    private static final Set<UUID> SKILL_FLIGHT_GRANTED = new HashSet<>();

    /** 玩家登出/切换存档时清理该玩家的临时状态（防跨会话残留） */
    public static void clearPlayer(ServerPlayer player) {
        UUID uuid = player.getUUID();
        reviveCooldownUntil.remove(uuid);
        masterUndyingUntil.remove(uuid);
        masterInvulnUntil.remove(uuid);
        SKILL_FLIGHT_GRANTED.remove(uuid);
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

    // ============ 再生体魄：每秒回血 + 宇宙的青睐：真创造飞行 + 不坏金身 buff ============
    @SubscribeEvent
    public static void onPlayerTick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Pre event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // ⚠️ 性能优化（2026-08-13）：PlayerSkillSavedData 已静态缓存（getRecord 零分配），
            // 这里只取一次 record 供本 tick 所有判断复用。
            PlayerSkillRecord record = getRecord(player);
            // 防刷物品快照兜底清理（2026-08-26）：每 5 秒清理超过 30 秒未结算的装备快照
            // （死亡被取消/极端时序残留，正常路径 put+remove 成对，Map 恒为空/极小）
            if (player.tickCount % 100 == 0 && !DEATH_SNAPSHOT_TIME.isEmpty()) {
                long now = player.level().getGameTime();
                var it = DEATH_SNAPSHOT_TIME.entrySet().iterator();
                while (it.hasNext()) {
                    var e = it.next();
                    if (now - e.getValue() > 600) { // 超过 30 秒（600 tick）
                        it.remove();
                        DEATH_EQUIPMENT_SNAPSHOT.remove(e.getKey());
                    }
                }
            }
            // 再生体魄：每秒结算（regen 计算移入 %20 内，避免每 tick 调用 getRegenPerSecond）
            if (player.tickCount % 20 == 0) {
                double regen = SkillEffects.getRegenPerSecond(record);
                if (regen > 0 && player.getHealth() < player.getMaxHealth()) {
                    player.heal((float) regen);
                }
            }
            // 不坏金身：常驻 buff（抗性提升/伤害吸收/抗火），点亮且启用时每 20 tick 刷新保持
            if (record.getLearnedPoints(Skills.ULT_GOLDEN) > 0 && record.isEnabled(Skills.ULT_GOLDEN)) {
                if (player.tickCount % 20 == 0) {
                    int resist = org.zifeng.skilltree.Config.GOLDEN_RESISTANCE_LEVEL.get();
                    int absorb = org.zifeng.skilltree.Config.GOLDEN_ABSORPTION_LEVEL.get();
                    int fire = org.zifeng.skilltree.Config.GOLDEN_FIRE_RESISTANCE_LEVEL.get();
                    if (resist > 0) {
                        var cur = player.getEffect(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE);
                        if (cur == null || cur.getAmplifier() < resist - 1 || cur.getDuration() < 300) {
                            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                                    net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, 400, resist - 1, false, false, false));
                        }
                    }
                    if (absorb > 0) {
                        var cur = player.getEffect(net.minecraft.world.effect.MobEffects.ABSORPTION);
                        if (cur == null || cur.getAmplifier() < absorb - 1 || cur.getDuration() < 300) {
                            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                                    net.minecraft.world.effect.MobEffects.ABSORPTION, 400, absorb - 1, false, false, false));
                        }
                    }
                    if (fire > 0) {
                        var cur = player.getEffect(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE);
                        if (cur == null || cur.getAmplifier() < fire - 1 || cur.getDuration() < 300) {
                            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                                    net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE, 400, fire - 1, false, false, false));
                        }
                    }
                }
            }
            // 宇宙的青睐：技能飞行权限管理（点亮→授予，关闭→回收，绝不触碰创造模式/其他模组的飞行）
            // 纯创造式飞行：点亮即授予 mayfly，玩家双击空格/Shift 落地由原版逻辑处理，不做任何强制。
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
            // 星食·饱腹：饱食度与饱和度永远满值（%20 节流，避免每 tick 标记 FoodData 脏）
            if (record.getLearnedPoints(Skills.SATURATION) > 0 && record.isEnabled(Skills.SATURATION)
                    && player.tickCount % 20 == 0) {
                player.getFoodData().setFoodLevel(20);
                player.getFoodData().setSaturation(20.0f);
            }
            // 村庄英雄（节点类多级终极，上限10级）：每级 +4 级原版村庄英雄效果
            // 1级=村庄英雄4级(amp3)，2级=8级(amp7)，10级=40级(amp39)
            // 实现参考夜视：每 20 tick 刷新保持（交易折扣永久生效）
            int heroLevel = record.isEnabled(Skills.VILLAGE_HERO) ? record.getActiveLevel(Skills.VILLAGE_HERO) : 0;
            if (heroLevel > 0 && player.tickCount % 20 == 0) {
                int heroAmp = heroLevel * 4 - 1; // 每级 +4 级村庄英雄
                var hero = player.getEffect(net.minecraft.world.effect.MobEffects.HERO_OF_THE_VILLAGE);
                if (hero == null || hero.getAmplifier() < heroAmp || hero.getDuration() < 300) {
                    player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                            net.minecraft.world.effect.MobEffects.HERO_OF_THE_VILLAGE, 400, heroAmp, false, false, false));
                }
            }
            // 发光（节点类终极）：给 35 格半径内所有生物（除玩家自身）施加发光效果
            // 实现参考夜视：定期刷新保持；每 40 tick（2秒）扫描一次，效果时长 100 tick（5秒）防闪烁
            int glowOn = record.getLearnedPoints(Skills.GLOW) > 0 && record.isEnabled(Skills.GLOW) ? 1 : 0;
            if (glowOn > 0 && player.tickCount % 40 == 0) {
                double glowRadius = org.zifeng.skilltree.Config.GLOW_RADIUS.get();
                List<LivingEntity> glowTargets = player.level().getEntitiesOfClass(LivingEntity.class,
                        player.getBoundingBox().inflate(glowRadius, glowRadius, glowRadius),
                        target -> target.isAlive() && target != player);
                for (LivingEntity target : glowTargets) {
                    var cur = target.getEffect(net.minecraft.world.effect.MobEffects.GLOWING);
                    if (cur == null || cur.getDuration() < 60) {
                        target.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                                net.minecraft.world.effect.MobEffects.GLOWING, 100, 0, false, false, false));
                    }
                }
            }
            // 全能精通：全方位防御（参考 Re:Avaritia 无尽套 + DE 混沌护胸）
            if (isMasterEnabled(record)) {
                // ① 负面效果免疫：每 tick 清除非有益效果（保留不坏金身的抗性/吸收等有益 buff）
                if (player.tickCount % 10 == 0) {
                    var effects = new java.util.ArrayList<>(player.getActiveEffects());
                    for (var effect : effects) {
                        if (!effect.getEffect().value().isBeneficial()) {
                            player.removeEffect(effect.getEffect());
                        }
                    }
                }
                // ② 火焰免疫：持续灭火
                if (player.isOnFire() && player.tickCount % 5 == 0) {
                    player.clearFire();
                }
                // ③ 溺水不扣血：无限氧气（空气值恒满）
                if (player.tickCount % 20 == 0) {
                    player.setAirSupply(player.getMaxAirSupply());
                }
                // ④ 无敌期持续（免死保底触发后的 3 秒，防止再次受伤死亡）
                long now = player.level().getGameTime();
                if (masterInvulnUntil.getOrDefault(player.getUUID(), 0L) > now) {
                    player.setInvulnerable(true);
                } else {
                    player.setInvulnerable(false);
                }
            } else {
                // 未点亮/关闭 → 确保无敌状态复位
                masterInvulnUntil.remove(player.getUUID());
                if (player.isInvulnerable()) {
                    player.setInvulnerable(false);
                }
            }
            // 虚空之躯：三层无敌之每 tick 修复层（参考虚空之矛）——回满血 + 吸收 20 + 氧气无限 + 灭火 + 清负面 + 虚空救援
            // ⚠️ 放在全能精通之后：虚空之躯是全能精通的升级，防御更强（血量恒满而非免死保底）
            if (isVoidBodyEnabled(record)) {
                // ① 血量只增不减：持续回满（等效虚空之矛 setHealth 只增不减）；%5 节流降低战斗中属性重算频率
                if (player.getHealth() < player.getMaxHealth() && player.tickCount % 5 == 0) {
                    player.setHealth(player.getMaxHealth());
                }
                // ② 保持吸收 20 点（10 颗黄心，虚空之矛同款，防伤害穿透）；%20 节流
                if (player.tickCount % 20 == 0) {
                    var voidAbs = player.getEffect(net.minecraft.world.effect.MobEffects.ABSORPTION);
                    if (voidAbs == null || voidAbs.getAmplifier() < 4 || voidAbs.getDuration() < 300) {
                        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                                net.minecraft.world.effect.MobEffects.ABSORPTION, 400, 4, false, false, false));
                    }
                }
                // ③ 氧气无限：永不溺水
                if (player.getAirSupply() < player.getMaxAirSupply()) {
                    player.setAirSupply(player.getMaxAirSupply());
                }
                // ④ 灭火
                if (player.isOnFire()) {
                    player.clearFire();
                }
                // ⑤ 清负面效果
                if (player.tickCount % 10 == 0) {
                    var effects = new java.util.ArrayList<>(player.getActiveEffects());
                    for (var effect : effects) {
                        if (!effect.getEffect().value().isBeneficial()) {
                            player.removeEffect(effect.getEffect());
                        }
                    }
                }
            }
            // ===== 终极节点·生存辅助（2026-08-27 新增） =====
            // 御风止步（FLY_NO_INERTIA）：客户端处理（ClientFlightEvents，客户端有完整输入状态）
            // 烈焰不侵：无需每 tick 灭火——EntityFireImmuneMixin（fireImmune→true）自动灭火/免伤/无视觉
            // 鲛人之息：水下无限呼吸（氧气条恒满，含岩浆）
            if (record.getLearnedPoints(Skills.WATER_BREATH) > 0 && record.isEnabled(Skills.WATER_BREATH)
                    && player.getAirSupply() < player.getMaxAirSupply()) {
                player.setAirSupply(player.getMaxAirSupply());
            }
            // 无限回路：AE2 无限频道（软集成，未装 AE2 时无操作；全局生效，玩家集合管理）
            // 开启 → 注册玩家并应用无限；关闭 → 注销玩家，最后一个关闭者恢复原频道模式
            boolean aeOn = record.getLearnedPoints(Skills.AE_INFINITE_CHANNEL) > 0 && record.isEnabled(Skills.AE_INFINITE_CHANNEL);
            if (aeOn) {
                org.zifeng.skilltree.compat.Ae2Compat.enable(player.getUUID());
            } else {
                org.zifeng.skilltree.compat.Ae2Compat.disable(player.getUUID());
            }
            // 凤凰涅槃：每秒同步冷却状态到客户端（HUD 图标提示冷却倒计时/就绪）
            // ⚠️ 性能优化（2026-08-27）：未学/未开启技能 → 不发包（避免全员每秒收无意义小包）
            if (player.tickCount % 20 == 0) {
                boolean reviveLearned = record.getLearnedPoints(Skills.ULT_REVIVE) > 0 && record.isEnabled(Skills.ULT_REVIVE);
                if (!reviveLearned) {
                    return; // 未学：无需同步（客户端默认隐藏 HUD）；此处已是方法末尾，安全返回
                }
                long cdUntil = reviveCooldownUntil.getOrDefault(player.getUUID(), 0L);
                int remaining = (int) Math.max(0, cdUntil - player.level().getGameTime());
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                        new org.zifeng.skilltree.network.ReviveCooldownS2CPacket(true, remaining));
            }
        }
    }

    // ============ 浴血奋战（常驻属性：攻击力/生命 +50%，由 SkillEffects 属性修饰符实现）+ 暴击/破甲/死神凝视 ============
    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent.Pre event) {
        // 烈焰不侵：火焰伤害免疫已由 EntityFireImmuneMixin（fireImmune→true）在伤害源头拦截，
        // 无需在此处理（LivingDamageEvent.Pre 不可取消，且 fireImmune 已覆盖 IS_FIRE 全部伤害）。
        if (event.getSource().getDirectEntity() instanceof ServerPlayer attacker) {
            PlayerSkillRecord record = getRecord(attacker);
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

    // ============ 横扫范围（ULT_SWEEP，2026-08-13）：参考龙之研究武器攻击范围升级（AOE） ============
    // 龙研 IModularMelee.dealAOEDamage 核心机制（学习后重写，自写实现）：
    //  ① 以【主目标为中心】的包围盒：水平 aoe 格、垂直仅 0.25 格（近战横扫是水平扇面）
    //  ② 【100° 扇形】角度过滤：只打玩家面朝方向 ±50° 内的敌人（非 360° 全向）
    //  ③ 排除：自己、主目标、友方（isAlliedTo）、距离过近（<1）
    //  ④ 命中：playerAttack 伤害源 + 击退 0.4（粒子已删：横扫高伤害 × 粒子数=伤害×0.5 会巨量红心卡顿）
    //  ⑤ 蓄力门槛：攻击强度 > 0.9 才触发（满蓄力横扫）
    // 用 LivingIncomingDamageEvent（1.21.1 无 LivingAttackEvent）；AOE 用 hurt 直伤 + SWEEP_SOURCE 防递归。
    private static final java.util.Set<UUID> SWEEP_SOURCE = new java.util.HashSet<>();

    @SubscribeEvent
    public static void onSweepAttack(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event) {
        // 横扫由主目标受击触发：攻击者必须是玩家（直接攻击）
        if (!(event.getSource().getDirectEntity() instanceof ServerPlayer attacker)) {
            return;
        }
        // 横扫造成的 hurt 直伤（SWEEP_SOURCE 标记）不再递归触发
        if (SWEEP_SOURCE.contains(attacker.getUUID())) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity target)) {
            return;
        }
        PlayerSkillRecord record = getRecord(attacker);
        int sweepLevel = record.isEnabled(Skills.ULT_SWEEP) ? record.getActiveLevel(Skills.ULT_SWEEP) : 0;
        if (sweepLevel <= 0) {
            return;
        }
        // 仅直接伤害（近战；远程箭/魔法等 isDirect=false 不横扫）
        if (!event.getSource().isDirect()) {
            return;
        }
        // 龙研门槛：攻击蓄力 > 0.9（满蓄力才横扫，防止连点快速横扫）
        if (attacker.getAttackStrengthScale(0.5F) < 0.9F) {
            return;
        }
        // 横扫半径 = 技能等级（每级 +1 格攻击范围）；垂直仅 0.25 格（水平扇面）
        double aoe = sweepLevel;
        float damage = event.getAmount();
        if (damage <= 0) {
            return;
        }
        // ① 主目标为中心的水平包围盒
        List<LivingEntity> entities = attacker.level().getEntitiesOfClass(LivingEntity.class,
                target.getBoundingBox().inflate(aoe, 0.25D, aoe));
        // ② 100° 扇形：玩家面朝方向（yaw）为中心
        double aoeAngle = 100;
        double yaw = attacker.getYRot() - 180;
        var damageSource = attacker.damageSources().playerAttack(attacker);
        // 击退方向：玩家面朝方向
        double kx = Math.sin(Math.toRadians(attacker.getYRot()));
        double kz = -Math.cos(Math.toRadians(attacker.getYRot()));
        // ⚠️ 先加递归标记再 hurt（否则 AOE 直伤会再次触发本事件无限递归）
        SWEEP_SOURCE.add(attacker.getUUID());
        try {
            for (LivingEntity entity : entities) {
                // ③ 排除：自己、主目标、友方、距离<1、超出横扫半径
                if (entity == attacker || entity == target || entity.isRemoved() || !entity.isAlive()
                        || attacker.isAlliedTo(entity) || attacker.distanceTo(entity) < 1.0
                        || entity.distanceTo(target) > aoe) {
                    continue;
                }
                // ② 角度过滤：实体相对玩家的角度是否落在面朝 ±50° 内
                double angle = Math.toDegrees(Math.atan2(attacker.getX() - entity.getX(), attacker.getZ() - entity.getZ()));
                double relativeAngle = Math.abs((angle + yaw) % 360);
                if (relativeAngle > aoeAngle / 2 && relativeAngle <= 360 - (aoeAngle / 2)) {
                    continue;
                }
                // ④ 命中：playerAttack 伤害源（吃护甲/减伤）+ 击退
                if (entity.hurt(damageSource, damage)) {
                    entity.knockback(0.4F, kx, kz);
                    // 粒子已删除（2026-08-15：横扫高伤害 × 粒子数=伤害×0.5 → 坚守者死亡时巨量红心卡顿）
                }
            }
        } finally {
            SWEEP_SOURCE.remove(attacker.getUUID());
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

    // ============ 游戏模式切换：创造→生存时恢复技能飞行权限 ============
    // 原版切换游戏模式会重置 abilities（mayfly=false），tick 授予可能被时序干扰，
    // 监听切换事件立即重新授予，避免"切回生存后技能飞行失效需重新开关"的问题。
    @SubscribeEvent
    public static void onGameModeChange(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerChangeGameModeEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.isAlive()) {
            PlayerSkillRecord record = getRecord(player);
            boolean favor = record.getLearnedPoints(Skills.ULT_FAVOR) > 0 && record.isEnabled(Skills.ULT_FAVOR);
            Abilities abilities = player.getAbilities();
            if (favor && !abilities.instabuild && !abilities.mayfly) {
                abilities.mayfly = true; // 恢复技能飞行（创造本身能飞无需授予；非创造授予）
                player.onUpdateAbilities();
                SKILL_FLIGHT_GRANTED.add(player.getUUID());
            }
        }
    }

    // ============ 凌空采掘（FLY_MINING，2026-08-27）：飞行中挖掘无视原版空中 5 倍惩罚 ============
    // 原版 Player.getDestroySpeed：!onGround() → 挖掘速度 /5。本事件在速度计算后触发，×5 恢复。
    @SubscribeEvent
    public static void onBreakSpeed(net.neoforged.neoforge.event.entity.player.PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer sp)) {
            return;
        }
        PlayerSkillRecord record = getRecord(sp);
        if (record.getLearnedPoints(Skills.FLY_MINING) > 0 && record.isEnabled(Skills.FLY_MINING)
                && !player.onGround()) {
            // 恢复空中 /5 惩罚（水下/水外惩罚独立计算，不受影响）
            event.setNewSpeed(event.getOriginalSpeed() * 5.0F);
        }
    }

    // ============ 破暗之瞳（DARK_VISION，2026-08-27）：免疫黑暗效果（坚守者/古城） ============
    // 在效果即将施加时拦截（MobEffectEvent.Applicable DO_NOT_APPLY）——效果根本不施加，
    // 无闪烁、无残留（比每 tick 移除更干净，玩家完全不受黑暗效果影响）
    @SubscribeEvent
    public static void onEffectApplicable(net.neoforged.neoforge.event.entity.living.MobEffectEvent.Applicable event) {
        if (event.getEntity() instanceof ServerPlayer sp
                && event.getEffectInstance() != null
                && event.getEffectInstance().getEffect() == net.minecraft.world.effect.MobEffects.DARKNESS) {
            PlayerSkillRecord record = getRecord(sp);
            if (record.getLearnedPoints(Skills.DARK_VISION) > 0 && record.isEnabled(Skills.DARK_VISION)) {
                event.setResult(net.neoforged.neoforge.event.entity.living.MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
            }
        }
    }

    // ============ 凤凰涅槃：死亡时原地复活 + 全能精通免死保底 ============

    // 无限回路（AE2 无限频道，2026-08-27）：玩家登出时从集合移除，
    // 最后一个开启者登出后恢复 AE 原频道模式（Ae2Compat.disable 内部处理）
    @SubscribeEvent
    public static void onPlayerLogout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            org.zifeng.skilltree.compat.Ae2Compat.disable(sp.getUUID());
        }
    }

    // 无限回路：服务器停止/重启时清理 Ae2Compat 全局状态（2026-08-27 性能审计修复：
    // 防异常退出后 previousMode 残留导致 AE 频道模式跨世界永久锁定 INFINITE）
    @SubscribeEvent
    public static void onServerStopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
        org.zifeng.skilltree.compat.Ae2Compat.onServerStopped();
    }

    // 防刷物品（2026-08-26）：生物死亡瞬间装备栏物品快照（玩家给予的装备）。
    // 战利品爆炸/掉落倍率只放大【战利品表掉落】，跳过装备栏来源的物品（玩家塞给生物的装备也能被刷）。
    // ⚠️ 性能（2026-08-26）：只在击杀者可能是玩家（含机器 FakePlayer，可能触发掉落放大）时存快照，
    //    自然死亡/环境死亡不存（否则服务器长期运行会内存泄漏）；时间戳 + 定期清理兜底（死亡被取消的残留）。
    private static final java.util.Map<java.util.UUID, java.util.List<ItemStack>> DEATH_EQUIPMENT_SNAPSHOT = new java.util.HashMap<>();
    private static final java.util.Map<java.util.UUID, Long> DEATH_SNAPSHOT_TIME = new java.util.HashMap<>();

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        // 非玩家生物：仅在击杀者可能是玩家（真玩家或机器 FakePlayer，都可能触发掉落放大）时快照装备栏
        if (!(event.getEntity() instanceof ServerPlayer)) {
            // 与 onLivingDrops 相同的击杀者判定（direct/间接投射物），排除自然死亡/环境死亡 → 不存快照，防泄漏
            boolean playerKill = event.getSource().getDirectEntity() instanceof ServerPlayer
                    || event.getSource().getEntity() instanceof ServerPlayer;
            if (playerKill && event.getEntity() instanceof LivingEntity living) {
                java.util.List<ItemStack> equipped = new java.util.ArrayList<>();
                for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
                    ItemStack stack = living.getItemBySlot(slot);
                    if (!stack.isEmpty()) {
                        equipped.add(stack.copy());
                    }
                }
                DEATH_EQUIPMENT_SNAPSHOT.put(living.getUUID(), equipped);
                DEATH_SNAPSHOT_TIME.put(living.getUUID(), living.level().getGameTime());
            }
            return;
        }
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerSkillRecord record = getRecord(player);
            long now = player.level().getGameTime();
            UUID uuid = player.getUUID();
            // 虚空之躯：绝对不死（三层无敌第二层，死亡事件直接取消并回满血，无冷却）
            //    参考虚空之矛 onLivingDeath cancel + 回满血；优先于全能精通免死保底
            if (isVoidBodyEnabled(record)) {
                event.setCanceled(true);
                player.setHealth(player.getMaxHealth());
                player.hurtTime = 0;
                player.invulnerableTime = 10;
                return;
            }

            // 全能精通免死保底（参考 DE Undying 不死模块）：冷却内保 1 血，冷却好则回血+清负面+无敌
            if (isMasterEnabled(record)) {
                long masterCdUntil = masterUndyingUntil.getOrDefault(uuid, 0L);
                long invulnUntil = masterInvulnUntil.getOrDefault(uuid, 0L);
                if (now < invulnUntil) {
                    // 无敌期内：直接取消死亡
                    event.setCanceled(true);
                    player.setHealth(Math.max(1.0F, player.getHealth()));
                    return;
                }
                if (now >= masterCdUntil) {
                    // 免死触发：回血 50% + 清负面 + 无敌 3 秒 + 图腾动画，进入冷却
                    event.setCanceled(true);
                    player.setHealth(Math.max(1.0F, player.getMaxHealth()
                            * org.zifeng.skilltree.Config.MASTER_UNDYING_HEALTH.get().floatValue()));
                    player.removeAllEffects();
                    player.level().broadcastEntityEvent(player, (byte) 35);
                    long cd = org.zifeng.skilltree.Config.MASTER_UNDYING_COOLDOWN.get();
                    long invuln = org.zifeng.skilltree.Config.MASTER_UNDYING_INVULN.get();
                    masterUndyingUntil.put(uuid, now + cd);
                    masterInvulnUntil.put(uuid, now + invuln);
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "🛡 全能精通防御生效，免死一次！（冷却 " + (cd / 20 / 60) + " 分钟）"));
                    return;
                }
                // 冷却中：保 1 血不死（真正的"保证玩家不死"，除非冷却已过）
                event.setCanceled(true);
                player.setHealth(Math.max(1.0F, player.getHealth() - 1));
                player.hurtTime = 0;
                player.invulnerableTime = 10;
                return;
            }

            // 凤凰涅槃：死亡原地复活（冷却 1 分钟，HUD 提示冷却状态）
            if (record.getLearnedPoints(Skills.ULT_REVIVE) <= 0 || !record.isEnabled(Skills.ULT_REVIVE)) {
                return;
            }
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
            int seconds = (int) (cooldown / 20);
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "🔥 凤凰涅槃！你已原地复活（冷却 " + seconds + " 秒）"));
        }
    }

    // ============ 物理减伤（自定义属性） + 全能精通全伤害减免 + 荆棘反伤 ============
    @SubscribeEvent
    public static void onLivingIncomingDamage(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerSkillRecord record = getRecord(player);
            // 0. 虚空之躯：全伤害完全免疫（三层无敌第一层，优先于全能精通的百分比减免）
            //    对所有伤害类型生效，包括真伤/混沌/指令伤害，数值直接归零
            if (isVoidBodyEnabled(record)) {
                event.setAmount(0);
                return; // 虚空之躯完全免疫，无需再走后续减伤层
            }
            // 0.5 全能精通：全伤害减免 100%（对所有伤害类型生效，包括真伤/混沌/指令；参考 DE 混沌护胸的全伤害防护）
            //    /kill 指令伤害也免疫（它走 LivingDeathEvent，由免死保底拦截；此处对走伤害事件的伤害全额减免）
            if (isMasterEnabled(record)) {
                float masterReduction = org.zifeng.skilltree.Config.MASTER_DAMAGE_REDUCTION.get().floatValue();
                event.setAmount(event.getAmount() * (1 - masterReduction));
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
            // ⚠️ 2026-08-16 修复：递归保护——荆棘伤害（minecraft:thorns）不再反弹。
            //    否则两个都学荆棘的实体互相反弹 → onLivingIncomingDamage → hurt → ... 无限递归 StackOverflowError。
            double thorns = SkillEffects.getThornsDamage(record);
            if (thorns > 0 && !isThornsDamage(event.getSource())) {
                var direct = event.getSource().getDirectEntity();
                if (direct instanceof LivingEntity attacker && attacker != player) {
                    attacker.hurt(player.damageSources().thorns(player), (float) thorns);
                }
            }
        }
    }

    /** 判断伤害源是否为荆棘反弹伤害（1.21.1 原版 DamageTypes.THORNS，防荆棘互弹递归） */
    private static boolean isThornsDamage(net.minecraft.world.damagesource.DamageSource source) {
        return source.is(net.minecraft.world.damagesource.DamageTypes.THORNS);
    }

    // ============ 全能精通：摔落免疫 + 击退免疫（参考 Re:Avaritia 无尽鞘翅/无尽盾） ============
    @SubscribeEvent
    public static void onLivingFall(net.neoforged.neoforge.event.entity.living.LivingFallEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerSkillRecord record = getRecord(player);
            if (isVoidBodyEnabled(record) || isMasterEnabled(record)) {
                event.setCanceled(true); // 摔落无伤
            }
        }
    }

    @SubscribeEvent
    public static void onKnockBack(net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerSkillRecord record = getRecord(player);
            if (isVoidBodyEnabled(record) || isMasterEnabled(record)) {
                event.setCanceled(true); // 免疫击退
            }
        }
    }

    // ============ 掉落增幅：只对支持时运（方块）/抢夺（生物）的掉落表生效 ============

    // 反射访问 Minecraft loot 表内部结构（LootTable.pools → LootPool.entries → entry.conditions/functions）
    // ⚠️ 2026-08-24 超大型整合包防御：字段允许为 null（反射失败时），不再 throw 导致类加载崩溃——
    //    降级为"掉落增幅/时运抢夺判断返回 false"（功能禁用，其他技能不受影响）。
    private static final Field TABLE_POOLS;
    private static final Field POOL_ENTRIES;
    private static final Field ENTRY_CONDITIONS;
    private static final Field ENTRY_FUNCTIONS;
    private static final Field COMPOSITE_CHILDREN;
    // EnchantedCountIncreaseFunction（enchanted_count_increase，抢夺类函数的附魔字段）
    private static final Field ECI_ENCHANTMENT;

    static {
        Field tablePools = null;
        Field poolEntries = null;
        Field entryConditions = null;
        Field entryFunctions = null;
        Field compositeChildren = null;
        Field eciEnchantment = null;
        try {
            tablePools = LootTable.class.getDeclaredField("pools");
            tablePools.setAccessible(true);
            poolEntries = LootPool.class.getDeclaredField("entries");
            poolEntries.setAccessible(true);
            entryConditions = LootPoolEntryContainer.class.getDeclaredField("conditions");
            entryConditions.setAccessible(true);
            entryFunctions = LootPoolSingletonContainer.class.getDeclaredField("functions");
            entryFunctions.setAccessible(true);
            compositeChildren = net.minecraft.world.level.storage.loot.entries.CompositeEntryBase.class.getDeclaredField("children");
            compositeChildren.setAccessible(true);
            eciEnchantment = EnchantedCountIncreaseFunction.class.getDeclaredField("enchantment");
            eciEnchantment.setAccessible(true);
        } catch (Exception e) {
            // ⚠️ 2026-08-24：原 throw RuntimeException 会让整个 UltimateEvents 类加载失败 → 模组加载崩溃。
            //    大型整合包中其他模组可能改 LootTable/LootPool 类结构 → 反射失败 → 这里降级为禁用掉落增幅。
            org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger("zifeng_skilltree");
            log.warn("[zifeng] 无法初始化掉落表反射字段，掉落增幅/时运判断功能将禁用（不影响其他技能）", e);
        }
        TABLE_POOLS = tablePools;
        POOL_ENTRIES = poolEntries;
        ENTRY_CONDITIONS = entryConditions;
        ENTRY_FUNCTIONS = entryFunctions;
        COMPOSITE_CHILDREN = compositeChildren;
        ECI_ENCHANTMENT = eciEnchantment;
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
        // 防刷物品（2026-08-26）：读取该生物死亡瞬间的装备栏快照（玩家给予的装备），
        // 战利品爆炸/掉落倍率跳过这些装备来源的物品，只放大战利品表掉落。取后移除防泄漏。
        java.util.UUID deadId = event.getEntity().getUUID();
        java.util.List<ItemStack> equippedSnapshot = DEATH_EQUIPMENT_SNAPSHOT.remove(deadId);
        DEATH_SNAPSHOT_TIME.remove(deadId);
        // ============ 战利品爆炸（终极节点，参考神化 FestiveAffix）============
        // 对所有生物（含 Boss、含其他模组怪物）击杀时 100% 触发：掉落物翻倍爆炸散射
        // 1 级 = 掉落 1 倍（即 2 份），100 级 = 100 倍（线性：倍率 = 1 + 等级）
        // ⚠️ 机械共鸣：假玩家（机器）需学习并开启 战利品爆炸·共鸣 才继承
        int bombLevel = SkillEffects.isEffectAllowedFor(sp, record, Skills.MACHINE_LOOT_BOMB)
                && record.isEnabled(Skills.LOOT_BOMB) ? record.getActiveLevel(Skills.LOOT_BOMB) : 0;
        if (bombLevel > 0 && !event.getDrops().isEmpty()) {
            // 倍率 = 1 + 等级（1级=2倍，100级=101倍，线性增长）
            int maxMult = org.zifeng.skilltree.Config.LOOT_BOMB_MAX_MULTIPLIER.get();
            int bombMult = Math.min(maxMult, 1 + bombLevel);
            if (bombMult > 1) {
                // 2026-08-15 优化：普通可堆叠物品按原倍率全量复制（恢复原效果，不设上限）；
                // 装备类（不可堆叠，如盔甲/武器/工具）限制单件最多 20 份——装备无法堆叠，
                //    复制 100 份会生成 100 个实体（卡顿+捡不完），20 份已足够。
                int maxCopies = org.zifeng.skilltree.Config.LOOT_BOMB_MAX_COPIES_PER_KILL.get();
                // 快照掉落物列表，避免遍历中修改
                List<ItemEntity> snapshot = new java.util.ArrayList<>(event.getDrops());
                for (ItemEntity item : snapshot) {
                    if (item == null || !item.isAlive()) {
                        continue;
                    }
                    // ⚠️ 防刷物品（2026-08-26）：跳过生物装备栏来源的物品（玩家主动给予的装备）
                    if (isEquippedItem(equippedSnapshot, item.getItem())) {
                        continue;
                    }
                    // 复制 (bombMult-1) 份（item.copy() 独立栈）
                    // ⚠️ 装备类（不可堆叠）：单件上限 20 份（防 100 个装备实体卡顿+捡不完）；可堆叠物品按原倍率全量复制
                    int copies;
                    if (item.getItem().getMaxStackSize() <= 1 && maxCopies > 0) {
                        copies = Math.min(bombMult - 1, maxCopies);
                    } else {
                        copies = bombMult - 1;
                    }
                    for (int i = 0; i < copies; i++) {
                        ItemEntity copy = new ItemEntity(sp.level(),
                                item.getX(), item.getY(), item.getZ(),
                                item.getItem().copy());
                        copy.setPickUpDelay(0);
                        event.getDrops().add(copy);
                    }
                }
                // 纯掉落翻倍：无音效、无粒子、无散射，掉落物像原版一样自然落地
                // 不发送聊天提示（每杀必触发会刷屏）
            }
        }
        // ============ 刷怪蛋掉落 / 头颅掉落（独立节点技能，不吃战利品爆炸/生物掉落倍率）============
        // 固定掉 1 个，数量不被任何技能增幅；概率逐级叠加，满级=100% 必掉
        // ⚠️ 机械共鸣：假玩家（机器）需对应共鸣技能开启才继承
        if (SkillEffects.isEffectAllowedFor(sp, record, Skills.MACHINE_SPAWN_EGG)) {
            dropSpawnEgg(sp, event, record);
        }
        if (SkillEffects.isEffectAllowedFor(sp, record, Skills.MACHINE_MOB_HEAD)) {
            dropMobHead(sp, event, record);
        }

        // ⚠️ 机械共鸣：假玩家（机器）需学习并开启 生物掉落·共鸣 才继承生物掉落倍率
        double mult = SkillEffects.isEffectAllowedFor(sp, record, Skills.MACHINE_MOB_DROP)
                ? SkillEffects.getMobDropMultiplier(record) : 1.0;
        if (mult > 1.0) {
            // 只对掉落表含"抢夺"条件的生物生效（如骷髅的骨头、僵尸的腐肉；猪肉/皮革不受抢夺影响不放大）
            net.minecraft.resources.ResourceKey<LootTable> lootKey = event.getEntity().getLootTable();
            if (lootKey != null && supportsLooting(lootKey, sp.serverLevel())) {
                // ⚠️ 防刷物品（2026-08-26）：掉落倍率跳过生物装备栏来源的物品（玩家主动给予的装备）
                java.util.List<ItemEntity> filterable = new java.util.ArrayList<>();
                for (ItemEntity drop : event.getDrops()) {
                    if (!isEquippedItem(equippedSnapshot, drop.getItem())) {
                        filterable.add(drop);
                    }
                }
                applyDropMultiplier(filterable, sp, mult);
            }
        }
        // ============ 凋落物挪移（光环技能，2026-08-24）：掉落物直传绑定容器，不生成实体（防卡顿）============
        // ⚠️ 必须放在所有掉落技能【最后】执行：等战利品爆炸/刷怪蛋/头颅/生物掉落倍率全部结算完，
        //    再把所有掉落物一起传送进容器——否则提前 return 会吞掉其他技能的掉落
        // 全送完 → 取消掉落实体生成（无 ItemEntity，刷怪塔不卡）
        if (LootVacuumEvents.tryVacuumDrops(sp, record, event.getDrops())) {
            event.setCanceled(true);
        }
    }

    /**
     * 刷怪蛋掉落（节点技能，独立机制）：击杀生物时按概率掉 1 个对应刷怪蛋。
     * 每级 +10% 概率（满 10 级 = 100% 必掉）；固定 1 个，不参与任何倍率/爆炸增幅。
     * 用 {@link SpawnEggItem#byId} 取对应刷怪蛋（所有原版+模组生物通用；无刷怪蛋的生物不掉）。
     */
    private static void dropSpawnEgg(ServerPlayer sp, LivingDropsEvent event, PlayerSkillRecord record) {
        int level = record.isEnabled(Skills.MOB_SPAWN_EGG) ? record.getActiveLevel(Skills.MOB_SPAWN_EGG) : 0;
        if (level <= 0) {
            return;
        }
        double chance = level * 0.10; // 每级 10%
        if (sp.level().random.nextDouble() >= chance) {
            return;
        }
        net.minecraft.world.item.SpawnEggItem eggItem = net.minecraft.world.item.SpawnEggItem.byId(event.getEntity().getType());
        if (eggItem == null) {
            return;
        }
        ItemEntity drop = new ItemEntity(sp.level(),
                event.getEntity().getX(), event.getEntity().getY(), event.getEntity().getZ(),
                new ItemStack(eggItem));
        drop.setPickUpDelay(10);
        event.getDrops().add(drop);
    }

    /**
     * 头颅掉落（节点技能，独立机制）：击杀生物时按概率掉 1 个对应头颅。
     * 每级 +10% 概率（满 5 级 = 50%）；固定 1 个，不参与任何倍率/爆炸增幅。
     * 原版可穿戴头颅生物（僵尸/骷髅/凋灵骷髅/苦力怕/猪灵）掉对应头；
     * 击杀玩家掉对方皮肤对应的玩家头颅（PROFILE 组件带皮肤）；
     * 无对应头颅的生物不掉（不再掉史蒂夫头）。
     */
    private static void dropMobHead(ServerPlayer sp, LivingDropsEvent event, PlayerSkillRecord record) {
        int level = record.isEnabled(Skills.MOB_HEAD) ? record.getActiveLevel(Skills.MOB_HEAD) : 0;
        if (level <= 0) {
            return;
        }
        double chance = level * 0.10; // 每级 10%（1.2.3 从 20% 下调，与刷怪蛋一致）
        if (sp.level().random.nextDouble() >= chance) {
            return;
        }
        // 击杀玩家：掉对方皮肤对应的玩家头颅（SkullOwner/PROFILE 数据）
        if (event.getEntity() instanceof net.minecraft.world.entity.player.Player victim) {
            ItemStack head = new ItemStack(net.minecraft.world.item.Items.PLAYER_HEAD);
            // 1.21：ResolvableProfile 存玩家 GameProfile（名字+UUID → 客户端自动解析皮肤）
            head.set(net.minecraft.core.component.DataComponents.PROFILE,
                    new net.minecraft.world.item.component.ResolvableProfile(victim.getGameProfile()));
            ItemEntity drop = new ItemEntity(sp.level(),
                    event.getEntity().getX(), event.getEntity().getY(), event.getEntity().getZ(),
                    head);
            drop.setPickUpDelay(10);
            event.getDrops().add(drop);
            return;
        }
        // 击杀生物：仅掉有原版对应头颅的（僵尸/骷髅/凋灵骷髅/苦力怕/猪灵）；无对应头不掉
        var type = event.getEntity().getType();
        ItemStack head;
        if (type == net.minecraft.world.entity.EntityType.ZOMBIE) {
            head = new ItemStack(net.minecraft.world.item.Items.ZOMBIE_HEAD);
        } else if (type == net.minecraft.world.entity.EntityType.SKELETON) {
            head = new ItemStack(net.minecraft.world.item.Items.SKELETON_SKULL);
        } else if (type == net.minecraft.world.entity.EntityType.WITHER_SKELETON) {
            head = new ItemStack(net.minecraft.world.item.Items.WITHER_SKELETON_SKULL);
        } else if (type == net.minecraft.world.entity.EntityType.CREEPER) {
            head = new ItemStack(net.minecraft.world.item.Items.CREEPER_HEAD);
        } else if (type == net.minecraft.world.entity.EntityType.PIGLIN) {
            head = new ItemStack(net.minecraft.world.item.Items.PIGLIN_HEAD);
        } else {
            return; // 无对应原版头颅的生物不掉（避免史蒂夫头）
        }
        ItemEntity drop = new ItemEntity(sp.level(),
                event.getEntity().getX(), event.getEntity().getY(), event.getEntity().getZ(),
                head);
        drop.setPickUpDelay(10);
        event.getDrops().add(drop);
    }

    /**
     * 方块掉落（时运类）：玩家挖掘方块时按掉落增幅放大掉落物数量，
     * 但仅限掉落表含"时运"加成函数的方块（如矿物；泥土/石头不受时运影响不放大）。
     * BlockDropsEvent 在原版掉落（含原版时运附魔）生成后触发 → 与原版时运叠加生效。
     * 顺序：先应用方块掉落倍率（放大数量），再自动熔炼（熔炼产物）→ 与其他技能兼容叠加。
     */
    @SubscribeEvent
    public static void onBlockDrops(net.neoforged.neoforge.event.level.BlockDropsEvent event) {
        if (event.getBreaker() instanceof ServerPlayer sp) {
            PlayerSkillRecord record = getRecord(sp);
            // 万物挖掘（ULT_BREAK_ALL）：不可破坏方块（基岩/屏障等，原版掉落表为空）挖掘后掉落对应方块。
            // 判断依据 getDestroySpeed < 0（基岩 -1.0F，原版"不可破坏"标准，不受 BlockStateMixin 影响）
            // ⚠️ 不能用 getDestroyProgress <= 0：Mixin 已把它改成黑曜石进度（>0），条件恒不成立！
            if (canBreakUnbreakable(sp)) {
                net.minecraft.world.level.block.state.BlockState state = event.getState();
                if (state.getDestroySpeed(event.getLevel(), event.getPos()) < 0
                        && event.getDrops().isEmpty()) {
                    Item blockItem = state.getBlock().asItem();
                    if (blockItem != null && blockItem != net.minecraft.world.item.Items.AIR) {
                        event.getDrops().add(new ItemEntity(sp.level(),
                                event.getPos().getX() + 0.5, event.getPos().getY() + 0.5, event.getPos().getZ() + 0.5,
                                new ItemStack(blockItem, 1)));
                    }
                }
            }
            // 自动熔炼（终极节点）：判断顺序【先判断熔炉 → 再时运 → 再技能增幅】
            //  1.【先判断熔炉】熔炉配方判断：把可熔炼的掉落物先换成成品（铁矿石×N → 铁锭×N）
            //  2.【再时运】时运额外掉落已含在掉落列表中（原版掉落阶段生效），熔炼保持数量一起烧
            //  3.【再技能增幅】方块掉落倍率最后应用，对成品同倍放大（铁锭×N → 铁锭×N×倍率）
            // ⚠️ 机械共鸣：假玩家（机器）需学习并开启 自动熔炼·共鸣 才继承自动熔炼
            if (SkillEffects.isEffectAllowedFor(sp, record, Skills.MACHINE_AUTO_SMELT)) {
                applyAutoSmelt(sp, event.getDrops(), record);
            }
            // ⚠️ 机械共鸣：假玩家（机器）需学习并开启 方块掉落·共鸣 才继承方块掉落倍率
            double mult = SkillEffects.isEffectAllowedFor(sp, record, Skills.MACHINE_BLOCK_DROP)
                    ? SkillEffects.getBlockDropMultiplier(record) : 1.0;
            if (mult > 1.0) {
                net.minecraft.resources.ResourceKey<LootTable> lootKey = event.getState().getBlock().getLootTable();
                if (lootKey != null && supportsFortune(lootKey, sp.serverLevel())) {
                    applyDropMultiplier(event.getDrops(), sp, mult);
                }
            }
            // 凋落物挪移（光环技能，2026-08-24）：掉落物直传绑定容器，不生成实体（防卡顿）
            // 放在最后：万物挖掘补掉落/自动熔炼/方块掉落倍率全部结算后再传送，收益最大化
            // 全送完 → 清空掉落列表（getDrops 是 mutable list），实体不生成，经验照常掉落
            if (LootVacuumEvents.tryVacuumDrops(sp, record, event.getDrops())) {
                event.getDrops().clear();
            }
        }
    }

    /**
     * 自动熔炼（终极节点 AUTO_SMELT）：把方块掉落物中可熔炼的物品熔炼成成品。
     * 判断顺序（以此为准）：【先判断熔炉 → 再时运 → 再技能增幅】
     *  1.【先判断熔炉】用熔炉配方 SmeltingRecipe 判断能否单次熔炼——铁/金/铜原矿→对应锭、
     *     沙→玻璃等；支持原版+模组所有熔炉可熔炼物品。不可熔炼直接跳过。
     *  2.【再时运】时运附魔的额外掉落已在原版掉落阶段生效（BlockDropsEvent 的掉落列表
     *     已含时运加成），这里保持数量（铁矿石×N → 铁锭×N，时运多掉的每份都熔炼）。
     *  3.【再技能增幅】方块掉落倍率由 onBlockDrops 在熔炼后统一应用（本方法不处理），
     *     对成品同倍放大（铁锭×N → 铁锭×N×倍率）。
     * ⚠️ 配方缓存：首次构建 Map<物品, 熔炼产物>，后续 O(1) 查找（大型整合包上千配方不卡顿）。
     * 经验不产生。不熔炼已有成品/不可熔炼物。
     */
    private static void applyAutoSmelt(ServerPlayer sp, java.util.Collection<net.minecraft.world.entity.item.ItemEntity> drops,
                                       PlayerSkillRecord record) {
        // 【先判断熔炉】技能已学且启用才继续
        if (record.getLearnedPoints(Skills.AUTO_SMELT) <= 0 || !record.isEnabled(Skills.AUTO_SMELT)) {
            return;
        }
        if (!(sp.level() instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return;
        }
        java.util.Map<Item, ItemStack> smeltMap = getSmeltMap(serverLevel); // 熔炉配方表
        for (net.minecraft.world.entity.item.ItemEntity drop : drops) {
            if (drop == null || drop.getItem().isEmpty() || drop.isRemoved()) {
                continue;
            }
            net.minecraft.world.item.ItemStack stack = drop.getItem();
            // ⚠️ 黑名单最先判定：黑名单中的物品（矿石/粗矿等）不做熔炼判定，当正常方块掉落
            if (isAutoSmeltBlacklisted(record, stack.getItem(), smeltMap)) {
                continue;
            }
            // 【再判断熔炉】熔炉配方匹配：不可熔炼直接跳过
            ItemStack result = smeltMap.get(stack.getItem());
            if (result == null || result.isEmpty()) {
                continue;
            }
            // 【再时运】保持时运额外掉落数量（N 份矿石 → N 份锭）
            net.minecraft.world.item.ItemStack smelted = result.copy();
            smelted.setCount(stack.getCount());
            drop.setItem(smelted);
            // 【再技能增幅】掉落倍率由 onBlockDrops 在熔炼后统一应用（本方法不处理）
        }
    }

    /** 熔炼配方缓存：物品 → 熔炼产物（懒构建；跟随配方管理器版本，/reload 后自动重建） */
    private static java.util.Map<Item, ItemStack> SMELT_CACHE = null;
    private static long SMELT_CACHE_TICK = -1;

    /**
     * 判断物品是否在自动熔炼黑名单中（2026-08-13 普适版）：黑名单中的掉落物不做熔炼判定，当正常方块处理。
     * 匹配规则：
     *  1. 精确匹配：黑名单项 == 掉落物（如添加 coal_ore 拦 coal_ore）
     *  2. 熔炼产物关联：黑名单矿石与掉落物熔炼出【同一种产物】→ 视为同类矿石（普适，不写死映射表）。
     *     例：添加 gold_ore，挖矿掉落 raw_gold（1.17+ 粗矿机制），两者都熔炼成 gold_ingot → 命中拦截。
     *     铁/铜同理；任何模组矿石（能熔炼成同种锭的）也自然生效。
     */
    public static boolean isAutoSmeltBlacklisted(PlayerSkillRecord record, Item item,
                                                 java.util.Map<Item, ItemStack> smeltMap) {
        var blacklist = record.getAutoSmeltBlacklist();
        // 1. 精确匹配
        if (blacklist.contains(item)) {
            return true;
        }
        // 2. 熔炼产物关联：掉落物需有熔炼产物（不可熔炼的直接不算同类）
        ItemStack dropResult = smeltMap.get(item);
        if (dropResult == null || dropResult.isEmpty()) {
            return false;
        }
        for (Item blackItem : blacklist) {
            if (blackItem == item) {
                continue;
            }
            ItemStack blackResult = smeltMap.get(blackItem);
            if (blackResult != null && !blackResult.isEmpty() && blackResult.is(dropResult.getItem())) {
                return true;
            }
        }
        return false;
    }
    /** 供其他模块复用熔炼配方表 */
    public static java.util.Map<Item, ItemStack> getSmeltMapPublic(net.minecraft.server.level.ServerLevel level) {
        return getSmeltMap(level);
    }

    /** 构建/复用熔炼配方表（物品→产物）；用【熔炉配方 SmeltingRecipe】判断能否单次熔炼 */
    private static java.util.Map<Item, ItemStack> getSmeltMap(net.minecraft.server.level.ServerLevel level) {
        var recipeManager = level.getServer().getRecipeManager();
        // 配方管理器每 /reload 会新建实例 → 用实例身份判断缓存是否过期（无需 tick）
        if (SMELT_CACHE == null || SMELT_CACHE_TICK != recipeManager.hashCode()) {
            SMELT_CACHE = new java.util.HashMap<>();
            SMELT_CACHE_TICK = recipeManager.hashCode();
            for (net.minecraft.world.item.crafting.RecipeHolder<net.minecraft.world.item.crafting.SmeltingRecipe> holder :
                    recipeManager.getAllRecipesFor(net.minecraft.world.item.crafting.RecipeType.SMELTING)) {
                var recipe = holder.value();
                var result = recipe.getResultItem(level.registryAccess());
                if (result.isEmpty()) {
                    continue;
                }
                for (net.minecraft.world.item.crafting.Ingredient ing : recipe.getIngredients()) {
                    for (net.minecraft.world.item.ItemStack input : ing.getItems()) {
                        SMELT_CACHE.putIfAbsent(input.getItem(), result);
                    }
                }
            }
        }
        return SMELT_CACHE;
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

    /** 是否为"矿石"方块（掉落表含时运加成；供自动熔炼判定普通方块/矿石，2026-08-13） */
    public static boolean isOreBlock(net.minecraft.world.level.block.state.BlockState state, net.minecraft.server.level.ServerLevel level) {
        net.minecraft.resources.ResourceKey<LootTable> key = state.getBlock().getLootTable();
        if (key == null) {
            return false;
        }
        return supportsFortune(key, level);
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
        // ⚠️ 2026-08-24 防御：反射字段初始化失败时为 null（static 块已降级为 warn 不崩溃）→ 功能禁用
        if (TABLE_POOLS == null || POOL_ENTRIES == null
                || ENTRY_CONDITIONS == null || ENTRY_FUNCTIONS == null
                || COMPOSITE_CHILDREN == null || ECI_ENCHANTMENT == null) {
            return false;
        }
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
     * 防刷物品（2026-08-26）：判断掉落物是否来自生物死亡瞬间的装备栏（玩家主动给予的装备）。
     * 战利品爆炸/生物掉落倍率跳过这类物品——玩家给生物塞装备再击杀会刷物品。
     * 战利品表掉落的物品（骨头/腐肉等）不在装备栏，不受影响。
     */
    private static boolean isEquippedItem(java.util.List<ItemStack> equippedSnapshot, ItemStack drop) {
        if (equippedSnapshot == null || equippedSnapshot.isEmpty() || drop == null || drop.isEmpty()) {
            return false;
        }
        for (ItemStack equipped : equippedSnapshot) {
            if (ItemStack.isSameItemSameComponents(equipped, drop)) {
                return true;
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
            // ⚠️ 机械共鸣：假玩家（机器）需学习并开启 经验获取·共鸣 才继承经验倍率
            double mult = SkillEffects.isEffectAllowedFor(sp, record, Skills.MACHINE_XP_GAIN)
                    ? SkillEffects.getExperienceMultiplier(record) : 1.0;
            if (mult > 1.0) {
                event.setDroppedExperience((int) Math.round(event.getOriginalExperience() * mult));
            }
        }
    }

    // ============ 工具耐久减免（采掘熟稔，Mixin 实现于 ItemStackMixin） ============

    /**
     * 村民交易技能（v1.3.0，2026-08-27）：交易完成后触发（NeoForge TradeWithVillagerEvent）。
     * <ul>
     *   <li>无限交易（UNLIMITED_TRADES）：resetUses() 把交易次数归 0 → 永不售罄、村民不用补货
     *       （参考 Tweakeroo disableVillagerTradeLocking：每次 use 后抬 maxUses；resetUses 更彻底）</li>
     *   <li>村民大师（VILLAGER_MASTER）：交易后村民 VillagerData 直接升到 5 级（满级）+ 刷新交易配方</li>
     * </ul>
     * 仅服务端真实记录判断（per-player，多人隔离）；流浪商人（WanderingTrader）不受村民大师影响。
     */
    @SubscribeEvent
    public static void onTradeWithVillager(net.neoforged.neoforge.event.entity.player.TradeWithVillagerEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) {
            return;
        }
        PlayerSkillRecord record = getRecord(sp);
        // ===== 无限交易：交易次数归 0（永不售罄） =====
        if (record.getLearnedPoints(Skills.UNLIMITED_TRADES) > 0 && record.isEnabled(Skills.UNLIMITED_TRADES)) {
            net.minecraft.world.item.trading.MerchantOffer offer = event.getMerchantOffer();
            if (offer != null && offer.getUses() > 0) {
                offer.resetUses(); // uses → 0，交易永不锁定
            }
        }
        // ===== 村民大师：村民直接满级（5 级）+ 逐级解锁全部交易配方 =====
        // ⚠️ 修复（2026-08-27 v2）：原实现 overrideOffers(null) 会清空原有交易，且 updateTrades 只加
        //    "当前等级"的交易集 → 2/3/4 级交易全丢。改为模拟原版 increaseMerchantCareer 逐级升级：
        //    每升一级 setLevel + updateTrades() 追加该级交易（原有 1 级交易保留，2-5 级依次追加）。
        if (record.getLearnedPoints(Skills.VILLAGER_MASTER) > 0 && record.isEnabled(Skills.VILLAGER_MASTER)) {
            if (event.getAbstractVillager() instanceof net.minecraft.world.entity.npc.Villager villager) {
                int currentLevel = villager.getVillagerData().getLevel();
                if (currentLevel < 5) {
                    // 先确保现有 offers 已生成（updateTrades 内部调 getOffers，需非 null 避免递归）
                    villager.getOffers();
                    // 反射调用 protected updateTrades()（编译环境无参签名）
                    java.lang.reflect.Method updateTrades = null;
                    try {
                        updateTrades = net.minecraft.world.entity.npc.AbstractVillager.class
                                .getDeclaredMethod("updateTrades");
                        updateTrades.setAccessible(true);
                    } catch (Exception ignored) {
                        // 反射失败（API 变动）→ 至少保证等级提升，交易下次打开菜单重建
                    }
                    for (int lv = currentLevel + 1; lv <= 5; lv++) {
                        villager.setVillagerData(villager.getVillagerData().setLevel(lv));
                        if (updateTrades != null) {
                            try {
                                updateTrades.invoke(villager); // 追加该级交易（2→3→4→5）
                            } catch (Exception ignored) {
                                break;
                            }
                        }
                    }
                    villager.setVillagerXp(1000000); // 经验远超过量
                }
            }
        }
    }

    /**
     * 不毁词条（终极节点 ULT_UNBREAK_TAG，2026-08-13 需求）：激活后在铁砧中
     * 放入两个相同的物品，可合成出带有【无法破坏】词条的工具（Unbreakable 组件，原版机制）。
     * <p>
     * 铁砧附魔（v1.3.0，2026-08-27）：检测左槽物品 + 右槽材料（青金石4=随机附魔 /
     * 青金石块2=附魔突破 / 下界之星2=超限附魔），随机给一个正面附魔或已有附魔+1级。
     */
    @SubscribeEvent
    public static void onAnvilUpdate(net.neoforged.neoforge.event.AnvilUpdateEvent event) {
        ItemStack left = event.getLeft();
        ItemStack right = event.getRight();
        if (left.isEmpty() || right.isEmpty()) {
            return;
        }
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }

        // ===== 铁砧附魔（v1.3.0）：右槽材料判定 =====
        // 技能判断：服务端查真实记录（权威），客户端用本地缓存（S2CPacket 校准，铁砧预览显示）
        if (right.is(Items.LAPIS_LAZULI)) {
            // 随机附魔：4 青金石 + 1 级经验，随机给一个该物品可拥有的正面附魔
            if (!isEnchantSkillEnabled(player, Skills.ENCHANT_RANDOM)) {
                return;
            }
            if (right.getCount() < 4) {
                return; // 材料不足
            }
            handleRandomEnchant(event, player, left, 4, 1);
            return;
        }
        if (right.is(Items.LAPIS_BLOCK)) {
            // 附魔突破：2 青金石块 + 4 级经验，所有已有附魔 +1 级（上限 20）
            if (!isEnchantSkillEnabled(player, Skills.ENCHANT_BREAK)) {
                return;
            }
            if (right.getCount() < 2) {
                return;
            }
            handleLevelUpEnchant(event, left, 2, 4, 20, 1);
            return;
        }
        if (right.is(Items.NETHER_STAR)) {
            // 超限附魔：2 下界之星 + 10 级经验，所有已有附魔 +2 级（上限 100）
            if (!isEnchantSkillEnabled(player, Skills.ENCHANT_OVER)) {
                return;
            }
            if (right.getCount() < 2) {
                return;
            }
            handleLevelUpEnchant(event, left, 2, 10, 100, 2);
            return;
        }

        // ===== 不毁词条：左右槽都是相同物品 =====
        if (left.getItem() != right.getItem()) {
            return;
        }
        if (!isEnchantSkillEnabled(player, Skills.ULT_UNBREAK_TAG)) {
            return;
        }
        // 输出：左槽物品副本 + 无法破坏组件（原版 Unbreakable，工具不再消耗耐久）
        ItemStack out = left.copy();
        out.set(net.minecraft.core.component.DataComponents.UNBREAKABLE,
                new net.minecraft.world.item.component.Unbreakable(true));
        event.setOutput(out);
        event.setCost(1); // 仅 1 级经验成本（合成费用低）
        event.setMaterialCost(1); // 消耗右槽 1 个
    }

    /**
     * 技能是否已学且开启（多人安全）：服务端查真实记录（权威，防作弊）；
     * 客户端用本地缓存（服务端 S2CPacket 校准），使铁砧 GUI 能显示预览。
     */
    private static boolean isEnchantSkillEnabled(Player player, String skillId) {
        if (player instanceof ServerPlayer sp) {
            PlayerSkillRecord record = getRecord(sp);
            return record.getLearnedPoints(skillId) > 0 && record.isEnabled(skillId);
        }
        // 客户端：本地缓存（服务端校准；懒加载安全，服务端不执行此分支）
        return org.zifeng.skilltree.client.ModKeyBindingEvents.isSkillEnabledClient(skillId);
    }

    /**
     * 随机附魔（ENCHANT_RANDOM）：遍历注册表所有附魔，过滤出
     * ①非诅咒 ②左槽物品可拥有 ③尚未拥有 ④与已有附魔不互斥的附魔，加权随机选一个，随机 1~最大等级。
     * 互斥检查（2026-08-26）：时运与精准采集只能拥有一种（原版 exclusiveSet）。
     */
    private static void handleRandomEnchant(net.neoforged.neoforge.event.AnvilUpdateEvent event, Player player,
                                            ItemStack left, int materialCost, int cost) {
        HolderLookup.RegistryLookup<Enchantment> reg = player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        ItemEnchantments has = left.getEnchantments();
        List<Holder.Reference<Enchantment>> candidates = new ArrayList<>();
        int totalWeight = 0;
        for (Holder.Reference<Enchantment> holder : reg.listElements().toList()) {
            Enchantment ench = holder.value();
            // ① 排除诅咒附魔（curse 标签） ② 该物品能否拥有 ③ 尚未拥有 ④ 与已有附魔互斥（时运/精准采集二选一）
            if (holder.is(EnchantmentTags.CURSE) || !ench.canEnchant(left) || has.getLevel(holder) > 0
                    || isExclusiveWithExisting(has, holder)) {
                continue;
            }
            candidates.add(holder);
            totalWeight += Math.max(1, ench.getWeight());
        }
        if (candidates.isEmpty()) {
            return; // 无可用附魔（如不可附魔的物品）
        }
        // 按权重随机选一个
        int roll = player.getRandom().nextInt(totalWeight);
        Holder.Reference<Enchantment> picked = candidates.get(0);
        for (Holder.Reference<Enchantment> holder : candidates) {
            roll -= Math.max(1, holder.value().getWeight());
            if (roll < 0) {
                picked = holder;
                break;
            }
        }
        // 随机等级 1 ~ 该附魔最大等级
        int level = 1 + player.getRandom().nextInt(Math.max(1, picked.value().getMaxLevel()));
        ItemStack out = left.copy();
        out.enchant(picked, level);
        event.setOutput(out);
        event.setCost(cost);
        event.setMaterialCost(materialCost);
    }

    /** 候选附魔是否与物品已有附魔互斥（原版 exclusiveSet：时运/精准采集等） */
    private static boolean isExclusiveWithExisting(ItemEnchantments has, Holder<Enchantment> candidate) {
        for (Holder<Enchantment> existing : has.keySet()) {
            if (!Enchantment.areCompatible(existing, candidate)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 附魔突破（ENCHANT_BREAK）/ 超限附魔（ENCHANT_OVER）：所有已有附魔一起升级
     * （突破 +1 / 超限 +2），单个附魔等级上限 maxLevel（突破=20，超限=100）。
     */
    private static void handleLevelUpEnchant(net.neoforged.neoforge.event.AnvilUpdateEvent event,
                                             ItemStack left, int materialCost, int cost, int maxLevel, int addLevel) {
        ItemEnchantments enchants = left.getEnchantments();
        if (enchants.isEmpty()) {
            return; // 没有附魔可突破
        }
        ItemStack out = left.copy();
        boolean changed = false;
        for (Holder<Enchantment> holder : enchants.keySet()) {
            int current = enchants.getLevel(holder);
            if (current >= maxLevel) {
                continue; // 已达上限，跳过
            }
            out.enchant(holder, Math.min(current + addLevel, maxLevel));
            changed = true;
        }
        if (!changed) {
            return; // 所有附魔都已达上限
        }
        event.setOutput(out);
        event.setCost(cost);
        event.setMaterialCost(materialCost);
    }

    /**
     * 万物挖掘（终极节点 ULT_BREAK_ALL，2026-08-13）：判断玩家是否能用镐子挖掘
     * 基岩等原版不可破坏方块。服务端查真实记录；客户端用本地缓存（S2CPacket 校准）。
     * 必须手持镐子（PickaxeItem）才生效。
     */
    public static boolean canBreakUnbreakable(Player player) {
        if (player == null) {
            return false;
        }
        // 必须手持镐子
        if (!(player.getMainHandItem().getItem() instanceof net.minecraft.world.item.PickaxeItem)) {
            return false;
        }
        if (player instanceof ServerPlayer sp) {
            PlayerSkillRecord record = getRecord(sp);
            return record.getLearnedPoints(Skills.ULT_BREAK_ALL) > 0 && record.isEnabled(Skills.ULT_BREAK_ALL);
        }
        // 客户端：本地缓存（服务端 SkillTreeDataS2CPacket 校准；懒加载安全，服务端不执行此分支）
        return org.zifeng.skilltree.client.ModKeyBindingEvents.isSkillEnabledClient(Skills.ULT_BREAK_ALL);
    }

    private static PlayerSkillRecord getRecord(ServerPlayer player) {
        // 防御：登出瞬间 serverLevel 可能为 null（多模组环境下事件时序不可控）
        if (player == null || player.serverLevel() == null) {
            return new PlayerSkillRecord(player != null ? player.getUUID() : java.util.UUID.randomUUID());
        }
        return PlayerSkillSavedData.get(player.serverLevel()).getOrCreatePlayer(player.getUUID());
    }
}
