package org.zifeng.skilltree.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.TickEvent;
import org.zifeng.skilltree.SkillTreeMod;
import org.zifeng.skilltree.client.screen.SkillTreeScreen;
import org.zifeng.skilltree.network.OpenSkillTreeC2SPacket;
import org.zifeng.skilltree.network.SetSkillToggleC2SPacket;
import org.zifeng.skilltree.skill.Skills;

import java.util.HashMap;
import java.util.Map;

/**
 * 快捷键检测（GAME 总线，由 ClientRegistrar 手动注册）：
 * N = 打开技能树；每个技能的开关快捷键由 技能树界面内 SkillKeyBinds 独立绑定（本地持久化）。
 */
public class ModKeyBindingEvents {

    /** 光环技能开关缓存（服务端回发校准，供快捷键取反发送） */
    private static final Map<String, Boolean> auraToggles = new HashMap<>();

    /** 光环总开关客户端缓存（服务端回发校准，供圆环渲染器判断是否显示） */
    private static boolean auraEnabledClient = true;

    /** 磁力光环是否已学习（客户端缓存，服务端回发校准，圆环渲染判断用） */
    private static boolean magnetLearnedClient = false;

    /** 杀戮光环·伤害 是否已学（客户端缓存，服务端回发校准；配合 toggles 判断光环真实状态） */
    private static boolean auraDamageLearned = false;

    /** 所有光环技能已学缓存（独立快捷键未学不触发用） */
    private static final Map<String, Boolean> auraLearnedCache = new HashMap<>();

    /** 所有技能已学缓存（2026-08-13：任意技能可绑定独立开关快捷键，用全部技能已学状态判断） */
    private static final Map<String, Boolean> allSkillsLearnedCache = new HashMap<>();

    /** 所有技能已学等级缓存（2026-08-13 第二快捷键循环等级用：技能ID → 已学等级） */
    private static final Map<String, Integer> allSkillsLevelsCache = new HashMap<>();

    /** 所有技能开关缓存（2026-08-13：全技能开关状态，供快捷键与渲染辅助） */
    private static final Map<String, Boolean> allTogglesCache = new HashMap<>();

    /** 所有技能生效等级缓存（2026-08-13 第二快捷键循环等级用：技能ID → 当前生效等级） */
    private static final Map<String, Integer> allActiveLevelsCache = new HashMap<>();

    /** 凋落物挪移绑定容器缓存（2026-08-24 服务端回发校准，技能树 tooltip 显示 "绑定容器+坐标"；null=未绑定） */
    private static String lootVacuumBindClient = null;

    // ============ 木棍工具层缓存（2026-09-08：总开关 + 模式；服务端回发校准） ============
    /** 工具总开关（true=木棍被工具层占用；false=还原原版木棍，技能被动/绑定/屏蔽数据不受影响） */
    private static boolean stickToolOnClient = true;
    /** 工具模式（0=BIND 潜行右键绑容器；1=RANGE 左键框选磁铁屏蔽区） */
    private static int stickToolModeClient = 0;

    /** 工具总开关是否开启（客户端缓存；供渲染/信息栏/手势路由） */
    public static boolean isStickToolOnClient() {
        return stickToolOnClient;
    }

    public static void setStickToolOnClient(boolean on) {
        stickToolOnClient = on;
    }

    /** 工具当前模式（0=BIND 1=RANGE；客户端缓存） */
    public static int getStickToolModeClient() {
        return stickToolModeClient;
    }

    public static void setStickToolModeClient(int mode) {
        if (mode >= 0 && mode <= 5) {
            stickToolModeClient = mode;
        }
    }

    /** 服务端数据包统一校准 */
    public static void setStickToolStateClient(boolean on, int mode) {
        stickToolOnClient = on;
        if (mode >= 0 && mode <= 5) {
            stickToolModeClient = mode;
        }
    }

