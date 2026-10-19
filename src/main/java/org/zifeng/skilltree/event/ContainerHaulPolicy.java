package org.zifeng.skilltree.event;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zifeng.skilltree.Config;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 搬运术「可搬运来源」过滤器（★ 2026-10-18 新增）。
 *
 * <h2>为什么要单独抽一个类</h2>
 * 旧实现（{@code ContainerHaulEvents.transferFromMenu}）的规则是<b>否定式</b>的：
 * <blockquote>
 *   「菜单里除了【玩家背包】【AE2 设备槽】【Curios 饰品槽】以外的所有槽位，都当成外部容器内容搬走」
 * </blockquote>
 * 这条规则天生不可能穷举：世界上有多少模组，就有多少种「不是容器的槽位」。
 * 每踩一个坑就补一条黑名单（AE → Curios → 下一个…），而<b>没被补上的模组一律在出事</b>：
 * 机器里的升级卡、能量单元、模具、进度槽、输入输出缓冲、甚至设备配置槽都会被搬进绑定容器，
 * 玩家看到的就是「打开某台机器，里面的东西自己没了」。
 *
 * <p>所以本类把判定<b>翻过来</b>：默认<b>不搬</b>，只有当能<b>正向证明</b>
 * 「这个槽位装的是某个存储容器的内容」时才搬；证明不了就跳过（安全失败）。
 * 新模组进来最坏的结果是「搬运术对它没反应」，而不是「把它拆了」。
 *
 * <h2>判定顺序（后面的规则一律不能否决前面的）</h2>
 * <ol>
 *   <li><b>玩家背包</b>（主物品栏/快捷栏/盔甲/副手）→ 不搬；</li>
 *   <li><b>黑名单</b>（内置 AE2 / RS / Curios + 配置）→ 不搬。<b>黑名单优先级最高</b>，
 *       写进白名单也搬不了（那是设备零件 / 网络投影，搬了就是元件消失）；</li>
 *   <li><b>白名单</b>（内置存储模组 + 配置）→ 搬。<b>白名单排在这里，就是为了让
 *       「潜影盒 / 末影箱 / 漏斗 / 熔炉」这些默认不搬的原版方块能被整合包按需放行</b>；</li>
 *   <li><b>原版方块</b>（{@code minecraft:} 命名空间）→ <b>只认纯存储三件套</b>
 *       （箱子 / 陷阱箱 / 木桶），其余<b>默认全部不搬</b>：
 *       漏斗、投掷器、发射器、熔炉、高炉、烟熏炉、酿造台、合成器、装饰罐……
 *       这些是<b>功能性方块</b>，搬走等于把机器/自动化拆了；
 *       潜影盒、末影箱则是玩家用来「随身转移物品」的，自动掏空同样不是用户预期
 *       —— 它们都能通过白名单放行，但默认不碰；</li>
 *   <li><b>模组方块</b>：走原版容器管线（{@code slot.container} 是 {@code BlockEntity}，
 *       即该 BE 实现了 {@code Container}）→ 视为存储容器，搬。这类几乎都是箱子类；
 *       若某台机器恰好也这么写，用配置黑名单一行解决；</li>
 *   <li>其余（绝大多数科技机器的 {@code SlotItemHandler}：升级卡槽、能量槽、输入输出缓冲）
 *       → <b>证明不了 → 不搬</b>。</li>
 * </ol>
 *
 * <h2>兜底闸门（与上面的来源判定并行生效）</h2>
 * <ul>
 *   <li>AE2 设备槽、Curios 饰品槽 —— 沿用 {@link ContainerHaulEvents} 的老判定；</li>
 *   <li>槽位自身语义：{@code isActive()==false}（隐藏/未激活）、{@code mayPickup()==false}
 *       （只进不出的输出槽、锁定槽）→ 不搬。</li>
 * </ul>
 *
 * <h2>为什么还留一个「宽松模式」开关</h2>
 * {@code containerHaul.containerHaulStrict=false} 时退回旧行为（只排除黑名单）。
 * 给两种情况用：① 整合包作者确认环境干净、就要「什么都能搬」；
 * ② 出问题时一键对比，确认新判定是不是元凶。<b>默认 true（安全优先）</b>。
 *
 * <p>⚠️ 本类只回答「搬不搬」，不碰物品本身：
 * 「读副本再插入、按实际搬走量扣源槽」的 2026-09-30 修复仍在 {@link ContainerHaulEvents} 里，未改动。
 */
public final class ContainerHaulPolicy {
    private ContainerHaulPolicy() {
    }

    private static final Logger LOG = LoggerFactory.getLogger("zifeng_skilltree");

