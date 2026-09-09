package org.zifeng.skilltree.skill;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.zifeng.skilltree.SkillTreeMod;
import org.zifeng.skilltree.data.PlayerSkillRecord;

import java.util.List;
import java.util.function.DoubleSupplier;

/**
 * 技能效果应用（统一公式）：
 * <pre>
 *   最终属性 = 全部基础固定数值总和 × (1 + 全部特殊百分比增幅总和)
 * </pre>
 * 实现方式（利用原版 AttributeModifier 计算顺序，天然符合统一公式）：
 * <ul>
 *   <li>基础技能固定值 → ADD_NUMBER 修饰符（叠加到 baseValue 上）</li>
 *   <li>增幅技能百分比 → MULTIPLY_TOTAL 修饰符（乘算 1+Σ增幅）</li>
 *   <li>全能精通 → 额外给所有属性一个 MULTIPLY_TOTAL +25%</li>
 * </ul>
 * 原版计算顺序：base + ADD_NUMBER，再 ×(1+ADD_MULTIPLIED_BASE)，再 ×(1+MULTIPLY_TOTAL)
 * 恰好实现：基础总和 × (1+增幅总和)。
 */
public final class SkillEffects {
    private SkillEffects() {
    }

    /**
     * 机器继承判定（机械共鸣系统）：真玩家总是生效；假玩家（模拟玩家机器，如数字型采矿机）
     * 需对应【共鸣技能】已学且开启才允许继承对应技能效果。
     * <p>设计意图：默认所有技能只对真玩家生效；学习共鸣技能并开启后，
     * 机器（FakePlayer 以主人 UUID 触发事件）才能继承掉落/熔炼/伤害类技能效果。
     * 关闭或重置共鸣技能 → 立即回收（事件每次实时判定，无持久状态）。</p>
     * @param player 触发事件的玩家（可能是假玩家）
     * @param record 玩家技能记录
     * @param resonanceSkillId 对应的机械共鸣技能 ID（如 Skills.MACHINE_AUTO_SMELT）
     * @return true = 该效果对当前玩家生效
     */
    public static boolean isEffectAllowedFor(ServerPlayer player, PlayerSkillRecord record, String resonanceSkillId) {
        if (!(player instanceof net.minecraftforge.common.util.FakePlayer)) {
            return true; // 真玩家：无需共鸣，直接生效
        }
        // 假玩家（模拟玩家机器）：需共鸣技能已学且开启
        return record.getLearnedPoints(resonanceSkillId) > 0 && record.isEnabled(resonanceSkillId);
    }

    public static final ResourceLocation MASTER_MOD = new ResourceLocation(SkillTreeMod.MOD_ID, "skill_master");

    /** 杀戮光环·伤害：每级 +5% 攻击伤害倍率（MULTIPLY_TOTAL，独立于基础/增幅技能） */
    public static final ResourceLocation AURA_DAMAGE_MOD = new ResourceLocation(SkillTreeMod.MOD_ID, "aura_damage_mult");

    /** 浴血奋战：常驻攻击力增幅（MULTIPLY_TOTAL） */
    public static final ResourceLocation BLOOD_ATTACK_MOD = new ResourceLocation(SkillTreeMod.MOD_ID, "blood_attack_bonus");

    /** 浴血奋战：常驻最大生命增幅（MULTIPLY_TOTAL） */
    public static final ResourceLocation BLOOD_HEALTH_MOD = new ResourceLocation(SkillTreeMod.MOD_ID, "blood_health_bonus");

    // ============ 技能属性统一注册表（Z-Link 原子 A 重构，2026-09-09） ============
    // 原 3 张表（BASE/AMPLIFY/终极属性）+ applyAll/getComputedValue 里的"特判技能"（全能精通/光环伤害/浴血）
    // 全部收敛成一张统一表 + 统一遍历。行为与旧版逐项一致，仅"数据"和"执行"分离：
    //   · 加新属性技能 = 表里加一行，applyAll/getComputedValue 自动生效，不再三处手写。
    //   · 每项含操作类型：ADD=加算(ADD_VALUE) / MULT=乘算(ADD_MULTIPLIED_TOTAL)。
    private enum Op {
        ADD, MULT
    }

