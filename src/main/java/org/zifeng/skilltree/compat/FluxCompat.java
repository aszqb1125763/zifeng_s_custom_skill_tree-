package org.zifeng.skilltree.compat;

import net.minecraft.core.Direction;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import org.zifeng.skilltree.blockentity.SkillPointConverterBlockEntity;

import java.lang.reflect.Proxy;

/**
 * Flux-Networks 兼容（2026-08-25）：
 * <p>Flux-Networks 优先使用自己的 long 能量接口 {@code FluxCapabilities.FN_ENERGY_STORAGE}
 * （IFNEnergyStorage，receiveEnergyL(long)）传输能量——普通 IEnergyStorage 是 int（21 亿上限），
 * 而 Flux 能超 21 亿。本类给技能点转换机提供该 capability（1.20.1 Forge 模式：
 * BlockEntity 覆写 getCapability，通过本类反射返回 Flux 的 capability），让 Flux 直接灌 long 能量。
 * <p>⚠️ 软引用：未装 Flux-Networks 时类加载安全降级（反射检查），不影响模组其他功能。
 */
public final class FluxCompat {
    private FluxCompat() {
    }

    /** Flux 是否已安装（懒检查） */
    private static boolean fluxLoaded = false;
    private static boolean fluxChecked = false;

    /** Flux 的 capability 实例（反射缓存） */
    private static Capability<?> fluxCapability = null;

    public static boolean isFluxLoaded() {
        if (!fluxChecked) {
            fluxChecked = true;
            try {
                Class.forName("sonar.fluxnetworks.api.FluxCapabilities", false, FluxCompat.class.getClassLoader());
                fluxLoaded = true;
            } catch (Throwable ignored) {
                fluxLoaded = false;
            }
        }
        return fluxLoaded;
    }

    /** 获取 Flux 的 FN_ENERGY_STORAGE capability（反射；未装 Flux 返回 null） */
    @SuppressWarnings("unchecked")
    public static Capability<Object> getFluxCapability() {
        if (!isFluxLoaded()) {
            return null;
        }
        if (fluxCapability == null) {
            try {
                Class<?> fluxCapabilitiesClass = Class.forName("sonar.fluxnetworks.api.FluxCapabilities");
                java.lang.reflect.Field fnField = fluxCapabilitiesClass.getField("FN_ENERGY_STORAGE");
                fluxCapability = (Capability<?>) fnField.get(null);
            } catch (Throwable ignored) {
                return null;
            }
        }
        return (Capability<Object>) fluxCapability;
    }

    /**
     * 由 SkillPointConverterBlockEntity.getCapability 调用：返回 Flux long 能量 capability 的 LazyOptional。
     * 未装 Flux / 非 Flux capability → 返回 null（上层走标准 int 能量）。
     */
    public static LazyOptional<Object> getFluxStorage(Capability<?> cap, SkillPointConverterBlockEntity be) {
        Capability<Object> fluxCap = getFluxCapability();
        if (fluxCap == null || fluxCap != cap) {
            return null;
        }
        try {
            Class<?> ifnClass = Class.forName("sonar.fluxnetworks.api.energy.IFNEnergyStorage");
            Object proxy = Proxy.newProxyInstance(FluxCompat.class.getClassLoader(), new Class[]{ifnClass},
                    (p, method, args) -> {
                        String name = method.getName();
                        switch (name) {
                            case "receiveEnergyL" -> {
                                long maxReceive = (Long) args[0];
                                boolean simulate = (Boolean) args[1];
                                return be.receiveEnergyLong(maxReceive, simulate);
                            }
                            case "extractEnergyL" -> {
                                return 0L; // 只进不出
                            }
                            case "getEnergyStoredL" -> {
                                return be.getProgress();
                            }
                            case "getMaxEnergyStoredL" -> {
                                return be.getCapacityLong();
                            }
                            case "canExtract" -> {
                                return false;
                            }
                            case "canReceive" -> {
                                return !be.isRedstoneBlocked();
                            }
                            default -> {
                                return null;
                            }
                        }
                    });
            return LazyOptional.of(() -> proxy);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