    /** 是否已学任一木棍工具功能技能（磁铁 RANGE / 挪移+搬运 BIND / 机械共鸣区块）——信息栏显示条件（2026-09-08） */
    public static boolean hasAnyStickToolSkillClient() {
        return auraLearnedCache.getOrDefault(Skills.AURA_MAGNET, Boolean.FALSE)
                || auraLearnedCache.getOrDefault(Skills.AURA_LOOT_VACUUM, Boolean.FALSE)
                || auraLearnedCache.getOrDefault(Skills.CONTAINER_HAUL, Boolean.FALSE)
                || allSkillsLearnedCache.getOrDefault(Skills.MACHINE_ZONE_PLACE, Boolean.FALSE)
                || allSkillsLearnedCache.getOrDefault(Skills.MACHINE_ZONE_EXCAVATE, Boolean.FALSE)
                || allSkillsLearnedCache.getOrDefault(Skills.MACHINE_ZONE_ATTACK, Boolean.FALSE)
                || allSkillsLearnedCache.getOrDefault(Skills.MACHINE_ZONE_PROTECT, Boolean.FALSE);
    }

    // ============ 机械共鸣·操作区缓存（2026-09-08，渲染操作区框用） ============
    /** 技能ID → 操作区（服务端回发校准；null=未框选） */
    private static final Map<String, org.zifeng.skilltree.data.OperZone> operZonesClient = new java.util.HashMap<>();

    /** 服务端回发校准全部操作区 */
    public static void setOperZonesClient(Map<String, org.zifeng.skilltree.data.OperZone> zones) {
        operZonesClient.clear();
        if (zones != null) {
            operZonesClient.putAll(zones);
        }
    }

    /** 取指定技能操作区（null=未框选） */
    public static org.zifeng.skilltree.data.OperZone getOperZoneClient(String skillId) {
        return operZonesClient.get(skillId);
    }

    /** 全部操作区 */
    public static Map<String, org.zifeng.skilltree.data.OperZone> getOperZonesClient() {
        return operZonesClient;
    }

    // ============ 防护区多块列表缓存（2026-09-08：渲染全部防护区框 + 射线删除用） ============
    private static final java.util.List<org.zifeng.skilltree.data.OperZone> protectZonesClient = new java.util.ArrayList<>();

    /** 服务端回发校准防护区列表 */
    public static void setProtectZonesClient(java.util.List<org.zifeng.skilltree.data.OperZone> zones) {
        protectZonesClient.clear();
        if (zones != null) {
            protectZonesClient.addAll(zones);
        }
    }

    /** 全部防护区（只读） */
    public static java.util.List<org.zifeng.skilltree.data.OperZone> getProtectZonesClient() {
        return java.util.Collections.unmodifiableList(protectZonesClient);
    }

    /**
     * 木棍工具下一模式（2026-09-08）：0..5 顺序循环，但跳过【未解锁】的模式——
     * BIND=学了容器绑技能；RANGE=学了磁铁；ZONE_PLACE/EXCAVATE/ATTACK/PROTECT=学了对应机械共鸣区块技能。
     */
    public static int nextUnlockedStickMode(int current) {
        int total = Skills.stickModeCount();
        for (int i = 1; i <= total; i++) {
            int m = (current + i) % total;
            if (isStickModeUnlocked(m)) {
                return m;
            }
        }
        return current; // 全未解锁（不应发生）
    }

    /** 该模式是否已解锁（客户端已学判定；BIND/RANGE 看对应绑技能/磁铁） */
    public static boolean isStickModeUnlocked(int mode) {
        return switch (mode) {
            case Skills.STICK_MODE_BIND -> hasBindSkillClient();
            case Skills.STICK_MODE_RANGE -> magnetLearnedClient;
            default -> {
                String skill = Skills.skillForStickMode(mode);
                yield skill != null && allSkillsLearnedCache.getOrDefault(skill, Boolean.FALSE);
            }
        };
    }

    /** 任意技能是否已学（客户端缓存统一入口，技能树 UI 勾选模块用） */
    public static boolean isSkillLearnedAnywhere(String skillId) {
        return auraLearnedCache.getOrDefault(skillId, Boolean.FALSE)
                || allSkillsLearnedCache.getOrDefault(skillId, Boolean.FALSE);
    }