    private record AttrEntry(String skillId, Attribute attribute, DoubleSupplier perPoint, Op op) {
    }

    /** 全能精通：全员乘算（特判项，量 = 是否点亮开启） */
    private static final String MASTER_SKILL = Skills.ULT_MASTER;
    /** 浴血奋战：攻击/生命 乘算（特判项，量 = 是否点亮开启） */
    private static final String BLOOD_SKILL = Skills.ULT_BLOOD;
    /** 杀戮光环·伤害：攻击乘算（特判项，量 = 生效等级×每级） */
    private static final String AURA_DMG_SKILL = Skills.AURA_DAMAGE;

    /** 全技能属性统一表（顺序 = 应用顺序，与旧 applyAll 完全一致） */
    private static final List<AttrEntry> ATTR_TABLE = new java.util.ArrayList<>() {{
        // 1. 基础固定值（ADD）
        add(new AttrEntry(Skills.BODY_HP, Attributes.MAX_HEALTH, () -> org.zifeng.skilltree.Config.BODY_HP_PER_POINT.get(), Op.ADD));
        add(new AttrEntry(Skills.BODY, Attributes.ARMOR, () -> org.zifeng.skilltree.Config.BODY_ARMOR_PER_POINT.get(), Op.ADD));
        add(new AttrEntry(Skills.BODY, org.zifeng.skilltree.init.ModAttributes.DAMAGE_REDUCTION.get(), () -> org.zifeng.skilltree.Config.BODY_DR_PER_POINT.get(), Op.ADD));
        add(new AttrEntry(Skills.TOUGH, Attributes.ARMOR_TOUGHNESS, () -> org.zifeng.skilltree.Config.TOUGH_TOUGHNESS_PER_POINT.get(), Op.ADD));
        add(new AttrEntry(Skills.TOUGH, Attributes.KNOCKBACK_RESISTANCE, () -> org.zifeng.skilltree.Config.TOUGH_KB_PER_POINT.get(), Op.ADD));
        add(new AttrEntry(Skills.BLADE, Attributes.ATTACK_DAMAGE, () -> org.zifeng.skilltree.Config.BLADE_DAMAGE_PER_POINT.get(), Op.ADD));
        add(new AttrEntry(Skills.ATTACK_SPEED, Attributes.ATTACK_SPEED, () -> org.zifeng.skilltree.Config.ATTACK_SPEED_PER_POINT.get(), Op.ADD));
        add(new AttrEntry(Skills.MINING, org.zifeng.skilltree.init.ModAttributes.MINING_EFFICIENCY.get(), () -> org.zifeng.skilltree.Config.MINING_SPEED_PER_POINT.get(), Op.ADD));
        add(new AttrEntry(Skills.MOVE, Attributes.MOVEMENT_SPEED, () -> org.zifeng.skilltree.Config.MOVE_SPEED_PER_POINT.get(), Op.ADD));
        add(new AttrEntry(Skills.LUCK, Attributes.LUCK, () -> org.zifeng.skilltree.Config.LUCK_PER_POINT.get(), Op.ADD));
        add(new AttrEntry(Skills.JUMP, Attributes.JUMP_STRENGTH, () -> org.zifeng.skilltree.Config.JUMP_PER_POINT.get(), Op.ADD));
        add(new AttrEntry(Skills.FLY, Attributes.FLYING_SPEED, () -> org.zifeng.skilltree.Config.FLY_SPEED_PER_POINT.get(), Op.ADD));
        add(new AttrEntry(Skills.SWIM, net.minecraftforge.common.ForgeMod.SWIM_SPEED.get(), () -> org.zifeng.skilltree.Config.SWIM_SPEED_PER_POINT.get(), Op.ADD));
        // 防御强化（增幅列）：物理减伤乘算层（独立于护甲，放基础组末尾保持应用顺序）
        add(new AttrEntry(Skills.AMP_ARMOR, org.zifeng.skilltree.init.ModAttributes.DAMAGE_REDUCTION.get(), () -> org.zifeng.skilltree.Config.AMP_ARMOR_DR_PER_POINT.get(), Op.ADD));
        // 2. 增幅百分比（MULT）
        add(new AttrEntry(Skills.AMP_HP, Attributes.MAX_HEALTH, () -> org.zifeng.skilltree.Config.AMP_HP_PER_POINT.get(), Op.MULT));
        add(new AttrEntry(Skills.AMP_TOUGH, Attributes.ARMOR_TOUGHNESS, () -> org.zifeng.skilltree.Config.AMP_TOUGH_PER_POINT.get(), Op.MULT));
        add(new AttrEntry(Skills.AMP_TOUGH, Attributes.KNOCKBACK_RESISTANCE, () -> org.zifeng.skilltree.Config.AMP_TOUGH_PER_POINT.get(), Op.MULT));
        add(new AttrEntry(Skills.AMP_LUCK, Attributes.LUCK, () -> org.zifeng.skilltree.Config.AMP_LUCK_PER_POINT.get(), Op.MULT));
        add(new AttrEntry(Skills.AMP_DAMAGE, Attributes.ATTACK_DAMAGE, () -> org.zifeng.skilltree.Config.AMP_DAMAGE_PER_POINT.get(), Op.MULT));
        add(new AttrEntry(Skills.AMP_ATTACK_SPEED, Attributes.ATTACK_SPEED, () -> org.zifeng.skilltree.Config.AMP_ATTACK_SPEED_PER_POINT.get(), Op.MULT));
        add(new AttrEntry(Skills.AMP_MINING, org.zifeng.skilltree.init.ModAttributes.MINING_EFFICIENCY.get(), () -> org.zifeng.skilltree.Config.AMP_MINING_PER_POINT.get(), Op.MULT));
        add(new AttrEntry(Skills.AMP_MOVE, Attributes.MOVEMENT_SPEED, () -> org.zifeng.skilltree.Config.AMP_MOVE_PER_POINT.get(), Op.MULT));
        add(new AttrEntry(Skills.AMP_JUMP, Attributes.JUMP_STRENGTH, () -> org.zifeng.skilltree.Config.AMP_JUMP_PER_POINT.get(), Op.MULT));
        add(new AttrEntry(Skills.AMP_FLY, Attributes.FLYING_SPEED, () -> org.zifeng.skilltree.Config.AMP_FLY_PER_POINT.get(), Op.MULT));
        add(new AttrEntry(Skills.AMP_SWIM, net.minecraftforge.common.ForgeMod.SWIM_SPEED.get(), () -> org.zifeng.skilltree.Config.AMP_SWIM_PER_POINT.get(), Op.MULT));
        // 2.5 节点类多级终极（接触距离双属性 / 击退抗性）：ADD
        add(new AttrEntry(Skills.REACH, net.minecraftforge.common.ForgeMod.ENTITY_REACH.get(), () -> org.zifeng.skilltree.Config.REACH_PER_LEVEL.get(), Op.ADD));
        add(new AttrEntry(Skills.REACH, net.minecraftforge.common.ForgeMod.BLOCK_REACH.get(), () -> org.zifeng.skilltree.Config.REACH_PER_LEVEL.get(), Op.ADD));
        add(new AttrEntry(Skills.ULT_KB_RESIST, Attributes.KNOCKBACK_RESISTANCE, () -> org.zifeng.skilltree.Config.KB_RESIST_PER_LEVEL.get(), Op.ADD));
    }};

