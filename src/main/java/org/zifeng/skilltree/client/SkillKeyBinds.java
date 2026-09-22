package org.zifeng.skilltree.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import org.zifeng.skilltree.SkillTreeMod;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

/**
 * 技能独立开关快捷键绑定 + 等级/目标循环快捷键 + 技能树界面位置/缩放持久化（客户端本地，存 config 目录 JSON）。
 * <ul>
 *   <li>每个技能可绑定两个键：① 开关快捷键（默认空键）；② 等级/目标循环快捷键（光环=循环目标模式，可调等级技能=循环生效等级）</li>
 *   <li>技能树界面退出时记录 panX/panY/scale，下次打开恢复到上次位置（2026-08-13 需求）</li>
 *   <li>存储文件：config/zifeng_s_custom_skill_tree_client.json</li>
 * </ul>
 */
public class SkillKeyBinds {

    /** 技能ID → 开关按键（KEYSYM，未绑定则无条目） */
    private static final Map<String, InputConstants.Key> BINDS = new HashMap<>();
    /** 技能ID → 等级/目标循环按键（2026-08-13 新增第二快捷键：光环循环目标模式，可调等级技能循环生效等级） */
    private static final Map<String, InputConstants.Key> LEVEL_BINDS = new HashMap<>();
    /** 技能ID → 功能触发按键（2026-09-07 新增第三类快捷键：子枫的搬运术手动搬运等主动技；容器 GUI 打开时也可响应） */
    private static final Map<String, InputConstants.Key> TRIGGER_BINDS = new HashMap<>();
    /** 技能树界面 panX / panY / scale（上次退出时的状态） */
    private static double lastPanX = 0;
    private static double lastPanY = 0;
    /** 默认缩放 0.4（2026-08-25：八列技能树更宽，0.4 初始能看到全部列） */
    private static double lastScale = 0.4;
    /** 技能点 HUD 位置偏移（2026-08-25：持久化，重启不重置；基准点 = 用户测试调好的位置，默认 0,0） */
    private static int hudOffsetX = 0;
    private static int hudOffsetY = 0;
    /** 技能点 HUD 显示开关（★ 2026-09-22：从 SkillPointHudRenderer 移入以便持久化） */
    private static boolean hudVisible = true;
    /** 真实血量数字显示开关（2026-09-11：HUD 设置里可关；在血条左侧显示真实生命值） */
    private static boolean hudHealthNumber = true;
    /** 伤害吸收数字显示开关（2026-09-11：HUD 设置里可关；吸收行在生命行上方） */
    private static boolean hudAbsorptionNumber = true;
    /** 护甲数字显示开关（2026-09-11：HUD 设置里可关；超阈值时在护甲条左侧显示） */
    private static boolean hudArmorNumber = true;
    /** 满值数字显示开关（2026-09-11：默认<b>关</b>；开后在当前值后追加 "/ 满值"）
     * <p>生命/吸收/护甲三个数字<b>共用</b>此开关（用户要求） */
    private static boolean hudShowMaxValue = false;
    /**
     * 属性面板数据源（2026-09-11 新增面板内 UI 开关）：
     * <p>{@code null} = 玩家未曾用面板按钮改过 → 跟随配置项 {@code panelUseVanillaAttributes}（默认 true）；
     * 非 null = 玩家点过面板按钮 → 以此为准。
     * <p>用客户端本地持久化（而非直接改 TOML），避免与服务端 COMMON 配置同步冲突，
     * 且与其它 HUD 开关（血量/吸收/护甲数字）的存储方式保持一致。
     */
    private static Boolean panelUseVanillaAttr = null;
    /** 子界面位置（2026-09-01：子界面标识 → [x, y] 面板左上角，持久化拖动结果） */
    private static final Map<String, int[]> SUB_SCREEN_POS = new HashMap<>();
    /**
     * 技能树界面尺寸【相对基准的偏移量】（★ 2026-09-21 新增，设置子界面可调）。
     *
     * <p><b>0 = 基准</b>（= 显示器 1920×1080 + 游戏 GUI Scale 3 的观感）；
     * {@code +1} 加大 1%，{@code -1} 缩小 1%；范围 {@code -50 ~ +100}
     * （即 50% ~ 200%）。
     * <p><b>客户端本地存储、不跟存档</b>（用户理由：一个玩家基本一台显示器）。
     * <p>⚠️ 目前仅保存设置值，真正的缩放接线待用户确认后实施。
     */
    private static int skillTreeGuiOffset = 0;
    /** 技能树界面缩放模式：true=自适应屏幕，false=手动偏移。 */
    private static boolean skillTreeGuiAdaptive = true;
    /** 技能树上次选中的类别（客户端本地视图状态，0~9）。 */
    private static int skillTreeSelectedCategory = 0;
    /** 技能树上次的技能列表纵向滚动位置（客户端本地视图状态）。 */
    private static int skillTreeScrollY = 0;
    /** 技能树上次的类别栏横向滚动位置（客户端本地视图状态）。 */
    private static int skillTreeCatScrollX = 0;
    private static final int SKILL_TREE_CATEGORY_COUNT = 10;

