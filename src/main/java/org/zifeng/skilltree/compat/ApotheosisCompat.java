package org.zifeng.skilltree.compat;

import net.minecraft.world.item.ItemStack;

/**
 * Apotheosis（神化）词条装备兼容（2026-09-06，v1.3.8）：
 * <p>财源滚滚/猎魂丰收对【生物装备栏掉落】默认跳过放大（防刷物品：玩家塞给生物的装备会被复制）。
 * 但词条 Boss/精英/随机强化怪【自然生成】的词条装备是世界合法产出，玩家想多倍掉落这类装备。
 * <p>⚠️ 1.20.1 神化 7.x 是 NBT（affix_data）老架构，物品上【没有】来源标记组件
 * （8.x 的 FROM_MOB/FROM_BOSS/FROM_CHEST 是 1.21 DataComponent 体系才有）→ 无法从物品区分
 * “世界产出 vs 玩家注入”，按用户拍板【甲：词条装一律放行】（配合调用方的持久怪分流：
 * 会自然消失的怪装备本就直接放行；持久怪里凡词条装都放行——覆盖词条 Boss，口子=已有词条装塞怪
 * 可复制，但词条装不可堆叠且受装备 20 份复制上限约束，风险有界）。
 * <p>⚠️ 软引用：未装神化或类结构变动时返回 false → 调用方维持原拦截逻辑（防刷兜底），
 * 类加载安全降级，不影响模组其他功能（与 Ae2Compat 同风格）。
 */
public final class ApotheosisCompat {
    private ApotheosisCompat() {
    }

    // ===== 反射成员缓存（首次使用时解析一次，热路径零开销；失败=未装/结构变动 → 全部降级 false）=====
    private static volatile java.lang.reflect.Method HAS_AFFIXES;

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
                // 词条判断：dev.shadowsoffire.apotheosis.adventure.affix.AffixHelper.hasAffixes(ItemStack)
                //（1.20.1 神化 7.x 包结构带 adventure 段，与 1.21.1 8.x 不同，勿混）
                Class<?> helper = Class.forName("dev.shadowsoffire.apotheosis.adventure.affix.AffixHelper");
                HAS_AFFIXES = helper.getMethod("hasAffixes", ItemStack.class);
            } catch (Throwable t) {
                // 未装神化 / 类结构变动 → 降级：全部识别为 false
                HAS_AFFIXES = null;
                org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger("zifeng_skilltree");
                log.debug("[zifeng] Apotheosis 兼容未启用（未安装或结构不同），词条装备按普通装备处理", t);
            }
        }
    }

    /**
     * 是否为神化词条装备（带词条 affix 数据）。
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
     * 综合判定：该掉落物是否为"可豁免拦截的词条装备"。
     * 1.20.1 老版无来源标记（用户拍板甲方案）→ 词条装一律视为世界产出可放行。
     */
    public static boolean isWorldLootAffix(ItemStack stack) {
        return hasAffixes(stack);
    }
}