    /** 全能精通适用属性集（= 旧 BASE_SKILLS 去重属性；⚠️ 严格等价旧版：不含接触距离 REACH 等终极属性） */
    private static final java.util.Set<Attribute> MASTER_ATTRS = java.util.Set.of(
            Attributes.MAX_HEALTH, Attributes.ARMOR,
            org.zifeng.skilltree.init.ModAttributes.DAMAGE_REDUCTION.get(),
            Attributes.ARMOR_TOUGHNESS, Attributes.KNOCKBACK_RESISTANCE,
            Attributes.ATTACK_DAMAGE, Attributes.ATTACK_SPEED,
            org.zifeng.skilltree.init.ModAttributes.MINING_EFFICIENCY.get(),
            Attributes.MOVEMENT_SPEED, Attributes.LUCK, Attributes.JUMP_STRENGTH,
            Attributes.FLYING_SPEED, net.minecraftforge.common.ForgeMod.SWIM_SPEED.get());

    /**
     * 本地计算某属性经技能加成后的总值（客户端属性面板用，不依赖服务端属性同步，实时生效）：
     * <pre>(玩家基础值 + Σ基础固定值) × (1 + Σ增幅倍率 + 全能精通25%)</pre>
     * 与 {@link #applyAll} 服务端逻辑保持一致（基础 ADD_VALUE、增幅 ADD_MULTIPLIED_TOTAL）。
     * 注意：不含装备/药水等外部修饰符。
     */
    public static double getComputedValue(net.minecraft.world.entity.player.Player player,
                                          Attribute attribute, PlayerSkillRecord record) {
        AttributeInstance instance = player.getAttribute(attribute);
        double base = instance != null ? instance.getBaseValue() : 0;
        double add = 0;
        double mult = 0;
        // 统一表：ADD → 加算；MULT → 乘算
        for (AttrEntry e : ATTR_TABLE) {
            if (!e.attribute().equals(attribute)) {
                continue;
            }
            int points = record.isEnabled(e.skillId()) ? record.getActiveLevel(e.skillId()) : 0;
            double amount = points * e.perPoint().getAsDouble();
            if (e.op() == Op.ADD) {
                add += amount;
            } else {
                mult += amount;
            }
        }
        // 特判：全能精通（仅对其适用属性集；与 applyAll 一致）
        if (MASTER_ATTRS.contains(attribute)) {
            boolean master = record.getLearnedPoints(MASTER_SKILL) > 0 && record.isEnabled(MASTER_SKILL);
            if (master) {
                mult += org.zifeng.skilltree.Config.MASTER_BONUS.get();
            }
        }
        // 特判：杀戮光环·伤害 → 仅攻击属性乘算（每级 +X%）
        if (Attributes.ATTACK_DAMAGE.equals(attribute)) {
            int auraDmg = record.isEnabled(AURA_DMG_SKILL) ? record.getActiveLevel(AURA_DMG_SKILL) : 0;
            mult += auraDmg * org.zifeng.skilltree.Config.AURA_DAMAGE_MULTIPLIER_PER_LEVEL.get();
        }
        // 特判：浴血奋战 → 攻击 +X% / 生命 +X%
        boolean blood = record.getLearnedPoints(BLOOD_SKILL) > 0 && record.isEnabled(BLOOD_SKILL);
        if (blood) {
            if (Attributes.ATTACK_DAMAGE.equals(attribute)) {
                mult += org.zifeng.skilltree.Config.BLOOD_ATTACK_BONUS.get();
            }
            if (Attributes.MAX_HEALTH.equals(attribute)) {
                mult += org.zifeng.skilltree.Config.BLOOD_HEALTH_BONUS.get();
            }
        }
        return (base + add) * (1 + mult);
    }