    private static boolean loaded = false;

    // ============ 开关键存取 ============

    public static InputConstants.Key getKey(String skillId) {
        return BINDS.get(skillId);
    }

    public static boolean hasKey(String skillId) {
        return BINDS.containsKey(skillId);
    }

    /** 设置绑定（key 为 null/UNKNOWN 视为清除） */
    public static void setKey(String skillId, InputConstants.Key key) {
        if (key == null || key == InputConstants.UNKNOWN) {
            BINDS.remove(skillId);
        } else {
            BINDS.put(skillId, key);
        }
        invalidateViews();
        save();
    }

    /** 清除绑定 */
    public static void clearKey(String skillId) {
        BINDS.remove(skillId);
        invalidateViews();
        save();
    }

    // ============ 等级/目标循环键存取（2026-08-13 新增） ============

    public static InputConstants.Key getLevelKey(String skillId) {
        return LEVEL_BINDS.get(skillId);
    }

    public static boolean hasLevelKey(String skillId) {
        return LEVEL_BINDS.containsKey(skillId);
    }

    /** 设置等级/目标循环键（key 为 null/UNKNOWN 视为清除） */
    public static void setLevelKey(String skillId, InputConstants.Key key) {
        if (key == null || key == InputConstants.UNKNOWN) {
            LEVEL_BINDS.remove(skillId);
        } else {
            LEVEL_BINDS.put(skillId, key);
        }
        invalidateViews();
        save();
    }

    public static void clearLevelKey(String skillId) {
        LEVEL_BINDS.remove(skillId);
        invalidateViews();
        save();
    }

    // ============ 功能触发键存取（2026-09-07 新增第三类快捷键：主动技能触发，如搬运术手动搬运） ============

    public static InputConstants.Key getTriggerKey(String skillId) {
        return TRIGGER_BINDS.get(skillId);
    }

    public static boolean hasTriggerKey(String skillId) {
        return TRIGGER_BINDS.containsKey(skillId);
    }

    /** 设置功能触发键（key 为 null/UNKNOWN 视为清除） */
    public static void setTriggerKey(String skillId, InputConstants.Key key) {
        if (key == null || key == InputConstants.UNKNOWN) {
            TRIGGER_BINDS.remove(skillId);
        } else {
            TRIGGER_BINDS.put(skillId, key);
        }
        invalidateViews();
        save();
    }

    public static void clearTriggerKey(String skillId) {
        TRIGGER_BINDS.remove(skillId);
        invalidateViews();
        save();
    }

