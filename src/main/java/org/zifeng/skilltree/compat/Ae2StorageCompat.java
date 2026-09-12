package org.zifeng.skilltree.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * AE2 存储网络兼容（纯反射、零构建依赖；2026-09-12 1.4.1「绑定 AE 无线访问点」）。
 *
 * <p>功能：让「子枫挪移术 / 子枫的搬运术」的绑定目标支持 AE2 的【无线访问点】——
 * 掉落物/搬运物直接进 ME 网络，取料时也能从 ME 网络取。
 *
 * <p><b>派发原则（用户需求）：</b>绑定的目标若有 AE 网络接口 → 优先走网络；
 * 没有 → 走普通物品容器。AE 判定必须<b>先于</b> ItemHandler 判定，因为无线访问点
 * 自身也暴露 1 格 {@code IItemHandler}（无线增压卡专用槽 + 过滤器）——
 * 先判 ItemHandler 会把 WAP 当成"普通容器"绑定成功却永远塞不进物品。
 *
 * <p><b>为什么纯反射：</b>本模组对 AE2 是软依赖，任何 {@code import appeng.*}
 * 都会在类加载期抛 {@code NoClassDefFoundError}，因此 AE2 类型只通过
 * {@code Class.forName} + {@code Method.invoke} 访问（与 {@link Ae2Compat} 同策略）。
 *
 * <p><b>⚠️ 2026-09-12 教训（重要，勿重犯）：</b>初版把 {@code IStorageService} 的包名
 * 写成了 {@code appeng.api.storage}，实际是 <b>{@code appeng.api.networking.storage}</b>。
 * 一个名字错 → {@code Class.forName} 抛异常 → 被 catch 吞掉 → {@code available = false}
 * → WAP 判定永远 false → 整条 AE 链路静默失效（物品进了增压卡槽被拒），
 * 且因为没有任何日志，排查困难。
 *
 * <p><b>本版加固（三层）：</b>
 * <ol>
 *   <li><b>不写 {@code IStorageService} / {@code MEStorage} 的类名</b>：改为拿到
 *       {@code getStorageService()} 的返回对象后，用 {@code runtimeMethod()} 在
 *       <b>运行时类型</b>上找 {@code getInventory()}，再在返回对象上找 {@code insert}/{@code extract}。
 *       包名/继承结构怎么变都不受影响（Building Gadgets 2 靠编译期依赖达到同效果）。</li>
 *   <li><b>解耦</b>：{@link #isWirelessAccessPoint} 只需 {@code IWirelessAccessPoint}
 *       一个接口类（{@link #wapReady}），不再依赖完整存取链（{@link #storageReady}）。
 *       这样即使存取链某天漂移，也不会把 WAP 误判成普通容器去踩增压卡槽。</li>
 *   <li><b>可诊断</b>：装了 AE2 但解析不完整时打 WARN 并列出缺失项（未装 AE2 则完全静默，不刷日志）。</li>
 * </ol>
 *
 * <p><b>双版本 API 已实测</b>（1.20.1 AE2-15.4.10 / 1.21.1 AE2-19.2.17，javap 逐项比对）：
 * <pre>
 *   IWirelessAccessPoint.getGrid()                          → IGrid
 *   IGrid.getStorageService()                               → (appeng.api.networking.storage.IStorageService)
 *   &lt;service&gt;.getInventory()                                → (appeng.api.storage.MEStorage)
 *   &lt;storage&gt;.insert(AEKey, long, Actionable, IActionSource) → long
 *   &lt;storage&gt;.extract(AEKey, long, Actionable, IActionSource)→ long
 *   &lt;storage&gt;.getAvailableStacks()                          → KeyCounter（appeng.api.stacks）
 *   &lt;counter&gt;.keySet()                                      → Set&lt;AEKey&gt;
 *   AEItemKey.of(ItemStack) / AEItemKey.is(AEKey)           （静态）
 *   AEKey.wrapForDisplayOrFilter()                          → ItemStack
 *   IActionSource.ofPlayer(Player)                          （静态）
 *   Actionable.MODULATE / Actionable.SIMULATE
 * </pre>
 * 其中前 6 项（含曾写错的那个包名）现已改为运行时动态解析，不再硬编码类名。
 *
 * <p>参考实现：Building Gadgets 2 {@code integration/AE2Methods.java}（结构一致：
 * 先 AE 后普通容器 + {@code IActionSource.ofPlayer(player)} + {@code AEItemKey.of(stack)}）。
 */
public final class Ae2StorageCompat {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("zifeng_skilltree");

    /** 绑定类型：普通物品容器（箱子/漏斗/模组容器） */
    public static final int TYPE_CONTAINER = 0;
    /** 绑定类型：AE2 无线访问点（ME 网络） */
    public static final int TYPE_AE = 1;

    private static volatile boolean resolved = false;
    /** WAP 识别可用（只需 IWirelessAccessPoint 一个接口类，几乎不会失败） */
    private static volatile boolean wapReady = false;
    /** ME 网络完整存取链可用 */
    private static volatile boolean storageReady = false;

    // ---- 必须具名的 AE2 成员（这些名字双版本已 javap 实测确认）----
    private static Class<?> wapClass;
    private static Method getGrid;
    private static Method getStorageService;
    private static Method aeItemKeyOf;
    private static Method aeItemKeyIs;
    private static Method aeKeyWrap;
    private static Method sourceOfPlayer;
    private static Object actionableModulate;
    private static Object actionableSimulate;

    // ---- 运行时方法的缓存（不再硬编码 getInventory/insert/extract 的宿主类名）----
    private static final Map<String, Method> METHOD_CACHE = new ConcurrentHashMap<>();
    private static final Set<String> METHOD_MISS = ConcurrentHashMap.newKeySet();

    private Ae2StorageCompat() {
    }

    /**
     * 一次性解析 AE2 反射成员。
     * <p>未装 AE2 → 全部静默降级（不打日志）；装了 AE2 但某项缺失 → WARN 列出缺失项。
     * <p>使用 {@code Class.forName(name, false, cl)}（不初始化）避免触发 AE2 静态初始化副作用。
     */
    private static synchronized void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;
        boolean ae2Present = false;
        List<String> missing = new ArrayList<>();
        try {
            ClassLoader cl = Ae2StorageCompat.class.getClassLoader();
            try {
                // ① WAP 识别：只需这一个接口类
                wapClass = Class.forName("appeng.api.implementations.blockentities.IWirelessAccessPoint", false, cl);
                wapReady = true;
                ae2Present = true;
            } catch (Throwable t) {
                // 未装 AE2（正常情况）→ 静默降级，不打日志
                mv(missing, "IWirelessAccessPoint", t);
            }
            if (ae2Present) {
                // ② 存取链所需的具名成员
                Class<?> gridCls = Class.forName("appeng.api.networking.IGrid", false, cl);
                Class<?> aeKeyCls = Class.forName("appeng.api.stacks.AEKey", false, cl);
                Class<?> aeItemKeyCls = Class.forName("appeng.api.stacks.AEItemKey", false, cl);
                Class<?> actionableCls = Class.forName("appeng.api.config.Actionable", false, cl);
                Class<?> sourceCls = Class.forName("appeng.api.networking.security.IActionSource", false, cl);

                // ⚠️ 2026-09-12 修复：改用【精确签名】getMethod，不再用"名字+参数个数"匹配。
                //    原因：AEItemKey 有两个同名的 1 参方法——
                //      public static boolean is(AEKey)               ← 我们要的
                //      public boolean is(ItemLike)                   ← 同名实例方法
                //    只按参数个数匹配会随机选中后者，而调用处是 invoke(null, key)（静态调用）
                //    → NullPointerException → 被 catch 吞掉 → extract 永远返回 EMPTY
                //    （insert 路径不用 is()，所以"存"正常而"取"失效——就是这个 bug）
                getGrid = wapClass.getMethod("getGrid");
                // IGrid.getStorageService() 的返回类型是 appeng.api.networking.storage.IStorageService；
                // 这里只取方法本身，不需要它的类名——返回对象后续用运行时类型动态解析
                getStorageService = gridCls.getMethod("getStorageService");
                aeItemKeyOf = aeItemKeyCls.getMethod("of", ItemStack.class);      // 精确避开 of(ItemLike)
                aeItemKeyIs = aeItemKeyCls.getMethod("is", aeKeyCls);            // 精确避开实例 is(ItemLike)
                aeKeyWrap = aeKeyCls.getMethod("wrapForDisplayOrFilter");
                sourceOfPlayer = sourceCls.getMethod("ofPlayer", Player.class);
                actionableModulate = enumConstant(actionableCls, "MODULATE");
                actionableSimulate = enumConstant(actionableCls, "SIMULATE");

                chk(missing, getGrid, "IWirelessAccessPoint.getGrid()");
                chk(missing, getStorageService, "IGrid.getStorageService()");
                chk(missing, aeItemKeyOf, "AEItemKey.of(ItemStack)");
                chk(missing, aeItemKeyIs, "AEItemKey.is(AEKey)");
                chk(missing, aeKeyWrap, "AEKey.wrapForDisplayOrFilter()");
                chk(missing, sourceOfPlayer, "IActionSource.ofPlayer(Player)");
                chk(missing, actionableModulate, "Actionable.MODULATE");
                chk(missing, actionableSimulate, "Actionable.SIMULATE");
            }
        } catch (Throwable t) {
            mv(missing, "解析过程异常", t);
        }
        storageReady = ae2Present && missing.isEmpty();
        if (ae2Present && !storageReady) {
            // 装了 AE2 却解析不全 = 真正的异常（版本漂移），必须可见
            //（初版就是这里静默吞掉才导致「绑定成功但塞不进去」难以排查）
            LOG.warn("[AE2] ME 网络存取反射解析不完整，「绑定 AE 无线访问点」将不可用。缺失：{}", missing);
        }
    }

    private static void chk(List<String> missing, Object got, String what) {
        if (got == null) {
            missing.add(what);
        }
    }

    /** 诊断日志节流时间戳（毫秒） */
    private static volatile long lastDiag = 0L;

    /**
     * 节流诊断日志：取料/失败时输出（每 5 秒最多一条），避免每方块刷屏。
     * <p>⚠️ 本次（2026-09-12）的两次 AE bug 都因为"静默吞异常"而难以排查，
     * 因此关键失败路径必须有可观察输出。
     */
    private static void diag(String msg, Object... args) {
        long now = System.currentTimeMillis();
        if (now - lastDiag < 5000L) {
            return;
        }
        lastDiag = now;
        LOG.warn("[AE2] " + msg, args);
    }

    private static void mv(List<String> missing, String what, Throwable t) {
        missing.add(what + " (" + t + ")");
    }

    /** 按名字 + 参数个数找方法（优先声明类为 public 的那个，避免 setAccessible 失败） */
    private static Method findMethod(Class<?> cls, String name, int paramCount) {
        if (cls == null) {
            return null;
        }
        Method fallback = null;
        for (Method m : cls.getMethods()) {
            if (!m.getName().equals(name) || m.getParameterCount() != paramCount) {
                continue;
            }
            if (Modifier.isPublic(m.getDeclaringClass().getModifiers())) {
                return m; // public 类的 public 方法，直接用
            }
            if (fallback == null) {
                fallback = m;
            }
        }
        return usable(fallback);
    }

    /** 非 public 声明类的方法才需要 setAccessible（对 public 方法调用可能抛 InaccessibleObjectException） */
    private static Method usable(Method m) {
        if (m == null) {
            return null;
        }
        if (!Modifier.isPublic(m.getDeclaringClass().getModifiers())
                || !Modifier.isPublic(m.getModifiers())) {
            try {
                m.setAccessible(true);
            } catch (Throwable ignored) {
                // 保持原样，invoke 时若失败会被上层 catch
            }
        }
        return m;
    }

    /**
     * 在<b>运行时类型</b>上解析方法（不依赖预知的接口类名，包名漂移免疫）。
     * <p>结果按 {@code 类名#方法#参数数} 缓存；找不到也缓存（避免反复扫描）。
     */
    private static Method runtimeMethod(Class<?> cls, String name, int paramCount) {
        if (cls == null) {
            return null;
        }
        String key = cls.getName() + '#' + name + '#' + paramCount;
        if (METHOD_MISS.contains(key)) {
            return null;
        }
        Method cached = METHOD_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        Method found = findMethod(cls, name, paramCount);
        if (found == null) {
            METHOD_MISS.add(key);
            return null;
        }
        METHOD_CACHE.put(key, found);
        return found;
    }

    /** 按名字取枚举常量（反射，无需编译期依赖） */
    private static Object enumConstant(Class<?> cls, String name) {
        if (cls == null || !cls.isEnum()) {
            return null;
        }
        for (Object c : cls.getEnumConstants()) {
            if (c instanceof Enum<?> e && e.name().equals(name)) {
                return c;
            }
        }
        return null;
    }

    /** ME 网络完整存取链是否可用（未装 AE2 / 解析不全 → false，一切走原有容器逻辑） */
    public static boolean isAvailable() {
        resolve();
        return storageReady;
    }

    /**
     * 该方块是否是 AE2 无线访问点（{@code IWirelessAccessPoint}）。
     * <p>⚠️ 只依赖 {@code wapReady}（1 个接口类），<b>不</b>依赖完整存取链——
     * 这样即使存取 API 漂移，也不会把 WAP 误当普通容器去踩它的增压卡槽。
     */
    public static boolean isWirelessAccessPoint(Level level, BlockPos pos) {
        if (level == null || pos == null) {
            return false;
        }
        resolve();
        if (!wapReady) {
            return false;
        }
        BlockEntity be = level.getBlockEntity(pos);
        return be != null && wapClass.isInstance(be);
    }

    /**
     * 取该无线访问点所在 ME 网络的存储视图（非 WAP / 无网络 / 网络未成形 → null）。
     * <p>链路：WAP.getGrid() → IGrid.getStorageService() → 返回对象.getInventory()。
     * 后两步用运行时类型解析，不写 AE2 的接口包名。
     */
    private static Object storageOf(Level level, BlockPos pos) {
        if (level == null || pos == null || !storageReady) {
            return null;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null || !wapClass.isInstance(be)) {
            return null;
        }
        try {
            Object grid = getGrid.invoke(be);
            if (grid == null) {
                return null; // 未连接到网络
            }
            Object service = getStorageService.invoke(grid);
            if (service == null) {
                return null;
            }
            Method getInventory = runtimeMethod(service.getClass(), "getInventory", 0);
            if (getInventory == null) {
                return null;
            }
            return getInventory.invoke(service);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 把物品插入该无线访问点所在的 ME 网络。
     *
     * <p>与 Building Gadgets 2 的 {@code insertIntoAE2} 一致：直接 MODULATE，
     * 按返回的实际插入量 shrink 剩余（网络满/不收该物品 → 插入量 0，原样退回）。
     *
     * @return 剩余未插入的物品（全部插入 → {@link ItemStack#EMPTY}）
     */
    public static ItemStack insert(Level level, BlockPos pos, ItemStack stack, Player player) {
        if (stack == null || stack.isEmpty() || player == null) {
            return stack;
        }
        Object storage = storageOf(level, pos);
        if (storage == null) {
            return stack;
        }
        try {
            Method insert = runtimeMethod(storage.getClass(), "insert", 4);
            if (insert == null) {
                return stack;
            }
            Object key = aeItemKeyOf.invoke(null, stack);
            if (key == null) {
                return stack; // 该物品无法表示为 AEKey（理论上不会）
            }
            Object source = sourceOfPlayer.invoke(null, player);
            Object movedObj = insert.invoke(storage, key, (long) stack.getCount(), actionableModulate, source);
            long moved = movedObj instanceof Number n ? n.longValue() : 0L;
            if (moved <= 0) {
                return stack; // 网络拒收/已满 → 剩余全部退回
            }
            if (moved >= stack.getCount()) {
                return ItemStack.EMPTY;
            }
            ItemStack leftover = stack.copy();
            leftover.shrink((int) moved);
            return leftover;
        } catch (Throwable t) {
            return stack; // 反射异常（跨版本 API 漂移）→ 静默回落
        }
    }

    /**
     * 从该无线访问点所在的 ME 网络取出一组匹配的物品（最多 count 个）。
     *
     * <p>先 SIMULATE 试探可提取量，再 MODULATE 真正取出（与 Building Gadgets 2 同策略）。
     * 供「选区放置」取料用。
     *
     * @return 取出的物品（取不到 → {@link ItemStack#EMPTY}）
     */
    public static ItemStack extract(Level level, BlockPos pos, Predicate<ItemStack> match,
                                    int count, Player player) {
        if (match == null || count <= 0 || player == null) {
            return ItemStack.EMPTY;
        }
        Object storage = storageOf(level, pos);
        if (storage == null) {
            return ItemStack.EMPTY;
        }
        try {
            Method extract = runtimeMethod(storage.getClass(), "extract", 4);
            Method available = runtimeMethod(storage.getClass(), "getAvailableStacks", 0);
            if (extract == null || available == null) {
                return ItemStack.EMPTY;
            }
            Object counter = available.invoke(storage);
            if (counter == null) {
                return ItemStack.EMPTY;
            }
            Method keySet = runtimeMethod(counter.getClass(), "keySet", 0);
            if (keySet == null) {
                return ItemStack.EMPTY;
            }
            Object rawKeys = keySet.invoke(counter);
            if (!(rawKeys instanceof Set<?> keys) || keys.isEmpty()) {
                return ItemStack.EMPTY;
            }
            Object source = sourceOfPlayer.invoke(null, player);
            int keyCount = 0;
            int itemKeyCount = 0;
            int matched = 0;
            for (Object key : keys) {
                if (key == null) {
                    continue;
                }
                keyCount++;
                // 只要可表示为物品的 key（跳过流体等其他 AEKey）
                // ⚠️ 单个 key 判定失败不应中断整体取料（容错）
                boolean isItem;
                try {
                    isItem = Boolean.TRUE.equals(aeItemKeyIs.invoke(null, key));
                } catch (Throwable t) {
                    isItem = false;
                }
                if (!isItem) {
                    continue;
                }
                itemKeyCount++;
                Object display = aeKeyWrap.invoke(key);
                if (!(display instanceof ItemStack rep) || rep.isEmpty() || !match.test(rep)) {
                    continue;
                }
                matched++;
                Object availObj = extract.invoke(storage, key, (long) count, actionableSimulate, source);
                long avail = availObj instanceof Number n ? n.longValue() : 0L;
                if (avail <= 0) {
                    continue;
                }
                Object takenObj = extract.invoke(storage, key, avail, actionableModulate, source);
                long taken = takenObj instanceof Number n ? n.longValue() : 0L;
                if (taken <= 0) {
                    continue;
                }
                return rep.copyWithCount((int) Math.min(taken, Integer.MAX_VALUE));
            }
            // 没取到：输出可诊断信息（节流，避免每方块刷屏）——
            // 若 key=0 说明网络库存读不到；若 物品key=0 可能 AEItemKey.is 解析错；
            // 若 匹配=0 说明网络里没有目标物品；若匹配>0 但无结果 则 AE2 拒提
            diag("AE 取料未命中（网络key=%d, 物品key=%d, 匹配=%d, 需求=%d）", keyCount, itemKeyCount, matched, count);
            return ItemStack.EMPTY;
        } catch (Throwable t) {
            return ItemStack.EMPTY;
        }
    }
}