    /** 是否已学容器绑技能（挪移/搬运任一） */
    private static boolean hasBindSkillClient() {
        return auraLearnedCache.getOrDefault(Skills.AURA_LOOT_VACUUM, Boolean.FALSE)
                || auraLearnedCache.getOrDefault(Skills.CONTAINER_HAUL, Boolean.FALSE);
    }

    /** 木棍模式切换即时反馈（2026-09-08）：actionbar 显示当前功能名 */
    public static void feedbackStickMode(int mode) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        mc.player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                "ui.zifeng_s_custom_skill_tree.stick_tool_now",
                net.minecraft.network.chat.Component.translatable(
                        "ui.zifeng_s_custom_skill_tree." + StickToolModes.modeLang(mode))), true);
    }

    /** 绑定容器缓存字段（结构化串 + 解析出的维度/坐标；2026-09-08） */
    private static String bindDimClient = null;
    private static int bindXClient, bindYClient, bindZClient;

    /**
     * 获取凋落物挪移绑定的容器显示串（"name [x, y, z]"，null=未绑定；tooltip 等 UI 用）。
     * 底层存结构化 "dim|name|x|y|z"，由 setLootVacuumBindClient 解析出维度/坐标。
     */
    public static String getLootVacuumBindClient() {
        if (lootVacuumBindClient == null) {
            return null;
        }
        String[] p = lootVacuumBindClient.split("\\|");
        if (p.length == 5) {
            return p[1] + " [" + p[2] + ", " + p[3] + ", " + p[4] + "]";
        }
        return lootVacuumBindClient; // 兼容旧存档数据格式
    }

    /** 绑定容器所在维度（null=未绑定；绑定容器固定框用） */
    public static String getBindDimClient() {
        return bindDimClient;
    }

    public static int getBindXClient() {
        return bindXClient;
    }

    public static int getBindYClient() {
        return bindYClient;
    }

    public static int getBindZClient() {
        return bindZClient;
    }

    /** 服务端回发校准凋落物挪移绑定容器（结构化 "dim|name|x|y|z"；null=未绑定） */
    public static void setLootVacuumBindClient(String bind) {
        lootVacuumBindClient = bind;
        bindDimClient = null;
        if (bind == null) {
            return;
        }
        String[] p = bind.split("\\|");
        if (p.length == 5) {
            bindDimClient = p[0];
            try {
                bindXClient = Integer.parseInt(p[2]);
                bindYClient = Integer.parseInt(p[3]);
                bindZClient = Integer.parseInt(p[4]);
            } catch (NumberFormatException ignored) {
                bindDimClient = null;
            }
        }
    }

    /** 玩家断开连接/切换服务器时清空全部缓存（2026-08-25 多人防跨服数据残留） */
    public static void onDisconnect() {
        auraToggles.clear();
        auraEnabledClient = true;
        magnetLearnedClient = false;
        auraDamageLearned = false;
        auraLearnedCache.clear();
        allSkillsLearnedCache.clear();
        allSkillsLevelsCache.clear();
        allTogglesCache.clear();
        allActiveLevelsCache.clear();
        lootVacuumBindClient = null;
        bindDimClient = null;
        auraTargetModes.clear();
        stickToolOnClient = true;   // 断开重置：默认开 + BIND
        stickToolModeClient = 0;
        operZonesClient.clear();
        protectZonesClient.clear();
    }

    /** 辅助：已学等级 */
    private static int learnedPointsOf(String skillId) {
        return allSkillsLevelsCache.getOrDefault(skillId, 0);
    }

    /** 辅助：当前生效等级 */
    private static int activeLevelOf(String skillId) {
        return allActiveLevelsCache.getOrDefault(skillId, 0);
    }

    /**
     * 辅助：技能开关是否开启（2026-09-07 三键串联逻辑核心）。
     * 子1级（开关键）未激活 → 子2级（模式键）/子3级（触发键）全部无功能。
     * 数据源：服务端校准的 allTogglesCache（默认开）；若本地开关键刚乐观切换过
     * （写 auraToggles）则优先读它，保证本次 tick 内操作即时生效。
     */
    private static boolean isToggleOnClient(String skillId) {
        if (auraToggles.containsKey(skillId)) {
            return auraToggles.getOrDefault(skillId, Boolean.TRUE);
        }
        return allTogglesCache.getOrDefault(skillId, Boolean.TRUE);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        org.zifeng.skilltree.client.SkillKeyBinds.load(); // 确保技能绑定已加载
        // 技能独立开关快捷键（2026-08-13：每个技能可单独绑定，SkillKeyBinds 本地持久化）
        // 按下即切换该技能开关；未学的技能不处理（等服务端校准）
        if (Minecraft.getInstance().screen == null) { // 界面打开时不触发（避免与设置窗口/技能树交互冲突）
            for (String skillId : org.zifeng.skilltree.client.SkillKeyBinds.allBinds().keySet()) {
                if (org.zifeng.skilltree.client.SkillKeyBinds.consumeClick(skillId)) {
                    // ===== 木棍工具占位（2026-09-08）：总开关只占/还原木棍手势，无"已学"门槛 =====
                    if (Skills.isStickTool(skillId)) {
                        boolean now = !stickToolOnClient;
                        stickToolOnClient = now;
                        org.zifeng.skilltree.network.ModNetwork.sendToServer(new org.zifeng.skilltree.network.StickToolC2SPacket(0, 0));
                        continue;
                    }
                    // 无需开关的技能不响应开关（规范 v1.0：当前全部技能都有开关，未来常驻被动在此登记）
                    if (!Skills.isTogglable(skillId)) {
                        continue;
                    }
                    // 已学才触发（光环技能走 auraLearnedCache，其余技能走 allSkillsLearnedCache）
                    boolean learned = auraLearnedCache.getOrDefault(skillId, Boolean.FALSE)
                            || allSkillsLearnedCache.getOrDefault(skillId, Boolean.FALSE);
                    if (learned) {
                        boolean now = !auraToggles.getOrDefault(skillId, Boolean.TRUE);
                        auraToggles.put(skillId, now);
                        org.zifeng.skilltree.network.ModNetwork.sendToServer(new SetSkillToggleC2SPacket(skillId, now));
                    }
                }
            }
            // 第二快捷键（2026-08-13 需求）：光环技能=循环目标模式；可调等级技能=循环生效等级（0→已学等级）
            // ⚠️ 2026-09-07 串联逻辑：子1级（开关键）未激活（技能关闭）→ 子2级（模式键）无功能，直接跳过不发包
            for (String skillId : org.zifeng.skilltree.client.SkillKeyBinds.allLevelBinds().keySet()) {
                if (org.zifeng.skilltree.client.SkillKeyBinds.consumeLevelClick(skillId)) {
                    // ===== 木棍工具占位（2026-09-08）：模式键 = 在【已解锁模式】间循环（BIND/RANGE/放置/挖掘/攻击/防护）。
                    // 未学对应技能的模式跳过；不受总开关门控（OFF 只是还原木棍，模式保留/可切）。 =====
                    if (Skills.isStickTool(skillId)) {
                        stickToolModeClient = nextUnlockedStickMode(stickToolModeClient);
                        org.zifeng.skilltree.network.ModNetwork.sendToServer(new org.zifeng.skilltree.network.StickToolC2SPacket(1, stickToolModeClient));
                        feedbackStickMode(stickToolModeClient); // 2026-09-08：切换后立即显示对应功能
                        continue;
                    }
                    boolean learned = auraLearnedCache.getOrDefault(skillId, Boolean.FALSE)
                            || allSkillsLearnedCache.getOrDefault(skillId, Boolean.FALSE);
                    if (!learned) {
                        continue;
                    }
                    if (!isToggleOnClient(skillId)) {
                        continue; // 子1级未激活：技能关闭 → 模式键无功能（不发包省性能）
                    }
                    // 子2 模式循环（hasModeCycle）：搬运术 0自动⇄1手动、敌我 0-2、天气 0晴-2雷暴
                    // ⚠️ 2026-09-07 规范 v1.0：统一读 Skills 注册表 getModeCount/类型分派
                    if (Skills.hasModeCycle(skillId)) {
                        int count = Skills.getModeCount(skillId);
                        if (count > 1) {
                            if (Skills.AURA_WEATHER.equals(skillId)) {
                                // 晴空环天气：走全局天气模式通道
                                int next = (weatherModeClient + 1) % 3;
                                weatherModeClient = next;
                                org.zifeng.skilltree.network.ModNetwork.sendToServer(new org.zifeng.skilltree.network.WeatherModeC2SPacket(next));
                            } else {
                                // 敌我目标(3态)/搬运(2态)：走 auraTargetModes（服务端按技能 clamp）
                                int cur = auraTargetModes.getOrDefault(skillId, 0);
                                int next = (cur + 1) % count;
                                auraTargetModes.put(skillId, next);
                                org.zifeng.skilltree.network.ModNetwork.sendToServer(new org.zifeng.skilltree.network.AuraTargetC2SPacket(skillId, next));
                            }
                            continue;
                        }
                    }
                    // 可调等级技能：生效等级循环（2026-08-13 需求：支持步进 + Alt 反向）
                    //  按键：+1 级；Shift：+10；Ctrl+Shift：+100；Alt：反向（-1/-10/-100）；到界回 0
                    int learnedPoints = learnedPointsOf(skillId);
                    if (learnedPoints <= 0) {
                        continue;
                    }
                    boolean altDown = net.minecraft.client.gui.screens.Screen.hasAltDown();
                    int step;
                    if (net.minecraft.client.gui.screens.Screen.hasShiftDown() && net.minecraft.client.gui.screens.Screen.hasControlDown()) {
                        step = 100;
                    } else if (net.minecraft.client.gui.screens.Screen.hasShiftDown()) {
                        step = 10;
                    } else {
                        step = 1;
                    }
                    int current = activeLevelOf(skillId);
                    int nextLevel;
                    if (altDown) {
                        // Alt：反向（递减；到 0 回到已学上限）
                        nextLevel = current <= 0 ? learnedPoints : Math.max(0, current - step);
                    } else {
                        // 正向（递增；到已学上限回 0）
                        nextLevel = current >= learnedPoints ? 0 : Math.min(learnedPoints, current + step);
                    }
                    org.zifeng.skilltree.network.ModNetwork.sendToServer(new org.zifeng.skilltree.network.SetSkillLevelC2SPacket(skillId, nextLevel));
                }
            }
        }
        while (ModKeyBindings.OPEN_SKILL_TREE.consumeClick()) {
            // 客户端乐观打开技能树界面（空数据），服务端回发数据包后 updateData 填充
            Minecraft mc = Minecraft.getInstance();
            if (!(mc.screen instanceof SkillTreeScreen)) {
                mc.setScreen(new SkillTreeScreen(0, Map.of(), Map.of(), Map.of(), true, Map.of()));
            }
            org.zifeng.skilltree.network.ModNetwork.sendToServer(new org.zifeng.skilltree.network.OpenSkillTreeC2SPacket(true));
        }
        // 功能触发键（2026-09-07 第三类快捷键）：主动技在场景内按一下触发一次。
        // ⚠️ 与开关/循环键不同：本检测块不要求 screen == null —— 容器 GUI 打开时也要能触发
        //（子枫的搬运术·手动模式就是"打开容器后按键搬运"）。为避免吞掉聊天/技能树输入，
        // 仅当屏幕为 null（正常游戏）或 AbstractContainerScreen（容器 GUI）时才响应。
        // ⚠️ 2026-09-07 串联逻辑：子1级（开关键）未激活（技能关闭）→ 子3级（触发键）无功能；
        //    且子3级需当前模式=手动（子2 切到手动）才触发（搬运术三级闸门）。
        net.minecraft.client.gui.screens.Screen curScreen = Minecraft.getInstance().screen;
        boolean noScreen = curScreen == null;
        if (noScreen || curScreen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen) {
            for (String skillId : org.zifeng.skilltree.client.SkillKeyBinds.allTriggerBinds().keySet()) {
                if (org.zifeng.skilltree.client.SkillKeyBinds.consumeTriggerClick(skillId)) {
                    boolean learned = auraLearnedCache.getOrDefault(skillId, Boolean.FALSE)
                            || allSkillsLearnedCache.getOrDefault(skillId, Boolean.FALSE);
                    if (!learned) {
                        continue;
                    }
                    if (!isToggleOnClient(skillId)) {
                        continue; // 子1级未激活：技能关闭 → 触发键无功能（不发包省性能）
                    }
                    // 屏幕穿透门控：技能声明"容器 GUI 可触发"才在屏幕打开时继续；否则仅无屏时继续
                    if (!noScreen && !Skills.canTriggerInScreen(skillId)) {
                        continue;
                    }
                    // 三级闸门：搬运术触发需当前模式=手动（自动模式由开箱事件驱动）；闪现无模式限制
                    if (Skills.isContainerHaul(skillId)
                            && auraTargetModes.getOrDefault(skillId, 0) != 1) {
                        continue;
                    }
                    // 主动触发（读 Skills.isTriggerBindable 登记，按技能派发）
                    if (Skills.isContainerHaul(skillId)) {
                        org.zifeng.skilltree.network.ModNetwork.sendToServer(new org.zifeng.skilltree.network.ContainerHaulC2SPacket());
                    } else if (Skills.BLINK.equals(skillId)) {
                        // 闪现（2026-09-07 规范改造）：子3触发键 = 按下传送一次（服务端冷却 2 tick 防连点）
                        org.zifeng.skilltree.network.ModNetwork.sendToServer(new org.zifeng.skilltree.network.BlinkC2SPacket());
                    } else if (Skills.MACHINE_ZONE_PLACE.equals(skillId)) {
                        // 机械共鸣·选区放置：按触发键把整区填满（2026-09-08）
                        org.zifeng.skilltree.network.ModNetwork.sendToServer(new org.zifeng.skilltree.network.ZoneC2SPacket(10, "", 0, 0, 0, 0, 0, 0));
                    } else if (Skills.MACHINE_ZONE_EXCAVATE.equals(skillId)) {
                        // 机械共鸣·选区挖掘：按触发键瞬间挖空全区（2026-09-08）
                        org.zifeng.skilltree.network.ModNetwork.sendToServer(new org.zifeng.skilltree.network.ZoneC2SPacket(11, "", 0, 0, 0, 0, 0, 0));
                    }
                }
            }
        }
        // 2026-08-13：原版 设置→控制 只保留打开技能树；
        // 光环总开关/目标模式/磁铁/光环锁定/光环独立开关的原版快捷键全部移除，
        // 统一改由技能树界面内为每个技能绑定独立开关快捷键（SkillKeyBinds，见上方循环）。
    }

    /** 各光环目标模式客户端缓存（2026-08-13 需求：每个光环独立，技能ID → 0/1/2） */
    private static final Map<String, Integer> auraTargetModes = new HashMap<>();

    /** 由服务端回发的技能数据校准各光环目标模式 */
    public static void setAuraTargetModes(Map<String, Integer> modes) {
        if (modes == null) {
            return;
        }
        auraTargetModes.clear();
        auraTargetModes.putAll(modes);
    }

    /** 晴空环天气模式客户端缓存（2026-08-27：0=晴 1=雨 2=雷暴） */
    private static int weatherModeClient = 0;

    /** 由服务端回发的技能数据校准晴空环天气模式 */
    public static void setWeatherModeClient(int mode) {
        weatherModeClient = Math.max(0, Math.min(2, mode));
    }

    /** 晴空环当前天气模式（客户端缓存，供界面显示） */
    public static int getWeatherModeClient() {
        return weatherModeClient;
    }
    /** 由服务端回发的技能数据校准光环总开关（供圆环渲染器使用） */
    public static void setAuraEnabledClient(boolean auraEnabled) {
        auraEnabledClient = auraEnabled;
    }

    /** 光环总开关是否开启（客户端缓存） */
    public static boolean isAuraEnabledClient() {
        return auraEnabledClient;
    }

    /** 光环是否有攻击能力（2026-08-15 需求：只跟伤害光环，速度不决定是否攻击；渲染圆环用） */
    public static boolean isAuraAttackEnabled() {
        return auraDamageLearned && auraToggles.getOrDefault(Skills.AURA_DAMAGE, Boolean.TRUE);
    }

    /** 磁力光环是否开启（已学习且开关开启，渲染蓝色圆环用） */
    public static boolean isMagnetEnabledClient() {
        return magnetLearnedClient && auraToggles.getOrDefault(Skills.AURA_MAGNET, Boolean.FALSE);
    }

    /** 磁力光环是否已学习（仅已学判定，不受开关影响——RANGE 工具模块配置屏蔽区用，2026-09-08） */
    public static boolean isMagnetLearnedClientOnly() {
        return magnetLearnedClient;
    }

    /** 由服务端回发的技能数据校准磁力光环已学状态 */
    public static void setMagnetLearnedClient(boolean learned) {
        magnetLearnedClient = learned;
    }
    /** 由服务端回发的技能数据校准光环技能开关缓存 */
    public static void updateAuraToggles(Map<String, Boolean> toggles) {
        if (toggles == null) {
            return;
        }
        for (String skillId : Skills.AURA_SKILLS) {
            auraToggles.put(skillId, toggles.getOrDefault(skillId, Boolean.TRUE));
        }
        // 技能开关缓存：由 updateAuraLearned 用 allTogglesCache 计算（已学 && 开启），这里只存全量开关
        // 全技能开关缓存（渲染光束隐藏等用）
        allTogglesCache.clear();
        allTogglesCache.putAll(toggles);
    }

    /** 由服务端回发的技能数据校准光环技能已学状态（圆环渲染防重置残留 + 未学快捷键不触发） */
    public static void updateAuraLearned(Map<String, Integer> learnedSkills) {
        if (learnedSkills == null) {
            return;
        }
        auraDamageLearned = learnedSkills.getOrDefault(Skills.AURA_DAMAGE, 0) > 0;
        for (String skillId : Skills.AURA_SKILLS) {
            auraLearnedCache.put(skillId, learnedSkills.getOrDefault(skillId, 0) > 0);
        }
        // 全技能已学缓存（2026-08-13：任意技能可绑定独立开关快捷键）
        allSkillsLearnedCache.clear();
        allSkillsLevelsCache.clear();
        for (String skillId : Skills.ALL_SKILLS) {
            int lv = learnedSkills.getOrDefault(skillId, 0);
            allSkillsLearnedCache.put(skillId, lv > 0);
            allSkillsLevelsCache.put(skillId, lv);
        }
    }

    /** 由服务端回发的技能数据校准生效等级缓存（2026-08-13 第二快捷键循环等级用） */
    public static void updateActiveLevels(Map<String, Integer> activeLevels) {
        if (activeLevels == null) {
            return;
        }
        allActiveLevelsCache.clear();
        allActiveLevelsCache.putAll(activeLevels);
    }

    /** 是否有光环快捷键未绑定（2026-08-13 起原版快捷键全部移除，技能开关统一在技能树界面绑定 → 恒 false 不再提示） */
    public static boolean hasUnboundAuraKeys() {
        return false;
    }

    /** 技能客户端状态：是否已学且开启（供客户端渲染/行为判断，服务端 S2CPacket 校准；2026-08-13 万物挖掘用） */
    public static boolean isSkillEnabledClient(String skillId) {
        return allSkillsLearnedCache.getOrDefault(skillId, Boolean.FALSE)
                && allTogglesCache.getOrDefault(skillId, Boolean.TRUE);
    }
}