    /** 上次 tick 各键按下状态（边沿检测：按下瞬间返回 true 一次） */
    private static final java.util.Set<String> lastPressed = new java.util.HashSet<>();
    /** 等级键上次 tick 按下状态（独立，避免与开关键状态混淆） */
    private static final java.util.Set<String> lastLevelPressed = new java.util.HashSet<>();
    /** 触发键上次 tick 按下状态（独立边沿检测，2026-09-07） */
    private static final java.util.Set<String> lastTriggerPressed = new java.util.HashSet<>();

    /** 边沿检测：该技能绑定键本 tick 是否刚按下（自动更新状态） */
    public static boolean consumeClick(String skillId) {
        InputConstants.Key key = BINDS.get(skillId);
        if (key == null || key.getValue() < 0) {
            return false;
        }
        boolean down = com.mojang.blaze3d.platform.InputConstants.isKeyDown(
                Minecraft.getInstance().getWindow().getWindow(), key.getValue());
        boolean prev = lastPressed.contains(skillId);
        if (down && !prev) {
            lastPressed.add(skillId);
            return true;
        }
        if (!down) {
            lastPressed.remove(skillId);
        }
        return false;
    }

    /** 等级/目标循环键边沿检测（2026-08-13 新增） */
    public static boolean consumeLevelClick(String skillId) {
        InputConstants.Key key = LEVEL_BINDS.get(skillId);
        if (key == null || key.getValue() < 0) {
            return false;
        }
        boolean down = com.mojang.blaze3d.platform.InputConstants.isKeyDown(
                Minecraft.getInstance().getWindow().getWindow(), key.getValue());
        boolean prev = lastLevelPressed.contains(skillId);
        if (down && !prev) {
            lastLevelPressed.add(skillId);
            return true;
        }
        if (!down) {
            lastLevelPressed.remove(skillId);
        }
        return false;
    }

    /** 触发键边沿检测：该技能触发键本 tick 是否刚按下（自动更新状态）
     *  ⚠️ 2026-09-07：触发键【容器 GUI 打开时也要响应】（手动搬运依赖此场景），
     *  与开关键/循环键的 screen==null 限制无关，单独调用此方法即可。 */
    public static boolean consumeTriggerClick(String skillId) {
        InputConstants.Key key = TRIGGER_BINDS.get(skillId);
        if (key == null || key.getValue() < 0) {
            return false;
        }
        boolean down = com.mojang.blaze3d.platform.InputConstants.isKeyDown(
                Minecraft.getInstance().getWindow().getWindow(), key.getValue());
        boolean prev = lastTriggerPressed.contains(skillId);
        if (down && !prev) {
            lastTriggerPressed.add(skillId);
            return true;
        }
        if (!down) {
            lastTriggerPressed.remove(skillId);
        }
        return false;
    }

    /** 按住检测：返回该技能绑定键当前是否被按住 */
    public static boolean isKeyDown(String skillId) {
        InputConstants.Key key = BINDS.get(skillId);
        if (key == null || key.getValue() < 0) {
            return false;
        }
        return com.mojang.blaze3d.platform.InputConstants.isKeyDown(
                Minecraft.getInstance().getWindow().getWindow(), key.getValue());
    }

    /** 缓存视图（2026-08-27 性能优化）：allBinds/allLevelBinds 每 tick 调用，原 Map.copyOf 每 tick 分配新 Map */
    private static volatile Map<String, InputConstants.Key> bindsView;
    private static volatile Map<String, InputConstants.Key> levelBindsView;
    private static volatile Map<String, InputConstants.Key> triggerBindsView; // 功能触发键视图（2026-09-07）

    private static void invalidateViews() {
        bindsView = null;
        levelBindsView = null;
        triggerBindsView = null;
    }

    public static Map<String, InputConstants.Key> allBinds() {
        Map<String, InputConstants.Key> v = bindsView;
        if (v == null) {
            v = Map.copyOf(BINDS);
            bindsView = v;
        }
        return v;
    }