    // ============ 匹配规则 ============
    // 条目支持通配符 '*'（任意长度，可出现在开头/中间/结尾），不分大小写；不含 '*' 时按全等处理。
    // 例："net.p3pp3rf1y.sophisticated*"、"minecraft:*shulker_box*"、"minecraft:hopper"。
    // 被匹配的「签名串」有四层：菜单类名 / 槽位类名 / 槽位容器类名 /（能取到时）方块 ID。

    /**
     * 内置白名单：已确认是「纯存储容器」的模组。
     * <p>它们都用 {@code IItemHandler} 存物品，{@code slot.container} 不是 BlockEntity，
     * 光靠原版管线判不出来，所以必须点名。
     */
    private static final String[] BUILTIN_STORAGE = {
            "net.p3pp3rf1y.sophisticated*",            // 精妙存储 / 精妙背包（Storage / Backpacks / Barrels）
            "com.jaquadro.minecraft.storagedrawers*",  // 储物抽屉
            "com.buuz135.functionalstorage*",          // 功能存储
            "com.progwml6.ironchest*",                 // Iron Chests（部分版本不走原版 BE 容器）
    };

    /**
     * 内置黑名单：<b>永远不搬</b>，写进配置白名单也救不回来。
     * <p>这些不是「容器」，而是「设备 / 网络视图」——槽里是设备零件或网络投影，
     * 取走 = 设备损坏（AE 驱动器里的元件当初就是这么消失的）。
     */
    private static final String[] BUILTIN_BLACKLIST = {
            "appeng.*",                          // AE2：存储元件 / 升级卡 / 样板 / 配置
            "com.refinedmods.refinedstorage*",   // RS（新版包名）
            "com.raoulvdberge.refinedstorage*",  // RS（旧版包名）
            "top.theillusivec4.curios.*",        // 饰品栏
    };

    /**
     * 原版命名空间下<b>默认允许搬运</b>的方块：纯存储三件套。
     * <p>不在这个集合里的原版方块<b>一律默认不搬</b>（可白名单放行）：
     * <ul>
     *   <li>功能性：{@code hopper} / {@code dropper} / {@code dispenser} / {@code furnace} /
     *       {@code blast_furnace} / {@code smoker} / {@code brewing_stand} / {@code crafter} /
     *       {@code decorated_pot} —— 搬它们等于拆机器；</li>
     *   <li>随身转移容器：{@code shulker_box} 及 16 种染色变体 / {@code ender_chest}
     *       —— 玩家拿它们运货，自动掏空不是预期行为。</li>
     * </ul>
     */
    private static final Set<String> VANILLA_STORAGE = Set.of(
            "minecraft:chest",
            "minecraft:trapped_chest",
            "minecraft:barrel"
    );

    /** 末影箱的槽位容器类名（不是 BlockEntity，取不到方块 ID，需映射成 pseudo id 才能按方块判定/白名单） */
    private static final String ENDER_CHEST_CONTAINER = "PlayerEnderChestContainer";

    /** 末影箱的 pseudo 方块 ID（供白名单/黑名单匹配） */
    private static final String ENDER_CHEST_ID = "minecraft:ender_chest";

    /** 已提示过的「未识别容器」类（每类只提示一次，上限 64 类，防刷日志） */
    private static final Set<String> WARNED_UNKNOWN = ConcurrentHashMap.newKeySet();

    // ============ 对外唯一入口 ============

    /**
     * 这个槽位是否应该被搬运术搬走。
     *
     * @param player 执行搬运的玩家（用于 {@code mayPickup} 与背包比对）
     * @param menu   玩家当前打开的菜单（参与签名匹配）
     * @param slot   待判定的槽位
     * @return true = 可以搬（已证明是存储容器的内容）；false = 跳过
     */
    public static boolean shouldHaul(ServerPlayer player, AbstractContainerMenu menu, Slot slot) {
        if (player == null || slot == null) {
            return false;
        }
        // ① 玩家背包（主物品栏/快捷栏/盔甲/副手都属于 player 的背包 Container）
        if (slot.container == player.getInventory()) {
            return false;
        }
        final Container container = slot.container;
        if (container == null) {
            return false; // 少数模组给 Slot 传 null 容器（纯展示槽）→ 一定不是容器内容
        }
        final String id = resolveBlockId(container);
        final List<String> sigs = signatures(menu, slot, container, id);

        // ② 黑名单优先：内置 + 配置（设备零件 / 网络视图 / 玩家自身）
        if (matches(BUILTIN_BLACKLIST, sigs) || matches(cfgBlacklist(), sigs)) {
            return false;
        }
        // ③ 沿用老判定：AE2 设备槽、Curios 饰品槽（白名单也绕不过）
        if (ContainerHaulEvents.isAeDeviceSlot(slot) || ContainerHaulEvents.isPlayerSideSlot(slot)) {
            return false;
        }
        // ④ 槽位自身语义：隐藏槽 / 只出不进的槽（结果槽、锁定槽、输出缓冲）
        if (!slot.isActive()) {
            return false;
        }
        try {
            if (!slot.mayPickup(player)) {
                return false;
            }
        } catch (Throwable ignored) {
            // 少数模组的 mayPickup 会抛异常（依赖尚未初始化的自己）→ 继续交给后面正向判定
        }

        // ⑤ 严格模式（默认）：必须正向证明这是存储容器的内容
        if (cfgStrict()) {
            return isProvenStorage(container, id, sigs);
        }
        return true; // 宽松模式：退回旧行为（除黑名单全搬）
    }

