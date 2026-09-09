package org.zifeng.skilltree.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Sophisticated（精妙存储/精妙背包，p3pp3rf1y）兼容（2026-09-07，v1.3.9）：
 * <p>问题：① 搬运/掉落直传把物品塞进 Sophisticated 箱子的外部 capability handler
 *（getExternalItemHandler / getInventoryForInputOutput，管道专用受限视图），绕过官方
 * 主存储管线（slotTracker/堆叠倍率/升级响应）→ 物品异常消失；
 * ② 其方块翻译是参数化模板（"%s%sBarrel" 带 wood 名），Block.getName() 无参渲染残留 "%s"；
 * ③ 其打开菜单的物品槽 container 是内部 InventoryHandler（非 BlockEntity），
 * 通用"打开绑定容器自身"检测失效 → 自动转移自搬清空。
 * <p>修复（软反射，未装 Sophisticated 全部降级，不崩溃）：
 * <ul>
 *   <li>官方插入：StorageBlockEntity.getStorageWrapper() → getInventoryForUpgradeProcessing()
 *       → IItemHandlerSimpleInserter.insertItem(ItemStack, boolean)（单参版，magnet/pickup/ShulkerBoxItem
 *       全部走这条官方路径，触发完整升级管线）</li>
 *   <li>干净显示名：优先反射 BE.getDisplayName()（displayName 缓存 = 带 wood 参数的完整翻译）；
 *       null 时降级调用方原逻辑</li>
 *   <li>菜单识别：Sophisticated 菜单类含 getStorageBlockEntity() → 返回其 BlockPos（自搬检测用）</li>
 * </ul>
 * <p>反射成员按需解析一次并缓存；任何一步失败 → 该项返回 null/false，调用方走原逻辑。
 * <p>注：本类全反射、只依赖 Minecraft 原版类 → 1.20.1/1.21.1 双版本通用（同一份源码）。
 */
public final class SophisticatedCompat {
    private SophisticatedCompat() {
    }

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("zifeng_skilltree");

    /** 已知 Sophisticated 方块实体类名前缀（storage + backpacks 共用 p3pp3rf1y.sophisticated*） */
    private static final String PACKAGE_PREFIX = "net.p3pp3rf1y.";

    // ===== 反射成员缓存（延迟解析）=====
    /** 存储插入链是否已解析（getStorageWrapper→getInventoryForUpgradeProcessing→insertItem） */
    private static volatile boolean resolvedStorage = false;
    /** getStorageWrapper() 方法（StorageBlockEntity 及子类） */
    private static volatile java.lang.reflect.Method GET_STORAGE_WRAPPER;
    /** getInventoryForUpgradeProcessing() 方法（StorageWrapper/MovingStorageWrapper/StackStorageWrapper） */
    private static volatile java.lang.reflect.Method GET_INV_UPGRADE;
    /** 官方单参插入：IItemHandlerSimpleInserter.insertItem(ItemStack, boolean) */
    private static volatile java.lang.reflect.Method OFFICIAL_INSERT;
    /** getDisplayName() → Component（BE 干净名） */
    private static volatile java.lang.reflect.Method GET_DISPLAY_NAME;
    /** displayName 字段（1.20.1 的 StorageBlockEntity 未 override getName/getDisplayName，干净名缓存在此 protected 字段） */
    private static volatile java.lang.reflect.Field DISPLAY_NAME_FIELD;

    /** 目标方块实体是否 Sophisticated（包名 + 存在 getStorageWrapper 方法） */
    public static boolean isSophisticatedStorage(BlockEntity be) {
        if (be == null) {
            return false;
        }
        String cn = be.getClass().getName();
        return cn.startsWith(PACKAGE_PREFIX) && cn.contains("sophisticated") && findGetStorageWrapper(be) != null;
    }

    private static void resolve(BlockEntity sample) {
        if (resolvedStorage) {
            return;
        }
        synchronized (SophisticatedCompat.class) {
            if (resolvedStorage) {
                return;
            }
            resolvedStorage = true;
            try {
                GET_STORAGE_WRAPPER = findGetStorageWrapper(sample);
                LOG.info("[zifeng] Sophisticated 解析: getStorageWrapper={}", GET_STORAGE_WRAPPER != null);
                if (GET_STORAGE_WRAPPER == null) {
                    return;
                }
                Object wrapper = GET_STORAGE_WRAPPER.invoke(sample);
                LOG.info("[zifeng] Sophisticated 解析: wrapper={}", wrapper != null ? wrapper.getClass().getName() : "null");
                if (wrapper == null) {
                    return;
                }
                try {
                    GET_INV_UPGRADE = wrapper.getClass().getMethod("getInventoryForUpgradeProcessing");
                } catch (NoSuchMethodException e) {
                    LOG.info("[zifeng] Sophisticated 解析: 无 getInventoryForUpgradeProcessing → 尝试向上查找");
                    GET_INV_UPGRADE = findMethod(wrapper.getClass(), "getInventoryForUpgradeProcessing");
                }
                LOG.info("[zifeng] Sophisticated 解析: getInventoryForUpgradeProcessing={}", GET_INV_UPGRADE != null);
                Object inv = GET_INV_UPGRADE != null ? GET_INV_UPGRADE.invoke(wrapper) : null;
                if (inv == null) {
                    LOG.info("[zifeng] Sophisticated 解析: getInventoryForUpgradeProcessing 调用结果 null");
                    return;
                }
                OFFICIAL_INSERT = findMethod(inv.getClass(), "insertItem", ItemStack.class, boolean.class);
                LOG.info("[zifeng] Sophisticated 解析: officialInsert={} (inv={})", OFFICIAL_INSERT != null, inv.getClass().getName());
                // 干净名（BE.getDisplayName；1.20.1 未 override 时读 displayName 字段）
                GET_DISPLAY_NAME = findMethod(sample.getClass(), "getDisplayName");
                DISPLAY_NAME_FIELD = findField(sample.getClass(), "displayName");
            } catch (Throwable t) {
                LOG.info("[zifeng] Sophisticated 兼容解析失败（未安装或结构变动），走通用逻辑: {}", t.toString());
            }
        }
    }