    public static Map<String, InputConstants.Key> allLevelBinds() {
        Map<String, InputConstants.Key> v = levelBindsView;
        if (v == null) {
            v = Map.copyOf(LEVEL_BINDS);
            levelBindsView = v;
        }
        return v;
    }

    public static Map<String, InputConstants.Key> allTriggerBinds() {
        Map<String, InputConstants.Key> v = triggerBindsView;
        if (v == null) {
            v = Map.copyOf(TRIGGER_BINDS);
            triggerBindsView = v;
        }
        return v;
    }

    public static double getLastPanX() {
        return lastPanX;
    }

    public static double getLastPanY() {
        return lastPanY;
    }

    public static double getLastScale() {
        return lastScale;
    }

    public static void saveViewState(double panX, double panY, double scale) {
        lastPanX = panX;
        lastPanY = panY;
        lastScale = scale;
        save();
    }

    // ============ 技能点 HUD 位置偏移持久化（2026-08-25） ============

    public static int getHudOffsetX() {
        return hudOffsetX;
    }

    public static int getHudOffsetY() {
        return hudOffsetY;
    }

    public static void setHudOffset(int x, int y) {
        hudOffsetX = x;
        hudOffsetY = y;
        save();
    }

    // ============ 技能点 HUD 显示开关（★ 2026-09-22 新增持久化） ============

    /**
     * 技能点 HUD 是否显示。
     *
     * <p>★ 2026-09-22 修复用户反馈「HUD 调整里关掉后重进游戏又出现了」：
     * 此前这个开关只存在 {@code SkillPointHudRenderer} 的静态字段里 ——
     * <b>既不写盘也不读盘</b>，重启后静态字段回到默认值 {@code true} → HUD 又显示。
     * 现在挪到本类（与其它 HUD 开关同一套持久化机制）。
     */
    public static boolean isHudVisible() {
        load();
        return hudVisible;
    }

    /** 设置技能点 HUD 显示开关（立即落盘） */
    public static void setHudVisible(boolean on) {
        hudVisible = on;
        save();
    }

    // ============ 血条数字显示开关（2026-09-11：HUD 设置面板可关） ============

    /** 是否显示真实血量数字 */
    public static boolean isHudHealthNumber() {
        return hudHealthNumber;
    }

    public static void setHudHealthNumber(boolean on) {
        hudHealthNumber = on;
        save();
    }

    /** 是否显示伤害吸收数字 */
    public static boolean isHudAbsorptionNumber() {
        return hudAbsorptionNumber;
    }

    public static void setHudAbsorptionNumber(boolean on) {
        hudAbsorptionNumber = on;
        save();
    }

    /** 是否显示护甲数字 */
    public static boolean isHudArmorNumber() {
        return hudArmorNumber;
    }

    public static void setHudArmorNumber(boolean on) {
        hudArmorNumber = on;
        save();
    }

    /** 是否显示满值（生命/吸收/护甲三个数字共用；默认关） */
    public static boolean isHudShowMaxValue() {
        return hudShowMaxValue;
    }

    // ============ 技能树界面尺寸（★ 2026-09-21 设置子界面可调） ============

    /**
     * 技能树界面尺寸偏移量（{@code -50~+100}，<b>0 = 基准</b>）。
     * <p>⚠️ 目前仅保存设置值，尚未接入真正的缩放渲染。
     */
    public static int getSkillTreeGuiOffset() {
        load();
        return skillTreeGuiOffset;
    }

    /** 设置技能树界面尺寸偏移量（自动钳制到 -50~+100） */
    public static void setSkillTreeGuiOffset(int off) {
        skillTreeGuiOffset = Math.max(-50, Math.min(100, off));
        save();
    }

    public static boolean isSkillTreeGuiAdaptive() {
        load();
        return skillTreeGuiAdaptive;
    }

    public static void setSkillTreeGuiAdaptive(boolean adaptive) {
        skillTreeGuiAdaptive = adaptive;
        save();
    }