    /**
     * 正向证明：这个槽位装的是「某个存储容器的内容」。
     *
     * @param id 方块注册 ID（取不到为 null；末影箱用 pseudo id）
     */
    private static boolean isProvenStorage(Container container, String id, List<String> sigs) {
        // (1) 白名单：内置存储模组 + 配置。★ 放在原版判定之前，
        //     这样「minecraft:*shulker_box*」「minecraft:ender_chest」「minecraft:hopper」
        //     都能被整合包按需放行 —— 默认不搬，但用户说了算。
        if (matches(BUILTIN_STORAGE, sigs) || matches(cfgWhitelist(), sigs)) {
            return true;
        }
        // (2) 原版方块：只认纯存储三件套，功能性方块与随身转移容器默认不搬
        if (id != null && id.startsWith("minecraft:")) {
            return VANILLA_STORAGE.contains(id);
        }
        // (3) 模组方块：走原版容器管线（BE 实现了 Container）→ 视为存储容器（这类几乎都是箱子类）
        if (container instanceof BlockEntity) {
            return true;
        }
        // (4) 证明不了 → 不搬（并给整合包作者留一条「怎么加白名单」的线索）
        warnUnknownOnce(sigs);
        return false;
    }

    /**
     * 是否处于「严格模式」（默认 true）。
     * <p>配置尚未加载时 {@code .get()} 会抛异常 → 回退 true（安全优先）。
     */
    public static boolean cfgStrict() {
        try {
            return Config.CONTAINER_HAUL_STRICT.get();
        } catch (Throwable t) {
            return true;
        }
    }

    /** 逐槽调试日志开关（默认 false；打开后每次搬运会打印每个槽位的判定结果） */
    public static boolean debugEnabled() {
        try {
            return Config.CONTAINER_HAUL_DEBUG.get();
        } catch (Throwable t) {
            return false;
        }
    }

    private static List<? extends String> cfgWhitelist() {
        try {
            return Config.CONTAINER_HAUL_EXTRA_WHITELIST.get();
        } catch (Throwable t) {
            return List.of();
        }
    }

    private static List<? extends String> cfgBlacklist() {
        try {
            return Config.CONTAINER_HAUL_BLACKLIST.get();
        } catch (Throwable t) {
            return List.of();
        }
    }

    // ============ 方块 ID ============

    /**
     * 槽位背后的方块注册 ID。
     * <ul>
     *   <li>普通方块实体 → 原版 {@code BuiltInRegistries.BLOCK} 的 ID；</li>
     *   <li>末影箱 → 容器是 {@code PlayerEnderChestContainer}（挂在玩家身上的 Container，没有方块实体），
     *       这里手动映射成 {@code minecraft:ender_chest}，否则它在白/黑名单里永远匹配不上；</li>
     *   <li>其他（{@code IItemHandler} 包装、{@code SimpleContainer}…）→ null。</li>
     * </ul>
     */
    private static String resolveBlockId(Container container) {
        if (container == null) {
            return null;
        }
        if (container instanceof BlockEntity be) {
            try {
                Block block = be.getBlockState().getBlock();
                if (!BuiltInRegistries.BLOCK.containsKey(block)) {
                    return null;
                }
                return BuiltInRegistries.BLOCK.getKey(block).toString();
            } catch (Throwable t) {
                return null;
            }
        }
        if (container.getClass().getName().contains(ENDER_CHEST_CONTAINER)) {
            return ENDER_CHEST_ID;
        }
        return null;
    }

    // ============ 签名 / 匹配 ============