    /** 向上找 getStorageWrapper()（在类/父类中查找无参方法） */
    private static java.lang.reflect.Method findGetStorageWrapper(BlockEntity be) {
        for (Class<?> c = be.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                java.lang.reflect.Method m = c.getDeclaredMethod("getStorageWrapper");
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) {
                // 继续父类
            }
        }
        return null;
    }

    private static java.lang.reflect.Method findMethod(Class<?> clazz, String name, Class<?>... params) {
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                java.lang.reflect.Method m = c.getDeclaredMethod(name, params);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) {
                // 继续父类
            }
        }
        return null;
    }

    /** 向上找字段（类/父类；含 protected/private） */
    private static java.lang.reflect.Field findField(Class<?> clazz, String name) {
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                java.lang.reflect.Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
                // 继续父类
            }
        }
        return null;
    }

    /**
     * 干净的容器显示名（解决 "%s%sBarrel" 残留 %s）。
     * @return 干净名；null = 非 Sophisticated 或无法取到，调用方降级原逻辑
     */
    public static String cleanDisplayName(BlockEntity be) {
        if (be == null || !be.getClass().getName().startsWith(PACKAGE_PREFIX)) {
            return null;
        }
        resolve(be);
        // 优先：getDisplayName()（1.21.1 等新版 override 返回干净名）
        try {
            if (GET_DISPLAY_NAME != null) {
                Object name = GET_DISPLAY_NAME.invoke(be);
                if (name instanceof net.minecraft.network.chat.Component comp) {
                    String s = comp.getString();
                    if (s != null && !s.isBlank() && !s.contains("%s") && !s.contains("block.sophisticated")) {
                        return s;
                    }
                }
            }
        } catch (Throwable ignored) {
            // 继续下一来源
        }
        // 回退：displayName 字段（1.20.1 的 StorageBlockEntity 未 override getName/getDisplayName，
        //   干净名缓存在此 protected 字段）
        try {
            if (DISPLAY_NAME_FIELD != null) {
                Object name = DISPLAY_NAME_FIELD.get(be);
                if (name instanceof net.minecraft.network.chat.Component comp) {
                    String s = comp.getString();
                    if (s != null && !s.isBlank() && !s.contains("%s") && !s.contains("block.sophisticated")) {
                        return s;
                    }
                }
            }
        } catch (Throwable ignored) {
            // 返回 null，调用方降级
        }
        return null;
    }

    /**
     * 打开菜单的源容器方块位置（Sophisticated storage/backpacks 自搬检测用）。
     * <p>按 menu 实例类动态反射，不缓存跨 mod 类方法：storage 与 backpacks 是不同 mod 的
     * 菜单类（StorageContainerMenu / BackpackContainer），静态缓存一个类的 Method 去 invoke
     * 另一个类的实例必然 IllegalArgumentException → 自搬检测对背包失效（2026-09-09 bug）。
     * 本方法调用频率极低（打开容器/手动触发各一次），逐次解析无性能问题。
     * @return 该菜单对应存储方块的 BlockPos；null = 非 Sophisticated 菜单或取不到
     */
    public static BlockPos storageMenuPos(AbstractContainerMenu menu) {
        if (menu == null) {
            return null;
        }
        if (!menu.getClass().getName().startsWith(PACKAGE_PREFIX)) {
            return null; // 非 Sophisticated 菜单 → 走通用逻辑
        }
        try {
            // 优先 getBlockPosition()：Optional<BlockPos>（storage/backpacks 新版菜单都有）
            java.lang.reflect.Method posM = findMethod(menu.getClass(), "getBlockPosition");
            if (posM != null) {
                Object result = posM.invoke(menu);
                if (result instanceof java.util.Optional<?> opt) {
                    return opt.orElse(null) instanceof BlockPos bp ? bp : null;
                }
                return null;
            }
            // 回退 getStorageBlockEntity() → 其返回 BE 的 getBlockPos()（老版 storage 菜单）
            java.lang.reflect.Method beM = findMethod(menu.getClass(), "getStorageBlockEntity");
            if (beM != null) {
                Object be = beM.invoke(menu);
                if (be != null) {
                    java.lang.reflect.Method getPos = findMethod(be.getClass(), "getBlockPos");
                    Object pos = getPos != null ? getPos.invoke(be) : null;
                    return pos instanceof BlockPos bp ? bp : null;
                }
            }
        } catch (Throwable ignored) {
            // 反射失败 → 返回 null 走通用
        }
        return null;
    }
}