    /**
     * 获取技能树上次关闭时的视图状态：[类别索引, 技能列表纵向滚动, 类别栏横向滚动]。
     *
     * <p>只保存视图位置，不保存搜索关键词；搜索是进入本次界面后的临时状态。
     */
    public static int[] getSkillTreeViewState() {
        load();
        return new int[]{skillTreeSelectedCategory, skillTreeScrollY, skillTreeCatScrollX};
    }

    /** 保存技能树视图状态（滚动上限由界面按当前窗口尺寸再次钳制）。 */
    public static void setSkillTreeViewState(int category, int scrollY, int catScrollX) {
        skillTreeSelectedCategory = Math.max(0, Math.min(SKILL_TREE_CATEGORY_COUNT - 1, category));
        skillTreeScrollY = Math.max(0, scrollY);
        skillTreeCatScrollX = Math.max(0, catScrollX);
        save();
    }

    public static void setHudShowMaxValue(boolean on) {
        hudShowMaxValue = on;
        save();
    }

    // ============ 属性面板数据源开关（2026-09-11：面板标题栏按钮） ============

    /**
     * 属性面板是否读取<b>原版实时属性值</b>（含装备/药水/其他模组加成）。
     * <p>优先级：玩家在面板里点过的值 &gt; 配置项 {@code panelUseVanillaAttributes}。
     */
    public static boolean isPanelUseVanillaAttr() {
        return panelUseVanillaAttr != null
                ? panelUseVanillaAttr
                : org.zifeng.skilltree.Config.PANEL_USE_VANILLA_ATTR.get();
    }

    /** 切换属性面板数据源（面板按钮调用），返回切换后的值 */
    public static boolean togglePanelUseVanillaAttr() {
        boolean now = !isPanelUseVanillaAttr();
        panelUseVanillaAttr = now;
        save();
        return now;
    }

    /** 是否已被玩家在面板里手动改过（false = 仍跟随配置文件） */
    public static boolean hasPanelUseVanillaAttrOverride() {
        return panelUseVanillaAttr != null;
    }

    // ============ 子界面位置持久化（2026-09-01） ============

    /** 获取子界面上次位置 [x, y]（null = 未保存过，用默认位置） */
    public static int[] getSubScreenPos(String key) {
        load();
        return SUB_SCREEN_POS.get(key);
    }

    /** 保存子界面位置（拖动后调用，重启恢复） */
    public static void setSubScreenPos(String key, int x, int y) {
        SUB_SCREEN_POS.put(key, new int[]{x, y});
        save();
    }

    // ============ 文件读写 ============

    private static File file() {
        return new File(Minecraft.getInstance().gameDirectory, "config/" + SkillTreeMod.MOD_ID + "_client.json");
    }