    /**
     * 重新应用全部属性修饰符（幂等，可安全重挂）。关闭/调低生效等级会立即移除对应修饰符。
     * 每个技能的修饰符 id 独立（skillId 区分），避免同属性技能互相覆盖。
     * <p>Z-Link 原子 A 重构（2026-09-09）：原"3 表 + 特判手写段"收敛为统一表遍历 + 统一特判，
     * 行为与旧版一致（ADD → ADD_VALUE 加算；MULT → ADD_MULTIPLIED_TOTAL 乘算）。
     * 旧三张表（BASE_SKILLS/AMPLIFY_SKILLS/MULTI_ULTIMATE_ATTRS）已并入 ATTR_TABLE。
     */
    public static void applyAll(ServerPlayer player, PlayerSkillRecord record) {
        // 1~2.5 全部属性项（基础 ADD / 增幅 MULT / 节点终极 ADD）——统一遍历
        for (AttrEntry e : ATTR_TABLE) {
            int points = record.isEnabled(e.skillId()) ? record.getActiveLevel(e.skillId()) : 0;
            double amount = points * e.perPoint().getAsDouble();
            // 防御强化（AMP_ARMOR）原在 BASE 组却走 ADD_VALUE（物理减伤加算层），保持 op=ADD 语义一致
            if (e.op() == Op.ADD) {
                applyAddValue(player, e.attribute(), attrModId(e.skillId()), amount);
            } else {
                applyMultiplier(player, e.attribute(), attrModId(e.skillId()), amount);
            }
        }
        // 3. 全能精通：受影响属性（旧 BASE_SKILLS 属性集）+增幅（乘算；未解锁/关闭 → amount=0 移除）
        double masterAmount = (record.getLearnedPoints(MASTER_SKILL) > 0 && record.isEnabled(MASTER_SKILL))
                ? org.zifeng.skilltree.Config.MASTER_BONUS.get() : 0;
        for (Attribute a : MASTER_ATTRS) {
            applyMultiplier(player, a, MASTER_MOD, masterAmount);
        }
        // 3.5 杀戮光环·伤害：每级 +X% 攻击伤害（乘算，独立修饰符；关闭/未学 → amount=0 移除）
        int auraDmg = record.isEnabled(AURA_DMG_SKILL) ? record.getActiveLevel(AURA_DMG_SKILL) : 0;
        applyMultiplier(player, Attributes.ATTACK_DAMAGE, AURA_DAMAGE_MOD,
                auraDmg * org.zifeng.skilltree.Config.AURA_DAMAGE_MULTIPLIER_PER_LEVEL.get());
        // 3.6 浴血奋战：常驻攻击 +X%、生命 +X%（点亮且启用才加；关闭/未学 → amount=0 移除）
        boolean blood = record.getLearnedPoints(BLOOD_SKILL) > 0 && record.isEnabled(BLOOD_SKILL);
        applyMultiplier(player, Attributes.ATTACK_DAMAGE, BLOOD_ATTACK_MOD,
                blood ? org.zifeng.skilltree.Config.BLOOD_ATTACK_BONUS.get() : 0);
        applyMultiplier(player, Attributes.MAX_HEALTH, BLOOD_HEALTH_MOD,
                blood ? org.zifeng.skilltree.Config.BLOOD_HEALTH_BONUS.get() : 0);
        // 3.7 魔法增幅（MAGIC 列）：全部反射兼容，未装模组自动跳过
        // 新生魔艺：最大魔力 ×(1+10%/级)、魔力恢复 ×(1+40%/级)
        double arsManaAmp = record.isEnabled(Skills.MANA_AMP) ? record.getActiveLevel(Skills.MANA_AMP) * 0.1 : 0;
        org.zifeng.skilltree.compat.ArsNouveauCompat.applyManaAmp(player, arsManaAmp);
        double arsManaRegen = record.isEnabled(Skills.ARS_MANA_REGEN) ? record.getActiveLevel(Skills.ARS_MANA_REGEN) * 0.4 : 0;
        org.zifeng.skilltree.compat.ArsNouveauCompat.applyManaRegenAmp(player, arsManaRegen);
        // 铁魔法：最大魔力 ×(1+10%/级)、魔力恢复 ×(1+40%/级)、吟唱缩减 ×(1+10%/级)、9流派强度 ×(1+10%/级)
        double ironManaAmp = record.isEnabled(Skills.IRON_MANA_AMP) ? record.getActiveLevel(Skills.IRON_MANA_AMP) * 0.1 : 0;
        org.zifeng.skilltree.compat.IronSpellsCompat.applyMaxManaAmp(player, ironManaAmp);
        double ironManaRegen = record.isEnabled(Skills.IRON_MANA_REGEN) ? record.getActiveLevel(Skills.IRON_MANA_REGEN) * 0.4 : 0;
        org.zifeng.skilltree.compat.IronSpellsCompat.applyManaRegenAmp(player, ironManaRegen);
        double ironCastTime = record.isEnabled(Skills.IRON_CAST_TIME) ? record.getActiveLevel(Skills.IRON_CAST_TIME) * 0.1 : 0;
        org.zifeng.skilltree.compat.IronSpellsCompat.applyCastTimeReduction(player, ironCastTime);
        double ironCooldown = record.isEnabled(Skills.IRON_COOLDOWN) ? record.getActiveLevel(Skills.IRON_COOLDOWN) * 0.1 : 0;
        org.zifeng.skilltree.compat.IronSpellsCompat.applyCooldownReduction(player, ironCooldown);
        org.zifeng.skilltree.compat.IronSpellsCompat.applySchoolPowerAmp(player, "fire",
                record.isEnabled(Skills.IRON_FIRE) ? record.getActiveLevel(Skills.IRON_FIRE) * 0.1 : 0);
        org.zifeng.skilltree.compat.IronSpellsCompat.applySchoolPowerAmp(player, "ice",
                record.isEnabled(Skills.IRON_ICE) ? record.getActiveLevel(Skills.IRON_ICE) * 0.1 : 0);
        org.zifeng.skilltree.compat.IronSpellsCompat.applySchoolPowerAmp(player, "lightning",
                record.isEnabled(Skills.IRON_LIGHTNING) ? record.getActiveLevel(Skills.IRON_LIGHTNING) * 0.1 : 0);
        org.zifeng.skilltree.compat.IronSpellsCompat.applySchoolPowerAmp(player, "holy",
                record.isEnabled(Skills.IRON_HOLY) ? record.getActiveLevel(Skills.IRON_HOLY) * 0.1 : 0);
        org.zifeng.skilltree.compat.IronSpellsCompat.applySchoolPowerAmp(player, "ender",
                record.isEnabled(Skills.IRON_ENDER) ? record.getActiveLevel(Skills.IRON_ENDER) * 0.1 : 0);
        org.zifeng.skilltree.compat.IronSpellsCompat.applySchoolPowerAmp(player, "blood",
                record.isEnabled(Skills.IRON_BLOOD) ? record.getActiveLevel(Skills.IRON_BLOOD) * 0.1 : 0);
        org.zifeng.skilltree.compat.IronSpellsCompat.applySchoolPowerAmp(player, "evocation",
                record.isEnabled(Skills.IRON_EVOCATION) ? record.getActiveLevel(Skills.IRON_EVOCATION) * 0.1 : 0);
        org.zifeng.skilltree.compat.IronSpellsCompat.applySchoolPowerAmp(player, "nature",
                record.isEnabled(Skills.IRON_NATURE) ? record.getActiveLevel(Skills.IRON_NATURE) * 0.1 : 0);
        org.zifeng.skilltree.compat.IronSpellsCompat.applySchoolPowerAmp(player, "eldritch",
                record.isEnabled(Skills.IRON_ELDRITCH) ? record.getActiveLevel(Skills.IRON_ELDRITCH) * 0.1 : 0);
        // 4. 生命值同步（避免加血上限后血量不涨）
        AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null && player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    /** 属性修饰符 id（按技能区分；ADD 前缀 skill_base_ / MULT 前缀 skill_amp_，保持与旧版一致的区分，防同属性技能互相覆盖） */
    private static ResourceLocation attrModId(String skillId) {
        // 查表确定该技能的 op，决定前缀
        for (AttrEntry e : ATTR_TABLE) {
            if (e.skillId().equals(skillId)) {
                return e.op() == Op.MULT
                        ? new ResourceLocation(SkillTreeMod.MOD_ID, "skill_amp_" + skillId)
                        : new ResourceLocation(SkillTreeMod.MOD_ID, "skill_base_" + skillId);
            }
        }
        // 表外（不应发生）：给基础前缀，防 NPE
        return new ResourceLocation(SkillTreeMod.MOD_ID, "skill_base_" + skillId);
    }

    /** 1.20.1：AttributeInstance.removeModifier 只接受 UUID（1.20.5+ 才接受 ResourceLocation）；由 id 派生确定性 UUID */
    private static java.util.UUID modUuid(ResourceLocation id) {
        return java.util.UUID.nameUUIDFromBytes(id.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static void applyAddValue(ServerPlayer player, Attribute attribute, ResourceLocation id, double amount) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) return;
        java.util.UUID uuid = modUuid(id);
        instance.removeModifier(uuid);
        if (amount != 0) {
            instance.addTransientModifier(new AttributeModifier(uuid, id.toString(), amount, AttributeModifier.Operation.ADDITION));
        }
    }

    private static void applyMultiplier(ServerPlayer player, Attribute attribute, ResourceLocation id, double amount) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) return;
        java.util.UUID uuid = modUuid(id);
        instance.removeModifier(uuid);
        if (amount != 0) {
            instance.addTransientModifier(new AttributeModifier(uuid, id.toString(), amount, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    /** 当前生命恢复速率（基础 + 再生体魄 + 生命涌泉增幅），用于每 tick 回血。尊重生效等级与开关 */
    public static double getRegenPerSecond(PlayerSkillRecord record) {
        double base = record.isEnabled(Skills.REGEN)
                ? record.getActiveLevel(Skills.REGEN) * 0.1 : 0;
        if (base <= 0) {
            return 0;
        }
        double amp = record.isEnabled(Skills.AMP_REGEN)
                ? record.getActiveLevel(Skills.AMP_REGEN) * 0.08 : 0; // 1.2.3 ×10：每点 +16%
        return base * (1 + amp);
    }

    /** 怪物掉落倍率（生物掉落倍率终极技能；尊重生效等级与开关）：每级 +1 倍，1级=2倍，10级=11倍 */
    public static double getMobDropMultiplier(PlayerSkillRecord record) {
        double points = record.isEnabled(Skills.MOB_DROP)
                ? record.getActiveLevel(Skills.MOB_DROP) : 0;
        return 1 + points;
    }

    /** 方块掉落倍率（方块掉落倍率终极技能；尊重生效等级与开关）：每级 +1 倍 */
    public static double getBlockDropMultiplier(PlayerSkillRecord record) {
        double points = record.isEnabled(Skills.BLOCK_DROP)
                ? record.getActiveLevel(Skills.BLOCK_DROP) : 0;
        return 1 + points;
    }

    /** 经验获取倍率（经验获取倍率终极技能；尊重生效等级与开关）：每级 +2 倍，1级=3倍，10级=21倍 */
    public static double getExperienceMultiplier(PlayerSkillRecord record) {
        double points = record.isEnabled(Skills.XP_GAIN)
                ? record.getActiveLevel(Skills.XP_GAIN) : 0;
        return 1 + points * 2;
    }

    /** 工具耐久损耗减免（工具不毁终极技能；尊重生效等级与开关）：每级 +20%，封顶 100%（工具不消耗耐久） */
    public static double getToolDurabilityReduction(PlayerSkillRecord record) {
        double points = record.isEnabled(Skills.UNBREAKABLE)
                ? record.getActiveLevel(Skills.UNBREAKABLE) : 0;
        return Math.min(1.0, points * 0.2);
    }

    /** 技能点获取速度（全能精通 -20% 默认，Config 可调，需开关启用） */
    public static double getSkillPointRate(PlayerSkillRecord record) {
        return record.getLearnedPoints(Skills.ULT_MASTER) > 0 && record.isEnabled(Skills.ULT_MASTER)
                ? org.zifeng.skilltree.Config.MASTER_SKILL_POINT_RATE.get() : 1.0;
    }

    /** 魔法增幅：新生魔艺魔力倍率（每级 +10%：level × 0.1；需技能启用） */
    public static double getManaAmpPercent(PlayerSkillRecord record) {
        return record.isEnabled(Skills.MANA_AMP)
                ? record.getActiveLevel(Skills.MANA_AMP) * 0.1 : 0;
    }

    /** 通用魔法增幅倍率（每级 +ratio/级；需技能启用） */
    private static double magicAmp(PlayerSkillRecord record, String skillId, double perLevel) {
        return record.isEnabled(skillId) ? record.getActiveLevel(skillId) * perLevel : 0;
    }

    /** 新生魔艺魔力恢复倍率（每级 +40%：level × 0.4；需技能启用） */
    public static double getArsManaRegenPercent(PlayerSkillRecord record) {
        return magicAmp(record, Skills.ARS_MANA_REGEN, 0.4);
    }

    /** 铁魔法魔力倍率（每级 +10%：level × 0.1；需技能启用） */
    public static double getIronManaAmpPercent(PlayerSkillRecord record) {
        return magicAmp(record, Skills.IRON_MANA_AMP, 0.1);
    }

    /** 铁魔法魔力恢复倍率（每级 +40%：level × 0.4；需技能启用） */
    public static double getIronManaRegenPercent(PlayerSkillRecord record) {
        return magicAmp(record, Skills.IRON_MANA_REGEN, 0.4);
    }

    /** 铁魔法吟唱缩减倍率（每级 +10%：level × 0.1；需技能启用） */
    public static double getIronCastTimePercent(PlayerSkillRecord record) {
        return magicAmp(record, Skills.IRON_CAST_TIME, 0.1);
    }

    /** 铁魔法法术冷却缩减倍率（每级 -10%：level × 0.1；需技能启用） */
    public static double getIronCooldownPercent(PlayerSkillRecord record) {
        return magicAmp(record, Skills.IRON_COOLDOWN, 0.1);
    }

    /** 铁魔法流派法术强度倍率（每级 +10%：level × 0.1；需技能启用） */
    public static double getIronSchoolPercent(PlayerSkillRecord record, String schoolSkillId) {
        return magicAmp(record, schoolSkillId, 0.1);
    }

    // ============ 暴击 / 吸血 / 治愈光环（事件驱动，非属性，尊重技能开关） ============

    /** 暴击几率（0~1，暴击精通，100% 封顶；需技能启用） */
    public static double getCritChance(PlayerSkillRecord record) {
        if (!record.isEnabled(Skills.CRIT)) {
            return 0;
        }
        double chance = record.getActiveLevel(Skills.CRIT) * org.zifeng.skilltree.Config.CRIT_CHANCE_PER_POINT.get();
        return Math.min(1.0, chance);
    }

    /** 暴击伤害倍率（暴击基础倍率 × (1 + 暴击增幅)；需增幅启用） */
    public static double getCritMultiplier(PlayerSkillRecord record) {
        double base = org.zifeng.skilltree.Config.CRIT_DAMAGE_BASE.get();
        double amp = record.isEnabled(Skills.AMP_CRIT)
                ? record.getActiveLevel(Skills.AMP_CRIT) * org.zifeng.skilltree.Config.CRIT_DAMAGE_PER_POINT.get() : 0;
        return base * (1 + amp);
    }

    /** 吸血率（生命汲取 × (1 + 吸血增幅)；需技能启用） */
    public static double getLifestealRate(PlayerSkillRecord record) {
        double rate = record.isEnabled(Skills.LIFESTEAL)
                ? Math.min(1.0, record.getActiveLevel(Skills.LIFESTEAL) * org.zifeng.skilltree.Config.LIFESTEAL_PER_POINT.get()) : 0;
        if (rate <= 0) {
            return 0;
        }
        double amp = record.isEnabled(Skills.AMP_LIFESTEAL)
                ? record.getActiveLevel(Skills.AMP_LIFESTEAL) * org.zifeng.skilltree.Config.LIFESTEAL_AMP_PER_POINT.get() : 0;
        return rate * (1 + amp);
    }

    /** 荆棘反伤值（荆棘反伤 × (1 + 荆棘强化)；需技能启用） */
    public static double getThornsDamage(PlayerSkillRecord record) {
        double base = record.isEnabled(Skills.THORNS)
                ? record.getActiveLevel(Skills.THORNS) * 0.05 : 0;
        if (base <= 0) {
            return 0;
        }
        double amp = record.isEnabled(Skills.AMP_THORNS)
                ? record.getActiveLevel(Skills.AMP_THORNS) * 0.04 : 0; // 1.2.3 ×10：每点 +8%
        return base * (1 + amp);
    }

    /** 破甲增伤比例（0~1：破甲精通每点 +0.15% 最终伤害 ×(1 + 破甲增幅)；需技能启用） */
    public static double getArmorPenPercent(PlayerSkillRecord record) {
        double base = record.isEnabled(Skills.ARMOR_PEN)
                ? record.getActiveLevel(Skills.ARMOR_PEN) * 0.0015 : 0;
        if (base <= 0) {
            return 0;
        }
        double amp = record.isEnabled(Skills.AMP_ARMOR_PEN)
                ? record.getActiveLevel(Skills.AMP_ARMOR_PEN) * 0.04 : 0; // 1.2.3 ×10：每点 +8%
        return base * (1 + amp);
    }

    /** 治愈光环：作用半径（格，Config 可调，默认 10 = xyz 三轴全 10） */
    public static double getAuraHealRadius() {
        return org.zifeng.skilltree.Config.AURA_HEAL_RADIUS.get();
    }
}
