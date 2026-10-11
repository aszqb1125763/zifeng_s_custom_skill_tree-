package org.zifeng.skilltree.init;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraftforge.registries.DeferredRegister;
import org.zifeng.skilltree.SkillTreeMod;

import java.util.function.Supplier;

/**
 * 伤害类型注册：混沌伤害（模拟龙之研究混沌武器，可击破混沌守卫/水晶护盾）。
 * 通过 data 包给 zifeng:chaos_damage 打上 draconicevolution:chaotic 标签，
 * DE 的 getDamageLevel() 会识别为 TechLevel.CHAOTIC → chaoticBypassCrystalShield 生效。
 */
public class ModDamageTypes {
    public static final DeferredRegister<DamageType> DAMAGE_TYPES =
            DeferredRegister.create(Registries.DAMAGE_TYPE, SkillTreeMod.MOD_ID);

    /** 混沌伤害类型：无视护甲、可破 DE 混沌守卫/水晶护盾 */
    public static final Supplier<DamageType> CHAOS_DAMAGE = DAMAGE_TYPES.register("chaos_damage",
            () -> new DamageType("chaos_damage", 0.1F));

    /** 混沌伤害标签（与 DE 对齐，供 getDamageLevel 判定） */
    public static TagKey<DamageType> chaoticTag() {
        return TagKey.create(Registries.DAMAGE_TYPE,
                new ResourceLocation("draconicevolution", "chaotic"));
    }

    /**
     * 真实伤害类型（★ 2026-10-11 新增，供「无视护甲与保护附魔」的伤害使用）。
     *
     * <p>通过 data 包打 {@code minecraft:bypasses_armor} 标签 → 完全无视护甲与保护附魔，
     * 配合调用方把 {@code invulnerableTime} 归零，即可做到"每发子弹都结算、不吞伤害"。
     * <p>⚠️ 刻意<b>不</b>打 {@code bypasses_invulnerability}：那会让伤害绕过整个 {@code hurt()}
     * 流程（等于 {@code /kill}），失去死亡消息与各类受击事件。
     * <p>注：原唯一调用方「枪械 · 真伤弹」已在【杀戮弹幕】改版中删除，故当前无引用。
     * 类型与其数据包标签一并保留 —— 删掉它要同时动注册项、damage_type json 与
     * {@code minecraft:bypasses_armor} 标签文件，收益只是少一个空注册项，
     * 不值得在发布版为此改动 registry 内容。
     */
    public static final Supplier<DamageType> TRUE_DAMAGE = DAMAGE_TYPES.register("true_damage",
            () -> new DamageType("true_damage", 0.1F));

    /** 真实伤害类型的 ResourceKey（供构造 DamageSource） */
    public static net.minecraft.resources.ResourceKey<DamageType> trueDamageKey() {
        return net.minecraft.resources.ResourceKey.create(Registries.DAMAGE_TYPE,
                new ResourceLocation(SkillTreeMod.MOD_ID, "true_damage"));
    }

    /** 混沌伤害类型的 ResourceKey（供构造 DamageSource） */
    public static net.minecraft.resources.ResourceKey<DamageType> chaosDamageKey() {
        return net.minecraft.resources.ResourceKey.create(Registries.DAMAGE_TYPE, chaosDamageId());
    }

    public static ResourceLocation chaosDamageId() {
        return new ResourceLocation(SkillTreeMod.MOD_ID, "chaos_damage");
    }
}
