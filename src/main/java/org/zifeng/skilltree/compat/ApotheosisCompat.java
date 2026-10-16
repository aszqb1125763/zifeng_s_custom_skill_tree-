package org.zifeng.skilltree.compat;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.ItemStack;

/**
 * Apotheosis（神化）词条装备兼容（2026-09-06，v1.3.8）：
 * <p>财源滚滚/猎魂丰收对【生物装备栏掉落】默认跳过放大（防刷物品：玩家塞给生物的装备会被复制）。
 * 但词条 Boss/精英/随机强化怪【自然生成】的词条装备是世界合法产出，玩家想多倍掉落这类装备。
 * 本类提供弱兼容识别：1.21.1 神化 8.x 用 DataComponent 体系，词条装备自带来源标记组件
 * FROM_MOB / FROM_BOSS / FROM_CHEST（生成那一刻打上、随物品走）。
 * <p>⚠️ 软引用：未装神化或类结构变动时所有方法返回 false → 调用方维持原拦截逻辑（防刷兜底），
 * 类加载安全降级，不影响模组其他功能（与 Ae2Compat 同风格）。
 * <p>1.20.1 老版神化（7.x，NBT affix_data 架构）无来源组件，见 1.20.1 分支的同名类实现。
 */
public final class ApotheosisCompat {
    private ApotheosisCompat() {
    }

    // ===== 反射成员缓存（首次使用时解析一次，热路径零开销；失败=未装/结构变动 → 全部降级 false）=====
    private static volatile java.lang.reflect.Method HAS_AFFIXES;
    private static volatile DataComponentType<?> FROM_MOB;
    private static volatile DataComponentType<?> FROM_BOSS;
    private static volatile DataComponentType<?> FROM_CHEST;

    /** 是否已尝试解析（解析失败也置 true，避免每掉落都重复反射尝试） */
    private static volatile boolean resolved = false;

    private static void resolve() {
        if (resolved) {
            return;
        }
        synchronized (ApotheosisCompat.class) {
            if (resolved) {
                return;
            }
            resolved = true;
            try {
                // 词条判断：dev.shadowsoffire.apotheosis.affix.AffixHelper.hasAffixes(ItemStack)
                Class<?> helper = Class.forName("dev.shadowsoffire.apotheosis.affix.AffixHelper");
                HAS_AFFIXES = helper.getMethod("hasAffixes", ItemStack.class);
                // 来源标记：dev.shadowsoffire.apotheosis.Apoth$Components.{FROM_MOB,FROM_BOSS,FROM_CHEST}
                Class<?> comps = Class.forName("dev.shadowsoffire.apotheosis.Apoth$Components");
                FROM_MOB = (DataComponentType<?>) comps.getField("FROM_MOB").get(null);
                FROM_BOSS = (DataComponentType<?>) comps.getField("FROM_BOSS").get(null);
                FROM_CHEST = (DataComponentType<?>) comps.getField("FROM_CHEST").get(null);
            } catch (Throwable t) {
                // 未装神化 / 类结构变动（1.20.1 老版无此包结构）→ 降级：全部识别为 false
                HAS_AFFIXES = null;
                FROM_MOB = null;
                FROM_BOSS = null;
                FROM_CHEST = null;
                org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger("zifeng_skilltree");
                log.debug("[zifeng] Apotheosis 兼容未启用（未安装或结构不同），词条装备按普通装备处理", t);
            }
        }
    }

    /**
     * 是否为神化词条装备（带词条 affix 数据，非仅 rarity）。
     * 无神化环境恒返回 false。
     */
    public static boolean hasAffixes(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        resolve();
        java.lang.reflect.Method m = HAS_AFFIXES;
        if (m == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(m.invoke(null, stack));
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 词条装备是否带【世界生成来源标记】（FROM_MOB 怪自然生成 / FROM_BOSS 词条 Boss/精英 / FROM_CHEST 宝箱注入）。
     * 带标记 = 世界合法产出（可安全多倍掉落）；玩家合成/灌注/塞给怪的物品不会新增这些标记。
     */
    public static boolean hasWorldSource(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        resolve();
        if (FROM_MOB == null && FROM_BOSS == null && FROM_CHEST == null) {
            return false;
        }
        return has(stack, FROM_MOB) || has(stack, FROM_BOSS) || has(stack, FROM_CHEST);
    }

    @SuppressWarnings("unchecked")
    private static boolean has(ItemStack stack, DataComponentType<?> type) {
        if (type == null) {
            return false;
        }
        try {
            return stack.has((DataComponentType) type);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 综合判定：该掉落物是否为"可豁免拦截的世界产出词条装备"。
     * 1.21.1 = 词条装备 && 带来源标记（能精确区分世界产出与玩家注入）。
     */
    public static boolean isWorldLootAffix(ItemStack stack) {
        return hasAffixes(stack) && hasWorldSource(stack);
    }
}
