package org.zifeng.skilltree.compat;

import net.minecraft.network.chat.Component;
import org.zifeng.skilltree.skill.Skills;

import java.util.HashSet;
import java.util.Set;

/**
 * 技能 → 可选模组依赖 的<b>唯一判定入口</b>（★ 2026-10-13）。
 *
 * <p>技能树界面据此判断「该技能在当前环境是否应该显示」——
 * 依赖模组未安装<b>且</b>玩家从未学过 → 整行隐藏（连该类别的胶囊也会一起消失）。
 * 已经学过的例外：仍然显示，但在卡片上标注「缺少 XXX 模组，暂不生效」
 * （旧版本允许未装模组时照常学，隐藏它会让那部分技能点在界面上凭空消失）。
 *
 * <p>为什么集中在这里：此前这份判定散在 {@code SkillTreeScreen#missingModName}，
 * 只服务「不可学 / 红字提示」。现在界面过滤、胶囊隐藏、卡片标注都要用它，
 * 分散实现早晚会出现「技能已隐藏、却还在提示缺模组」这类自相矛盾的状态。
 * 以后新增兼容模组只改本类 + 语言文件。
 *
 * <p>判定沿用各 {@code *Compat} 的 isLoaded/isXxxLoaded —— 它代表
 * 「本模组针对该模组的功能是否真的可用」，比单看 modid 更准确
 * （例如 {@link Ae2Compat#isAe2Loaded()} 是探测 AE2 的 API 类，被禁用时会判为不可用）。
 * 全部为静态缓存，可安全地在渲染循环里逐行调用。
 *
 * <p>⚠️ 本工程（1.20.1）独有的「枪械列」依赖 TaCZ —— TaCZ 只发布 1.20.1，
 * 故 1.21.1 工程没有这一节。
 */
public final class ModDependencies {

    private ModDependencies() {
    }

    /** 语言键前缀（与语言文件里的 ui.* 一组） */
    private static final String LANG = "ui.zifeng_s_custom_skill_tree.";

    private static final String KEY_ARS = LANG + "mod_ars";
    private static final String KEY_IRON = LANG + "mod_iron";
    private static final String KEY_GOETY = LANG + "mod_goety";
    private static final String KEY_TACZ = LANG + "mod_tacz";
    private static final String KEY_AE2 = LANG + "mod_ae2";

    /** 新生魔艺（Ars Nouveau）：魔力上限 / 魔力恢复 */
    private static final Set<String> ARS_SKILLS = Set.of(
            Skills.MANA_AMP, Skills.ARS_MANA_REGEN);

    /** 铁魔法（Iron's Spells 'n Spellbooks）：魔力上限 / 恢复 / 吟唱 / 冷却 + 9 流派强度 */
    private static final Set<String> IRON_SKILLS = Set.of(
            Skills.IRON_MANA_AMP, Skills.IRON_MANA_REGEN, Skills.IRON_CAST_TIME, Skills.IRON_COOLDOWN,
            Skills.IRON_FIRE, Skills.IRON_ICE, Skills.IRON_LIGHTNING, Skills.IRON_HOLY,
            Skills.IRON_ENDER, Skills.IRON_BLOOD, Skills.IRON_EVOCATION, Skills.IRON_NATURE,
            Skills.IRON_ELDRITCH);

    /** Goety（诡术）：通用强度 / 通用灵魂折扣 + 9 流派精通 */
    private static final Set<String> GOETY_SKILLS = Set.of(
            Skills.GOETY_POTENCY, Skills.GOETY_SOUL_DISCOUNT,
            Skills.GOETY_ABYSS, Skills.GOETY_FROST, Skills.GOETY_GEOMANCY, Skills.GOETY_NECROMANCY,
            Skills.GOETY_NETHER, Skills.GOETY_STORM, Skills.GOETY_VOID, Skills.GOETY_WILD,
            Skills.GOETY_WIND);

    /** 枪械（TaCZ）：整个第 12 列 */
    private static final Set<String> GUN_SKILLS = new HashSet<>(Skills.GUN_SKILLS);

    /** 应用能源2（AE2）：无限回路（寰宇法则列） */
    private static final Set<String> AE2_SKILLS = Set.of(Skills.AE_INFINITE_CHANNEL);

    /**
     * 该技能是否<b>缺少</b>依赖模组。
     *
     * <p>不做本地化（比 {@link #missingName} 便宜），适合在被高频调用的过滤逻辑里使用。
     *
     * @return true = 有依赖模组且当前未安装；false = 无依赖 / 依赖已满足
     */
    public static boolean isMissing(String skillId) {
        return missingKey(skillId) != null;
    }

    /**
     * 缺少依赖模组时，返回该模组的<b>本地化显示名</b>（如「铁魔法（Iron's Spells）」）；
     * 模组已安装或该技能本就无依赖 → {@code null}。
     *
     * <p>注意：<b>不能</b>用返回值非 null 之外的方式判断「是否缺模组」——
     * 未安装的模组其显示名只能来自我们自己的语言文件。
     */
    public static String missingName(String skillId) {
        String key = missingKey(skillId);
        return key == null ? null : Component.translatable(key).getString();
    }

    /** 缺模组时返回对应语言键，否则 null（其余方法都基于它，避免判定逻辑出现第二份） */
    private static String missingKey(String skillId) {
        if (skillId == null) {
            return null;
        }
        if (ARS_SKILLS.contains(skillId)) {
            return ArsNouveauCompat.isLoaded() ? null : KEY_ARS;
        }
        if (IRON_SKILLS.contains(skillId)) {
            return IronSpellsCompat.isLoaded() ? null : KEY_IRON;
        }
        if (GOETY_SKILLS.contains(skillId)) {
            return GoetyCompat.isLoaded() ? null : KEY_GOETY;
        }
        if (GUN_SKILLS.contains(skillId)) {
            return TaczSkills.isTaczLoaded() ? null : KEY_TACZ;
        }
        if (AE2_SKILLS.contains(skillId)) {
            return Ae2Compat.isAe2Loaded() ? null : KEY_AE2;
        }
        return null; // 自有技能（基础/增幅/终极/光环/馈赠/垂钓/奥术防护…）不依赖任何模组
    }
}
