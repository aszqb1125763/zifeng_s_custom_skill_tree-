package org.zifeng.skilltree.client;

import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
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

    /** 获取凋落物挪移绑定的容器坐标（"维度 x,y,z"，null=未绑定） */
    public static String getLootVacuumBindClient() {
        return lootVacuumBindClient;
    }

    /** 服务端回发校准凋落物挪移绑定容器 */
    public static void setLootVacuumBindClient(String bind) {
        lootVacuumBindClient = bind;
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
        auraTargetModes.clear();
    }

    /** 辅助：已学等级 */
    private static int learnedPointsOf(String skillId) {
        return allSkillsLevelsCache.getOrDefault(skillId, 0);
    }

    /** 辅助：当前生效等级 */
    private static int activeLevelOf(String skillId) {
        return allActiveLevelsCache.getOrDefault(skillId, 0);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        org.zifeng.skilltree.client.SkillKeyBinds.load(); // 确保技能绑定已加载
        // 技能独立开关快捷键（2026-08-13：每个技能可单独绑定，SkillKeyBinds 本地持久化）
        // 按下即切换该技能开关；未学的技能不处理（等服务端校准）
        if (Minecraft.getInstance().screen == null) { // 界面打开时不触发（避免与设置窗口/技能树交互冲突）
            for (String skillId : org.zifeng.skilltree.client.SkillKeyBinds.allBinds().keySet()) {
                if (org.zifeng.skilltree.client.SkillKeyBinds.consumeClick(skillId)) {
                    // 无需开关的技能（时之环/晴空环常驻被动）不响应开关
                    if (!Skills.isTogglable(skillId)) {
                        continue;
                    }
                    // 已学才触发（光环技能走 auraLearnedCache，其余技能走 allSkillsLearnedCache）
                    boolean learned = auraLearnedCache.getOrDefault(skillId, Boolean.FALSE)
                            || allSkillsLearnedCache.getOrDefault(skillId, Boolean.FALSE);
                    if (learned) {
                        boolean now = !auraToggles.getOrDefault(skillId, Boolean.TRUE);
                        auraToggles.put(skillId, now);
                        PacketDistributor.sendToServer(new SetSkillToggleC2SPacket(skillId, now));
                    }
                }
            }
            // 第二快捷键（2026-08-13 需求）：光环技能=循环目标模式；可调等级技能=循环生效等级（0→已学等级）
            for (String skillId : org.zifeng.skilltree.client.SkillKeyBinds.allLevelBinds().keySet()) {
                if (org.zifeng.skilltree.client.SkillKeyBinds.consumeLevelClick(skillId)) {
                    boolean learned = auraLearnedCache.getOrDefault(skillId, Boolean.FALSE)
                            || allSkillsLearnedCache.getOrDefault(skillId, Boolean.FALSE);
                    if (!learned) {
                        continue;
                    }
                    // 光环技能：循环该光环自己的目标模式（0 敌对 → 1 友好 → 2 所有 → 0）
                    if (Skills.AURA_SKILLS.contains(skillId)) {
                        int cur = auraTargetModes.getOrDefault(skillId, 0);
                        int next = (cur + 1) % 3;
                        auraTargetModes.put(skillId, next);
                        PacketDistributor.sendToServer(new org.zifeng.skilltree.network.AuraTargetC2SPacket(skillId, next));
                        continue;
                    }
                    // 晴空环（寰宇法则）：循环天气模式（0 晴 → 1 雨 → 2 雷暴 → 0 晴，2026-08-27）
                    if (Skills.AURA_WEATHER.equals(skillId)) {
                        int next = (weatherModeClient + 1) % 3;
                        weatherModeClient = next;
                        PacketDistributor.sendToServer(new org.zifeng.skilltree.network.WeatherModeC2SPacket(next));
                        continue;
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
                    PacketDistributor.sendToServer(new org.zifeng.skilltree.network.SetSkillLevelC2SPacket(skillId, nextLevel));
                }
            }
        }
        while (ModKeyBindings.OPEN_SKILL_TREE.consumeClick()) {
            // 客户端乐观打开技能树界面（空数据），服务端回发数据包后 updateData 填充
            Minecraft mc = Minecraft.getInstance();
            if (!(mc.screen instanceof SkillTreeScreen)) {
                mc.setScreen(new SkillTreeScreen(0, Map.of(), Map.of(), Map.of(), true, Map.of()));
            }
            PacketDistributor.sendToServer(new OpenSkillTreeC2SPacket(true));
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
