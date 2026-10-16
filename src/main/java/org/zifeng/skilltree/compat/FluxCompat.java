package org.zifeng.skilltree.compat;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.zifeng.skilltree.blockentity.SkillPointConverterBlockEntity;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Flux-Networks 兼容（2026-08-25）：
 * <p>Flux-Networks 优先使用自己的 long 能量接口 {@code FluxCapabilities.BLOCK}
 * （IFNEnergyStorage，receiveEnergyL(long)）传输能量——普通 IEnergyStorage 是 int（21 亿上限），
 * 而 Flux 能超 21 亿。本类给技能点转换机注册该 capability，让 Flux 直接灌 long 能量突破 int 限制。
 * <p>⚠️ 软引用：未装 Flux-Networks 时类加载安全降级（反射检查 FluxCapabilities 是否存在），
 * 不影响模组其他功能。
 */
public final class FluxCompat {
    private FluxCompat() {
    }

    /** Flux 是否已安装（懒检查） */
    private static boolean fluxLoaded = false;
    private static boolean fluxChecked = false;

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

    /** 注册 Flux long 能量 capability（若 Flux 已安装） */
    public static void register(IEventBus modEventBus) {
        if (!isFluxLoaded()) {
            return; // 未装 Flux，跳过
        }
        modEventBus.addListener(FluxCompat::registerFluxCapability);
    }

    private static void registerFluxCapability(RegisterCapabilitiesEvent event) {
        try {
            Class<?> fluxCapabilitiesClass = Class.forName("sonar.fluxnetworks.api.FluxCapabilities");
            // FluxCapabilities.BLOCK 是 BlockCapability<IFNEnergyStorage, Direction>
            java.lang.reflect.Field blockField = fluxCapabilitiesClass.getField("BLOCK");
            Object blockCap = blockField.get(null); // BlockCapability
            // 生成 IFNEnergyStorage 动态代理（对接转换机 long progress）
            Class<?> ifnClass = Class.forName("sonar.fluxnetworks.api.energy.IFNEnergyStorage");
            // 注册：event.registerBlockEntity(BlockCapability, BlockEntityType, 函数)
            event.registerBlockEntity((net.neoforged.neoforge.capabilities.BlockCapability) blockCap,
                    org.zifeng.skilltree.init.ModBlockEntities.SKILL_POINT_CONVERTER.get(),
                    (be, side) -> createProxy(ifnClass, (SkillPointConverterBlockEntity) be));
        } catch (Throwable ignored) {
            // 反射失败（API 变动等）→ 静默跳过（仍可用标准 int 能量）
        }
    }

    /** 用动态代理实现 IFNEnergyStorage（对接转换机 long 缓冲），避免编译期依赖 Flux */
    private static Object createProxy(Class<?> ifnClass, SkillPointConverterBlockEntity be) {
        return Proxy.newProxyInstance(FluxCompat.class.getClassLoader(), new Class[]{ifnClass},
                (proxy, method, args) -> {
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
                            return method.getDefaultValue();
                        }
                    }
                });
    }
}
