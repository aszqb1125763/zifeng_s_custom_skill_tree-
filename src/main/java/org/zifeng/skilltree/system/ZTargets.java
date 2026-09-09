package org.zifeng.skilltree.system;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Z-Link 目标判定共享工具（2026-09-09 第 3 件：抽伤害共享零件）。
 * <p>从 AuraEvents 原样抽出（逻辑一字未改）：DE 守卫/水晶类名判定（带缓存）。
 * 供光环伤害链 / 未来新伤害技能 / 任何需要"目标是否 DE 特殊实体"的模块复用。
 * <p>DE（Draconic Evolution）未装时这些类不存在 → 全部用类名匹配，不依赖 DE 编译。
 */
public final class ZTargets {
    private ZTargets() {
    }

    /** DE 类名匹配缓存（2026-08-27 性能优化）：刷怪塔海量实体时避免每目标每 tick 分配类名字符串 */
    private static final java.util.concurrent.ConcurrentHashMap<Class<?>, Boolean> DRACONIC_CRYSTAL_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentHashMap<Class<?>, Boolean> DRACONIC_GUARDIAN_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    /** DE 守卫水晶特判（GuardianCrystalEntity 是 Entity 不是 LivingEntity，用类名匹配不依赖 DE 编译） */
    public static boolean isDraconicCrystal(Entity target) {
        return DRACONIC_CRYSTAL_CACHE.computeIfAbsent(target.getClass(), cls -> {
            String name = cls.getName();
            return name.startsWith("com.brandon3055.draconicevolution.entity.")
                    && (name.contains("GuardianCrystal") || name.contains("ChaosCrystal"));
        });
    }

    /** DE 混沌守卫本体特判（类名匹配，不依赖 DE 编译） */
    public static boolean isDraconicGuardian(LivingEntity target) {
        return DRACONIC_GUARDIAN_CACHE.computeIfAbsent(target.getClass(), cls -> {
            String name = cls.getName();
            return name.startsWith("com.brandon3055.draconicevolution.entity.")
                    && (name.contains("DraconicGuardian") || name.contains("ChaosGuardian"));
        });
    }
}