    /**
     * 生成用于通配符匹配的「签名串」。
     *
     * <p>故意把菜单、槽位、容器、方块四层名字都放进去：同一个模组可能只有菜单类带包名
     * （槽位复用原版 Slot），也可能只有容器类带包名，甚至槽位就是原版 {@code Slot} 而方块是它自己的。
     * 四层全比一遍，整合包作者随便填哪个前缀都能命中，不用先去猜「该填哪一层」。
     */
    private static List<String> signatures(AbstractContainerMenu menu, Slot slot, Container container, String id) {
        List<String> out = new ArrayList<>(6);
        if (menu != null) {
            out.add(menu.getClass().getName());
        }
        out.add(slot.getClass().getName());
        out.add(container.getClass().getName());
        if (container instanceof BlockEntity be) {
            out.add(be.getClass().getName());
        }
        if (id != null) {
            out.add(id);
        }
        return out;
    }

    private static boolean matches(String[] fixed, List<String> sigs) {
        return matches(fixed, null, sigs);
    }

    private static boolean matches(List<? extends String> extra, List<String> sigs) {
        return matches(null, extra, sigs);
    }

    private static boolean matches(String[] fixed, List<? extends String> extra, List<String> sigs) {
        if (sigs == null || sigs.isEmpty()) {
            return false;
        }
        for (String sig : sigs) {
            if (sig == null) {
                continue;
            }
            if (fixed != null) {
                for (String p : fixed) {
                    if (glob(p, sig)) {
                        return true;
                    }
                }
            }
            if (extra != null) {
                for (String p : extra) {
                    if (glob(p, sig)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * 通配符匹配（不分大小写，{@code *} 表示任意长度，{@code ?} 表示单字符）。
     * <p>经典双指针 + 回溯写法：不依赖正则、不分配对象，槽位循环里逐条比对也不心疼。
     * 不含 {@code *} 时等价于全等比较 —— 想要「前缀放行」就写 {@code "foo.bar.*"}，
     * 想精确到单个方块就写 {@code "minecraft:hopper"}。
     */
    private static boolean glob(String pattern, String sig) {
        if (pattern == null || sig == null) {
            return false;
        }
        final String p = pattern.trim();
        if (p.isEmpty()) {
            return false;
        }
        int pi = 0;
        int si = 0;
        int star = -1;
        int mark = 0;
        while (si < sig.length()) {
            if (pi < p.length()
                    && (p.charAt(pi) == '?'
                    || Character.toLowerCase(p.charAt(pi)) == Character.toLowerCase(sig.charAt(si)))) {
                pi++;
                si++;
            } else if (pi < p.length() && p.charAt(pi) == '*') {
                star = pi++;
                mark = si;
            } else if (star >= 0) {
                pi = star + 1;
                si = ++mark;
            } else {
                return false;
            }
        }
        while (pi < p.length() && p.charAt(pi) == '*') {
            pi++;
        }
        return pi == p.length();
    }

    /**
     * 「未识别的容器」提示（每类菜单只打一次，上限 64 类）。
     * <p>这是给整合包作者的关键线索：日志里会直接给出该填进白名单的前缀，
     * 不用再让人去翻 jar 找类名。
     */
    private static void warnUnknownOnce(List<String> sigs) {
        String menuName = sigs.isEmpty() ? "?" : sigs.get(0);
        if (WARNED_UNKNOWN.size() >= 64 || !WARNED_UNKNOWN.add(menuName)) {
            return;
        }
        String hint = menuName.contains(".")
                ? menuName.substring(0, menuName.lastIndexOf('.')) + ".*"
                : menuName;
        LOG.info("[搬运术] 未识别的容器，已跳过搬运（安全优先）：menu={} slot={} container={} → 若确认它是纯存储容器，"
                        + "把 \"{}\" 加入 common 配置 containerHaul.containerHaulExtraWhitelist 即可搬运",
                menuName,
                sigs.size() > 1 ? sigs.get(1) : "?",
                sigs.size() > 2 ? sigs.get(2) : "?",
                hint);
    }

    /** 单槽判定过程的文字描述（仅调试开关打开时调用，用于定位「为什么没搬/为什么搬了」） */
    public static String describe(AbstractContainerMenu menu, Slot slot) {
        if (slot == null) {
            return "slot=null";
        }
        Container c = slot.container;
        return "slot=" + slot.getClass().getName()
                + " container=" + (c == null ? "null" : c.getClass().getName())
                + " block=" + (c == null ? "-" : String.valueOf(resolveBlockId(c)))
                + " active=" + slot.isActive()
                + " index=" + slot.index
                + " strict=" + cfgStrict();
    }
}