    /** 加载（幂等，重复调用不重读） */
    public static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        try {
            File f = file();
            if (!f.isFile()) {
                return;
            }
            String json = Files.readString(f.toPath(), StandardCharsets.UTF_8);
            Data data = new Gson().fromJson(json, Data.class);
            if (data == null) {
                return;
            }
            BINDS.clear();
            if (data.binds != null) {
                for (Map.Entry<String, String> e : data.binds.entrySet()) {
                    InputConstants.Key key = InputConstants.getKey(e.getValue());
                    if (key != null && key != InputConstants.UNKNOWN) {
                        BINDS.put(e.getKey(), key);
                    }
                }
            }
            LEVEL_BINDS.clear();
            if (data.levelBinds != null) {
                for (Map.Entry<String, String> e : data.levelBinds.entrySet()) {
                    InputConstants.Key key = InputConstants.getKey(e.getValue());
                    if (key != null && key != InputConstants.UNKNOWN) {
                        LEVEL_BINDS.put(e.getKey(), key);
                    }
                }
            }
            TRIGGER_BINDS.clear();
            if (data.triggerBinds != null) {
                for (Map.Entry<String, String> e : data.triggerBinds.entrySet()) {
                    InputConstants.Key key = InputConstants.getKey(e.getValue());
                    if (key != null && key != InputConstants.UNKNOWN) {
                        TRIGGER_BINDS.put(e.getKey(), key);
                    }
                }
            }
            lastPanX = data.panX;
            lastPanY = data.panY;
            lastScale = data.scale > 0 ? data.scale : 0.4;
            // 2026-08-28：加载时 clamp 到新调整范围（X ±400 / Y ±200），防止旧配置残留超范围值
            // ★ 2026-09-22 修复：读到 ±800/±400 —— 与界面允许的调整范围
            //   （SkillPointHudRenderer.adjustOffsetX/Y）保持一致。
            //   旧代码读到 ±400/±200，导致超出 ±400/±200 的位置存进去也读不回来。
            hudOffsetX = Math.max(-800, Math.min(800, data.hudOffsetX));
            hudOffsetY = Math.max(-400, Math.min(400, data.hudOffsetY));
            // 技能点 HUD 显示开关（★ 2026-09-22）：旧配置无此字段 → null → 默认显示
            hudVisible = data.hudVisible == null || data.hudVisible;
            // 血条数字显示开关（2026-09-11；旧配置无此字段 → Gson 给 null → 用包装类型兼容默认开）
            hudHealthNumber = data.hudHealthNumber == null || data.hudHealthNumber;
            hudAbsorptionNumber = data.hudAbsorptionNumber == null || data.hudAbsorptionNumber;
            hudArmorNumber = data.hudArmorNumber == null || data.hudArmorNumber;
            // 满值显示：旧配置无此字段 / null → 默认<b>关闭</b>
            hudShowMaxValue = data.hudShowMaxValue != null && data.hudShowMaxValue;
            // 属性面板数据源：null = 未改过 → 继续跟随配置文件
            panelUseVanillaAttr = data.panelUseVanillaAttr;
            // 技能树界面尺寸偏移量（★ 2026-09-21）：旧配置无此字段 → null → 基准 0；并钳制到合法范围
            //   注：字段名用 Offset（而非早期的 Scale），旧值自动忽略 → 回到基准，无需迁移
            skillTreeGuiOffset = data.skillTreeGuiOffset != null
                    ? Math.max(-50, Math.min(100, data.skillTreeGuiOffset)) : 0;
            skillTreeGuiAdaptive = data.skillTreeGuiAdaptive == null || data.skillTreeGuiAdaptive;
            // 技能树视图状态：旧配置没有这些字段时回到默认类别/顶部；滚动上限由界面按当前尺寸重算
            skillTreeSelectedCategory = data.skillTreeSelectedCategory != null
                    ? Math.max(0, Math.min(SKILL_TREE_CATEGORY_COUNT - 1, data.skillTreeSelectedCategory)) : 0;
            skillTreeScrollY = data.skillTreeScrollY != null ? Math.max(0, data.skillTreeScrollY) : 0;
            skillTreeCatScrollX = data.skillTreeCatScrollX != null ? Math.max(0, data.skillTreeCatScrollX) : 0;
            // 子界面位置（2026-09-01）
            SUB_SCREEN_POS.clear();
            if (data.subScreenPos != null) {
                for (Map.Entry<String, int[]> e : data.subScreenPos.entrySet()) {
                    if (e.getValue() != null && e.getValue().length == 2) {
                        SUB_SCREEN_POS.put(e.getKey(), new int[]{e.getValue()[0], e.getValue()[1]});
                    }
                }
            }
            invalidateViews(); // 首次加载填充 BINDS 后失效视图缓存
        } catch (Exception ignored) {
            // 读取失败（文件损坏等）→ 用默认值，不崩溃
        }
    }

    private static void save() {
        try {
            File f = file();
            if (f.getParentFile() != null && !f.getParentFile().isDirectory()) {
                f.getParentFile().mkdirs();
            }
            Data data = new Data();
            data.binds = new HashMap<>();
            for (Map.Entry<String, InputConstants.Key> e : BINDS.entrySet()) {
                data.binds.put(e.getKey(), e.getValue().getName());
            }
            data.levelBinds = new HashMap<>();
            for (Map.Entry<String, InputConstants.Key> e : LEVEL_BINDS.entrySet()) {
                data.levelBinds.put(e.getKey(), e.getValue().getName());
            }
            data.triggerBinds = new HashMap<>();
            for (Map.Entry<String, InputConstants.Key> e : TRIGGER_BINDS.entrySet()) {
                data.triggerBinds.put(e.getKey(), e.getValue().getName());
            }
            data.panX = lastPanX;
            data.panY = lastPanY;
            data.scale = lastScale;
            data.hudOffsetX = hudOffsetX;
            data.hudOffsetY = hudOffsetY;
            data.hudVisible = hudVisible;
            data.hudHealthNumber = hudHealthNumber;
            data.hudAbsorptionNumber = hudAbsorptionNumber;
            data.hudArmorNumber = hudArmorNumber;
            data.hudShowMaxValue = hudShowMaxValue;
            data.panelUseVanillaAttr = panelUseVanillaAttr;
            data.skillTreeGuiOffset = skillTreeGuiOffset;
            data.skillTreeGuiAdaptive = skillTreeGuiAdaptive;
            data.skillTreeSelectedCategory = skillTreeSelectedCategory;
            data.skillTreeScrollY = skillTreeScrollY;
            data.skillTreeCatScrollX = skillTreeCatScrollX;
            data.subScreenPos = new HashMap<>();
            for (Map.Entry<String, int[]> e : SUB_SCREEN_POS.entrySet()) {
                data.subScreenPos.put(e.getKey(), new int[]{e.getValue()[0], e.getValue()[1]});
            }
            String json = new GsonBuilder().setPrettyPrinting().create().toJson(data);
            Files.writeString(f.toPath(), json, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // 写入失败不崩溃（仅丢失本次绑定）
        }
    }

    /** JSON 数据结构（Gson 映射） */
    private static class Data {
        Map<String, String> binds;
        Map<String, String> levelBinds;
        Map<String, String> triggerBinds; // 功能触发键（2026-09-07）
        double panX;
        double panY;
        double scale = 1.0;
        int hudOffsetX = 0; // 基准点 = 用户测试调好的位置，显示从 0 开始
        int hudOffsetY = 0;
        Boolean hudVisible;          // 技能点 HUD 显示开关（★ 2026-09-22；null=旧配置→显示）
        Boolean hudHealthNumber;     // 真实血量数字开关（2026-09-11；Boolean 可空以兼容旧配置）
        Boolean hudAbsorptionNumber; // 伤害吸收数字开关（2026-09-11）
        Boolean hudArmorNumber;      // 护甲数字开关（2026-09-11）
        Boolean hudShowMaxValue;     // 满值数字开关（2026-09-11；三数字共用，默认关）
        Boolean panelUseVanillaAttr; // 属性面板数据源（2026-09-11；null=跟随配置文件）
        Integer skillTreeGuiOffset;  // 技能树界面尺寸偏移量（★ 2026-09-21；0=基准，null=旧配置→0）
        Boolean skillTreeGuiAdaptive; // 技能树界面缩放模式（null=旧配置→自适应）
        Integer skillTreeSelectedCategory; // 技能树上次选中的类别（旧配置无此字段→0）
        Integer skillTreeScrollY; // 技能树技能列表纵向滚动位置（旧配置无此字段→0）
        Integer skillTreeCatScrollX; // 技能树类别栏横向滚动位置（旧配置无此字段→0）
        Map<String, int[]> subScreenPos; // 子界面位置（2026-09-01）
    }
}
